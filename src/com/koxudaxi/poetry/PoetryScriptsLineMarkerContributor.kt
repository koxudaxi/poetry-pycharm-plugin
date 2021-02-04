package com.koxudaxi.poetry


import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons.Actions.Execute
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.sdk.pythonSdk
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

object PoetryScriptsLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val sdk = element.project.pythonSdk ?: return null
        if (!sdk.isPoetry) return null
        try {
            if (element !is TomlKey) return null
        } catch (e: NoClassDefFoundError) {
            return null //Toml plugin is installed. But, PyCharm has not restarted yet.
        }
        val keyValue = element.parent as? TomlKeyValue ?: return null
        val key = (keyValue.parent as? TomlTable)?.header?.key ?: return null
        if (key.text != "tool.poetry.scripts") return null
        if (keyValue.key.text == null) return null
        val value = keyValue.value as? TomlLiteral ?: return null
        if (value.textLength < 3) return null
        val action = ActionManager.getInstance().getAction(PoetryRunScript.actionID)
        action.templatePresentation.text = "Run '${keyValue.key.text}'"
        return Info(Execute, { parameter -> "Run '${((parameter as? TomlKey)?.text ?: "script")}'" }, arrayOf(action))
    }
}



