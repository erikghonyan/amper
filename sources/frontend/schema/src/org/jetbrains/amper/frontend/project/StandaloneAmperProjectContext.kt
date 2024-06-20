/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.convertProject
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.relativeTo
import com.intellij.openapi.project.Project as IJProject

private val amperProjectFileNames = setOf("project.yaml", "project.amper")

class StandaloneAmperProjectContext(
    override val projectRootDir: VirtualFile,
    override val amperModuleFiles: List<VirtualFile>,
) : AmperProjectContext {

    // TODO should we really discover them like this?
    override val amperCustomTaskFiles: List<VirtualFile> = projectRootDir.filterDescendants { it.isAmperCustomTaskFile() }

    override val gradleBuildFilesWithoutAmper: List<VirtualFile> = emptyList()

    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? =
        GradleVersionsCatalogFinder.findDefaultCatalogIn(projectRootDir)

    companion object {
        /**
         * Finds a standalone Amper project, starting at the given [start] directory or file, or returns null if no
         * Amper project is found.
         *
         * Conceptually, we first find the closest ancestor directory of [start] that contains a project file or a
         * module file. Then:
         * * If a project file is found, that's our root.
         * * If a module file is found, that's part of our project. In that case we check if a project file higher up
         * contains this module. If that's the case, then the project file defines the root. If not, then our module
         * file defines the root.
         * * If neither a project nor a module file are found, we don't have an Amper project and null is returned. The
         * caller is responsible for handling this situation as desired.
         *
         * The given IntelliJ [project] is used to access the VFS. If null, a mock project will be created.
         */
        context(ProblemReporterContext)
        fun find(start: Path, project: IJProject? = null): StandaloneAmperProjectContext? {
            val frontendPathResolver = FrontendPathResolver(project)
            val result = preSearchProjectRoot(frontendPathResolver.loadVirtualFile(start)) ?: return null

            val potentialContext = with(frontendPathResolver) {
                create(result.potentialRoot) ?: error("potentialRoot should point to a valid project root")
            }
            if (result.startModuleFile == null || result.startModuleFile in potentialContext.amperModuleFiles) {
                return potentialContext
            }
            return StandaloneAmperProjectContext(
                projectRootDir = result.startModuleFile,
                amperModuleFiles = listOf(result.startModuleFile),
            )
        }

        /**
         * Creates an [AmperProjectContext] for the specified [rootDir] based on the contained project or module file.
         * If there is no project file nor module file in the given directory, null is returned and the caller is
         * responsible for handling the situation (only the caller knows whether this is a valid situation).
         *
         * The given IntelliJ [project] is used to resolve virtual files and PSI files. If null, a mock project is
         * created.
         */
        context(ProblemReporterContext)
        fun create(rootDir: Path, project: IJProject? = null): StandaloneAmperProjectContext? =
            with(FrontendPathResolver(project = project)) {
                create(loadVirtualFile(rootDir))
            }

        /**
         * Creates an [AmperProjectContext] for the specified [rootDir] based on the contained project or module file.
         * If there is no project file nor module file in the given directory, null is returned and the caller is
         * responsible for handling the situation (only the caller knows whether this is a valid situation).
         *
         * The given [FrontendPathResolver] is used to resolve virtual files and PSI files.
         */
        context(ProblemReporterContext, FrontendPathResolver)
        fun create(rootDir: VirtualFile): StandaloneAmperProjectContext? {
            val rootModuleFile = rootDir.findChildMatchingAnyOf(amperModuleFileNames)

            val amperProject = parseAmperProject(rootDir)
            if (rootModuleFile == null && amperProject == null) {
                return null
            }
            val explicitProjectModuleFiles = amperProject?.modulePaths(rootDir) ?: emptyList()

            return StandaloneAmperProjectContext(
                projectRootDir = rootDir,
                amperModuleFiles = listOfNotNull(rootModuleFile) + explicitProjectModuleFiles,
            )
        }
    }
}

private data class RootSearchResult(
    /**
     * A directory containing either a project file or a module file, and that is a potential root for the project.
     * It might be the root of an unrelated project higher in the file system hierarchy, so we still need to verify that
     * [startModuleFile], if present, is part of that project.
     */
    val potentialRoot: VirtualFile,
    /**
     * The first module file that was found while going up the file system hierarchy (if any), and which must be
     * included in the current project.
     * It defines the root of the project unless a project file is found higher up and includes this module.
     */
    val startModuleFile: VirtualFile?,
)

/**
 * Attempts to find the project root directory in a pure Amper project from the given [start] point.
 *
 * In practice, this function goes up the file hierarchy and looks for module files or a project file along the way.
 * The returned [RootSearchResult] is a record of what was found.
 * If neither a project file nor a module file are found, we don't have an Amper project and null is returned.
 */
private fun preSearchProjectRoot(start: VirtualFile): RootSearchResult? {
    var currentDir: VirtualFile? = if (start.isDirectory) start else start.parent
    var closestModuleFile: VirtualFile? = null
    while (currentDir != null) {
        if (currentDir.hasChildMatchingAnyOf(amperProjectFileNames)) {
            return RootSearchResult(potentialRoot = currentDir, startModuleFile = closestModuleFile)
        }
        if (closestModuleFile == null) {
            val moduleFile = currentDir.findChildMatchingAnyOf(amperModuleFileNames)
            if (moduleFile != null) {
                closestModuleFile = moduleFile
            }
        }
        currentDir = currentDir.parent
    }
    if (closestModuleFile != null) {
        return RootSearchResult(potentialRoot = closestModuleFile.parent, startModuleFile = closestModuleFile)
    }
    return null // neither project nor module file found
}

context(ProblemReporterContext, FrontendPathResolver)
private fun parseAmperProject(projectRootDir: VirtualFile): Project? {
    val projectFile = projectRootDir.findChildMatchingAnyOf(amperProjectFileNames) ?: return null
    return with(ConvertCtx(projectRootDir, pathResolver = this@FrontendPathResolver)) {
        convertProject(projectFile)
    }
}

private val globChars = setOf('*', '?', '{', '}', '[', ']', ',')

private fun String.isPotentialGlob() = any { it in globChars }

context(ProblemReporterContext)
private fun Project.modulePaths(projectRootDir: VirtualFile): List<VirtualFile> {
    // short-circuit if there are no glob patterns, to avoid walking the file system unnecessarily
    if (modules.none { it.value.isPotentialGlob() }) {
        return modules.mapNotNull { projectRootDir.resolveModuleFileOrNull(relativeModulePath = it) }
    }
    // TODO report warning if a glob doesn't match anything?
    val moduleFileNamesPattern = amperModuleFileNames.joinToString(",")
    val moduleFilesGlobs = modules.map { dirGlob -> "${dirGlob.value.normalizeGlob()}/{$moduleFileNamesPattern}" }
    return projectRootDir.resolveGlobsRecursively(moduleFilesGlobs)
}

context(ProblemReporterContext)
private fun VirtualFile.resolveModuleFileOrNull(relativeModulePath: TraceableString): VirtualFile? {
    val moduleDir = findChild(relativeModulePath.value)
    if (moduleDir == null) {
        SchemaBundle.reportBundleError(
            value = relativeModulePath,
            messageKey = "project.module.dir.0.does.not.exist",
            relativeModulePath.value,
            level = Level.Warning,
        )
        return null
    }
    val moduleFile = moduleDir.findChildMatchingAnyOf(amperModuleFileNames)
    if (moduleFile == null) {
        SchemaBundle.reportBundleError(
            value = relativeModulePath,
            messageKey = "project.module.dir.0.has.no.module.file",
            relativeModulePath.value,
            level = Level.Warning,
        )
    }
    return moduleFile
}

// Regular path normalization doesn't work with globs, because they contain invalid characters (e.g. '*').
// We do need to normalize to some extent, at least for frequently used elements like './' or '/' suffix.
// More advanced cases like '/../' can be added later.
private fun String.normalizeGlob(): String = removePrefix("./")
    .replace("/./", "")
    .removeSuffix("/.")
    .removeSuffix("/")

private fun VirtualFile.resolveGlobsRecursively(globs: List<String>): List<VirtualFile> {
    val base = this.toNioPath()
    val matchers = globs.map { glob ->
        FileSystems.getDefault().getPathMatcher("glob:$glob")
    }
    val matchingFiles = mutableListOf<VirtualFile>()

    // We cannot skip the subtree when we have a match, because other modules could be deeper in it.
    // The most obvious example: `**` should match `foo`, `foo/bar`, `foo/bar/baz`
    forEachDescendant { file ->
        val pathToTest = file.toNioPath().relativeTo(base).normalize()
        if (matchers.any { it.matches(pathToTest) }) {
            matchingFiles.add(file)
        }
        true
    }
    return matchingFiles
}