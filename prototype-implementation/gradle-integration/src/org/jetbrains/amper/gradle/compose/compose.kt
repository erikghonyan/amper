package org.jetbrains.amper.gradle.compose

import org.jetbrains.amper.frontend.ComposePart
import org.jetbrains.amper.gradle.base.BindingPluginPart
import org.jetbrains.amper.gradle.base.DeftNamingConventions
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.kmpp.KMPEAware
import org.jetbrains.amper.gradle.kmpp.KotlinDeftNamingConvention.kotlinSourceSet
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ComposePluginPart(ctx: PluginPartCtx) : KMPEAware, DeftNamingConventions, BindingPluginPart by ctx {
    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    override val needToApply by lazy {
        module.leafFragments.any { it.parts.find<ComposePart>()?.enabled == true }
    }

    override fun applyBeforeEvaluate() {
        project.plugins.apply("org.jetbrains.compose")

        val commonFragment = module.fragments.find { it.fragmentDependencies.isEmpty() }
        commonFragment?.kotlinSourceSet?.dependencies {
            implementation(ComposePlugin.Dependencies(project).runtime)
        }
    }
}