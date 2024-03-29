// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.koxudaxi.poetry

import com.google.gson.annotations.SerializedName
import com.intellij.CommonBundle
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.process.ProcessOutput
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationDisplayType
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
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.PathUtil
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.packaging.*
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.*
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.statistics.modules
import icons.PythonIcons
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlInvalidTypeException
import org.apache.tuweni.toml.TomlParseResult
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Nullable
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.TestOnly
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlTableHeader
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier
import java.util.regex.Pattern
import kotlin.reflect.full.memberProperties

const val PY_PROJECT_TOML: String = "pyproject.toml"
const val POETRY_LOCK: String = "poetry.lock"
const val POETRY_DEFAULT_SOURCE_URL: String = "https://pypi.org/simple"
const val POETRY_PATH_SETTING: String = "PyCharm.Poetry.Path"
const val REPLACE_PYTHON_VERSION = """import re,sys;f=open("pyproject.toml", "r+");orig=f.read();f.seek(0);f.write(re.sub(r"(python = \"\^)[^\"]+(\")", "\g<1>"+'.'.join(str(v) for v in sys.version_info[:2])+"\g<2>", orig))"""

// TODO: Provide a special icon for poetry
// TODO: Need a extension point
val POETRY_ICON = PythonIcons.Python.Virtualenv

/**
 *  This source code is edited by @koxudaxi  (Koudai Aono)
 */

fun getPyProjectTomlForPoetry(virtualFile: VirtualFile): Pair<Long, VirtualFile?> {
    return Pair(virtualFile.modificationStamp, try {
        ReadAction.compute<VirtualFile, Throwable> {
            Toml.parse(virtualFile.inputStream).getTable("tool.poetry")?.let { virtualFile }
        }
    } catch (e: Throwable) {
        null
    })
}

/**
 * The PyProject.toml found in the main content root of the module.
 */
val pyProjectTomlCache = mutableMapOf<String, Pair<Long, VirtualFile?>>()
val Module.pyProjectToml: VirtualFile?
    get() =
        baseDir?.findChild(PY_PROJECT_TOML)?.let { virtualFile ->
            (this.name + virtualFile.path).let { key ->
                pyProjectTomlCache.getOrPut(key, { getPyProjectTomlForPoetry(virtualFile) }).let { pair ->
                    when (virtualFile.modificationStamp) {
                        pair.first -> pair.second
                        else -> pyProjectTomlCache.put(key, getPyProjectTomlForPoetry(virtualFile))?.second
                    }
                }
            }
        }

/**
 * Tells if the SDK was added as a poetry.
 */

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
        SystemInfo.isWindows -> "poetry.bat"
        else -> "poetry"
    }
    return PathEnvironmentVariableUtil.findInPath(name) ?: System.getProperty("user.home")?.let { homePath ->
        File(homePath + File.separator + ".poetry" + File.separator + "bin" + File.separator + name).takeIf { it.exists() }
    }
}

/**
 * Returns the configured poetry executable or detects it automatically.
 */
fun getPoetryExecutable(): File? =
        PropertiesComponent.getInstance().poetryPath?.let { File(it) }?.takeIf { it.exists() } ?: detectPoetryExecutable()

fun validatePoetryExecutable(poetryExecutable: @SystemDependent String?): ValidationInfo? {
    val message = if (poetryExecutable.isNullOrBlank()) {
        "Poetry executable is not found"
    }
    else {
        val file = File(poetryExecutable)
        when {
            !file.exists() -> "File ${file.absolutePath} is not found"
            !file.canExecute() || !file.isFile -> "Cannot execute ${file.absolutePath}"
            else -> null
        }
    }

    return message?.let { ValidationInfo(it) }
}

fun suggestedSdkName(basePath: @NlsSafe String): @NlsSafe String = "Poetry (${PathUtil.getFileName(basePath)})"


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
                                installPackages: Boolean,
                                poetryPath: String? = null): Sdk? {
    val projectPath = newProjectPath ?: module?.basePath ?: project?.basePath ?: return null
    val task = object : Task.WithResult<String, ExecutionException>(project, "Setting Up Poetry Environment", true) {
        override fun compute(indicator: ProgressIndicator): String {
            indicator.isIndeterminate = true
            val poetry = when (poetryPath) {
                is String -> poetryPath
                else -> {
                    val init = StandardFileSystems.local().findFileByPath(projectPath)?.findChild(PY_PROJECT_TOML)?.let { getPyProjectTomlForPoetry(it) } == null
                    setupPoetry(FileUtil.toSystemDependentName(projectPath), python, installPackages, init)
                }
            }
            return getPythonExecutable(poetry)
        }
    }

    return createSdkByGenerateTask(task, existingSdks, null, projectPath, suggestedSdkName(projectPath))?.apply {
        isPoetry = true
        associateWithModule(module ?: project?.modules?.firstOrNull(), newProjectPath)
//        project?.let { project ->
//            existingSdks.find {
//                it.associatedModulePath == projectPath && isPoetry(project, it) && it.homePath == homePath
//            }?.run {
//                 re-use existing invalid sdk
//                return null
//            }
//            PoetryConfigService.getInstance(project).poetryVirtualenvPaths.add(homePath!!)
//        }
    }
}

/**
 * Sets up the poetry environment for the specified project path.
 *
 * @return the path to the poetry environment.
 */
fun setupPoetry(projectPath: @SystemDependent String, python: String?, installPackages: Boolean, init: Boolean): @SystemDependent String {
    if (init) {
        runPoetry(projectPath, *listOf("init", "-n").toTypedArray())
        if (python != null) {
            // Replace python version in toml
            runCommand(projectPath, python, "-c", REPLACE_PYTHON_VERSION)
        }
    }
    when {
        installPackages -> {
            python?.let { runPoetry(projectPath, "env", "use", it) }
            runPoetry(projectPath, "install")
        }
        python != null -> runPoetry(projectPath, "env", "use", python)
        else -> runPoetry(projectPath, "run", "python", "-V")
    }
    return runPoetry(projectPath, "env", "info", "-p")
}


@Deprecated("This function is for backward compatibility")
fun isPoetryFromConfig(project: Project, sdk: Sdk): Boolean {
    return PoetryConfigService.getInstance(project).poetryVirtualenvPaths.contains(sdk.homePath)
}


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
 * Runs the configured poetry for the specified Poetry SDK with the associated project path.
 */
fun runPoetry(sdk: Sdk, vararg args: String): String {
    val projectPath = sdk.associatedModulePath
            ?: throw PyExecutionException("Cannot find the project associated with this Poetry environment",
                    "Poetry", emptyList(), ProcessOutput())
    runPoetry(projectPath, "env", "use", sdk.homePath!!)
    return runPoetry(projectPath, *args)
}

/**
 * Runs the configured poetry for the specified project path.
 */
fun runPoetry(projectPath: @SystemDependent String?, vararg args: String): String {
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
            else -> stdout.trim()
        }
    }
}

fun runCommand(projectPath: @SystemDependent String, command: String, vararg args: String): String {
    val commandLine = GeneralCommandLine(listOf(command) + args).withWorkDirectory(projectPath)
    val handler = CapturingProcessHandler(commandLine)

    val result = with(handler) {
        runProcess()
    }
    return with(result) {
        when {
            isCancelled ->
                throw RunCanceledByUserException()
            exitCode != 0 ->
                throw PyExecutionException("Error Running", command, args.asList(),
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
val Sdk.poetrySources: List<String>
    // TODO parse pyproject.toml for tool.poetry.source.url
    get() = listOf(POETRY_DEFAULT_SOURCE_URL)

/**
 * The list of requirements defined in the poetry.lock of the module associated with this SDK.
 */
//val Sdk.poetryLockRequirements: List<PyRequirement>?
//    get() {
//        return poetryLock?.let { PyPoetryPackageManager.getInstance(this).getRequirements() }
//    }

/**
 * A quick-fix for setting up the poetry for the module of the current PSI element.
 */
class UsePoetryQuickFix(sdk: Sdk?, module: Module) : LocalQuickFix {
    private val quickFixName = when {
        sdk != null && sdk.isAssociatedWithAnotherModule(module) -> "Fix Poetry interpreter"
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
            val newSdk = setupPoetrySdkUnderProgress(project, module, existingSdks, null, null, false)
                    ?: return
            val existingSdk = existingSdks.find { it.isPoetry && it.homePath == newSdk.homePath }
            val sdk = existingSdk ?: newSdk
            if (sdk == newSdk) {
                SdkConfigurationUtil.addSdk(newSdk)
            } else {
                sdk.associateWithModule(module, null)
            }
            project.pythonSdk = sdk
            module.pythonSdk = sdk
            PoetryConfigService.getInstance(project).poetryVirtualenvPaths.add(sdk.homePath!!)

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
            if (!sdk.isPoetry) return
            // TODO: create UI
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
    private val content: String = if (poetryVersion?.let { it < "1.1.1" } == true) {
        "Run <a href='#lock'>poetry lock</a> or <a href='#update'>poetry update</a>"
    } else {
        "Run <a href='#lock'>poetry lock</a>, <a href='#noupdate'>poetry lock --no-update</a> or <a href='#update'>poetry update</a>"
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        val project = event.editor.project
        if (project == null || !isPyProjectTomlEditor(event.editor)) return
        val listener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                try {
                    val document = event.document
                    val module = document.virtualFile?.getModule(project) ?: return
                    // TODO: Should we remove listener when a sdk is changed to non-poetry sdk?
//                    if (!isPoetry(module.project)) {
//                        with(document) {
//                            putUserData(notificationActive, null)
//                            val listener = getUserData(changeListenerKey) ?: return
//                            removeDocumentListener(listener)
//                            putUserData(changeListenerKey, null)
//                            return
//                        }
//                    }
                    if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
                        notifyPyProjectTomlChanged(module)
                    }
                } catch (e: AlreadyDisposedException) {
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
            module.poetryLock == null -> "not found"
            else -> "out of date"
        }
        val title = "$POETRY_LOCK is $what"
        val notification = LOCK_NOTIFICATION_GROUP.createNotification(title = title, content = content, listener = NotificationListener { notification, event ->
            FileDocumentManager.getInstance().saveAllDocuments()
            when (event.description) {
                "#lock" ->
                    runPoetryInBackground(module, listOf("lock"), "Locking $POETRY_LOCK")
                "#noupdate" ->
                    runPoetryInBackground(module, listOf("lock", "--no-update"), "Locking $POETRY_LOCK without updating")
                "#update" ->
                    runPoetryInBackground(module, listOf("update"), "Updating Poetry environment")
            }
            notification.expire()
            module.putUserData(notificationActive, null)
        })
        module.putUserData(notificationActive, true)
        notification.whenExpired {
            module.putUserData(notificationActive, null)
        }
        notification.notify(module.project)
    }

    private fun isPyProjectTomlEditor(editor: Editor): Boolean {
        val file = editor.document.virtualFile ?: return false
        if (file.name != PY_PROJECT_TOML) return false
        val project = editor.project ?: return false
        val module = file.getModule(project) ?: return false
        val sdk = module.pythonSdk ?: return false
        if (!sdk.isPoetry) return false
        return module.pyProjectToml == file
    }
}

private val Document.virtualFile: VirtualFile?
    get() = FileDocumentManager.getInstance().getFile(this)

private fun VirtualFile.getModule(project: Project): Module? =
        ModuleUtil.findModuleForFile(this, project)

private val LOCK_NOTIFICATION_GROUP = NotificationGroup("$PY_PROJECT_TOML Watcher", NotificationDisplayType.STICKY_BALLOON, false)

//private val Sdk.packageManager: PyPoetryPackageManager
//    get() = PyPoetryPackageManager.getInstance(this)
//

@TestOnly
fun getPoetryLockRequirements(virtualFile: VirtualFile, packageManager: PyPackageManager): List<PyRequirement>? {
    fun getVersion(version: String): String {
        return if (Regex("^[0-9]").containsMatchIn(version)) "==$version" else version
    }

    fun toRequirements(packages: Map<String, PoetryLockPackage>): List<PyRequirement> =
            packages
                    .asSequence()
//                    .filterNot { (_, pkg) -> pkg.editable ?: false }
                    // TODO: Support requirements markers (PEP 496), currently any packages with markers are ignored due to PY-30803
//                    .filter { (_, pkg) -> pkg.markers == null }
                    .flatMap { (name, pkg) -> packageManager.parseRequirements("$name${pkg.version?.let { getVersion(it) } ?: ""}").asSequence() }
                    .toList()

    //TODO: Support extras
    val poetryLock = parsePoetryLock(virtualFile) ?: return null
    return poetryLock.packages?.let { toRequirements(it) } ?: emptyList()
}

private fun Sdk.parsePoetryLock(): PoetryLock? {
    // TODO: Log errors if poetry.lock is not found
    val file = poetryLock ?: return null
    return parsePoetryLock(file)
}

val Sdk.poetryLock: VirtualFile?
    get() =
        associatedModulePath?.let { StandardFileSystems.local().findFileByPath(it)?.findChild(POETRY_LOCK) }

private val Module.poetryLock: VirtualFile?
    get() = baseDir?.findChild(POETRY_LOCK)

private fun parsePoetryLock(pyProjectToml: VirtualFile): PoetryLock? {
    val text = ReadAction.compute<String, Throwable> { FileDocumentManager.getInstance().getDocument(pyProjectToml)?.text }
    return try {
        val result: TomlParseResult = Toml.parse(text)
        val packages = result.getArrayOrEmpty("package")
        if (packages.isEmpty) return null
        PoetryLock(packages = packages.toList().filterIsInstance(TomlTable::class.java).map {
            Pair(it["name"] as String,
                    PoetryLockPackage(
                            version = it["version"] as? String
                    )
            )
        }.toMap())

//        return try {
//            Gson().fromJson(Toml.parse(text).toJson(), PoetryLock::class.java)
    } catch (e: Throwable) {
        if (e is IllegalArgumentException || e is TomlInvalidTypeException || e is ClassCastException) return null
        throw e
    }
}


private data class PoetryLock(
        @SerializedName("package") var packages: Map<String, PoetryLockPackage>?)

//private data class PoetryLockMeta(@SerializedName("sources") var sources: List<PoetryLockSource>?)

private data class PoetryLockSource(@SerializedName("url") var url: String?)

private data class PoetryLockPackage(@SerializedName("version") var version: String?,
//                                     @SerializedName("category") var category: String?,
//                                            @SerializedName("editable") var editable: Boolean?,
                                     @SerializedName("hashes") var hashes: List<String>? = null,
                                     @SerializedName("markers") var markers: MutableList<Any> = mutableListOf(),
                                     @SerializedName("extras") var extras: List<MutableMap<String, List<String>>>? = null)

fun runPoetryInBackground(module: Module, args: List<String>, description: String) {
    val task = object : Task.Backgroundable(module.project, StringUtil.toTitleCase(description), true) {
        override fun run(indicator: ProgressIndicator) {
            val sdk = module.pythonSdk ?: return
            indicator.text = "$description..."
            try {
                runPoetry(sdk, *args.toTypedArray())
            } catch (e: RunCanceledByUserException) {
            } catch (e: ExecutionException) {
                showSdkExecutionException(sdk, e, "Error Running Poetry")
            } finally {
                PythonSdkUtil.getSitePackagesDirectory(sdk)?.refresh(true, true)
                sdk.associatedModuleDir?.refresh(true, false)
                PyPackageManager.getInstance(sdk).refreshAndGetPackages(true)
            }
        }
    }
    ProgressManager.getInstance().run(task)
}

private fun allowCreatingNewEnvironments(project: Project?) =
        project != null || !PlatformUtils.isPyCharm() || PlatformUtils.isPyCharmEducational()

fun createPoetryPanel(project: Project?,
                      module: Module?,
                      existingSdks: List<Sdk>,
                      newProjectPath: String?,
                      context: UserDataHolder
): PyAddSdkPanel {
    val newPoetryPanel = when {
        allowCreatingNewEnvironments(project) -> PyAddNewPoetryPanel(project, module, existingSdks, null, context)
        else -> null
    }
    val existingPoetryPanel = PyAddExistingPoetryEnvPanel(project, module, existingSdks, null, context)
    val panels = listOfNotNull(newPoetryPanel, existingPoetryPanel)
    val existingSdkPaths = sdkHomes(existingSdks)
    val defaultPanel = when {
     detectPoetryEnvs(module, existingSdkPaths, project?.basePath
             ?: newProjectPath).any { it.isAssociatedWithModule(module) } -> existingPoetryPanel
        newPoetryPanel != null -> newPoetryPanel
        else -> existingPoetryPanel
    }
    return PyAddSdkGroupPanel(Supplier { "Poetry environment" },
            POETRY_ICON, panels, defaultPanel)
}


fun allModules(project: Project?): List<Module> {
    return project?.let {
        ModuleUtil.getModulesOfType(it, PythonModuleTypeBase.getInstance())
    }?.sortedBy { it.name } ?: emptyList()
}

fun sdkHomes(sdks: List<Sdk>): Set<String> = sdks.mapNotNull { it.homePath }.toSet()

fun detectPoetryEnvs(module: Module?, existingSdkPaths: Set<String>, projectPath: String?): List<PyDetectedSdk> {
    val path = module?.basePath ?: projectPath ?: return emptyList()
    return try {
        getPoetryEnvs(path).filterNot { existingSdkPaths.contains(getPythonExecutable(it)) }.map { PyDetectedSdk(it) }
    } catch (e: Throwable) {
        emptyList()
    }
}

fun getPoetryEnvs(projectPath: String): List<String> =
        syncRunPoetry(projectPath, "env", "list", "--full-path", defaultResult = emptyList()) { result ->
            result.lineSequence().mapNotNull { it.split(" ")[0] }.filterNot { it.isEmpty() }.toList()
        }


fun isVirtualEnvsInProject(projectPath: String): Boolean? =
        syncRunPoetry(projectPath, "config", "virtualenvs.in-project", defaultResult = null) {
            it.trim() == "true"
        }

val poetryVersion: String?
    get() = syncRunPoetry(null, "--version", defaultResult = "") {
        it.split(' ').lastOrNull()
    }

inline fun <reified T> syncRunCommand(projectPath: @SystemDependent String, command: String, vararg args: String, defaultResult: T, crossinline callback: (String) -> T): T {
    return try {
        ApplicationManager.getApplication().executeOnPooledThread<T> {
            try {
                val result = runCommand(projectPath, command, *args)
                callback(result)
            } catch (e: PyExecutionException) {
                defaultResult
            } catch (e: ProcessNotCreatedException) {
                defaultResult
            }
        }.get(30, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
        defaultResult
    }
}

inline fun <reified T> syncRunPoetry(projectPath: @SystemDependent String?, vararg args: String, defaultResult: T, crossinline callback: (String) -> T): T {
    return try {
        ApplicationManager.getApplication().executeOnPooledThread<T> {
            try {
                val result = runPoetry(projectPath, *args)
                callback(result)
            } catch (e: PyExecutionException) {
                defaultResult
            } catch (e: ProcessNotCreatedException) {
                defaultResult
            }
        }.get(30, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
        defaultResult
    }
}

fun getPythonExecutable(homePath: String): String =
        PythonSdkUtil.getPythonExecutable(homePath) ?: FileUtil.join(homePath, "bin", "python")

/**
 * Parses the output of `poetry show --outdated` into a list of packages.
 */
fun parsePoetryShowOutdated(input: String): Map<String, PoetryOutdatedVersion> {
    return input
            .lines()
            .mapNotNull { line ->
                line.split(Pattern.compile(" +"))
                        .takeIf { it.size > 3 }?.let { it[0] to PoetryOutdatedVersion(it[1], it[2]) }
            }.toMap()
}


data class PoetryOutdatedVersion(
        @SerializedName("currentVersion") var currentVersion: String,
        @SerializedName("latestVersion") var latestVersion: String)

