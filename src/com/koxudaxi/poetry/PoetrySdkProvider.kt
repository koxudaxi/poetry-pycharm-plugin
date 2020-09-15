package com.koxudaxi.poetry

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PyInterpreterInspectionQuickFixData
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import org.jdom.Element
import javax.swing.Icon

class PoetrySdkProvider : PySdkProvider {
    override val configureSdkProgressText: String
        get() = "Looking for a pyproject.toml"

    override fun configureSdk(project: Project, module: Module, existingSdks: List<Sdk>): Sdk? {
        return detectAndSetupPoetry(project, module, existingSdks)
    }

    override fun createEnvironmentAssociationFix(module: Module, sdk: Sdk, isPyCharm: Boolean, associatedModulePath: String?): PyInterpreterInspectionQuickFixData? {
        return null
    }

    override fun createInstallPackagesQuickFix(module: Module): LocalQuickFix? {
        val sdk = PythonSdkUtil.findPythonSdk(module) ?: return null
        return if (sdk.isPoetry) PoetryInstallQuickFix() else null
    }

    override fun createMissingSdkFix(module: Module, file: PyFile): PyInterpreterInspectionQuickFixData? {
        return when {
            UsePoetryQuickFix.isApplicable(module) -> PyInterpreterInspectionQuickFixData(UsePoetryQuickFix(null, module), "No Python interpreter configured for the project")
            else -> null
        }
    }

    override fun createNewEnvironmentPanel(project: Project?, module: Module?, existingSdks: List<Sdk>, newProjectPath: String?, context: UserDataHolder): PyAddNewEnvPanel {
        return PyAddNewPoetryPanel(null, null, existingSdks, newProjectPath, context)
    }

    override fun getSdkAdditionalText(sdk: Sdk): String? {
        return null
    }

    override fun getSdkIcon(sdk: Sdk): Icon? {
        return POETRY_ICON
    }

    override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
        return PyPoetrySdkAdditionalData.load(element)
    }

    override fun tryCreatePackageManagementServiceForSdk(project: Project, sdk: Sdk): PyPackageManagementService? {
        return null
    }
}