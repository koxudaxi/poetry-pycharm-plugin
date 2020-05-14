// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.koxudaxi.poetry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk

/**
 * @author yole
 */

/**
 *  This source code is edited by @koxudaxi  (Koudai Aono)
 */
abstract class PyPoetryPackageManagers {
    abstract fun forSdk(sdk: Sdk): PyPoetryPackageManager
//    abstract fun getManagementService(project: Project?, sdk: Sdk?): PackageManagementService?
    abstract fun clearCache(sdk: Sdk)

    companion object {
        fun getInstance(): PyPoetryPackageManagers {
            return ApplicationManager.getApplication().getService(PyPoetryPackageManagers::class.java)
        }
    }
}