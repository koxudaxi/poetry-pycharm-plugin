// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.koxudaxi.poetry

import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.ListEditForm
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.util.QualifiedName
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.codeInsight.imports.PythonImportUtils
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.inspections.unresolvedReference.PyPackageAliasesProvider
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.impl.references.PyImportReference
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher
import one.util.streamex.StreamEx
import java.util.*
import javax.swing.JComponent

/**
 * Marks references that fail to resolve. Also tracks unused imports and provides "optimize imports" support.
 * Creator: dcheryasov
 * This source code is edited by @koxudaxi  (Koudai Aono)
 */
class PoetryUnresolvedReferencesInspection : PyInspection() {
    var ignoredIdentifiers: List<String> = ArrayList()
    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor {
        val visitor = Visitor(holder, session, ignoredIdentifiers)
        // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
        val existingVisitor = session.getUserData(KEY)
        if (existingVisitor == null) {
            session.putUserData(KEY, visitor)
        }
        session.putUserData(PoetryUnresolvedReferencesVisitor.INSPECTION, this)
        return visitor
    }

    override fun inspectionFinished(session: LocalInspectionToolSession, holder: ProblemsHolder) {
        session.putUserData(KEY, null)
    }

    override fun createOptionsPanel(): JComponent? {
        val form = ListEditForm("Ignore references", ignoredIdentifiers)
        return form.contentPanel
    }

    class Visitor(holder: ProblemsHolder?, session: LocalInspectionToolSession, ignoredIdentifiers: List<String>?) : PoetryUnresolvedReferencesVisitor(holder, session, ignoredIdentifiers) {
        @Volatile
        private var myIsEnabled: Boolean? = null
        override fun isEnabled(anchor: PsiElement): Boolean {
            if (myIsEnabled == null) {
                val isPyCharm = PlatformUtils.isPyCharm()
                val overridden = overriddenUnresolvedReferenceInspection(anchor.containingFile)
                myIsEnabled = overridden
                        ?: if (PySkeletonRefresher.isGeneratingSkeletons()) {
                            false
                        } else if (isPyCharm) {
                            PythonSdkUtil.findPythonSdk(anchor) != null || PythonRuntimeService.getInstance().isInScratchFile(anchor)
                        } else {
                            true
                        }
            }
            return myIsEnabled as Boolean
        }

        override fun getInstallPackageQuickFixes(node: PyElement,
                                                 reference: PsiReference,
                                                 refName: String?): Iterable<LocalQuickFix>? {
            if (reference is PyImportReference) {
                // TODO: Ignore references in the second part of the 'from ... import ...' expression
                val qname = QualifiedName.fromDottedString(refName!!)
                val components = qname.components
                if (components.isNotEmpty()) {
                    val packageName = components[0]
                    val module = ModuleUtilCore.findModuleForPsiElement(node)
                    val sdk = PythonSdkUtil.findPythonSdk(module)
                    if (module != null && sdk != null && PyPackageUtil.packageManagementEnabled(sdk)) {
                        return StreamEx
                                .of(packageName)
                                .append(PyPsiPackageUtil.PACKAGES_TOPLEVEL.getOrDefault(packageName, emptyList()))
                                .filter { packageName: String? -> PyPIPackageUtil.INSTANCE.isInPyPI(packageName!!) }
                                .flatCollection { pkg: String -> getInstallPackageActions(pkg, module, sdk) }
                    }
                }
            }
            return emptyList()
        }

        override fun getAutoImportFixes(node: PyElement?, reference: PsiReference?, element: PsiElement?): Iterable<LocalQuickFix>? {
            // look in other imported modules for this whole name
            if (!PythonImportUtils.isImportable(element)) {
                return emptyList()
            }
            val result: MutableList<LocalQuickFix> = ArrayList()
            val importFix = PythonImportUtils.proposeImportFix(node, reference)
            if (importFix != null) {
                return ArrayList()
            } else {
                val refName = (if (node is PyQualifiedExpression) node.referencedName else node!!.text)
                        ?: return result
                val qname = QualifiedName.fromDottedString(refName)
                val components = qname.components
                if (components.isNotEmpty()) {
                    val packageName = components[0]
                    val module = ModuleUtilCore.findModuleForPsiElement(node)
                    if (PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkUtil.findPythonSdk(module) != null) {
                        result.add(PoetryPackageRequirementsInspection.InstallAndImportQuickFix(packageName, packageName, node, false))
                        result.add(PoetryPackageRequirementsInspection.InstallAndImportQuickFix(packageName, packageName, node, true))
                    } else {
                        val packageAlias = PyPackageAliasesProvider.commonImportAliases[packageName]
                        if (packageAlias != null && PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkUtil.findPythonSdk(module) != null) {
                            result.add(PoetryPackageRequirementsInspection.InstallAndImportQuickFix(packageAlias, packageName, node, false))
                            result.add(PoetryPackageRequirementsInspection.InstallAndImportQuickFix(packageAlias, packageName, node, true))
                        }
                    }
                }
            }
            return result
        }

        companion object {
            private fun overriddenUnresolvedReferenceInspection(file: PsiFile): Boolean? {
                return PyInspectionExtension.EP_NAME.extensionList.stream()
                        .map { e: PyInspectionExtension -> e.overrideUnresolvedReferenceInspection(file) }
                        .filter { obj: Boolean? -> Objects.nonNull(obj) }
                        .findFirst()
                        .orElse(null)
            }

            private fun getInstallPackageActions(packageName: String, module: Module, sdk: Sdk): List<LocalQuickFix> {
                val requirements = listOf(pyRequirement(packageName))
                val result: MutableList<LocalQuickFix> = ArrayList()
                val name = "Install package $packageName"
                result.add(PoetryPackageRequirementsInspection.PyInstallRequirementsFix(name, module, sdk, requirements, false))
                result.add(PoetryPackageRequirementsInspection.PyInstallRequirementsFix(name, module, sdk, requirements, true))
                return result
            }
        }
    }

    companion object {
        private val KEY = Key.create<Visitor>("PoetryUnresolvedReferencesInspection.Visitor")
        val SHORT_NAME_KEY = Key.create<PoetryUnresolvedReferencesInspection>(PoetryUnresolvedReferencesInspection::class.java.simpleName)
        fun getInstance(element: PsiElement): PoetryUnresolvedReferencesInspection {
            val inspectionProfile: InspectionProfile = InspectionProjectProfileManager.getInstance(element.project).currentProfile
            return inspectionProfile.getUnwrappedTool(SHORT_NAME_KEY.toString(), element) as PoetryUnresolvedReferencesInspection
        }
    }
}