package com.koxudaxi.poetry

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class PoetryPySdkConfigurationCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    companion object {

        internal enum class Source { CONFIGURATOR, INSPECTION }
        internal enum class InputData { NOT_FILLED, SPECIFIED }
        internal enum class PoetryResult { CREATION_FAILURE, NO_EXECUTABLE, NO_EXECUTABLE_FILE, CREATED }

        internal fun logPoetryDialog(project: Project, permitted: Boolean, source: Source, poetryPath: InputData) {
            poetryDialogEvent.log(project, permitted.asDialogResult, source, poetryPath)
        }

        internal fun logPoetry(project: Project, result: PoetryResult): Unit = poetryEvent.log(project, result)

        private val GROUP = EventLogGroup("python.sdk.configuration", 1)

        private enum class DialogResult { OK, CANCELLED, SKIPPED }
        private val Boolean.asDialogResult: DialogResult
            get() = if (this) DialogResult.OK else DialogResult.CANCELLED

        private val poetryDialogEvent = GROUP.registerEvent(
                "poetry.dialog.closed",
                EventFields.Enum("dialog_result", DialogResult::class.java),
                EventFields.Enum("source", Source::class.java),
                EventFields.Enum("poetry_path", InputData::class.java)
        )

        private val poetryEvent = GROUP.registerEvent("poetry.created", EventFields.Enum("env_result", PoetryResult::class.java))
    }
}