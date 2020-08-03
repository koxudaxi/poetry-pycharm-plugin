package com.koxudaxi.poetry

import com.intellij.execution.Location
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.sdk.associatedModule
import com.jetbrains.python.sdk.pythonSdk
import org.toml.lang.psi.TomlKey


class PoetryRunScript : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val tomlKey = e.dataContext.getData(Location.DATA_KEY)?.psiElement as? TomlKey ?: return
        e.project?.pythonSdk?.associatedModule?.let {
            val toolWindow = ToolWindowManager.getInstance(e.project!!).getToolWindow("Poetry") ?: return
            val consoleView = ConsoleViewImpl(e.project!!, true)
            val contentName = when (val tabCount = toolWindow.contentManager.contentCount) {
                0 -> "Script"
                else -> "Script(${tabCount + 1})"
            }
            val executable = getPoetryExecutable()?.path
                    ?: throw PyExecutionException("Cannot find Poetry", "poetry", emptyList(), ProcessOutput())
            val commandLine = GeneralCommandLine(executable, "run", tomlKey.text).withWorkDirectory(e.project!!.basePath)
            val factory = ProcessHandlerFactory.getInstance()
            val handler = factory.createColoredProcessHandler(commandLine)
            consoleView.attachToProcess(handler)
            ProcessTerminatedListener.attach(handler)
            handler.setShouldDestroyProcessRecursively(true)
            val content = toolWindow.contentManager.factory.createContent(consoleView.component, contentName, false)
            content.setShouldDisposeContent(true)
            content.isCloseable = true
            toolWindow.contentManager.addContent(content)
            toolWindow.contentManager.setSelectedContent(content)
            toolWindow.show()
            handler.startNotify()
        }

    }
    companion object {
        const val actionID = "poetryRunScript"
    }
}