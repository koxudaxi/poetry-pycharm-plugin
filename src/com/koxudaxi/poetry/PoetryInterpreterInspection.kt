// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// this file is copied from com.jetbrains.python.inspections.PyInterpreterInspection
package com.koxudaxi.poetry

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.containers.toArray
import com.jetbrains.python.PythonIdeLanguageCustomization
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.*
import com.koxudaxi.poetry.UsePoetryQuickFix.Companion.isApplicable

class PoetryInterpreterInspection : PyInspection() {
    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor {
        return Visitor(holder, session)
    }

    class Visitor(holder: ProblemsHolder?,
                  session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {
        private fun guessModule(element: PsiElement): Module? {
            return ModuleUtilCore.findModuleForPsiElement(element)
                    ?: ModuleManager.getInstance(element.project).modules.let { if (it.size != 1) null else it[0] }
        }

        private fun isFileIgnored(pyFile: PyFile): Boolean {
            return PyInspectionExtension.EP_NAME.extensionList.any { it.ignoreInterpreterWarnings(pyFile) }
        }

        override fun visitPyFile(node: PyFile) {
            val module = guessModule(node) ?: return
            if (isFileIgnored(node)) return
            val sdk = PythonSdkUtil.findPythonSdk(module)
            val pyCharm = PythonIdeLanguageCustomization.isMainlyPythonIde()
            val interpreterOwner = if (pyCharm) "project" else "module"
            val fixes: MutableList<LocalQuickFix> = ArrayList()
            if (isApplicable(module)) {
                fixes.add(UsePoetryQuickFix(sdk, module))
            }

            if (sdk == null) {
                if (fixes.isNotEmpty()) {
                    // TODO change message
                    registerProblem(node, "Found pyproject.toml in this project", *fixes.toArray(LocalQuickFix.EMPTY_ARRAY))
                }
            } else {
                val associatedModule = sdk.associatedModule
                val associatedName = associatedModule?.name ?: sdk.associatedModulePath
                if (sdk.isPoetry && associatedModule !== module) {
                    val message = if (associatedName == null) {
                        "Poetry interpreter is not associated with any $interpreterOwner"
                    } else {
                        "Poetry interpreter is associated with another $interpreterOwner: '$associatedName'"
                    }
                    registerProblem(node, message, *fixes.toArray(LocalQuickFix.EMPTY_ARRAY))
                }
            }
        }
    }
}