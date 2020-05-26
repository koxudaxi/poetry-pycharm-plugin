package com.koxudaxi.poetry

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons.Actions.Install
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.sdk.pythonSdk
import org.toml.lang.psi.*

object PoetryExtrasLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val sdk = element.project.pythonSdk ?: return null
        if (!isPoetry(element.project, sdk)) return null
        try {
            if (element !is TomlKey) return null
        } catch (e: NoClassDefFoundError) {
            return null //Toml plugin is installed. But, PyCharm has not restarted yet.
        }
        val keyValue = element.parent as? TomlKeyValue ?: return null
        val names = (keyValue.parent as? TomlTable)?.header?.names ?: return null
        if (names.joinToString(".") { it.text } != "tool.poetry.extras") return null
        if (keyValue.key.text == null) return null
        val value = keyValue.value as? TomlArray ?: return null
        if (value.elements.isEmpty() || value.elements.any { it !is TomlLiteral || it.textLength < 3}) return null
        val action =ActionManager.getInstance().getAction(PoetryInstallExtras.actionID)
        return Info(Install, { parameter ->  "Install " + ((parameter as? TomlKey)?.text ?: "extra")}, arrayOf(action))
    }
}



