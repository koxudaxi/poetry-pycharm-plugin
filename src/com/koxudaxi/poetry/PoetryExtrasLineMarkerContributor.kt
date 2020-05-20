package com.koxudaxi.poetry

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons.Actions.Install
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.sdk.pythonSdk
import org.jetbrains.kotlin.idea.util.module
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

object PoetryExtrasLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val sdk = element.module?.pythonSdk ?: return null
        if (!isPoetry(element.project, sdk)) return null
        if (element !is TomlKey) return null
        val keyValue = element.parent as? TomlKeyValue ?: return null
        val names = (keyValue.parent as? TomlTable)?.header?.names ?: return null
        if (names.joinToString(".") { it.text } != "tool.poetry.extras") return null
        if (keyValue.key.text == null || keyValue.value?.text == null) return null
        val action =ActionManager.getInstance().getAction(PoetryInstallExtras.actionID)
        return Info(Install, { parameter ->  "Install " + ((parameter as? TomlKey)?.text ?: "extra")}, arrayOf(action))
    }
}



