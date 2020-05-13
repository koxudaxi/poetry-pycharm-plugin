// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.koxudaxi.poetry

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkType
import java.util.*

/**
 * @author yole
 */
class PyPoetryPackageManagersImpl : PyPoetryPackageManagers() {
    // TODO: Introduce a Python SDK provider EP that is capable of providing a custom package manager and a package management service
    private val myInstances: MutableMap<String, PyPoetryPackageManager> = HashMap()

    // Only Poetry
    @Synchronized
    override fun forSdk(sdk: Sdk): PyPoetryPackageManager {
        val key = PythonSdkType.getSdkKey(sdk)
        return myInstances.getOrPut(key, { PyPoetryPackageManager(sdk) })
    }

//    fun getManagementService(project: Project, sdk: Sdk): PyPackageManagementService {
//        if (PythonSdkUtil.isConda(sdk)) {
//            return PyCondaManagementService(project, sdk)
//        } else if (sdk.isPipEnv) {
//            return PyPipEnvPackageManagementService(project, sdk)
//        }
//        return PyPackageManagementService(project, sdk)
//    }

    override fun clearCache(sdk: Sdk) {
        val key = PythonSdkType.getSdkKey(sdk)
        myInstances.remove(key)
    }
}