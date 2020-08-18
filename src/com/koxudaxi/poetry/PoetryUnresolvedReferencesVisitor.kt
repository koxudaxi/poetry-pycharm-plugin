// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.koxudaxi.poetry

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.toArray
import com.jetbrains.python.PyCustomType
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.docstrings.DocStringParameterReference
import com.jetbrains.python.documentation.docstrings.DocStringTypeReference
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.PyInspectionsUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.references.PyFromImportNameReference
import com.jetbrains.python.psi.impl.references.PyOperatorReference
import com.jetbrains.python.psi.resolve.ImportedResolveResult
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.sdk.pythonSdk
import one.util.streamex.StreamEx
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.ArrayList

/**
 * This source code is edited by @koxudaxi  (Koudai Aono)
 */
abstract class PoetryUnresolvedReferencesVisitor internal constructor(holder: ProblemsHolder?,
                                                                      session: LocalInspectionToolSession,
                                                                      val ignoredIdentifiers: List<String>?) : PyInspectionVisitor(holder, session) {
    private val myAllImports = Collections.synchronizedSet(HashSet<PyImportedNameDefiner>())
    private val myUsedImports = Collections.synchronizedSet(HashSet<PyImportedNameDefiner>())
    override fun visitPyElement(node: PyElement) {
        val project = node.project
        val sdk = project.pythonSdk ?: return
        if (!isPoetry(project, sdk)) {
            return
        }
        if (node is PyReferenceOwner) {
            val resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(myTypeEvalContext)
            processReference(node, node.getReference(resolveContext))
        } else {
            for (reference in node.references) {
                processReference(node, reference)
            }
        }
    }

    private fun processReference(node: PyElement, reference: PsiReference?) {
        if (!isEnabled(node) || reference == null || reference.isSoft) {
            return
        }
        val guard = getImportErrorGuard(node)
        if (guard != null) {
            return
        }
        if (node is PyQualifiedExpression) {
            val qualifier = node.qualifier
            val name = node.getName()
            if (qualifier != null && name != null && isGuardedByHasattr(qualifier, name)) {
                return
            }
        }
        var target: PsiElement? = null
        val unresolved: Boolean
        if (reference is PsiPolyVariantReference) {
            val resolveResults = reference.multiResolve(false)
            unresolved = resolveResults.isEmpty()
            for (resolveResult in resolveResults) {
                if (target == null && resolveResult.isValidResult) {
                    target = resolveResult.element
                }
                if (resolveResult is ImportedResolveResult) {
                    val definer = resolveResult.definer
                    if (definer != null) {
                        myUsedImports.add(definer)
                    }
                }
            }
        } else {
            target = reference.resolve()
            unresolved = target == null
        }
        if (unresolved) {
            val ignoreUnresolved = ignoreUnresolved(node, reference)
            if (!ignoreUnresolved) {
                val severity = (if (reference is PsiReferenceEx) reference.getUnresolvedHighlightSeverity(myTypeEvalContext) else HighlightSeverity.ERROR)
                        ?: return
                registerUnresolvedReferenceProblem(node, reference, severity)
            }
            // don't highlight unresolved imports as unused
            if (node.parent is PyImportElement) {
                myAllImports.remove(node.parent as PyImportElement)
            }
        }
    }

    private fun registerUnresolvedReferenceProblem(node: PyElement, reference: PsiReference,
                                                   severity: HighlightSeverity) {
        if (reference is DocStringTypeReference) {
            return
        }
        var mSeverity = severity
        var description: String? = null
        val element = reference.element
        val text = element.text
        val rangeInElement = reference.rangeInElement
        var refText = text // text of the part we're working with
        if (rangeInElement.startOffset > 0 && rangeInElement.endOffset > 0) {
            refText = rangeInElement.substring(text)
        }
        val refName = if (element is PyQualifiedExpression) element.referencedName else refText
        // Empty text, nothing to highlight
        if (refName == null || refName.isEmpty()) {
            return
        }
        val qualifiedNames = getCanonicalNames(reference, myTypeEvalContext)
        for (name in qualifiedNames) {
            val canonicalName = name.toString()
            if (ignoredIdentifiers != null) {
                for (ignored in ignoredIdentifiers) {
                    if (ignored.endsWith(PyNames.END_WILDCARD)) {
                        val prefix = ignored.substring(0, ignored.length - PyNames.END_WILDCARD.length)
                        if (canonicalName.startsWith(prefix)) {
                            return
                        }
                    } else if (canonicalName == ignored) {
                        return
                    }
                }
            }
        }
        // Legacy non-qualified ignore patterns
        if (ignoredIdentifiers!!.contains(refName)) {
            return
        }
        val fixes: MutableCollection<LocalQuickFix> = ArrayList()
        if (element is PyReferenceExpression) {
            if (PyNames.COMPARISON_OPERATORS.contains(refName)) {
                return
            }
            if (!element.isQualified) {
                if (PyInspectionsUtil.hasAnyInterruptedControlFlowPaths(element)) {
                    return
                }
            }
            // unqualified:
            // may be module's
            if (PyModuleType.getPossibleInstanceMembers().contains(refName)) {
                return
            }
            // may be a "try: import ..."; not an error not to resolve
            if (PsiTreeUtil.getParentOfType(
                            PsiTreeUtil.getParentOfType(node, PyImportElement::class.java), PyTryExceptStatement::class.java, PyIfStatement::class.java
                    ) != null) {
                mSeverity = HighlightSeverity.WEAK_WARNING
                description = PoetryPsiBundle.message("INSP.module.$0.not.found", refText)
                // TODO: mark the node so that future references pointing to it won't result in a error, but in a warning
            }
        }
        if (reference is PsiReferenceEx && description == null) {
            description = reference.unresolvedDescription
        }
        if (description == null) {
            var markedQualified = false
            if (element is PyQualifiedExpression) {
                // TODO: Add __qualname__ for Python 3.3 to the skeleton of <class 'object'>, introduce a pseudo-class skeleton for
                // <class 'function'>
                if ("__qualname__" == refText && !LanguageLevel.forElement(element).isPython2) {
                    return
                }
                if (PyNames.COMPARISON_OPERATORS.contains(element.referencedName)) {
                    return
                }
            }
            val qualifier = getReferenceQualifier(reference)
            if (qualifier != null) {
                val type = myTypeEvalContext.getType(qualifier)
                if (type != null) {
                    if (ignoreUnresolvedMemberForType(type, reference, refName) || isDeclaredInSlots(type, refName)) {
                        return
                    }
                    markedQualified = true
                }
            }
            if (!markedQualified) {
                ContainerUtil.addAll<LocalQuickFix>(fixes, getAutoImportFixes(node, reference, element)!!)
            }
        }
        val highlightType: ProblemHighlightType
        highlightType = when {
            mSeverity === HighlightSeverity.WARNING -> {
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            }
            mSeverity === HighlightSeverity.ERROR -> {
                ProblemHighlightType.GENERIC_ERROR
            }
            else -> {
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
            }
        }
        ContainerUtil.addAll<LocalQuickFix>(fixes, getInstallPackageQuickFixes(node, reference, refName)!!)
        if (reference is PySubstitutionChunkReference) {
            return
        }
        if (fixes.isEmpty()) {
            return
        }
        description = "Poetry does not manage this package"
        registerProblem(node, description, highlightType, null, rangeInElement, *fixes.toArray(LocalQuickFix.EMPTY_ARRAY))
    }

    private fun isDeclaredInSlots(type: PyType, attrName: String): Boolean {
        return PyTypeUtil.toStream(type)
                .select(PyClassType::class.java)
                .map { obj: PyClassType -> obj.pyClass }
                .flatMap { cls: PyClass -> StreamEx.of(cls).append(cls.getAncestorClasses(myTypeEvalContext)) }
                .nonNull()
                .filter { c: PyClass -> c.isNewStyleClass(myTypeEvalContext) }
                .flatCollection { obj: PyClass -> obj.ownSlots }
                .anyMatch { anObject: String? -> attrName == anObject }
    }

    private fun ignoreUnresolvedMemberForType(type: PyType, reference: PsiReference, name: String): Boolean {
        if (PyTypeChecker.isUnknown(type, myTypeEvalContext)) {
            // this almost always means that we don't know the type, so don't show an error in this case
            return true
        }
        if (type is PyStructuralType && type.isInferredFromUsages) {
            return true
        }
        if (type is PyImportedModuleType) {
            val module = type.importedModule
            if (module.resolve() == null) {
                return true
            }
        }
        if (type is PyCustomType) {
            // Skip custom member types that mimics another class with fuzzy parents
            for (mimic in type.typesToMimic) {
                if (mimic !is PyClassType) {
                    continue
                }
                if (PyUtil.hasUnresolvedAncestors(mimic.pyClass, myTypeEvalContext)) {
                    return true
                }
            }
        }
        if (type is PyClassType) {
            val cls = type.pyClass
            if (PyTypeChecker.overridesGetAttr(cls, myTypeEvalContext)) {
                return true
            }
            if (cls.findProperty(name, true, myTypeEvalContext) != null) {
                return true
            }
            if (PyUtil.hasUnresolvedAncestors(cls, myTypeEvalContext)) {
                return true
            }
            if (isDecoratedAsDynamic(cls, true)) {
                return true
            }
            if (hasUnresolvedDynamicMember(type, reference, name, myTypeEvalContext)) return true
            if (isAwaitOnGeneratorBasedCoroutine(name, reference, cls)) return true
        }
        if (type is PyFunctionTypeImpl) {
            val callable = type.callable
            if (callable is PyFunction &&
                    PyKnownDecoratorUtil.hasUnknownOrUpdatingAttributesDecorator(callable, myTypeEvalContext)) {
                return true
            }
        }
        if (type is PyUnionType) {
            return ContainerUtil.exists(type.members) { member: PyType -> ignoreUnresolvedMemberForType(member, reference, name) }
        }
        if (type is PyModuleType) {
            val module = type.module
            if (module.languageLevel.isAtLeast(LanguageLevel.PYTHON37)) {
                return PyTypeChecker.definesGetAttr(module, myTypeEvalContext)
            }
        }
        for (extension in PyInspectionExtension.EP_NAME.extensionList) {
            if (extension.ignoreUnresolvedMember(type, name, myTypeEvalContext)) {
                return true
            }
        }
        return false
    }

    private fun isDecoratedAsDynamic(cls: PyClass, inherited: Boolean): Boolean {
        if (inherited) {
            if (isDecoratedAsDynamic(cls, false)) {
                return true
            }
            for (base in cls.getAncestorClasses(myTypeEvalContext)) {
                if (base != null && isDecoratedAsDynamic(base, false)) {
                    return true
                }
            }
        } else {
            if (PyKnownDecoratorUtil.hasUnknownDecorator(cls, myTypeEvalContext)) {
                return true
            }
            val docString = cls.docStringValue
            return docString != null && docString.contains("@DynamicAttrs")
        }
        return false
    }

    private fun isAwaitOnGeneratorBasedCoroutine(name: String, reference: PsiReference, cls: PyClass): Boolean {
        if (PyNames.DUNDER_AWAIT == name &&
                reference is PyOperatorReference && PyTypingTypeProvider.GENERATOR == cls.qualifiedName) {
            val receiver = reference.receiver
            if (receiver is PyCallExpression) {
                return PyKnownDecoratorUtil.isResolvedToGeneratorBasedCoroutine((receiver as PyCallExpression?)!!, resolveContext, myTypeEvalContext)
            }
        }
        return false
    }

    abstract fun isEnabled(anchor: PsiElement): Boolean
    private fun ignoreUnresolved(node: PyElement, reference: PsiReference): Boolean {
        var ignoreUnresolved = false
        for (extension in PyInspectionExtension.EP_NAME.extensionList) {
            if (extension.ignoreUnresolvedReference(node, reference, myTypeEvalContext)) {
                ignoreUnresolved = true
                break
            }
        }
        return ignoreUnresolved
    }

    open fun getInstallPackageQuickFixes(node: PyElement,
                                         reference: PsiReference,
                                         refName: String?): Iterable<LocalQuickFix>? {
        return emptyList()
    }

    open fun getAutoImportFixes(node: PyElement?, reference: PsiReference?, element: PsiElement?): Iterable<LocalQuickFix>? {
        return emptyList()
    }

    companion object {
        @JvmField
        val INSPECTION = Key.create<PyInspection>("PoetryUnresolvedReferencesVisitor.inspection")
        private fun getImportErrorGuard(node: PyElement): PyExceptPart? {
            val importStatement = PsiTreeUtil.getParentOfType(node, PyImportStatementBase::class.java)
            if (importStatement != null) {
                val tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart::class.java)
                if (tryPart != null) {
                    val tryExceptStatement = PsiTreeUtil.getParentOfType(tryPart, PyTryExceptStatement::class.java)
                    if (tryExceptStatement != null) {
                        for (exceptPart in tryExceptStatement.exceptParts) {
                            val expr = exceptPart.exceptClass
                            if (expr != null && "ImportError" == expr.name) {
                                return exceptPart
                            }
                        }
                    }
                }
            }
            return null
        }

        private fun isGuardedByHasattr(node: PyElement, name: String): Boolean {
            val nodeName = node.name
            if (nodeName != null) {
                val owner = ScopeUtil.getDeclarationScopeOwner(node, nodeName)
                var e = PsiTreeUtil.getParentOfType(node, PyConditionalStatementPart::class.java, PyConditionalExpression::class.java)
                while (e != null && PsiTreeUtil.isAncestor(owner, e, true)) {
                    val calls = ArrayList<PyCallExpression>()
                    var cond: PyExpression? = null
                    if (e is PyConditionalStatementPart) {
                        cond = e.condition
                    } else if (e is PyConditionalExpression && PsiTreeUtil.isAncestor(e.truePart, node, true)) {
                        cond = e.condition
                    }
                    if (cond is PyCallExpression) {
                        calls.add(cond)
                    }
                    if (cond != null) {
                        val callExpressions = PsiTreeUtil.getChildrenOfType(cond, PyCallExpression::class.java)
                        if (callExpressions != null) {
                            calls.addAll(callExpressions as Collection<PyCallExpression>)
                        }
                        for (call in calls) {
                            val callee = call.callee
                            val args = call.arguments
                            // TODO: Search for `node` aliases using aliases analysis
                            if (callee != null && "hasattr" == callee.name && args.size == 2 && nodeName == args[0].name && args[1] is PyStringLiteralExpression && (args[1] as PyStringLiteralExpression).stringValue == name) {
                                return true
                            }
                        }
                    }
                    e = PsiTreeUtil.getParentOfType(e, PyConditionalStatementPart::class.java)
                }
            }
            return false
        }

        private fun getReferenceQualifier(reference: PsiReference): PyExpression? {
            val element = reference.element
            if (element is PyQualifiedExpression) {
                val qualifier = element.qualifier
                if (qualifier != null) {
                    return qualifier
                }
            }
            if (reference is PyFromImportNameReference) {
                val statement = PsiTreeUtil.getParentOfType(element, PyFromImportStatement::class.java)
                if (statement != null) {
                    return statement.importSource
                }
            }
            return null
        }

        /**
         * Return the canonical qualified names for a reference (even for an unresolved one).
         * If reference is qualified and its qualifier has union type, all possible canonical names will be returned.
         */
        private fun getCanonicalNames(reference: PsiReference, context: TypeEvalContext): List<QualifiedName> {
            val element = reference.element
            val result: MutableCollection<QualifiedName> = SmartList()
            if (reference is PyOperatorReference && element is PyQualifiedExpression) {
                val receiver = reference.receiver
                if (receiver != null) {
                    val type = context.getType(receiver)
                    if (type is PyClassType) {
                        val methodName = element.referencedName
                        ContainerUtil.addIfNotNull(result, extractAttributeQNameFromClassType(methodName, type))
                    }
                }
            } else if (element is PyReferenceExpression) {
                val qualifier = element.qualifier
                val exprName = element.name
                if (exprName != null) {
                    if (qualifier != null) {
                        val qualifierType = context.getType(qualifier)
                        PyTypeUtil.toStream(qualifierType)
                                .map<QualifiedName> { type: PyType? ->
                                    if (type is PyClassType) {
                                        return@map extractAttributeQNameFromClassType(exprName, type)
                                    } else if (type is PyModuleType) {
                                        val file = type.module
                                        val name = QualifiedNameFinder.findCanonicalImportPath(file, element)
                                        if (name != null) {
                                            return@map name.append(exprName)
                                        }
                                    } else if (type is PyImportedModuleType) {
                                        val module = type.importedModule
                                        val resolved = module.resolve()
                                        if (resolved != null) {
                                            val path = QualifiedNameFinder.findCanonicalImportPath(resolved, element)
                                            if (path != null) {
                                                return@map path.append(exprName)
                                            }
                                        }
                                    } else if (type is PyFunctionType) {
                                        val callable = type.callable
                                        val callableName = callable.name
                                        if (callableName != null) {
                                            val path = QualifiedNameFinder.findCanonicalImportPath(callable, element)
                                            if (path != null) {
                                                return@map path.append(QualifiedName.fromComponents(callableName, exprName))
                                            }
                                        }
                                    }
                                    null
                                }
                                .nonNull()
                                .into<MutableCollection<QualifiedName>>(result)
                    } else {
                        val parent = element.getParent()
                        if (parent is PyImportElement) {
                            val importStmt = PsiTreeUtil.getParentOfType(parent, PyImportStatementBase::class.java)
                            if (importStmt is PyImportStatement) {
                                ContainerUtil.addIfNotNull(result, QualifiedName.fromComponents(exprName))
                            } else if (importStmt is PyFromImportStatement) {
                                val resolved: PsiElement? = importStmt.resolveImportSource()
                                if (resolved != null) {
                                    val path = QualifiedNameFinder.findCanonicalImportPath(resolved, element)
                                    if (path != null) {
                                        ContainerUtil.addIfNotNull(result, path.append(exprName))
                                    }
                                }
                            }
                        } else {
                            val path = QualifiedNameFinder.findCanonicalImportPath(element, element)
                            if (path != null) {
                                ContainerUtil.addIfNotNull(result, path.append(exprName))
                            }
                        }
                    }
                }
            } else if (reference is DocStringParameterReference) {
                ContainerUtil.addIfNotNull(result, QualifiedName.fromDottedString(reference.getCanonicalText()))
            }
            return result.toList()
        }

        private fun extractAttributeQNameFromClassType(exprName: String?, type: PyClassType): QualifiedName? {
            val name = type.classQName
            return if (name != null) {
                QualifiedName.fromDottedString(name).append(exprName)
            } else null
        }

        private fun hasUnresolvedDynamicMember(type: PyClassType,
                                               reference: PsiReference,
                                               name: String, typeEvalContext: TypeEvalContext): Boolean {
            val types: MutableList<PyClassType> = ArrayList(listOf(type))
            types.addAll(type.getAncestorTypes(typeEvalContext).stream().filter { obj: PyClassLikeType? -> PyClassType::class.java.isInstance(obj) }.map { obj: PyClassLikeType? -> PyClassType::class.java.cast(obj) }.collect(Collectors.toList()))
            for (typeToCheck in types) {
                for (provider in PyClassMembersProvider.EP_NAME.extensionList) {
                    val resolveResult = provider.getMembers(typeToCheck, reference.element, typeEvalContext)
                    for (member in resolveResult) {
                        if (member.name == name) return true
                    }
                }
            }
            return false
        }
    }

}