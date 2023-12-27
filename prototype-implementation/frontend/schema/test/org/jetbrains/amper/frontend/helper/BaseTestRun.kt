/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.frontend.old.helper.TestBase
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.test.assertEquals

/**
 * Base test, that derives standard and input paths from case name.
 */
open class BaseTestRun(
    protected val caseName: String,
) {
    open val base: Path get() = Path("testResources")
    open val expectPostfix: String = ".result.txt"
    open val inputPostfix: String = ".yaml"

    protected val ctx = TestProblemReporterContext()

    context(TestBase, TestProblemReporterContext)
    open fun getInputContent(inputPath: Path): String = inputPath.readText()

    context(TestBase, TestProblemReporterContext)
    open fun getExpectContent(inputPath: Path, expectedPath: Path): String = expectedPath.readText()

    context(TestBase)
    open fun doTest() = with(ctx) {
        with(buildFile) {
            val input = base / "$caseName$inputPostfix"
            val inputContent = getInputContent(input)

            val expect = base / "$caseName$expectPostfix"
            val expectContent = getExpectContent(input, expect)

            assertEquals(expectContent, inputContent)
        }
    }
}