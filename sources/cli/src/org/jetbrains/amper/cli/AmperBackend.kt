/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.core.use
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.runTasksAndReportOnFailure
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.SchemaBasedModelImport
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.tasks.BuildTask
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.PublishTask
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.TestTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.PlatformUtil
import org.jetbrains.annotations.TestOnly
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.pathString

class AmperBackend(val context: ProjectContext) {
    private val resolvedModel: Model by lazy {
        with(CliProblemReporterContext()) {
            val model = spanBuilder("loading model")
                .setAttribute("root", context.projectRoot.path.pathString)
                .startSpan().use {
                    when (val result = SchemaBasedModelImport.getModel(context.projectContext)) {
                        is Result.Failure -> throw result.exception
                        is Result.Success -> result.value
                    }
                }

            if (problemReporter.wereProblemsReported()) {
                userReadableError("failed to build tasks graph, refer to the errors above")
            }

            for ((moduleUserReadableName, moduleList) in model.modules.groupBy { it.userReadableName }) {
                if (moduleList.size > 1) {
                    userReadableError("Module name '${moduleUserReadableName}' is not unique, it's declared in " +
                    moduleList.joinToString(" ") { (it.source as? PotatoModuleFileSource)?.buildFile?.toString() ?: "" })
                }
            }

            model
        }
    }

    private val taskGraph: TaskGraph by lazy {
        ProjectTasksBuilder(context = context, model = resolvedModel).build()
    }

    private val taskExecutor: TaskExecutor by lazy {
        val progress = TaskProgressRenderer(context.terminal, context.backgroundScope)
        TaskExecutor(taskGraph, context.taskExecutionMode, progress)
    }

    /**
     * Called by the 'build' command. Compiles and links all code in the project.
     *
     * If [platforms] is specified, only compilation/linking for those platforms should be run.
     */
    suspend fun build(platforms: Set<Platform>? = null) {
        if (platforms != null) {
            logger.info("Compiling for platforms: ${platforms.map { it.name }.sorted().joinToString(" ")}")
        }

        val possibleCompilationPlatforms = if (OsFamily.current.isMac) {
            Platform.leafPlatforms
        } else {
            // Apple targets could be compiled only on Mac OS X due to legal obstacles
            Platform.leafPlatforms.filter { !it.isDescendantOf(Platform.APPLE) }.toSet()
        }

        val platformsToCompile: Set<Platform> = platforms ?: possibleCompilationPlatforms
        val taskNames = taskGraph
            .tasks
            .filterIsInstance<BuildTask>()
            .filter { platformsToCompile.contains(it.platform) }
            .map { it.taskName }
            .toSet()
        logger.info("Selected tasks to compile: ${taskNames.sortedBy { it.name }.joinToString(" ") { it.name }}")
        taskExecutor.runTasksAndReportOnFailure(taskNames)
    }

    suspend fun runTask(taskName: TaskName): kotlin.Result<TaskResult>? = taskExecutor.runTasksAndReportOnFailure(setOf(taskName))[taskName]

    @TestOnly
    fun tasks() = taskGraph.tasks.toList()

    fun showTasks() {
        for (taskName in taskGraph.tasks.map { it.taskName }.sortedBy { it.name }) {
            context.terminal.println(buildString {
                append("task ${taskName.name}")
                taskGraph.dependencies[taskName]?.let { taskDeps ->
                    append(" -> ${taskDeps.joinToString { it.name }}")
                }
            })
        }
    }

    @TestOnly
    fun modules(): List<PotatoModule> = resolvedModel.modules

    fun showModules() {
        for (moduleName in resolvedModel.modules.map { it.userReadableName }.sorted()) {
            context.terminal.println(moduleName)
        }
    }

    suspend fun publish(modules: Set<String>?, repositoryId: String) {
        require(modules == null || modules.isNotEmpty())

        if (modules != null) {
            for (moduleName in modules) {
                val module = resolvedModel.modules.firstOrNull { it.userReadableName == moduleName }
                    ?: userReadableError("Unable to resolve module by name '$moduleName'.\n\n" +
                                "Available modules: ${availableModulesString()}")

                if (module.mavenRepositories.any { it.id == repositoryId }) {
                    userReadableError("Module '$moduleName' does not have repository with id '$repositoryId'")
                }
            }
        }

        val publishTasks = taskGraph.tasks
            .filterIsInstance<PublishTask>()
            .filter { it.targetRepository.id == repositoryId }
            .filter { modules == null || modules.contains(it.module.userReadableName) }
            .map { it.taskName }
            .toSet()

        if (publishTasks.isEmpty()) {
            userReadableError("No publish tasks were found for specified module and repository filters")
        }

        context.terminal.println("Tasks that will be executed:\n" +
            publishTasks.sorted().joinToString("\n"))

        taskExecutor.runTasksAndReportOnFailure(publishTasks)
    }

    suspend fun test(
        includeModules: Set<String>? = null,
        platforms: Set<Platform>? = null,
        excludeModules: Set<String> = emptySet(),
    ) {
        require(platforms == null || platforms.isNotEmpty())

        val moduleNamesToCheck = (includeModules ?: emptySet()) + excludeModules
        for (moduleName in moduleNamesToCheck) {
            if (resolvedModel.modules.none { it.userReadableName == moduleName }) {
                userReadableError("Unable to resolve module by name '$moduleName'.\n\n" +
                        "Available modules: ${availableModulesString()}")
            }
        }

        if (platforms != null) {
            for (platform in platforms) {
                if (!PlatformUtil.platformsMayRunOnCurrentSystem.contains(platform)) {
                    userReadableError(
                        "Unable to run platform '$platform' on current system.\n\n" +
                                "Available platforms on current system: " +
                                PlatformUtil.platformsMayRunOnCurrentSystem.map { it.name }.sorted().joinToString(" ")
                    )
                }
            }
        }

        val allTestTasks = taskGraph.tasks.filterIsInstance<TestTask>()
        if (allTestTasks.isEmpty()) {
            userReadableError("No test tasks were found in the entire project")
        }

        val platformTestTasks = allTestTasks
            .filter { platforms == null || platforms.contains(it.platform) }
        if (platforms != null && platformTestTasks.isEmpty()) {
            userReadableError("No test tasks were found for platforms: " +
                    platforms.map { it.name }.sorted().joinToString(" ")
            )
        }

        val includedTestTasks = if (includeModules != null) {
            platformTestTasks.filter { task -> includeModules.contains(task.module.userReadableName) }
        } else {
            platformTestTasks
        }
        if (includedTestTasks.isEmpty()) {
            userReadableError("No test tasks were found for specified include filters")
        }

        val testTasks = includedTestTasks
            .filter { task -> !excludeModules.contains(task.module.userReadableName) }
            .map { it.taskName }
            .toSet()
        if (testTasks.isEmpty()) {
            userReadableError("No test tasks were found after applying exclude filters")
        }

        taskExecutor.runTasksAndReportOnFailure(testTasks)
    }

    suspend fun runApplication(moduleName: String? = null, platform: Platform? = null, buildType: BuildType? = BuildType.Debug) {
        val moduleToRun = if (moduleName != null) {
            resolvedModel.modules.firstOrNull { it.userReadableName == moduleName }
                ?: userReadableError("Unable to resolve module by name '$moduleName'.\n\nAvailable modules: ${availableModulesString()}")
        } else {
            val candidates = resolvedModel.modules.filter { it.type.isApplication() }
            when {
                candidates.isEmpty() -> userReadableError("No modules in the project with application type")
                candidates.size > 1 ->
                    userReadableError(
                        "There are several application modules in the project. Please specify one with '--module' argument.\n\n" +
                                "Available application modules: ${candidates.map { it.userReadableName }.sorted()}"
                    )

                else -> candidates.single()
            }
        }

        val moduleRunTasks = taskGraph.tasks.filterIsInstance<RunTask>()
            .filter { it.module == moduleToRun }
            .filter { it.buildType == buildType }
        if (moduleRunTasks.isEmpty()) {
            userReadableError("No run tasks are available for module '${moduleToRun.userReadableName}'")
        }

        fun availablePlatformsForModule() = moduleRunTasks.map { it.platform.pretty }.sorted().joinToString(" ")

        val task: RunTask = if (platform == null) {
            if (moduleRunTasks.size == 1) {
                moduleRunTasks.single()
            } else {
                userReadableError("""
                    Multiple platforms are available to run in module '${moduleToRun.userReadableName}'.
                    Please specify one with '--platform' argument.

                    Available platforms: ${availablePlatformsForModule()}
                """.trimIndent())
            }
        } else {
            moduleRunTasks.firstOrNull { it.platform == platform }
                ?: userReadableError("""
                    Platform '${platform.pretty}' is not found for module '${moduleToRun.userReadableName}'.

                    Available platforms: ${availablePlatformsForModule()}
                """.trimIndent())
        }

        runTask(task.taskName)
    }

    private fun availableModulesString() =
        resolvedModel.modules.map { it.userReadableName }.sorted().joinToString(" ")

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
}
