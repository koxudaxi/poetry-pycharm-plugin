package com.koxudaxi.poetry


import com.intellij.openapi.vfs.VirtualFile


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

    private val testShowOutdatedDataAsText: String
        get() {
            return getTestDataAsText("show-outdated.txt")
        }

    fun testParsePoetryShoOutdated() {
        val result = parsePoetryShowOutdated(testShowOutdatedDataAsText)
        assertEquals(result.size, 4)
        assertEquals(result,
                mapOf(
                        "boto3" to PoetryOutdatedVersion(currentVersion = "1.13.26", latestVersion = "1.14.38"),
                        "botocore" to PoetryOutdatedVersion(currentVersion = "1.16.26", latestVersion = "1.17.38"),
                        "docutils" to PoetryOutdatedVersion(currentVersion = "0.15.2", latestVersion = "0.16"),
                        "pydantic" to PoetryOutdatedVersion(currentVersion = "1.4", latestVersion = "1.6.1")
                )
        )
    }

}