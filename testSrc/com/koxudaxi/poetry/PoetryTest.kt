package com.koxudaxi.poetry


import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import org.jetbrains.kotlin.konan.file.File


class PoetryTest : PoetryTestCase() {
    override val testClassName: String = "poetry"

    fun testGetPyProjectTomlForPoetry() {
        val testFile = LocalFileSystemImpl.getInstance().findFileByPath(dataDir + File.separator + "pyproject.toml")
        val result = getPyProjectTomlForPoetry(testFile!!)
        assertEquals(result.first, 0)
        assertEquals(result.second, testFile)
    }
    fun testGetPyProjectTomlForPoetryInvalid() {
        val testFile = LocalFileSystemImpl.getInstance().findFileByPath(dataDir + File.separator + "pyproject.toml")
        val result = getPyProjectTomlForPoetry(testFile!!)
        assertEquals(result.first, 0)
        assertEquals(result.second, null)
    }

}