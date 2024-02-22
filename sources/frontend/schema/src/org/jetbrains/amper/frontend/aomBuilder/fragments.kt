/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.FragmentLink
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.doCapitalize
import org.jetbrains.amper.core.mapStartAware
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.Settings


class DefaultLeafFragment(
    seed: FragmentSeed, isTest: Boolean, externalDependencies: List<Notation>, relevantSettings: Settings?, moduleFile: VirtualFile,
) : DefaultFragment(seed, isTest, externalDependencies, relevantSettings, moduleFile), LeafFragment {
    init {
        assert(seed.isLeaf) { "Should be created only for leaf platforms!" }
    }

    override val platform = seed.platforms.single()
}

open class DefaultFragment(
    seed: FragmentSeed,
    final override val isTest: Boolean,
    override val externalDependencies: List<Notation>,
    relevantSettings: Settings?,
    moduleFile: VirtualFile,
) : Fragment {

    private val isCommon = seed.modifiersAsStrings == setOf(Platform.COMMON.pretty)

    /**
     * Modifier that is used to reference this fragment in the module file or within source directory.
     */
    private val modifier = if (isCommon) "" else "@" + seed.modifiersAsStrings.joinToString(separator = "+")

    final override val fragmentDependencies = mutableListOf<FragmentLink>()

    override val fragmentDependants = mutableListOf<FragmentLink>()

    override val name = seed.modifiersAsStrings
        .mapStartAware { isStart, it -> if (isStart) it else it.doCapitalize() }
        .joinToString() +
            if (isTest) "Test" else ""

    final override val platforms = seed.platforms

    override val variants = emptyList<String>()

    override val settings = relevantSettings ?: Settings()

    override val isDefault = true

    private val srcOnlyOwner by lazy {
        !isCommon && fragmentDependencies.none { it.type == FragmentDependencyType.REFINE }
    }

    override val src by lazy {
        val srcStringPrefix = if (isTest) "test" else "src"
        val srcPathString =
            if (srcOnlyOwner) srcStringPrefix
            else "$srcStringPrefix$modifier"
        moduleFile.parent.toNioPath().resolve(srcPathString)
    }

    override val resourcesPath by lazy {
        val resourcesStringPrefix = if (isTest) "testResources" else "resources"
        val resourcesPathString =
            if (srcOnlyOwner) resourcesStringPrefix
            else "$resourcesStringPrefix$modifier"
        moduleFile.parent.toNioPath().resolve(resourcesPathString)
    }
}

fun createFragments(
    seeds: Collection<FragmentSeed>,
    moduleFile: VirtualFile,
    resolveDependency: (Dependency) -> Notation?,
): List<DefaultFragment> {
    data class FragmentBundle(
        val mainFragment: DefaultFragment,
        val testFragment: DefaultFragment,
    )

    fun FragmentSeed.toFragment(isTest: Boolean, externalDependencies: List<Notation>) =
        if (isLeaf) DefaultLeafFragment(
            this,
            isTest,
            externalDependencies,
            if (isTest) relevantTestSettings else relevantSettings,
            moduleFile
        )
        else DefaultFragment(
            this,
            isTest,
            externalDependencies,
            if (isTest) relevantTestSettings else relevantSettings,
            moduleFile
        )

    // Create fragments.
    val initial = seeds.associateWith {
        FragmentBundle(
            // TODO Report unresolved dependencies
            it.toFragment(false, it.relevantDependencies?.mapNotNull { resolveDependency(it) }.orEmpty()),
            it.toFragment(true, it.relevantTestDependencies?.mapNotNull { resolveDependency(it) }.orEmpty()),
        )
    }

    // Set fragment dependencies.
    initial.entries.forEach { (seed, bundle) ->
        // Main fragment dependency.
        bundle.mainFragment.fragmentDependencies +=
            seed.dependencies.map { initial[it]!!.mainFragment.asRefine() }

        seed.dependencies.forEach {
            initial[it]!!.mainFragment.fragmentDependants += bundle.mainFragment.asRefine()
        }

        // Test fragment dependency.
        bundle.testFragment.fragmentDependencies +=
            seed.dependencies.map { initial[it]!!.testFragment.asRefine() }

        seed.dependencies.forEach {
            initial[it]!!.testFragment.fragmentDependants += bundle.testFragment.asRefine()
        }

        // Main - test dependency.
        bundle.testFragment.fragmentDependencies +=
            bundle.mainFragment.asFriend()

        bundle.mainFragment.fragmentDependants +=
            bundle.testFragment.asFriend()
    }

    // Unfold fragments bundles.
    return initial.values.flatMap { listOf(it.mainFragment, it.testFragment) }
}

class SimpleFragmentLink(
    override val target: Fragment,
    override val type: FragmentDependencyType
) : FragmentLink

private fun Fragment.asFriend() = SimpleFragmentLink(this, FragmentDependencyType.FRIEND)
private fun Fragment.asRefine() = SimpleFragmentLink(this, FragmentDependencyType.REFINE)

/**
 * Returns the path from this [Fragment] to its farthest ancestor (more general parents). *
 * Closer parents appear first, even when they're on different paths.
 *
 * Example:
 * ```
 *      common
 *      /    \
 *  desktop  apple
 *      \    /
 *     macosX64
 * ```
 * In this situation calling [ancestralPath] on `macosX64` yields `[macosX64, desktop, apple, common]`.
 * The order between `desktop` and `apple` is unspecified.
 */
internal fun Fragment.ancestralPath(): Sequence<Fragment> = sequence {
    val seenAncestorNames = mutableSetOf<String>()

    val queue = ArrayDeque<Fragment>()
    queue.add(this@ancestralPath)
    while(queue.isNotEmpty()) {
        val fragment = queue.removeFirst()
        yield(fragment)
        fragment.fragmentDependencies.forEach { link ->
            val parent = link.target
            if (parent.name !in seenAncestorNames) {
                queue.add(parent)
                seenAncestorNames.add(parent.name)
            }
        }
    }
}
