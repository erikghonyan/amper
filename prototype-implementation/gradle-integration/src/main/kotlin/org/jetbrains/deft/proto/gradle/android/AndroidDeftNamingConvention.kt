package org.jetbrains.deft.proto.gradle.android

import com.android.build.api.dsl.AndroidSourceSet
import org.jetbrains.deft.proto.frontend.KotlinFragmentPart
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.part

@Suppress("UnstableApiUsage")
object AndroidDeftNamingConvention {

    context(AndroidAwarePart)
    val AndroidSourceSet.deftFragment: FragmentWrapper? get() = when(name) {
        "main" -> leafNonTestFragment
        "test" -> leafTestFragment
        else -> null
    }

    context(AndroidAwarePart)
    val FragmentWrapper.androidResPath
        get() = part<KotlinFragmentPart>()?.srcFolderName?.let { "$it/res" }
            ?: path.resolve("res").toString()

}