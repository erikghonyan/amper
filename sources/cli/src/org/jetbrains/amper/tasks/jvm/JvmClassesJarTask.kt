/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.jar.JarConfig
import org.jetbrains.amper.jar.JarInputDir
import org.jetbrains.amper.jvm.findEffectiveJvmMainClass
import org.jetbrains.amper.tasks.AbstractJarTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

/**
 * Creates a jar file containing all the JVM classes produced by task dependencies of type [JvmCompileTask].
 */
class JvmClassesJarTask(
    override val taskName: TaskName,
    private val module: PotatoModule,
    private val isTest: Boolean,
    private val taskOutputRoot: TaskOutputRoot,
    executeOnChangedInputs: ExecuteOnChangedInputs,
) : AbstractJarTask(taskName, executeOnChangedInputs) {

    override fun getInputDirs(dependenciesResult: List<TaskResult>): List<JarInputDir> {
        val compileTaskResults = dependenciesResult.filterIsInstance<JvmCompileTask.Result>()
        require(compileTaskResults.isNotEmpty()) {
            "Call Jar task without any compilation dependency"
        }
        return compileTaskResults.map { JarInputDir(path = it.classesOutputRoot, destPathInJar = Path(".")) }
    }

    // TODO add version here?
    override fun outputJarPath(): Path {
        val testSuffix = if (isTest) "-test" else ""
        return taskOutputRoot.path / "${module.userReadableName}$testSuffix-jvm.jar"
    }

    override fun jarConfig(): JarConfig = JarConfig(
        mainClassFqn = if (module.type.isApplication()) module.fragments.findEffectiveJvmMainClass() else null
    )

    override fun createResult(jarPath: Path): AbstractJarTask.Result =
        Result(jarPath)

    class Result(jarPath: Path) :
        AbstractJarTask.Result(jarPath)
}
