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
    fun testParsePoetryInstallDryRun1_0() {
        val sdk = PythonMockSdk.create("3.7")
        val pyPoetryPackageManager = PyPoetryPackageManager(sdk)
        val result = pyPoetryPackageManager.parsePoetryInstallDryRun(testDataAsText)
        assertEquals(result.first.size, 3)
        assertEquals(result.first,
                listOf(
                        getPyPackage("six", "1.15.0"),
                        getPyPackage("attrs", "20.2.0"),
                        getPyPackage("colorama", "0.4.3")
                )
        )
        assertEquals(result.second.size, 5)
        assertEquals(result.second,
                listOf(
                        getPyRequirement("six","1.15.0"),
                        getPyRequirement("attrs","20.2.0"),
                        getPyRequirement("jmespath","0.10.0"),
                        getPyRequirement("botocore","1.18.18"),
                        getPyRequirement("colorama","0.4.4")
                )
        )

    }
    fun testParsePoetryInstallDryRun1_1() {
        val sdk = PythonMockSdk.create("3.7")
        val pyPoetryPackageManager = PyPoetryPackageManager(sdk)
        val result = pyPoetryPackageManager.parsePoetryInstallDryRun(testDataAsText)
        assertEquals(result.first.size, 3)
        assertEquals(result.first,
                listOf(
                        getPyPackage("six", "1.15.0"),
                        getPyPackage("attrs", "20.2.0"),
                        getPyPackage("colorama", "0.4.3")
                )
        )
        assertEquals(result.second.size, 5)
        assertEquals(result.second,
                listOf(
                        getPyRequirement("six","1.15.0"),
                        getPyRequirement("attrs","20.2.0"),
                        getPyRequirement("jmespath","0.10.0"),
                        getPyRequirement("botocore","1.18.18"),
                        getPyRequirement("colorama","0.4.4")
                )
        )

    }
}