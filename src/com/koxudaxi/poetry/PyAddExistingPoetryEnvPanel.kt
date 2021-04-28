/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.koxudaxi.poetry

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.add.PyAddSdkView
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addInterpretersAsync
import java.awt.BorderLayout
import javax.swing.Icon

/**
 * @author vlan
 */

/**
 *  This source code is edited by @koxudaxi  (Koudai Aono)
 */

class PyAddExistingPoetryEnvPanel(private val project: Project?,
                                  private val module: Module?,
                                  private val existingSdks: List<Sdk>,
                                  override var newProjectPath: String?,
                                  context: UserDataHolder) : PyAddSdkPanel() {
    override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.existing.environment")
    override val icon: Icon = POETRY_ICON
    private val sdkComboBox = PySdkPathChoosingComboBox()

    init {
        layout = BorderLayout()
        val formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(PySdkBundle.message("python.interpreter.label"), sdkComboBox)
                .panel
        add(formPanel, BorderLayout.NORTH)
        addInterpretersAsync(sdkComboBox) {
            detectPoetryEnvs(module, existingSdks, project?.basePath ?: newProjectPath)
                    .filterNot { it.isAssociatedWithAnotherModule(module) }
        }
    }

    override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox, this))

    override fun getOrCreateSdk(): Sdk? {
        return when (val sdk = sdkComboBox.selectedSdk) {
            is PyDetectedSdk ->
                setupPoetrySdkUnderProgress(project, module, existingSdks, newProjectPath,
                        getPythonExecutable(sdk.name), false, sdk.name)?.apply {
                    PySdkSettings.instance.preferredVirtualEnvBaseSdk = getPythonExecutable(sdk.name)
                }
            else -> sdk
        }
    }

    companion object {
        fun validateSdkComboBox(field: PySdkPathChoosingComboBox, view: PyAddSdkView): ValidationInfo? {
            return when (val sdk = field.selectedSdk) {
                null -> ValidationInfo(PySdkBundle.message("python.sdk.field.is.empty"), field)
                // This plugin does not support installing python sdk.
//                is PySdkToInstall -> {
//                    val message = sdk.getInstallationWarning(getDefaultButtonName(view))
//                    ValidationInfo(message).asWarning().withOKEnabled()
//                }
                else -> null
            }
        }
    }
}
