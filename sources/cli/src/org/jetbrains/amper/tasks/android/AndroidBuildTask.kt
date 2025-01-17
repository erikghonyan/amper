/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.android.AndroidModuleData
import org.jetbrains.amper.android.ApkPathAndroidBuildResult
import org.jetbrains.amper.android.ResolvedDependency
import org.jetbrains.amper.android.runAndroidBuild
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.repr
import org.jetbrains.amper.util.toAndroidRequestBuildType
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div

class AndroidBuildTask(
    val module: PotatoModule,
    private val buildType: BuildType,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val androidSdkPath: Path,
    private val fragments: List<Fragment>,
    private val projectRoot: AmperProjectRoot,
    private val taskOutputPath: TaskOutputRoot,
    private val buildLogsRoot: AmperBuildLogsRoot,
    override val taskName: TaskName,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val runtimeClasspathTaskResult = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>().singleOrNull()
            ?: error("${JvmRuntimeClasspathTask::class.simpleName} result is not found in dependencies of $taskName")
        val runtimeClasspath = runtimeClasspathTaskResult.jvmRuntimeClasspath

        val moduleGradlePath = module.gradlePath(projectRoot)
        val androidModuleData = AndroidModuleData(
            modulePath = moduleGradlePath,
            moduleClasses = listOf(),
            resolvedAndroidRuntimeDependencies = runtimeClasspath.map {
                ResolvedDependency("group", "artifact", "version", it)
            },
        )
        val request = AndroidBuildRequest(
            root = projectRoot.path,
            phase = AndroidBuildRequest.Phase.Build,
            modules = setOf(androidModuleData),
            buildTypes = setOf(buildType.toAndroidRequestBuildType),
            sdkDir = androidSdkPath,
            targets = setOf(moduleGradlePath),
        )
        val androidConfig = fragments.joinToString { it.settings.android.repr }
        val configuration = mapOf("androidConfig" to androidConfig)
        val executionResult = executeOnChangedInputs.execute(taskName.name, configuration, runtimeClasspath) {
            val logFileName = UUID.randomUUID()
            val gradleLogStdoutPath = buildLogsRoot.path / "gradle" / "build-$logFileName.stdout"
            val gradleLogStderrPath = buildLogsRoot.path / "gradle" / "build-$logFileName.stderr"
            gradleLogStdoutPath.createParentDirectories()
            val result = runAndroidBuild(
                request,
                taskOutputPath.path / "gradle-project",
                gradleLogStdoutPath,
                gradleLogStderrPath,
                ApkPathAndroidBuildResult::class.java,
                eventHandler = { it.handle(gradleLogStdoutPath, gradleLogStderrPath) }
            )
            ExecuteOnChangedInputs.ExecutionResult(result.paths.map { Path.of(it) }, mapOf())
        }
        taskOutputPath.path.createDirectories()
        executionResult
            .outputs
            .map {
                it.copyToRecursively(
                    taskOutputPath.path.resolve(it.fileName),
                    followLinks = false,
                    overwrite = true
                )
            }
        return Task(executionResult.outputs)
    }

    class Task(
        val artifacts: List<Path>,
    ) : TaskResult
}
