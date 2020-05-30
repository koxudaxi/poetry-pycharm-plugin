package com.koxudaxi.poetry

class PoetryPackageRequirementsInspectionTest : PoetryTestCase() {
    private val firstChar = 0.toChar().toString()
    fun testGetPoetryName() {
        assertEquals(
                PoetryPackageRequirementsInspection.getPoetryName("Install and import package"),
                 firstChar + "Install and import package " + firstChar + "with poetry"
        )
        assertEquals(
                PoetryPackageRequirementsInspection.getPoetryName("Install and import package", true),
                firstChar + "Install and import package as dev with poetry"
        )
    }
}