package com.koxudaxi.poetry

import com.intellij.execution.ExecutionManager
import com.intellij.execution.Location
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.PsiFile
import com.jetbrains.extensions.python.toPsi
import com.jetbrains.python.run.PythonRunConfigurationProducer
import com.jetbrains.python.sdk.associatedModule
import com.jetbrains.python.sdk.pythonSdk
import org.toml.lang.psi.TomlKey


class PoetryRunScript : AnAction() {
    private fun runScriptFromRunConfiguration(project: Project, file: PsiFile) {
        val configurationFromContext = RunConfigurationProducer
                .getInstance(PythonRunConfigurationProducer::class.java)
                .createConfigurationFromContext(ConfigurationContext(file)) ?: return
        val settings = configurationFromContext.configurationSettings
        val runManager = RunManager.getInstance(project)
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
        val builder = ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), settings)
                ?: return
        ExecutionManager.getInstance(project).restartRunProfile(builder.build())
    }

    override fun actionPerformed(e: AnActionEvent) {
        val tomlKey = e.dataContext.getData(Location.DATA_KEY)?.psiElement as? TomlKey ?: return
        e.project?.pythonSdk?.associatedModule?.let {
            val scriptPath = it.pythonSdk?.homeDirectory?.parent?.findChild(tomlKey.text)?.path ?: return
            val file = StandardFileSystems.local().findFileByPath(scriptPath)?.toPsi(it.project) ?: return
            runScriptFromRunConfiguration(it.project, file.containingFile)
        }
    }

    companion object {
        const val actionID = "poetryRunScript"
    }
}