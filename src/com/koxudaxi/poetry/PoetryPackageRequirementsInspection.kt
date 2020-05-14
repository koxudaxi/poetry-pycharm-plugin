// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// this file is copied from com.jetbrains.python.inspections.PyPackageRequirementsInspection
package com.koxudaxi.poetry

import com.google.common.collect.ImmutableSet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.ListEditForm
import com.intellij.lang.Language
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.JDOMExternalizableStringList
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPlainTextFile
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.toArray
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.sdk.pythonSdk
import one.util.streamex.StreamEx
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
                if (!isPoetry(file.project)) return
                val unsatisfied: List<PyRequirement> = findUnsatisfiedRequirements(module, sdk, myIgnoredPackages)
                 if (unsatisfied.isNotEmpty()) {
                    val plural = unsatisfied.size > 1
                    val msg = String.format("Package requirement%s %s %s not satisfied",
                            if (plural) "s" else "",
                            PyPackageUtil.requirementsToString(unsatisfied),
                            if (plural) "are" else "is")
                    val quickFixes: MutableList<LocalQuickFix> = ArrayList()
                    if (isPoetry(module.project)) {
                        quickFixes.add(PoetryInstallQuickFix())
                        registerProblem(file, msg,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null,
                                *quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY))
                    }
                }
            }
        }
    }
}
