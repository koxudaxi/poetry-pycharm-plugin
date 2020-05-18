// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.koxudaxi.poetry

import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.packaging.*
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.associatedModule
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.pipenv.runPipEnv

/**
 * @author vlan
 */

/**
 *  This source code is edited by @koxudaxi  (Koudai Aono)
 */

class PyPoetryPackageManager(val sdk: Sdk) : PyPackageManager() {
    @Volatile
    private var packages: List<PyPackage>? = null

    private var requirements: List<PyRequirement>? = null

    init {
        PyPackageUtil.runOnChangeUnderInterpreterPaths(sdk) {
            PythonSdkType.getInstance().setupSdkPaths(sdk)
        }
    }

    override fun installManagement() {}

    override fun hasManagement() = true

    override fun install(requirementString: String) {
        install(parseRequirements(requirementString), emptyList())
    }

    override fun install(requirements: List<PyRequirement>?, extraArgs: List<String>) {
        val args = listOfNotNull(listOf("install"),
                requirements?.flatMap { it.installOptions },
                extraArgs)
                .flatten()
        try {
            runPoetry(sdk, *args.toTypedArray())
        } finally {
            sdk.associatedModule?.baseDir?.refresh(true, false)
            refreshAndGetPackages(true)
        }
    }

    override fun uninstall(packages: List<PyPackage>) {
        val args = listOf("uninstall") +
                packages.map { it.name }
        try {
            runPipEnv(sdk, *args.toTypedArray())
        } finally {
            sdk.associatedModule?.baseDir?.refresh(true, false)
            refreshAndGetPackages(true)
        }
    }

    override fun refresh() {
        with(ApplicationManager.getApplication()) {
            invokeLater {
                runWriteAction {
                    val files = sdk.rootProvider.getFiles(OrderRootType.CLASSES)
                    VfsUtil.markDirtyAndRefresh(true, true, true, *files)
                }
                PythonSdkType.getInstance().setupSdkPaths(sdk)
            }
        }
    }

    override fun createVirtualEnv(destinationDir: String, useGlobalSite: Boolean): String {
        throw ExecutionException("Creating virtual environments based on Pipenv environments is not supported")
    }

    override fun getPackages() = packages

    fun getRequirements() = requirements

    override fun refreshAndGetPackages(alwaysRefresh: Boolean): List<PyPackage> {
        if (alwaysRefresh || packages == null) {
            packages = null
            val output = try {
                runPoetry(sdk, "install", "--dry-run")
            } catch (e: ExecutionException) {
                packages = emptyList()
                throw e
            }
            val allPackage = parsePoetryInstallDryRun(output)
            packages = allPackage.first
            requirements = allPackage.second
            val notify = sdk.associatedModule?.getUserData(refreshAndGetPackagesNotificationActive) ?: true
            if (notify) {
                ApplicationManager.getApplication().messageBus.syncPublisher(PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)
            } else {
                sdk.associatedModule?.putUserData(refreshAndGetPackagesNotificationActive, null)
            }
        }
        return packages ?: emptyList()
    }

    override fun getRequirements(module: Module): List<PyRequirement>? = requirements

    override fun parseRequirements(text: String): List<PyRequirement> =
            PyRequirementParser.fromText(text)

    override fun parseRequirement(line: String): PyRequirement? =
            PyRequirementParser.fromLine(line)

    override fun parseRequirements(file: VirtualFile): List<PyRequirement> =
            PyRequirementParser.fromFile(file)

    override fun getDependents(pkg: PyPackage): Set<PyPackage> {
        // TODO: Parse the dependency information from `pipenv graph`
        return emptySet()
    }

    companion object {
        fun getInstance(sdk: Sdk): PyPoetryPackageManager {
            return PyPoetryPackageManagers.getInstance().forSdk(sdk)
        }
        val refreshAndGetPackagesNotificationActive = Key.create<Boolean>("PyPoetryPackageManagerRefreshAndGetPackages.notification.active")

    }

    private fun getVersion(version: String): String {
        return if (Regex("^[0-9]").containsMatchIn(version)) "==$version" else version
    }

    private fun toRequirements(packages: List<PyPackage>): List<PyRequirement> =
            packages
                    .asSequence()
//                    .filterNot { (_, pkg) -> pkg.editable ?: false }
                    // TODO: Support requirements markers (PEP 496), currently any packages with markers are ignored due to PY-30803
//                    .filter { (_, pkg) -> pkg.markers == null }
                    .flatMap { it -> this.parseRequirements("${it.name}${it.version?.let { getVersion(it) } ?: ""}").asSequence() }
                    .toList()

    /**
     * Parses the output of `poetry install --dry-run ` into a list of packages.
     */
    private fun parsePoetryInstallDryRun(input: String): Pair<List<PyPackage>, List<PyRequirement>> {
        fun getNameAndVersion(line: String): Pair<String, String> {
            return line.split(" ").let {
                Pair(it[4], it[5].replace(Regex("[()]"), ""))
            }
        }

        val pyPackages = mutableListOf<PyPackage>()
        val pyRequirements = mutableListOf<PyRequirement>()
        input
                .lineSequence()
                .filter { it.endsWith(")") }
                .forEach { line ->
                    getNameAndVersion(line).also {
                        when {
                            line.contains("Already installed") -> pyPackages.add(PyPackage(it.first, it.second, null, emptyList()))
                            line.contains("Installing") -> pyRequirements.addAll(this.parseRequirements(it.first + getVersion(it.second)).asSequence())
                        }
                    }
                }
        return Pair(pyPackages.distinct().toList(), pyRequirements.distinct().toList())
    }
}