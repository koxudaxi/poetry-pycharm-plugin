package com.koxudaxi.poetry

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageManagerProvider

class PyPoetryPackageManagerProvider : PyPackageManagerProvider {
    override fun tryCreateForSdk(sdk: Sdk): PyPackageManager? = if (sdk.isPoetry)  PyPoetryPackageManager(sdk) else null
}