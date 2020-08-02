package com.koxudaxi.poetry

import com.intellij.execution.Location
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.python.sdk.associatedModule
import com.jetbrains.python.sdk.pythonSdk
import org.toml.lang.psi.TomlKey

class PoetryRunScript : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val tomlKey = e.dataContext.getData(Location.DATA_KEY)?.psiElement  as? TomlKey ?: return
        e.project?.pythonSdk?.associatedModule?.let { runPoetryInBackground(it, listOf("run", tomlKey.text), "run ${tomlKey.text}") }
    }
    companion object {
        const val actionID = "poetryRunScript"
    }
}