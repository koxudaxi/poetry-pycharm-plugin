package com.koxudaxi.poetry

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

abstract class PoetryTestCase : BasePlatformTestCase() {
    protected open val testClassName: String = this.javaClass.simpleName.replace("Test", "")
    protected val dataDir: String
        get() {
            return "testData" + File.separator + testClassName + File.separator + getTestName(true)
        }
    fun getTestData(fileName: String): VirtualFile {
        return LocalFileSystemImpl.getInstance().refreshAndFindFileByPath(dataDir + File.separator + fileName)!!
    }
    fun getTestDataAsText(fileName: String): String {
        return getTestData(fileName).inputStream.bufferedReader().readText()
    }
}

