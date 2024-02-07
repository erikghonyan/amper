/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.util.OS
import org.jetbrains.amper.util.OS.Type.Linux
import org.jetbrains.amper.util.OS.Type.Mac
import org.jetbrains.amper.util.OS.Type.Windows
import java.nio.file.Path
import kotlin.io.path.div

data class ProjectContext(
    val projectRoot: AmperProjectRoot,
    val userCacheRoot: AmperUserCacheRoot,
    val projectTempRoot: AmperProjectTempRoot,
    // in the future it'll be customizable to support out-of-tree builds, e.g., on CI
    val buildOutputRoot: AmperBuildOutputRoot,
    val commonRunSettings: CommonRunSettings,
) {
    companion object {
        fun create(projectRoot: Path, commonRunSettings: CommonRunSettings): ProjectContext {
            return ProjectContext(
                projectRoot = AmperProjectRoot(projectRoot),
                buildOutputRoot = AmperBuildOutputRoot(projectRoot.resolve("build")),
                projectTempRoot = AmperProjectTempRoot(projectRoot.resolve("build/temp-temp")),
                userCacheRoot = AmperUserCacheRoot.fromCurrentUser(),
                commonRunSettings = commonRunSettings,
            )
        }
    }
}

data class AmperUserCacheRoot(val path: Path) {
    companion object {
        fun fromCurrentUser(): AmperUserCacheRoot {
            val userHome = Path.of(System.getProperty("user.home"))

            val localAppData = when (OS.type) {
                Windows -> Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_LocalAppData)?.let { Path.of(it) } ?: (userHome / "AppData/Local")
                Mac -> userHome / "Library/Application Support"
                Linux -> {
                    val xdgDataHome = System.getenv("XDG_DATA_HOME")
                    if (xdgDataHome.isNullOrBlank()) userHome.resolve(".local/share") else Path.of(xdgDataHome)
                }
            }

            val localAppDataAmper = localAppData.resolve("Amper").resolve("caches")

            return AmperUserCacheRoot(localAppDataAmper)
        }
    }
}

data class AmperBuildOutputRoot(val path: Path)
data class AmperProjectTempRoot(val path: Path)
data class AmperProjectRoot(val path: Path)
