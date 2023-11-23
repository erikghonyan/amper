/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.ismVisitor.IsmVisitor
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.amper.frontend.schema.ProductType
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class EqualsVisitor(private val otherModule: Module) : IsmVisitor {
  override fun visitModule(module: Module) {
    if (module.product.withoutDefault != null && otherModule.product.withoutDefault == null) {
      fail("product has not been parsed")
    }
    if (module.product.withoutDefault == null && otherModule.product.withoutDefault != null) {
      fail("Unexpected product has been parsed")
    }
    if (module.module.withoutDefault != null && otherModule.module.withoutDefault == null) {
      fail("module meta has not been parsed")
    }
    if (module.module.withoutDefault == null && otherModule.module.withoutDefault != null) {
      fail("Unexpected module meta has been parsed")
    }

    assertEquals(module.aliases.withoutDefault?.keys.orEmpty(), otherModule.aliases.withoutDefault?.keys.orEmpty(),
      "Aliases were parsed incorrectly")
    assertEquals(module.apply.withoutDefault?.toSet().orEmpty(), otherModule.apply.withoutDefault?.toSet().orEmpty(),
      "Imports (apply) were parsed incorrectly"
    )
  }

  override fun visitProduct(product: ModuleProduct) {
    assertValueEquals(product.platforms, otherModule.product.withoutDefault?.platforms,
      "product platforms differs")
    assertValueEquals(product.type, otherModule.product.withoutDefault?.type,
      "product type differs")
  }

  override fun visitProductType(productType: ProductType) { }
  override fun visitProductPlatform(productPlatform: Platform) { }

  override fun visitModuleMeta(meta: Meta) {
    assertValueEquals(meta.layout, otherModule.module.withoutDefault?.layout,
      "module layout differs")
  }
  override fun visitModuleMetaLayout(layout: AmperLayout) { }

  override fun visitAlias(name: String, platforms: Set<Platform>) { }
  override fun visitApply(path: Path) { }

  override fun visitRepositories(repo: List<Repository>) {
    assertEquals(repo.size, otherModule.repositories.withoutDefault.orEmpty().size,
      "number of product repositories differs")

    repo.forEachIndexed { index, repository ->
      val otherRepository =  otherModule.repositories.withoutDefault.orEmpty()[index]
      assertValueEquals(repository.url, otherRepository.url,"repository url differs")
      assertValueEquals(repository.id, otherRepository.id,"repository id differs")
      assertValueEquals(repository.publish, otherRepository.publish,"repository publish flag differs")

      val repositoryUrl = repository.url.withoutDefault

      val credentials = repository.credentials.withoutDefault
      val otherCredentials = otherRepository.credentials.withoutDefault
      if (credentials != null) {
        assertNotNull(otherCredentials, "repository credentials are absent for repo with url $repositoryUrl")
        assertValueEquals(credentials.file, otherCredentials.file,"credentials file path differs for repo with url $repositoryUrl")
        assertValueEquals(credentials.passwordKey, otherCredentials.passwordKey,"credentials username key differs for repo with url $repositoryUrl")
        assertValueEquals(credentials.usernameKey, otherCredentials.usernameKey,"credentials username key differs for repo with url $repositoryUrl")
      } else {
        assertNull(otherCredentials, "repository credentials are presented for the repo with url $repositoryUrl")
      }
    }
  }

  override fun visitRepository(repo: Repository) { }
  override fun visitCredentials(credentials: Repository.Credentials) { }

  override fun visitDependencies(dependencies: Map<Modifiers, List<Dependency>>) {
    val otherDependenciesMap = otherModule.dependencies.withoutDefault
    checkDependenciesEquality(dependencies, otherDependenciesMap)
  }

  private fun checkDependenciesEquality(
    dependencies: Map<Modifiers, List<Dependency>>,
    otherDependenciesMap: Map<Modifiers, List<Dependency>>?
  ) {
    assertEquals(
      dependencies.size, otherDependenciesMap.orEmpty().size,
      "number of product dependencies differs"
    )

    dependencies.forEach {
      val dependencyKey = it.key
      val dependencyBlock = it.value
      val otherDependencyBlock = otherDependenciesMap.orEmpty()[dependencyKey]
      assertNotNull(otherDependencyBlock, "dependencies with key $dependencyKey is missing")
      assertEquals(
        dependencyBlock.size, otherDependencyBlock.size,
        "number of dependencies differs for dependencies with key $dependencyKey"
      )

      dependencyBlock.forEachIndexed { index, dependency ->
        val otherDependency = otherDependencyBlock[index]
        when (dependency) {
          is ExternalMavenDependency -> {
            assertNotNull(
              otherDependency,
              "External dependency ${dependency.coordinates.withoutDefault} with key $dependencyKey is missing"
            )
            assertEquals(
              dependency::class.simpleName,
              otherDependency::class.simpleName,
              "Type ${dependency::class.simpleName} of $index dependency with key $dependencyKey differs"
            )
            assertValueEquals(
              dependency.coordinates,
              (otherDependency as ExternalMavenDependency).coordinates,
              "coordinates of $index dependency (external) with key $dependencyKey differs"
            )
          }

          is InternalDependency -> {
            assertNotNull(
              otherDependency,
              "Internal dependency ${dependency.path.withoutDefault} with key $dependencyKey is missing"
            )
            assertEquals(
              dependency::class.simpleName,
              otherDependency::class.simpleName,
              "Type ${dependency::class.simpleName} of $index dependency with key $dependencyKey differs"
            )
            assertValueEquals(
              dependency.path, (otherDependency as InternalDependency).path,
              "path of $index dependency (internal) with key $dependencyKey differs"
            )
          }

          is CatalogDependency -> {
            assertNotNull(
              otherDependency,
              "Catalog dependency ${dependency.catalogKey.withoutDefault} with key $dependencyKey is missing"
            )
            assertEquals(
              dependency::class.simpleName,
              otherDependency::class.simpleName,
              "Type ${dependency::class.simpleName} of $index dependency with key $dependencyKey differs"
            )
            assertValueEquals(
              dependency.catalogKey, (otherDependency as CatalogDependency).catalogKey,
              "path of $index dependency (internal) with key $dependencyKey differs"
            )
          }
        }
        assertValueEquals(
          dependency.exported, otherDependency.exported,
          "exported flag of $index dependency with key $dependencyKey differs"
        )
        assertValueEquals(
          dependency.scope, otherDependency.scope,
          "scope of $index dependency with key $dependencyKey differs"
        )
      }
    }
  }

  override fun visitDependencies(modifiers: Modifiers, dependencies: List<Dependency>) { }
  override fun visitDependency(dependency: Dependency) { }

  override fun visitTestDependencies(dependencies: Map<Modifiers, List<Dependency>>) {
    val otherDependenciesMap = otherModule.`test-dependencies`.withoutDefault
    checkDependenciesEquality(dependencies, otherDependenciesMap)
  }

  override fun visitTestDependencies(modifiers: Modifiers, dependencies: List<Dependency>) { }

  override fun visitTestSettings(settings: Map<Modifiers, Settings>) {
    val otherTestSettingsMap = otherModule.`test-settings`.withoutDefault
    checkSettingsEquality(settings, otherTestSettingsMap)
  }

  private fun checkSettingsEquality(settingsMap: Map<Modifiers, Settings>, otherSettingsMap: Map<Modifiers, Settings>?) {
    assertEquals(settingsMap.size, otherSettingsMap.orEmpty().size,"number of settings differs")
    settingsMap.forEach {
      val settingsKey = it.key
      val settings = it.value
      val otherSettings = otherSettingsMap.orEmpty()[settingsKey]
      assertNotNull(otherSettings, "settings with key $settingsKey is missing")

      settings.java.withoutDefault?.let { javaSettings ->
        assertNotNull(otherSettings.java.withoutDefault, "java settings with key $settingsKey is missing")
        assertValueEquals(javaSettings.source, otherSettings.java.withoutDefault?.source,
          "source of java settings with key $settingsKey differs")
      } ?: assertNull(otherSettings.java.withoutDefault, "java settings with key $settingsKey are presented")

      settings.jvm.withoutDefault?.let { jvmSettings ->
        assertNotNull(otherSettings.jvm.withoutDefault, "jvm settings with key $settingsKey is missing")
        assertValueEquals(jvmSettings.mainClass, otherSettings.jvm.withoutDefault?.mainClass,
          "mainClass of jvm settings with key $settingsKey differs")
        assertValueEquals(jvmSettings.target, otherSettings.jvm.withoutDefault?.target,
          "target of jvm settings with key $settingsKey differs")
      } ?: assertNull(otherSettings.jvm.withoutDefault, "jvm settings with key $settingsKey are presented")

      settings.kotlin.withoutDefault?.let { kotlinSettings ->
        assertNotNull(otherSettings.kotlin.withoutDefault, "kotlin settings with key $settingsKey is missing")
        assertValueEquals(kotlinSettings.allWarningsAsErrors, otherSettings.kotlin.withoutDefault?.allWarningsAsErrors,
          "allWarningsAsErrors flag of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.apiVersion, otherSettings.kotlin.withoutDefault?.apiVersion,
          "apiVersion of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.debug, otherSettings.kotlin.withoutDefault?.debug,
          "allWarningsAsErrors flag of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.languageVersion, otherSettings.kotlin.withoutDefault?.languageVersion,
          "languageVersion of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.freeCompilerArgs, otherSettings.kotlin.withoutDefault?.freeCompilerArgs,
          "freeCompilerArgs of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.suppressWarnings, otherSettings.kotlin.withoutDefault?.suppressWarnings,
          "suppressWarnings flag of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.verbose, otherSettings.kotlin.withoutDefault?.verbose,
          "verbose flag of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.linkerOpts, otherSettings.kotlin.withoutDefault?.linkerOpts,
          "linkerOpts of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.progressiveMode, otherSettings.kotlin.withoutDefault?.progressiveMode,
          "progressiveMode flag of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.languageFeatures, otherSettings.kotlin.withoutDefault?.languageFeatures,
          "languageFeatures of kotlin settings with key $settingsKey differs")
        assertValueEquals(kotlinSettings.optIns, otherSettings.kotlin.withoutDefault?.optIns,
          "optIns of kotlin settings with key $settingsKey differs")
      } ?: assertNull(otherSettings.kotlin.withoutDefault, "kotlin settings with key $settingsKey are presented")

      settings.android.withoutDefault?.let { androidSettings ->
        assertNotNull(otherSettings.android.withoutDefault, "android settings with key $settingsKey is missing")
        assertValueEquals(androidSettings.applicationId, otherSettings.android.withoutDefault?.applicationId,
        "applicationId of android settings with key $settingsKey differs")
        assertValueEquals(androidSettings.compileSdk, otherSettings.android.withoutDefault?.compileSdk,
          "compileSdk of android settings with key $settingsKey differs")
        assertValueEquals(androidSettings.maxSdk, otherSettings.android.withoutDefault?.maxSdk,
          "maxSdk of android settings with key $settingsKey differs")
        assertValueEquals(androidSettings.minSdk, otherSettings.android.withoutDefault?.minSdk,
          "minSdk of android settings with key $settingsKey differs")
        assertValueEquals(androidSettings.namespace, otherSettings.android.withoutDefault?.namespace,
          "namespace of android settings with key $settingsKey differs")
        assertValueEquals(androidSettings.targetSdk, otherSettings.android.withoutDefault?.targetSdk,
          "targetSdk of android settings with key $settingsKey differs")
      } ?: assertNull(otherSettings.android.withoutDefault, "android settings with key $settingsKey are presented")

      settings.compose.withoutDefault?.let { composeSettings ->
        assertNotNull(otherSettings.compose.withoutDefault, "compose settings with key $settingsKey is missing")
        assertValueEquals(composeSettings.enabled, otherSettings.compose.withoutDefault?.enabled,
          "enabled flag of compose settings with key $settingsKey differs")
      } ?: assertNull(otherSettings.compose.withoutDefault, "compose settings with key $settingsKey are presented")

      settings.ios.withoutDefault?.let { iosSettings ->
        assertNotNull(otherSettings.ios.withoutDefault, "ios settings with key $settingsKey is missing")
        assertValueEquals(iosSettings.teamId, otherSettings.ios.withoutDefault?.teamId,
          "teamId of ios settings with key $settingsKey differs")
        // check framework settings
        iosSettings.framework.withoutDefault?.let { iosFrameworkSettings ->
          val otherFrameworkSettings = otherSettings.ios.withoutDefault?.framework?.withoutDefault
          assertNotNull(otherFrameworkSettings, "ios framework settings with key $settingsKey are missing")
          assertValueEquals(iosFrameworkSettings.basename, otherFrameworkSettings.basename,
            "basename of ios framework settings with key $settingsKey differs")
          assertValueEquals(iosFrameworkSettings.isStatic, otherFrameworkSettings.isStatic,
            "isStatic of ios framework settings with key $settingsKey differs")
          // todo (AB) : Check Maps equality
          assertValueEquals(iosFrameworkSettings.mappings, otherFrameworkSettings.mappings,
            "mappings of ios framework settings with key $settingsKey differs")
        } ?: assertNull(otherSettings.ios.withoutDefault?.framework, "ios framework settings with key $settingsKey are presented")
      } ?: assertNull(otherSettings.ios.withoutDefault, "ios settings with key $settingsKey are presented")

      settings.publishing.withoutDefault?.let { publishingSettings ->
        assertNotNull(otherSettings.publishing.withoutDefault, "publishing settings with key $settingsKey is missing")
        assertValueEquals(publishingSettings.group, otherSettings.publishing.withoutDefault?.group,
          "group of publishing settings with key $settingsKey differs")
        assertValueEquals(publishingSettings.version, otherSettings.publishing.withoutDefault?.version,
          "version of publishing settings with key $settingsKey differs")
      } ?: assertNull(otherSettings.publishing.withoutDefault, "publishing settings with key $settingsKey are presented")

      settings.kover.withoutDefault?.let { koverSettings ->
        assertNotNull(otherSettings.publishing.withoutDefault, "kover settings with key $settingsKey is missing")
        assertValueEquals(koverSettings.enabled, otherSettings.kover.withoutDefault?.enabled,
          "enabled flag of kover settings with key $settingsKey differs")
        koverSettings.html.withoutDefault?.let { koverHtmlSettings ->
          val otherKoverHtmlSettings = otherSettings.kover.withoutDefault?.html?.withoutDefault
          assertNotNull(otherKoverHtmlSettings, "kover html settings with key $settingsKey are missing")
          assertValueEquals(koverHtmlSettings.onCheck, otherKoverHtmlSettings.onCheck,
            "onCheck of kover html settings with key $settingsKey differs")
          assertValueEquals(koverHtmlSettings.title, otherKoverHtmlSettings.title,
            "title of kover html settings with key $settingsKey differs")
          assertValueEquals(koverHtmlSettings.charset, otherKoverHtmlSettings.charset,
            "charset of kover html settings with key $settingsKey differs")
          assertValueEquals(koverHtmlSettings.reportDir, otherKoverHtmlSettings.reportDir,
            "reportDir of kover html settings with key $settingsKey differs")
        } ?: assertNull(otherSettings.kover.withoutDefault?.html, "kover html settings with key $settingsKey are presented")

        koverSettings.xml.withoutDefault?.let { koverXmllSettings ->
          val otherKoverXmlSettings = otherSettings.kover.withoutDefault?.xml?.withoutDefault
          assertNotNull(otherKoverXmlSettings, "kover xml settings with key $settingsKey are missing")
          assertValueEquals(koverXmllSettings.onCheck, otherKoverXmlSettings.onCheck,
            "onCheck of kover xml settings with key $settingsKey differs")
          assertValueEquals(koverXmllSettings.reportFile, otherKoverXmlSettings.reportFile,
            "reportFile of kover xml settings with key $settingsKey differs")
        } ?: assertNull(otherSettings.kover.withoutDefault?.xml, "kover xml settings with key $settingsKey are presented")
      } ?: assertNull(otherSettings.kover.withoutDefault, "kover settings with key $settingsKey are presented")
    }
  }

  private fun assertValueEquals(value: ValueBase<*>, otherValue: ValueBase<*>?, message: String) {
    value.withoutDefault?.let {
      assertEquals(it, otherValue?.withoutDefault, message)
    } ?: assertNull(otherValue?.withoutDefault, "null is expected")
  }

  override fun visitTestSettings(modifiers: Modifiers, settings: Settings) { }
  override fun visitSettings(settings: Map<Modifiers, Settings>) {
    val otherSettingsMap = otherModule.settings.withoutDefault
    checkSettingsEquality(settings, otherSettingsMap)
  }

  override fun visitSettings(modifiers: Modifiers, settings: Settings) { }
  override fun visitSettings(settings: Settings) { }
  override fun visitJavaSettings(settings: JavaSettings) { }
  override fun visitJvmSettings(settings: JvmSettings) { }
  override fun visitAndroidSettings(settings: AndroidSettings) { }
  override fun visitKotlinSettings(settings: KotlinSettings) { }
  override fun visitComposeSettings(settings: ComposeSettings) { }
  override fun visitSerializationSettings(settings: SerializationSettings) { }
}