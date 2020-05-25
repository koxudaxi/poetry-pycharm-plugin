package com.koxudaxi.poetry

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.konan.file.File

abstract class PoetryTestCase : BasePlatformTestCase() {
    protected open val testClassName: String = this.javaClass.simpleName.replace("Poetry", "").replace("Test", "").toLowerCase()
    protected val dataDir: String
        get() {
            return "testData" + File.separator + testClassName + File.separator + getTestName(true)
        }
}

