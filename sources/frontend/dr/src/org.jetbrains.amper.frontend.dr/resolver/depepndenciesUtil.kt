/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.frontend.MavenDependency
import org.slf4j.LoggerFactory

internal val logger = LoggerFactory.getLogger("files.kt")

@Suppress("UNUSED") // Used in Idea plugin
val DependencyNode.fragmentDependencies: List<DirectFragmentDependencyNodeHolder>
    get() = findParents<DirectFragmentDependencyNodeHolder>()

private inline fun <reified T: DependencyNode> DependencyNode.findParents(): List<T> {
    val result = mutableSetOf<T>()
    findParentsImpl(T::class.java, result = result)
    return result.toList()
}

private fun <T: DependencyNode> DependencyNode.findParentsImpl(
    kClass: Class<T>,
    visited: MutableSet<DependencyNode> = mutableSetOf(),
    result: MutableSet<T> = mutableSetOf()
) {
    if (!visited.add(this)) {
        return
    }

    if (kClass.isInstance(this)) {
        @Suppress("UNCHECKED_CAST")
        result.add(this as T)
    } else {
        parents.forEach { it.findParentsImpl(kClass, visited = visited, result = result) }
    }
}

internal fun parseCoordinates(coordinates: String): MavenCoordinates? {
    val parts = coordinates.split(":")
    if (parts.size < 3) {
        return null
    }
    return MavenCoordinates(parts[0], parts[1], parts[2], classifier = if (parts.size > 3) parts[3] else null)
}

internal fun MavenDependency.parseCoordinates(): MavenCoordinates {
    return parseCoordinates(this.coordinates)
        ?: throw IllegalArgumentException("Invalid coordinates format: ${this.coordinates}")
}

fun MavenDependencyNode.mavenCoordinates(suffix: String? = null): MavenCoordinates {
    return MavenCoordinates(
        groupId = this.dependency.group,
        artifactId = if (suffix == null) dependency.module else "${dependency.module}:${suffix}",
        version = this.dependency.version,
    )
}

/**
 * Describes coordinates of a Maven artifact.
 */
data class MavenCoordinates(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String? = null
) {
    override fun toString(): String {
        return "$groupId:$artifactId:$version${if (classifier != null) ":$classifier" else ""}"
    }
}
