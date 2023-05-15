package org.jetbrains.deft.proto.gradle.java

import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.deft.proto.frontend.JavaApplicationArtifactPart
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.java.JavaDeftNamingConvention.deftFragment
import org.jetbrains.deft.proto.gradle.java.JavaDeftNamingConvention.maybeCreateJavaSourceSet
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.target
import org.jetbrains.deft.proto.gradle.part
import org.jetbrains.deft.proto.gradle.requireSingle
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun applyJavaAttributes(ctx: PluginPartCtx) = JavaBindingPluginPart(ctx).apply()

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class JavaBindingPluginPart(
        ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.JVM), KMPEAware, DeftNamingConventions {

    companion object {
        val logger: Logger = LoggerFactory.getLogger("some-logger")
    }

    private val javaAPE: JavaApplication = project.extensions.getByType(JavaApplication::class.java)

    internal val javaPE: JavaPluginExtension = project.extensions.getByType(JavaPluginExtension::class.java)

    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        project.plugins.apply(JavaPlugin::class.java)
        applyJavaApplication()
        adjustJavaSourceSets()
    }

    private fun applyJavaApplication() {
        val jvmArtifacts = module.artifacts
            .filter { Platform.JVM in it.platforms }
            .filter { it.part<JavaApplicationArtifactPart>() != null }
        if (jvmArtifacts.size > 1)
            logger.warn(
                "Cant apply multiple settings for application plugin. " +
                        "Affected artifacts: ${jvmArtifacts.joinToString { it.name }}. " +
                        "Applying application settings from first one."
            )
        val artifact = jvmArtifacts.firstOrNull() ?: return
        val applicationSettings = artifact.part<JavaApplicationArtifactPart>()!!
        javaAPE.apply {
            mainClass.set(applicationSettings.mainClass)
        }
    }


    // TODO Rewrite this completely by not calling
    //  KMPP code and following out own conventions.
    private fun adjustJavaSourceSets() {
        project.plugins.apply(JavaPlugin::class.java)

        // Set sources for all deft related source sets.
        platformFragments.forEach {
            it.maybeCreateJavaSourceSet {
                java.setSrcDirs(it.sourcePaths)
                resources.setSrcDirs(it.resourcePaths)
            }
        }

        val akpClass = Thread.currentThread().contextClassLoader
                .loadClass("org.jetbrains.kotlin.gradle.plugin.AbstractKotlinPlugin")

        val apkCompanion = akpClass.declaredFields
                .filter { it.name == "Companion" }
                .requireSingle { "Field Companion must be present in ${akpClass.simpleName}" }
                .get(null)

        val akpCompanionClass = Thread.currentThread().contextClassLoader
                .loadClass("org.jetbrains.kotlin.gradle.plugin.AbstractKotlinPlugin\$Companion")

        val setUpJavaSourceSetsMethod = akpCompanionClass.declaredMethods
                .filter { it.name == "setUpJavaSourceSets\$kotlin_gradle_plugin_common" }
                .requireSingle { "Method setUpJavaSourceSets must be present in ${akpCompanionClass.simpleName}" }

        setUpJavaSourceSetsMethod.invoke(apkCompanion, Platform.JVM.target, false)
    }
}