package com.koxudaxi.poetry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.associatedModule
import com.koxudaxi.poetry.PyPoetryPackageManager.Companion.refreshAndGetPackagesNotificationActive

class PoetryPackageManagerListener : PyPackageManager.Listener {
    override fun packagesRefreshed(sdk: Sdk) {
        val module = sdk.associatedModule ?: return
        if (!isPoetry(module.project, sdk)) return
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().executeOnPooledThread {
                if (module.pyProjectToml == null) return@executeOnPooledThread
                module.putUserData(refreshAndGetPackagesNotificationActive, false)
                PyPoetryPackageManager.getInstance(sdk).refreshAndGetPackages(true)
            }
        }
    }
}