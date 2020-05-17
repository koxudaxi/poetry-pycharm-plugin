package com.koxudaxi.poetry

import com.intellij.execution.Location
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.python.sdk.associatedModule
import com.jetbrains.python.sdk.pythonSdk
import org.toml.lang.psi.TomlKey

class PoetryInstallExtras : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val tomlKey = e.dataContext.getData(Location.DATA_KEY)?.psiElement  as? TomlKey ?: return
        e.project?.pythonSdk?.associatedModule?.let { runPoetryInBackground(it, listOf("install", "--extras", tomlKey.text), "installing ${tomlKey.text}") }
    }
    companion object {
        const val actionID = "poetryInstallExtras"
    }
}