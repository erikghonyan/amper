package org.jetbrains.deft.proto.frontend


@Suppress("unused")
enum class KotlinVersion(internal val version: String) {
    Kotlin20("2.0"),
    Kotlin19("1.9"),
    Kotlin18("1.8"),
    Kotlin17("1.7"),
    Kotlin16("1.6"),
    Kotlin15("1.5"),
    Kotlin14("1.4"),
    Kotlin13("1.3"),
    Kotlin12("1.2"),
    Kotlin11("1.1"),
    Kotlin10("1.0");

    override fun toString(): String = version
    companion object Index : EnumMap<KotlinVersion, String>(
        KotlinVersion::values,
        KotlinVersion::version,
        KotlinVersion::class
    )
}

enum class KotlinSerialization(val format: String) {
    None("none"),
    Json("json") {
        override fun changeDependencies(existing: MutableSet<Notation>) {
            val coordinate = "org.jetbrains.kotlinx:kotlinx-serialization-json"
            val version = "1.5.1"

            if (existing.any { it is MavenDependency && it.coordinates.startsWith(coordinate) }) return

            existing.add(
                MavenDependency(
                    coordinates = "$coordinate:$version",
                    compile = true,
                    runtime = true,
                    exported = false
                )
            )
        }
    };

    open fun changeDependencies(existing: MutableSet<Notation>) {
        // do nothing
    }

    companion object Index : EnumMap<KotlinSerialization, String>(
        KotlinSerialization::values,
        KotlinSerialization::format,
        KotlinSerialization::class
    )
}

data class KotlinPartBuilder(
    var languageVersion: KotlinVersion? = null,
    var apiVersion: KotlinVersion? = null,
    var allWarningsAsErrors: Boolean? = null,
    val freeCompilerArgs: MutableList<String> = mutableListOf(),
    var suppressWarnings: Boolean? = null,
    var verbose: Boolean? = null,
    val likerOpts: MutableList<String> = mutableListOf(),
    var debug: Boolean? = null,
    var progressiveMode: Boolean? = null,
    val languageFeatures: MutableList<String> = mutableListOf(),
    var optIns: MutableList<String> = mutableListOf(),
    var serialization: KotlinSerialization? = null
) {
    companion object : BuilderCompanion<KotlinPartBuilder>(::KotlinPartBuilder)
}

data class AndroidPartBuilder(
    var compileSdkVersion: String? = null,
    var minSdk: String? = null,
    var minSdkPreview: String? = null,
    var maxSdk: Int? = null,
    var targetSdk: String? = null,
    var applicationId: String? = null,
    var namespace: String? = null,
) {
    companion object : BuilderCompanion<AndroidPartBuilder>(::AndroidPartBuilder)
}

data class IosPartBuilder(var teamId: String? = null) {
    companion object: BuilderCompanion<IosPartBuilder>(::IosPartBuilder)
}

data class JavaPartBuilder(
    var source: String? = null,
) {
    companion object : BuilderCompanion<JavaPartBuilder>(::JavaPartBuilder)
}

data class JvmPartBuilder(
    var mainClass: String? = null,
    var target: String? = null,
) {
    companion object : BuilderCompanion<JvmPartBuilder>(::JvmPartBuilder)
}

data class PublishingPartBuilder(
    var group: String? = null,
    var version: String? = null,
) {
    companion object : BuilderCompanion<PublishingPartBuilder>(::PublishingPartBuilder)
}

data class NativePartBuilder(
    var entryPoint: String? = null,
) {
    companion object : BuilderCompanion<NativePartBuilder>(::NativePartBuilder)
}

data class JunitPartBuilder(
    var platformEnabled: Boolean? = null,
) {
    companion object : BuilderCompanion<JunitPartBuilder>(::JunitPartBuilder)
}

data class ComposePartBuilder(var enabled: Boolean? = null) {
    companion object: BuilderCompanion<ComposePartBuilder>(::ComposePartBuilder)
}