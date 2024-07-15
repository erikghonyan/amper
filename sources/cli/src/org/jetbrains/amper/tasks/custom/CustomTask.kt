/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.AddToModuleRootsFromCustomTask
import org.jetbrains.amper.frontend.CompositeString
import org.jetbrains.amper.frontend.CompositeStringPart
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.KnownCurrentTaskProperty
import org.jetbrains.amper.frontend.KnownModuleProperty
import org.jetbrains.amper.frontend.PublishArtifactFromCustomTask
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.jvm.findEffectiveJvmMainClass
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

class CustomTask(
    private val custom: CustomTaskDescription,
    private val taskOutputRoot: TaskOutputRoot,
    private val userCacheRoot: AmperUserCacheRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val terminal: Terminal,
): Task {
    override val taskName: TaskName
        get() = custom.name

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        // do not clean custom task output
        // it's a responsibility of the custom task itself right now
        // in the future, we want to automatically track what's accessed by custom task and
        // call it only if something changed on subsequent runs
        taskOutputRoot.path.createDirectories()

        val codeModule = custom.customTaskCodeModule
        val fragments = codeModule.fragments

        check(codeModule.type == ProductType.JVM_APP) {
            "Custom task module '${codeModule.userReadableName}' should have 'jvm/app' type"
        }

        val effectiveMainClassFqn = fragments.findEffectiveJvmMainClass()
            ?: error("Main Class was not found for ${codeModule.userReadableName} in any of the following " +
                    "source directories:\n" + fragments.joinToString("\n") { it.src.pathString })

        val jvmRuntimeClasspathTask = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>()
            .singleOrNull { it.module == codeModule && !it.isTest}
            ?: error("${JvmRuntimeClasspathTask::class.simpleName} result for module '${codeModule.userReadableName}' is not found in dependencies")

        val jdk = JdkDownloader.getJdk(userCacheRoot)

        val workingDir = custom.source.parent

        // TODO add a span?

        val result = jdk.runJava(
            workingDir = workingDir,
            mainClass = effectiveMainClassFqn,
            classpath = jvmRuntimeClasspathTask.jvmRuntimeClasspath,
            programArgs = custom.programArguments.map { interpolateString(it, dependenciesResult) },
            jvmArgs = custom.jvmArguments.map { interpolateString(it, dependenciesResult) },
            environment = custom.environmentVariables.map { it.key to interpolateString(it.value, dependenciesResult) }.toMap(),
            outputListener = PrintToTerminalProcessOutputListener(terminal),
            tempRoot = tempRoot,
        )

        // Move into runJava and under runJava span?
        val message = "Process exited with exit code ${result.exitCode}" +
                (if (result.stderr.isNotEmpty()) "\nSTDERR:\n${result.stderr}\n" else "") +
                (if (result.stdout.isNotEmpty()) "\nSTDOUT:\n${result.stdout}\n" else "")
        if (result.exitCode != 0) {
            userReadableError(message)
        } else {
            logger.info(message)
        }

        custom.addToModuleRootsFromCustomTask.forEach { addToSourceSet ->
            val path = taskOutputRoot.path.resolve(addToSourceSet.taskOutputRelativePath).normalize()
            if (!path.startsWith(taskOutputRoot.path)) {
                // TODO Move to frontend and BuildProblems?
                userReadableError("Task output relative path '${addToSourceSet.taskOutputRelativePath}'" +
                        "must be under task output '${taskOutputRoot.path}', but got: $path")
            }

            if (!path.exists()) {
                userReadableError("After running a custom task '${custom.name.name}' output file or folder '$path'" +
                        "is not found, but required for module source sets")
            }
        }

        custom.publishArtifacts.forEach { publish ->
            // TODO wildcard matching support?
            val path = taskOutputRoot.path.resolve(publish.pathWildcard).normalize()
            if (!path.startsWith(taskOutputRoot.path)) {
                // TODO Move to frontend and BuildProblems?
                userReadableError("Task output relative path '${publish.pathWildcard}'" +
                        "must be under task output '${taskOutputRoot.path}', but got: $path")
            }

            if (!path.isRegularFile()) {
                userReadableError("After running a custom task '${custom.name.name}' output file or folder '$path'" +
                        "is not found, but required for publishing")
            }
        }

        return Result(
            outputDirectory = taskOutputRoot.path,
            artifactsToPublish = custom.publishArtifacts,
            moduleRoots = custom.addToModuleRootsFromCustomTask,
        )
    }

    private fun interpolateString(compositeString: CompositeString, dependencies: List<TaskResult>): String {
        val stringChunks = compositeString.parts.map { part ->
            when (part) {
                is CompositeStringPart.Literal -> part.value
                is CompositeStringPart.ModulePropertyReference -> {
                    when (part.property) {
                        KnownModuleProperty.VERSION -> {
                            val commonFragment = part.referencedModule.fragments
                                .find { !it.isTest && it.fragmentDependencies.isEmpty() }
                            commonFragment?.settings?.publishing?.version
                                ?: userReadableError("Version is not defined for module '${part.referencedModule.userReadableName}', but it's referenced in custom task '${taskName.name}'")
                        }
                        KnownModuleProperty.NAME -> part.referencedModule.userReadableName
                        KnownModuleProperty.JVM_RUNTIME_CLASSPATH -> {
                            val jvmRuntimeClasspathResult = dependencies
                                .filterIsInstance<JvmRuntimeClasspathTask.Result>()
                                .single { it.module == part.referencedModule && !it.isTest }
                            jvmRuntimeClasspathResult.jvmRuntimeClasspath.joinToString(File.pathSeparator) { it.pathString }
                        }
                    }
                }

                is CompositeStringPart.CurrentTaskProperty -> {
                    when (part.property) {
                        KnownCurrentTaskProperty.OUTPUT_DIRECTORY -> {
                            taskOutputRoot.path.pathString
                        }
                    }
                }
/*
                is CompositeStringPart.TaskPropertyReference -> {
                    val taskResult = dependencies.firstOrNull { it.taskName == part.taskName }
                    // This is an internal error since Amper should automatically add this dependency
                        ?: error("Task '${part.taskName.name}' is not in dependencies of task '${taskName.name}', " +
                                "but referenced by '${part.originalReferenceText}' in task parameters")

                    val value = taskResult.outputProperties[part.propertyName.propertyName]
                        ?: userReadableError("Task output property '${part.propertyName.propertyName}' is not found in " +
                                "task '${part.taskName}' results")

                    value
                }
*/
            }
        }
        return stringChunks.joinToString(separator = "")
    }

    class Result(
        val outputDirectory: Path,
        val artifactsToPublish: List<PublishArtifactFromCustomTask>,
        val moduleRoots: List<AddToModuleRootsFromCustomTask>,
    ): TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
