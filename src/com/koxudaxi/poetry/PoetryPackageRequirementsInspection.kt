// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// this file is copied from com.jetbrains.python.inspections.PyPackageRequirementsInspection
package com.koxudaxi.poetry

import PoetryPackageManagerUI
import com.google.common.collect.ImmutableSet
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ui.ListEditForm
import com.intellij.core.CoreBundle
import com.intellij.execution.ExecutionException
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.JDOMExternalizableStringList
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.toArray
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.inspections.PyPackageRequirementsInspection.RunningPackagingTasksListener
import com.jetbrains.python.packaging.*
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.adminPermissionsNeeded
import com.jetbrains.python.sdk.associatedModule
import com.jetbrains.python.sdk.pythonSdk
import one.util.streamex.StreamEx
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.JComponent

/**
 * @author vlan
 */
/**
 *  This source code is edited by @koxudaxi  (Koudai Aono)
 */

class PoetryPackageRequirementsInspection : PyInspection() {
    var ignoredPackages = JDOMExternalizableStringList()
    override fun createOptionsPanel(): JComponent? {
        val form = ListEditForm("Ignore packages", ignoredPackages)
        return form.contentPanel
    }

    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor {
        return if (holder.file !is PyFile && holder.file !is PsiPlainTextFile
                && !isPythonInTemplateLanguages(holder.file)) {
            PsiElementVisitor.EMPTY_VISITOR
        } else Visitor(holder, session, ignoredPackages)
    }

    private fun isPythonInTemplateLanguages(psiFile: PsiFile): Boolean {
        return StreamEx.of(psiFile.viewProvider.languages)
                .findFirst { x: Language -> x.isKindOf(PythonLanguage.getInstance()) }
                .isPresent
    }

    private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {
        lateinit var myIgnoredPackages: Set<String>

        constructor(holder: ProblemsHolder, session: LocalInspectionToolSession, ignoredPackages: Collection<String>?) : this(holder, session) {
            this.myIgnoredPackages = ignoredPackages?.let { ImmutableSet.copyOf(it) } ?: emptySet()
        }

        override fun visitPyFile(node: PyFile) {
            ModuleUtilCore.findModuleForPsiElement(node)?.let { checkPackagesHaveBeenInstalled(node, it) }
        }

        override fun visitPlainTextFile(file: PsiPlainTextFile) {
            val module = ModuleUtilCore.findModuleForPsiElement(file)
            if (module != null && file.virtualFile == PyPackageUtil.findRequirementsTxt(module)) {
                if (file.text.trim { it <= ' ' }.isNotEmpty()) {
                    checkPackagesHaveBeenInstalled(file, module)
                }
            }
        }

        private fun isRunningPackagingTasks(module: Module): Boolean {
            return module.getUserData(PyPackageManager.RUNNING_PACKAGING_TASKS) ?: false

        }

        private fun collectPackagesInModule(module: Module): List<PyPackage> {
            val metadataExtensions = arrayOf("egg-info", "dist-info")
            val result: MutableList<PyPackage> = mutableListOf()
            for (srcRoot in PyUtil.getSourceRoots(module)) {
                for (metadata in VfsUtil.getChildren(srcRoot) { file: VirtualFile -> ArrayUtil.contains(file.extension, *metadataExtensions) }) {
                    val nameAndVersionAndRest = metadata.nameWithoutExtension.split("-", limit = 3).toTypedArray()
                    if (nameAndVersionAndRest.size >= 2) {
                        result.add(PyPackage(nameAndVersionAndRest[0], nameAndVersionAndRest[1], null, emptyList()))
                    }
                }
            }
            return result
        }

        private fun findUnsatisfiedRequirements(module: Module, sdk: Sdk,
                                                ignoredPackages: Set<String>): List<PyRequirement> {
            val manager = PyPoetryPackageManager.getInstance(sdk)
            val requirements = manager.getRequirements() ?: emptyList()

            val packages = manager.packages ?: return emptyList()
            val packagesInModule: List<PyPackage> = collectPackagesInModule(module)
            val unsatisfied: MutableList<PyRequirement> = ArrayList()
            for (req in requirements) {
                if (!ignoredPackages.contains(req.name) && req.match(packages) == null && req.match(packagesInModule) == null) {
                    unsatisfied.add(req)
                }
            }
            return unsatisfied
        }

        private fun checkPackagesHaveBeenInstalled(file: PsiElement, module: Module) {
            if (!isRunningPackagingTasks(module)) {
                // TODO: Fix this logic
//                val sdk = PythonSdkUtil.findPythonSdk(module)
                val sdk = module.project.pythonSdk ?: return
                if (!isPoetry(file.project, sdk)) return
                if (sdk.associatedModule?.pyProjectToml == null) return
                val unsatisfied: List<PyRequirement> = findUnsatisfiedRequirements(module, sdk, myIgnoredPackages)
                 if (unsatisfied.isNotEmpty()) {
                    val plural = unsatisfied.size > 1
                    val msg = String.format("Package requirement%s %s %s not satisfied",
                            if (plural) "s" else "",
                            PyPackageUtil.requirementsToString(unsatisfied),
                            if (plural) "are" else "is")
                    val quickFixes: MutableList<LocalQuickFix> = ArrayList()
                    if (isPoetry(module.project,sdk)) {
                        quickFixes.add(PoetryInstallQuickFix())
                        registerProblem(file, msg,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null,
                                *quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY))
                    }
                }
            }
        }
    }
    class InstallAndImportQuickFix(private val myPackageName: String,
                                   private val myAsName: String?,
                                   node: PyElement) : LocalQuickFix {
        private val mySdk: Sdk?
        private val myModule: Module?
        private val myNode: SmartPsiElementPointer<PyElement>
        override fun getName(): @Nls String {
            return  0.toChar().toString()  + PoetryPsiBundle.message("QFIX.NAME.install.and.import.package", myPackageName)
        }

        override fun getFamilyName(): String {
            return  0.toString() + PoetryPsiBundle.message("QFIX.install.and.import.package")
        }

        override fun startInWriteAction(): Boolean {
            return false
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            if (mySdk == null || !checkAdminPermissionsAndConfigureInterpreter(project, descriptor, mySdk)) {
                installAndImportPackage(project)
            }
        }

        private fun installAndImportPackage(project: Project) {
            if (mySdk == null) return
            val ui = PoetryPackageManagerUI(project, mySdk, object : RunningPackagingTasksListener(myModule!!) {
                override fun finished(exceptions: List<ExecutionException?>) {
                    super.finished(exceptions)
                    if (exceptions.isEmpty()) {
                        val element = myNode.element ?: return
                        CommandProcessor.getInstance().executeCommand(project, {
                            ApplicationManager.getApplication().runWriteAction {
                                AddImportHelper.addImportStatement(element.containingFile, myPackageName, myAsName,
                                        AddImportHelper.ImportPriority.THIRD_PARTY, element)
                            }
                        }, PoetryPsiBundle.message("INSP.package.requirements.add.import"), "Add import")
                    }
                }
            })
            ui.install(listOf(pyRequirement(myPackageName)), emptyList())
        }

        private fun checkAdminPermissionsAndConfigureInterpreter(project: Project,
                                                                 descriptor: ProblemDescriptor,
                                                                 sdk: Sdk): Boolean {
            if (!PythonSdkUtil.isRemote(sdk) && sdk.adminPermissionsNeeded()) {
                val answer = askToConfigureInterpreter(project, sdk)
                when (answer) {
                    Messages.YES -> {
                        PyInterpreterInspection.ConfigureInterpreterFix().applyFix(project, descriptor)
                        return true
                    }
                    Messages.CANCEL, -1 -> return true
                }
            }
            return false
        }

        private fun askToConfigureInterpreter(project: Project, sdk: Sdk): Int {
            val sdkName = StringUtil.shortenTextWithEllipsis(sdk.name, 25, 0)
            val text = PoetryPsiBundle.message("INSP.package.requirements.administrator.privileges.required.description", sdkName)
            val options = arrayOf(
                    PoetryPsiBundle.message("INSP.package.requirements.administrator.privileges.required.button.configure"),
                    PoetryPsiBundle.message("INSP.package.requirements.administrator.privileges.required.button.install.anyway"),
                    CoreBundle.message("button.cancel")
            )
            return Messages.showIdeaMessageDialog(
                    project,
                    text,
                    PoetryPsiBundle.message("INSP.package.requirements.administrator.privileges.required"),
                    options,
                    0,
                    Messages.getWarningIcon(),
                    null)
        }

        init {
            myNode = SmartPointerManager.getInstance(node.project).createSmartPsiElementPointer(node, node.containingFile)
            myModule = ModuleUtilCore.findModuleForPsiElement(node)
            mySdk = PythonSdkUtil.findPythonSdk(myModule)
        }
    }
}
