/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tools

import org.jetbrains.amper.cli.AmperUserCacheRoot

interface Tool {
    val name: String
    fun run(args: List<String>, userCacheRoot: AmperUserCacheRoot)
}