// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.koxudaxi.poetry

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.CommonBundle
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.packaging.*
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.statistics.modules
import icons.PythonIcons
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.TestOnly
import java.io.File
import javax.swing.Icon

const val PY_PROJECT_TOML: String = "pyproject.toml"
const val POETRY_LOCK: String = "poetry.lock"
const val POETRY_DEFAULT_SOURCE_URL: String = "https://pypi.org/simple"
const val POETRY_PATH_SETTING: String = "PyCharm.Poetry.Path"

// TODO: Provide a special icon for poetry
val POETRY_ICON: Icon = PythonIcons.Python.PythonClosed

/**
 *  This source code is edited by @koxudaxi  (Koudai Aono)
 */

/**
 * The Pipfile found in the main content root of the module.
 */
val Module.pyProjectToml: VirtualFile?
    get() =
        baseDir?.findChild(PY_PROJECT_TOML)

/**
 * Tells if the SDK was added as a poetry.
 */
var Sdk.isPoetry: Boolean
    get() = sdkAdditionalData is PyPoetrySdkAdditionalData
    set(value) {
        val oldData = sdkAdditionalData
        val newData = if (value) {
            when (oldData) {
                is PythonSdkAdditionalData -> PyPoetrySdkAdditionalData(oldData)
                else -> PyPoetrySdkAdditionalData()
            }
        } else {
            when (oldData) {
                is PyPoetrySdkAdditionalData -> PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(this))
                else -> oldData
            }
        }
        val modificator = sdkModificator
        modificator.sdkAdditionalData = newData
        ApplicationManager.getApplication().runWriteAction { modificator.commitChanges() }
    }

/**
 * The user-set persisted path to the poetry executable.
 */
var PropertiesComponent.poetryPath: @SystemDependent String?
    get() = getValue(POETRY_PATH_SETTING)
    set(value) {
        setValue(POETRY_PATH_SETTING, value)
    }

/**
 * Detects the poetry executable in `$PATH`.
 */
fun detectPoetryExecutable(): File? {
    val name = when {
        SystemInfo.isWindows -> "poetry.exe"
        else -> "poetry"
    }
    return PathEnvironmentVariableUtil.findInPath(name)
}

/**
 * Returns the configured poetry executable or detects it automatically.
 */
fun getPoetryExecutable(): File? =
        PropertiesComponent.getInstance().poetryPath?.let { File(it) } ?: detectPoetryExecutable()

/**
 * Sets up the poetry environment under the modal progress window.
 *
 * The poetry is associated with the first valid object from this list:
 *
 * 1. New project specified by [newProjectPath]
 * 2. Existing module specified by [module]
 * 3. Existing project specified by [project]
 *
 * @return the SDK for poetry, not stored in the SDK table yet.
 */
fun setupPoetrySdkUnderProgress(project: Project?,
                                module: Module?,
                                existingSdks: List<Sdk>,
                                newProjectPath: String?,
                                python: String?,
                                installPackages: Boolean): Sdk? {
    val projectPath = newProjectPath ?: module?.basePath ?: project?.basePath ?: return null
    val task = object : Task.WithResult<String, ExecutionException>(project, "Setting Up Poetry Environment", true) {
        override fun compute(indicator: ProgressIndicator): String {
            indicator.isIndeterminate = true
            val poetry = setupPoetry(FileUtil.toSystemDependentName(projectPath), python, installPackages)
            return PythonSdkUtil.getPythonExecutable(poetry) ?: FileUtil.join(poetry, "bin", "python")
        }
    }
    val suggestedName = "Poetry (${PathUtil.getFileName(projectPath)})"
    return createSdkByGenerateTask(task, existingSdks, null, projectPath, suggestedName)?.apply {
        isPoetry = true
        associateWithModule(module ?: project?.modules?.firstOrNull(), newProjectPath)
    }
}

/**
 * Sets up the poetry environment for the specified project path.
 *
 * @return the path to the poetry environment.
 */
fun setupPoetry(projectPath: @SystemDependent String, python: String?, installPackages: Boolean): @SystemDependent String {
    when {
        installPackages -> {
            python?.let { runPoetry(projectPath, "env", "use", it) }
            val command = listOf("install")
            runPoetry(projectPath, *command.toTypedArray())
        }
        python != null ->
            runPoetry(projectPath, "env", "use", python)
        else ->
            runPoetry(projectPath, "run", "python", "-V")
    }
    return runPoetry(projectPath, "env", "info", "-p")
}


val sdkCache = mutableMapOf<String, Boolean>()
fun isPoetry(projectPath: @SystemDependent String?, pythonPath: String?): @SystemDependent Boolean {
    if (projectPath == null || pythonPath == null) return false
    return sdkCache.getOrElse("$projectPath POETRY_PLUGIN $pythonPath") {
        try {
            runPoetry(projectPath, "env", "use", pythonPath)
            true
        } catch (e: PyExecutionException) {
            false
        }
    }
}

/**
 * Runs the configured poetry for the specified Poetry SDK with the associated project path.
 */
fun runPoetry(sdk: Sdk, vararg args: String): String {
    val projectPath = sdk.associatedModulePath
            ?: throw PyExecutionException("Cannot find the project associated with this Poetry environment",
                    "Poetry", emptyList(), ProcessOutput())
    return runPoetry(projectPath, *args)
}

/**
 * Runs the configured poetry for the specified project path.
 */
fun runPoetry(projectPath: @SystemDependent String, vararg args: String): String {
    val executable = getPoetryExecutable()?.path
            ?: throw PyExecutionException("Cannot find Poetry", "poetry", emptyList(), ProcessOutput())

    val command = listOf(executable) + args
    val commandLine = GeneralCommandLine(command).withWorkDirectory(projectPath)
    val handler = CapturingProcessHandler(commandLine)
    val indicator = ProgressManager.getInstance().progressIndicator
    val result = with(handler) {
        when {
            indicator != null -> {
                addProcessListener(IndicatedProcessOutputListener(indicator))
                runProcessWithProgressIndicator(indicator)
            }
            else ->
                runProcess()
        }
    }
    return with(result) {
        when {
            isCancelled ->
                throw RunCanceledByUserException()
            exitCode != 0 ->
                throw PyExecutionException("Error Running Poetry", executable, args.asList(),
                        stdout, stderr, exitCode, emptyList())
            else -> stdout
        }
    }
}

/**
 * Detects and sets up poetry SDK for a module with Pipfile.
 */
fun detectAndSetupPoetry(project: Project?, module: Module?, existingSdks: List<Sdk>): Sdk? {
    if (module?.pyProjectToml == null || getPoetryExecutable() == null) {
        return null
    }
    return setupPoetrySdkUnderProgress(project, module, existingSdks, null, null, false)
}

/**
 * The URLs of package sources configured in the Pipfile.lock of the module associated with this SDK.
 */
val Sdk.pyProjectTomlLockSources: List<String>
    get() = parsePyProjectTomlLock()?.meta?.sources?.mapNotNull { it.url } ?: listOf(POETRY_DEFAULT_SOURCE_URL)

/**
 * The list of requirements defined in the Pipfile.lock of the module associated with this SDK.
 */
val Sdk.pyProjectTomlLockRequirements: List<PyRequirement>?
    get() {
        return pyProjectTomlLock?.let { getPyProjectTomlLockRequirements(it, packageManager) }
    }

/**
 * A quick-fix for setting up the poetry for the module of the current PSI element.
 */
class UsePoetryQuickFix(sdk: Sdk?, module: Module) : LocalQuickFix {
    private val quickFixName = when {
        sdk != null && sdk.associatedModule != module -> "Fix Poetry interpreter"
        else -> "Use Poetry interpreter"
    }

    companion object {
        fun isApplicable(module: Module): Boolean = module.pyProjectToml != null

        fun setUpPoetry(project: Project, module: Module) {
            val sdksModel = ProjectSdksModel().apply {
                reset(project)
            }
            val existingSdks = sdksModel.sdks.filter { it.sdkType is PythonSdkType }
            // XXX: Should we show an error message on exceptions and on null?
            val newSdk = setupPoetrySdkUnderProgress(project, module, existingSdks, null, null, false) ?: return
            val existingSdk = existingSdks.find { isPoetry(project.basePath, it.homePath) && it.homePath == newSdk.homePath }
            val sdk = existingSdk ?: newSdk
            if (sdk == newSdk) {
                SdkConfigurationUtil.addSdk(newSdk)
            } else {
                sdk.associateWithModule(module, null)
            }
            project.pythonSdk = sdk
            module.pythonSdk = sdk
        }
    }

    override fun getFamilyName() = quickFixName

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
        // Invoke the setup later to escape the write action of the quick fix in order to show the modal progress dialog
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || module.isDisposed) return@invokeLater
            setUpPoetry(project, module)
        }
    }
}

/**
 * A quick-fix for installing packages specified in Pipfile.lock.
 */
class PoetryInstallQuickFix : LocalQuickFix {
    companion object {
        fun poetryInstall(project: Project, module: Module) {
            val sdk = module.pythonSdk ?: return
            if (!isPoetry(project.basePath, sdk.homePath)) return
            val listener = PyPackageRequirementsInspection.RunningPackagingTasksListener(module)
            val ui = PyPackageManagerUI(project, sdk, listener)
            ui.install(null, listOf())
        }
    }

    override fun getFamilyName() = "Install requirements from poetry.lock"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
        poetryInstall(project, module)
    }
}

/**
 * Watches for edits in PyProjectToml inside modules with a poetry SDK set.
 */
class PyProjectTomlWatcher : EditorFactoryListener {
    private val changeListenerKey = Key.create<DocumentListener>("PyProjectToml.change.listener")
    private val notificationActive = Key.create<Boolean>("PyProjectToml.notification.active")

    override fun editorCreated(event: EditorFactoryEvent) {
        val project = event.editor.project
        if (project == null || !isPyProjectTomlEditor(event.editor)) return
        val listener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val document = event.document
                val module = document.virtualFile?.getModule(project) ?: return
                if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
                    notifyPyProjectTomlChanged(module)
                }
            }
        }
        with(event.editor.document) {
            addDocumentListener(listener)
            putUserData(changeListenerKey, listener)
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val listener = event.editor.getUserData(changeListenerKey) ?: return
        event.editor.document.removeDocumentListener(listener)
    }

    private fun notifyPyProjectTomlChanged(module: Module) {
        if (module.getUserData(notificationActive) == true) return
        val what = when {
            module.pyProjectTomlLock == null -> "not found"
            else -> "out of date"
        }
        val title = "$POETRY_LOCK is $what"
        val content = "Run <a href='#lock'>poetry lock</a> or <a href='#update'>poetry update</a>"
        val notification = LOCK_NOTIFICATION_GROUP.createNotification(title = title, content = content, listener = NotificationListener { notification, event ->
            notification.expire()
            module.putUserData(notificationActive, null)
            FileDocumentManager.getInstance().saveAllDocuments()
            when (event.description) {
                "#lock" ->
                    runPoetryInBackground(module, listOf("lock"), "Locking $POETRY_LOCK")
                "#update" ->
                    runPoetryInBackground(module, listOf("update"), "Updating Poetry environment")
            }
        })
        module.putUserData(notificationActive, true)
        notification.whenExpired {
            module.putUserData(notificationActive, null)
        }
        notification.notify(module.project)
    }

    private fun runPoetryInBackground(module: Module, args: List<String>, description: String) {
        val task = object : Task.Backgroundable(module.project, StringUtil.toTitleCase(description), true) {
            override fun run(indicator: ProgressIndicator) {
                val sdk = module.pythonSdk ?: return
                indicator.text = "$description..."
                try {
                    runPoetry(sdk, *args.toTypedArray())
                } catch (e: RunCanceledByUserException) {
                } catch (e: ExecutionException) {
                    runInEdt {
                        Messages.showErrorDialog(project, e.toString(), CommonBundle.message("title.error"))
                    }
                } finally {
                    PythonSdkUtil.getSitePackagesDirectory(sdk)?.refresh(true, true)
                    sdk.associatedModule?.baseDir?.refresh(true, false)
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    private fun isPyProjectTomlEditor(editor: Editor): Boolean {
        val file = editor.document.virtualFile ?: return false
        if (file.name != PY_PROJECT_TOML) return false
        val project = editor.project ?: return false
        val module = file.getModule(project) ?: return false
        if (module.pyProjectToml != file) return false
        val basePath = project.basePath ?: return false
        val pythonPath = module.pythonSdk?.homePath ?: return false
        return isPoetry(basePath, pythonPath)
//        return module.pythonSdk?.isPoetry == true
    }
}

private val Document.virtualFile: VirtualFile?
    get() = FileDocumentManager.getInstance().getFile(this)

private fun VirtualFile.getModule(project: Project): Module? =
        ModuleUtil.findModuleForFile(this, project)

private val LOCK_NOTIFICATION_GROUP = NotificationGroup("$PY_PROJECT_TOML Watcher", NotificationDisplayType.STICKY_BALLOON, false)

private val Sdk.packageManager: PyPackageManager
    get() = PyPackageManagers.getInstance().forSdk(this)


@TestOnly
fun getPyProjectTomlLockRequirements(virtualFile: VirtualFile, packageManager: PyPackageManager): List<PyRequirement>? {
    fun toRequirements(packages: Map<String, PyProjectTomlLockPackage>): List<PyRequirement> =
            packages
                    .asSequence()
                    .filterNot { (_, pkg) -> pkg.editable ?: false }
                    // TODO: Support requirements markers (PEP 496), currently any packages with markers are ignored due to PY-30803
                    .filter { (_, pkg) -> pkg.markers == null }
                    .flatMap { (name, pkg) -> packageManager.parseRequirements("$name${pkg.version ?: ""}").asSequence() }
                    .toList()

    val pyProjectTomlLock = parsePyProjectTomlLock(virtualFile) ?: return null
    val packages = pyProjectTomlLock.packages?.let { toRequirements(it) } ?: emptyList()
    val devPackages = pyProjectTomlLock.devPackages?.let { toRequirements(it) } ?: emptyList()
    return packages + devPackages
}

private fun Sdk.parsePyProjectTomlLock(): PyProjectTomlLock? {
    // TODO: Log errors if poetry.lock is not found
    val file = pyProjectTomlLock ?: return null
    return parsePyProjectTomlLock(file)
}

private fun parsePyProjectTomlLock(virtualFile: VirtualFile): PyProjectTomlLock? {
    val text = ReadAction.compute<String, Throwable> { FileDocumentManager.getInstance().getDocument(virtualFile)?.text }
    return try {
        Gson().fromJson(text, PyProjectTomlLock::class.java)
    } catch (e: JsonSyntaxException) {
        // TODO: Log errors
        return null
    }
}

val Sdk.pyProjectTomlLock: VirtualFile?
    get() =
        associatedModulePath?.let { StandardFileSystems.local().findFileByPath(it)?.findChild(POETRY_LOCK) }

private val Module.pyProjectTomlLock: VirtualFile?
    get() = baseDir?.findChild(POETRY_LOCK)

private data class PyProjectTomlLock(@SerializedName("_meta") var meta: PyProjectTomlLockMeta?,
                                     @SerializedName("default") var packages: Map<String, PyProjectTomlLockPackage>?,
                                     @SerializedName("develop") var devPackages: Map<String, PyProjectTomlLockPackage>?)

private data class PyProjectTomlLockMeta(@SerializedName("sources") var sources: List<PyProjectTomlLockSource>?)

private data class PyProjectTomlLockSource(@SerializedName("url") var url: String?)

private data class PyProjectTomlLockPackage(@SerializedName("version") var version: String?,
                                            @SerializedName("editable") var editable: Boolean?,
                                            @SerializedName("hashes") var hashes: List<String>?,
                                            @SerializedName("markers") var markers: String?)

