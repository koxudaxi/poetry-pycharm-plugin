/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManagerUI
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.koxudaxi.poetry.PyPoetryPackageManager
import java.util.*

/**
 * @author vlan
 */

/**
 *  This source code is edited by @koxudaxi  (Koudai Aono)
 */

class PoetryPackageManagerUI(private val myProject: Project, private val mySdk: Sdk, private val myListener: PyPackageRequirementsInspection.RunningPackagingTasksListener) {

    interface Listener {
        fun started()
        fun finished(exceptions: List<ExecutionException?>?)
    }

    fun installManagement() {
        ProgressManager.getInstance().run(InstallManagementTask(myProject, mySdk, myListener))
    }

    fun install(requirements: List<PyRequirement>?, extraArgs: List<String>) {
        ProgressManager.getInstance().run(InstallTask(myProject, mySdk, requirements, extraArgs, myListener))
    }

    fun uninstall(packages: List<PyPackage?>) {
        if (checkDependents(packages)) {
            return
        }
        packages.filterIsInstance<PyPackage>().let { ProgressManager.getInstance().run(UninstallTask(myProject, mySdk, myListener, it)) }
    }

    private fun checkDependents(packages: List<PyPackage?>): Boolean {
        try {
            val dependentPackages = collectDependents(packages, mySdk)
            val warning = intArrayOf(0)
            if (!dependentPackages.isEmpty()) {
                ApplicationManager.getApplication().invokeAndWait({
                    if (dependentPackages.size == 1) {
                        var message: String? = "You are attempting to uninstall "
                        val dep: MutableList<String> = ArrayList()
                        var size = 1
                        for ((key, value) in dependentPackages) {
                            size = value.size
                            dep.add(key + " package which is required for " + StringUtil.join(value, ", "))
                        }
                        message += StringUtil.join(dep, "\n")
                        message += if (size == 1) " package" else " packages"
                        message += "\n\nDo you want to proceed?"
                        warning[0] = Messages.showYesNoDialog(message, PyBundle.message("python.packaging.warning"),
                                AllIcons.General.BalloonWarning)
                    } else {
                        var message: String? = "You are attempting to uninstall packages which are required for another packages.\n\n"
                        val dep: MutableList<String> = ArrayList()
                        for ((key, value) in dependentPackages) {
                            dep.add(key + " -> " + StringUtil.join(value, ", "))
                        }
                        message += StringUtil.join(dep, "\n")
                        message += "\n\nDo you want to proceed?"
                        warning[0] = Messages.showYesNoDialog(message, PyBundle.message("python.packaging.warning"),
                                AllIcons.General.BalloonWarning)
                    }
                }, ModalityState.current())
            }
            if (warning[0] != Messages.YES) return true
        } catch (e: ExecutionException) {
            LOG.info("Error loading packages dependents: " + e.message, e)
        }
        return false
    }

    private abstract class PackagingTask internal constructor(project: Project?, protected val mySdk: Sdk, title: String, protected val myListener: PyPackageRequirementsInspection.RunningPackagingTasksListener) : Backgroundable(project, title) {
        override fun run(indicator: ProgressIndicator) {
            taskStarted(indicator)
            taskFinished(runTask(indicator))
        }

        protected abstract fun runTask(indicator: ProgressIndicator): List<ExecutionException?>
        protected abstract val successTitle: String
        protected abstract val successDescription: String
        protected abstract val failureTitle: String

        protected fun taskStarted(indicator: ProgressIndicator) {
            val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(PackagingNotification::class.java, project)
            for (notification in notifications) {
                notification.expire()
            }
            indicator.text = "$title..."
            ApplicationManager.getApplication().invokeLater { myListener.started() }
        }

        protected fun taskFinished(exceptions: List<ExecutionException?>) {
            val notificationRef = Ref<Notification>(null)
            if (exceptions.isEmpty()) {
                notificationRef.set(PackagingNotification(PACKAGING_GROUP_ID, successTitle, successDescription,
                        NotificationType.INFORMATION, null))
            } else {
                val description = PyPackageManagementService.toErrorDescription(exceptions, mySdk)
                if (description != null) {
                    val firstLine = "$title: error occurred."
                    val listener = NotificationListener { _, _ ->
                        assert(myProject != null)
                        val title = StringUtil.capitalizeWords(failureTitle, true)
                        PackagesNotificationPanel.showError(title, description)
                    }
                    notificationRef.set(PackagingNotification(PACKAGING_GROUP_ID, failureTitle, "$firstLine <a href=\"xxx\">Details...</a>",
                            NotificationType.ERROR, listener))
                }
            }
            ApplicationManager.getApplication().invokeLater {
                myListener.finished(exceptions)
                val notification = notificationRef.get()
                notification?.notify(myProject)
            }
        }

        private class PackagingNotification internal constructor(groupDisplayId: String,
                                                                 title: String,
                                                                 content: String,
                                                                 type: NotificationType, listener: NotificationListener?) : Notification(groupDisplayId, title, content, type, listener)

        companion object {
            private const val PACKAGING_GROUP_ID = "Packaging"
        }

    }

    private open class InstallTask internal constructor(project: Project?,
                                                        sdk: Sdk,
                                                        private val myRequirements: List<PyRequirement>?,
                                                        private val myExtraArgs: List<String>,
                                                        listener: PyPackageRequirementsInspection.RunningPackagingTasksListener) : PackagingTask(project, sdk, "Installing packages", listener) {
        override fun runTask(indicator: ProgressIndicator): List<ExecutionException> {
            val exceptions: MutableList<ExecutionException> = ArrayList()
            val manager = PyPoetryPackageManager(mySdk)
            if (myRequirements == null) {
                indicator.text = PyBundle.message("python.packaging.installing.packages")
                indicator.isIndeterminate = true
                try {
                    manager.install(null, myExtraArgs)
                } catch (e: RunCanceledByUserException) {
                    exceptions.add(e)
                } catch (e: ExecutionException) {
                    exceptions.add(e)
                }
            } else {
                val size = myRequirements.size
                for (i in 0 until size) {
                    val requirement = myRequirements[i]
                    indicator.text = String.format("Installing package '%s'...", requirement.presentableText)
                    if (i == 0) {
                        indicator.isIndeterminate = true
                    } else {
                        indicator.isIndeterminate = false
                        indicator.fraction = i.toDouble() / size
                    }
                    try {
                        manager.install(listOf(requirement), myExtraArgs)
                    } catch (e: RunCanceledByUserException) {
                        exceptions.add(e)
                        break
                    } catch (e: ExecutionException) {
                        exceptions.add(e)
                    }
                }
            }
            manager.refresh()
            return exceptions
        }

        override val successTitle: String
            get() = "Packages installed successfully"

        override val successDescription: String
            get() = if (myRequirements != null) "Installed packages: " + PyPackageUtil.requirementsToString(myRequirements) else "Installed all requirements"

        override val failureTitle: String
            get() = "Install packages failed"

    }

    private open class InstallManagementTask internal constructor(project: Project?,
                                                                  sdk: Sdk,
                                                                  listener: PyPackageRequirementsInspection.RunningPackagingTasksListener) : InstallTask(project, sdk, emptyList(), emptyList<String>(), listener) {
        override fun runTask(indicator: ProgressIndicator): List<ExecutionException> {
            val exceptions: MutableList<ExecutionException> = ArrayList()
            val manager = PyPoetryPackageManager(mySdk)
            indicator.text = PyBundle.message("python.packaging.installing.packaging.tools")
            indicator.isIndeterminate = true
            try {
                manager.installManagement()
            } catch (e: ExecutionException) {
                exceptions.add(e)
            }
            manager.refresh()
            return exceptions
        }

        override val successDescription: String
            get() = "Installed Python packaging tools"
    }

    private open class UninstallTask internal constructor(project: Project?,
                                                          sdk: Sdk,
                                                          listener: PyPackageRequirementsInspection.RunningPackagingTasksListener,
                                                          private val myPackages: List<PyPackage>) : PackagingTask(project, sdk, "Uninstalling packages", listener) {
        override fun runTask(indicator: ProgressIndicator): List<ExecutionException> {
            val manager = PyPoetryPackageManager(mySdk)
            indicator.isIndeterminate = true
            return try {
                manager.uninstall(myPackages)
                emptyList()
            } catch (e: ExecutionException) {
                listOf(e)
            } finally {
                manager.refresh()
            }
        }

        override val successTitle: String
            get() = "Packages uninstalled successfully"

        override val successDescription: String
            get() {
                val packagesString = StringUtil.join(myPackages, { pkg: PyPackage? -> "'" + pkg!!.name + "'" }, ", ")
                return "Uninstalled packages: $packagesString"
            }

        override val failureTitle: String
            get() = "Uninstall packages failed"

    }

    companion object {
        private val LOG = Logger.getInstance(PyPackageManagerUI::class.java)

        @Throws(ExecutionException::class)
        private fun collectDependents(packages: List<PyPackage?>,
                                      sdk: Sdk): Map<String, Set<PyPackage?>> {
            val dependentPackages: MutableMap<String, Set<PyPackage?>> = HashMap()
            for (pkg in packages) {
                val dependents = PyPoetryPackageManager(sdk).getDependents(pkg!!)
                if (!dependents.isEmpty()) {
                    for (dependent in dependents) {
                        if (!packages.contains(dependent)) {
                            dependentPackages[pkg.name] = dependents
                        }
                    }
                }
            }
            return dependentPackages
        }
    }

}