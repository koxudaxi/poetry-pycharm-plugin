package com.koxudaxi.poetry


import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import org.jetbrains.kotlin.konan.file.File


class PoetryTest : PoetryTestCase() {
    private val testFile: VirtualFile
        get() {
            return getTestData("pyproject.toml")
        }
    fun testGetPyProjectTomlForPoetry() {
        val result = getPyProjectTomlForPoetry(testFile)
        assertEquals(result.first, 0)
        assertEquals(result.second, testFile)
    }

    fun testGetPyProjectTomlForPoetryInvalid() {
        val result = getPyProjectTomlForPoetry(testFile)
        assertEquals(result.first, 0)
        assertEquals(result.second, null)
    }

    fun testGetPyProjectTomlForPoetryBroken() {
        val result = getPyProjectTomlForPoetry(testFile)
        assertEquals(result.first, 0)
        assertEquals(result.second, null)
    }
}