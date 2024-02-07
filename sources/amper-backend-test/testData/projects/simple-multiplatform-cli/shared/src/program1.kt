/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package package1

import World
import Utils

fun main(args: Array<String>) {
    println(Utils.superCommonMethod() + " Multiplatform CLI: ${World().get()}")
    for ((index, arg) in args.withIndex()) {
        println("ARG${index}: <$arg>")
    }
}
