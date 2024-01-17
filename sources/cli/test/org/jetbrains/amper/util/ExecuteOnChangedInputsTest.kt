/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExecuteOnChangedInputsTest {
    @TempDir
    lateinit var tempDir: Path

    private val executeOnChanged by lazy { ExecuteOnChangedInputs(AmperBuildOutputRoot(tempDir)) }
    private val executionsCount = AtomicInteger(0)

    @Test
    fun trackingFile() {
        val file = tempDir.resolve("file.txt")

        fun call() = runBlocking {
            executeOnChanged.execute("1", emptyMap(), listOf(file)) {
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(emptyList())
            }
        }

        // initial, MISSING state
        call()
        assertEquals(executionsCount.get(), 1)

        // up-to-date, file is still MISSING
        call()
        assertEquals(executionsCount.get(), 1)

        // changed
        file.writeText("1")
        call()
        assertEquals(executionsCount.get(), 2)

        // up-to-date
        call()
        assertEquals(executionsCount.get(), 2)
    }

    @Test
    fun trackingFileInSubdirectory() {
        val dir = tempDir.resolve("dir").also { it.createDirectories() }
        val file = dir.resolve("file.txt")

        fun call() = runBlocking {
            executeOnChanged.execute("1", emptyMap(), listOf(dir)) {
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(emptyList())
            }
        }

        // initial, MISSING state
        call()
        assertEquals(executionsCount.get(), 1)

        // up-to-date, file is still MISSING
        call()
        assertEquals(executionsCount.get(), 1)

        // changed
        file.writeText("1")
        call()
        assertEquals(executionsCount.get(), 2)

        // up-to-date
        call()
        assertEquals(executionsCount.get(), 2)
    }

    @Test
    fun `executes on missing output`() {
        runBlocking {
            val output = tempDir.resolve("out.txt")
            val result1 = executeOnChanged.execute("1", emptyMap(), emptyList()) {
                output.writeText("1")
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(listOf(output))
            }
            assertEquals(listOf(output), result1.outputs)
            assertEquals("1", output.readText())

            // up-to-date
            val result2 = executeOnChanged.execute("1", emptyMap(), emptyList()) {
                output.writeText("2")
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(listOf(output))
            }
            assertEquals(listOf(output), result2.outputs)
            assertEquals("1", output.readText())

            output.deleteExisting()

            // output was deleted
            val result3 = executeOnChanged.execute("1", emptyMap(), emptyList()) {
                output.writeText("3")
                executionsCount.incrementAndGet()
                ExecuteOnChangedInputs.ExecutionResult(listOf(output))
            }
            assertEquals(listOf(output), result3.outputs)
            assertEquals("3", output.readText())
        }
        assertEquals(2, executionsCount.get())
    }

    @Test
    fun `reporting missing output must fail`() {
        assertFailsWith(NoSuchFileException::class) {
            runBlocking {
                executeOnChanged.execute("1", emptyMap(), emptyList()) {
                    ExecuteOnChangedInputs.ExecutionResult(listOf(tempDir.resolve("1.out")))
                }
            }
        }
    }
}