package org.jetbrains.amper.tasks

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule

interface TestTask : Task {
    val platform: Platform
    val module: PotatoModule
}