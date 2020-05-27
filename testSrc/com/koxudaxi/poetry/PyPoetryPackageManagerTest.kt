package com.koxudaxi.poetry


import com.jetbrains.python.fixtures.PythonMockSdk
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser


class PyPoetryPackageManagerTest : PoetryTestCase() {
    private val testDataAsText: String
        get() {
            return getTestDataAsText("dry-run-result.txt")
        }
    private fun getPyPackage(name: String, version: String) :PyPackage {
        return PyPackage(name, version, null, emptyList())
    }
    fun getPyRequirement(name: String, version: String): PyRequirement? {
        return PyRequirementParser.fromLine("${name}==${version}")
    }
    fun testParsePoetryInstallDryRun() {
        val sdk = PythonMockSdk.create("3.7")
        val pyPoetryPackageManager = PyPoetryPackageManager(sdk)
        val result = pyPoetryPackageManager.parsePoetryInstallDryRun(testDataAsText)
        assertEquals(result.first.size, 1)
        assertEquals(result.first,
                listOf(getPyPackage("typed-ast", "1.4.1"))
        )
        assertEquals(result.second.size, 3)
        assertEquals(result.second,
                listOf(
                        getPyRequirement("mypy-extensions","0.4.3"),
                        getPyRequirement("typing-extensions","3.7.4.2"),
                        getPyRequirement("mypy","0.770")
                )
        )

    }
}