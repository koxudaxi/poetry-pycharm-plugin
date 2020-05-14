package com.koxudaxi.poetry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageManager

class PoetryPackageManagerListener : PyPackageManager.Listener {
    override fun packagesRefreshed(sdk: Sdk) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().executeOnPooledThread {
                PyPoetryPackageManager.getInstance(sdk).refreshAndGetPackages(true, notify = false)
            }
        }
    }
}