package com.intellij.rt.execution.junit5

import com.intellij.rt.execution.junit.FileComparisonData
import org.opentest4j.AssertionFailedError

class FileComparisonFailedError(
    message: String,
    override val expected: String,
    override val actual: String,
    override val filePath: String,
    override val actualFilePath: String? = null
) : AssertionFailedError(message, expected, actual), FileComparisonData {
    override val actualStringPresentation: String
        get() = actual

    override val expectedStringPresentation: String
        get() = expected
}
