/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.*
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals

class MavenCacheDirectoryTest {

    @field:TempDir
    lateinit var temp: File

    private val cache: MavenCacheDirectory
        get() = MavenCacheDirectory(temp.toPath())

    @Test
    fun `get name`() {
        assertEquals("kotlin-test-1.9.10.jar", getName(kotlinTest(), "jar"))
    }

    @Test
    fun `guess path`() {
        val node = kotlinTest()
        val path = cache.guessPath(node, "jar")
        assertEquals(
            "org/jetbrains/kotlin/kotlin-test/1.9.10/kotlin-test-1.9.10.jar",
            path.relativeTo(temp.toPath()).toString().replace('\\', '/')
        )
    }

    @Test
    fun `get path`() {
        val sha1 = randomString()
        val path = cache.getPath(kotlinTest(), "jar", sha1)
        assertEquals(
            "org/jetbrains/kotlin/kotlin-test/1.9.10/kotlin-test-1.9.10.jar",
            path.relativeTo(temp.toPath()).toString().replace('\\', '/')
        )
    }

    private fun kotlinTest() = MavenDependency(listOf(cache), "org.jetbrains.kotlin", "kotlin-test", "1.9.10")

    private fun randomString() = UUID.randomUUID().toString()
}