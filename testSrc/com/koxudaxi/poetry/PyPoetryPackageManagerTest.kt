package com.koxudaxi.poetry


import com.jetbrains.python.fixtures.PythonMockSdk


class PyPoetryPackageManagerTest : PoetryTestCase() {
    private val testDataAsText: String
        get() {
            return getTestDataAsText("dry-run-result.txt")
        }
    fun testParsePoetryInstallDryRun() {
        val sdk = PythonMockSdk.create("3.7")
        val pyPoetryPackageManager = PyPoetryPackageManager(sdk)
        val result = pyPoetryPackageManager.parsePoetryInstallDryRun(testDataAsText)
        assertEquals(result.first.size, 1)
        assertEquals(result.second.size, 3)
    }
}