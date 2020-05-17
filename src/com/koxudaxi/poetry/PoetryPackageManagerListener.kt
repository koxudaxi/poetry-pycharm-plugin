package com.koxudaxi.poetry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.associatedModule

class PoetryPackageManagerListener : PyPackageManager.Listener {
    override fun packagesRefreshed(sdk: Sdk) {
        val module = sdk.associatedModule ?: return
        if (!isPoetry(module.project, sdk)) return
        ApplicationManager.getApplication().invokeLater {
            if (module.pyProjectToml == null) return@invokeLater
            PyPoetryPackageManager.getInstance(sdk).refreshAndGetPackages(true, notify = false)
        }
    }
}