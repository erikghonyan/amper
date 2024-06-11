/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.compilation.kotlinNativeCompilerArgs
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.BuildTask
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.walkRecursively
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.pathString

class NativeCompileKlibTask(
    override val module: PotatoModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    override val isTest: Boolean,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
): BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
    }

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): TaskResult {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform isTest=$isTest")
        }

        // TODO The native compiler needs recursive dependencies
        val externalDependencies = dependenciesResult.walkRecursively()
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.compileClasspath }
            .distinct()
            .filter { !it.pathString.endsWith(".jar") }
            .toList()
        logger.warn("" +
                "native compile ${module.userReadableName} -- collected external dependencies" +
                if (externalDependencies.isNotEmpty()) "\n" else "" +
                externalDependencies.sorted().joinToString("\n").prependIndent("  ")
        )

        val compiledModuleDependencies = dependenciesResult
            .walkRecursively()
            .filterIsInstance<TaskResult>()
            .map { it.compiledKlib }
            .toList()

        // todo native resources are what exactly?

        // TODO kotlin version settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinUserSettings = fragments.mergedKotlinSettings()

        logger.info("native compile klib '${module.userReadableName}' -- ${fragments.joinToString(" ") { it.name }}")

        val configuration: Map<String, String> = mapOf(
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val libraryPaths = compiledModuleDependencies + externalDependencies
        val inputs = fragments.map { it.src } + libraryPaths

        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val artifact = taskOutputRoot.path.resolve(KotlinCompilationType.LIBRARY.outputFilename(module, platform, isTest))

            val tempFilesToDelete = mutableListOf<Path>()

            val nativeCompiler = downloadNativeCompiler(kotlinVersion, userCacheRoot)
            val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(kotlinVersion, kotlinUserSettings)
            try {
                val existingSourceRoots = fragments.map { it.src }.filter { it.exists() }
                val rootsToCompile = existingSourceRoots.ifEmpty {
                    // konanc does not want to compile an application with zero sources files,
                    // but it's a perfectly valid situation where all code is in shared libraries
                    val emptyKotlinFile = Files.createTempFile(tempRoot.path, "empty", ".kt")
                        .also { tempFilesToDelete.add(it) }
                    listOf(emptyKotlinFile)
                }

                val args = kotlinNativeCompilerArgs(
                    kotlinUserSettings = kotlinUserSettings,
                    compilerPlugins = compilerPlugins,
                    entryPoint = null,
                    libraryPaths = libraryPaths,
                    // can't pass fragments if we have no sources, because empty.kt wouldn't be part of them (konan fails)
                    fragments = if (existingSourceRoots.isEmpty()) emptyList() else fragments,
                    sourceFiles = rootsToCompile,
                    outputPath = artifact,
                    compilationType = KotlinCompilationType.LIBRARY,
                    include = null,
                )

                nativeCompiler.compile(args, tempRoot, module)
            } finally {
                for (tempPath in tempFilesToDelete) {
                    tempPath.deleteExisting()
                }
            }

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(artifact))
        }.outputs.single()

        return TaskResult(
            dependencies = dependenciesResult,
            compiledKlib = artifact,
            dependencyKlibs = libraryPaths,
            taskName = taskName,
        )
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val compiledKlib: Path,
        val dependencyKlibs: List<Path>,
        val taskName: TaskName,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
