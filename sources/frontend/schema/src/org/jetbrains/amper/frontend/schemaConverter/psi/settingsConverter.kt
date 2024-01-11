/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.ComposeSettings
import org.jetbrains.amper.frontend.schema.IosFrameworkSettings
import org.jetbrains.amper.frontend.schema.IosSettings
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.KoverHtmlSettings
import org.jetbrains.amper.frontend.schema.KoverSettings
import org.jetbrains.amper.frontend.schema.KoverXmlSettings
import org.jetbrains.amper.frontend.schema.NativeSettings
import org.jetbrains.amper.frontend.schema.PublishingSettings
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schemaConverter.psi.util.adjustTrace
import org.jetbrains.amper.frontend.schemaConverter.psi.util.asAbsolutePath
import org.jetbrains.amper.frontend.schemaConverter.psi.util.asMappingNode
import org.jetbrains.amper.frontend.schemaConverter.psi.util.convertChild
import org.jetbrains.amper.frontend.schemaConverter.psi.util.convertChildBoolean
import org.jetbrains.amper.frontend.schemaConverter.psi.util.convertChildScalar
import org.jetbrains.amper.frontend.schemaConverter.psi.util.convertChildScalarCollection
import org.jetbrains.amper.frontend.schemaConverter.psi.util.convertChildString
import org.jetbrains.amper.frontend.schemaConverter.psi.util.convertChildValue
import org.jetbrains.amper.frontend.schemaConverter.psi.util.convertSelf
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLValue

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLPsiElement.convertSettings() = assertNodeType<YAMLMapping, Settings>("settings") {
    doConvertSettings()
}?.adjustTrace(this)

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.doConvertSettings() = Settings().apply {
    ::java.convertChildValue { asMappingNode()?.convertJavaSettings() }
    ::jvm.convertChildValue { asMappingNode()?.convertJvmSettings() }
    ::android.convertChildValue { asMappingNode()?.convertAndroidSettings() }
    ::kotlin.convertChildValue { asMappingNode()?.convertKotlinSettings() }
    ::compose.convertChildValue { convertComposeSettings() }
    ::ios.convertChildValue { asMappingNode()?.convertIosSettings() }
    ::publishing.convertChildValue { asMappingNode()?.convertPublishingSettings() }
    ::kover.convertChildValue { asMappingNode()?.convertKoverSettings() }
    ::native.convertChildValue { asMappingNode()?.convertNativeSettings() }

    ::junit.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertJavaSettings() = JavaSettings().apply {
    ::source.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertJvmSettings() = JvmSettings().apply {
    ::target.convertChildString()
    ::mainClass.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertAndroidSettings() = AndroidSettings().apply {
    ::compileSdk.convertChildString()
    ::minSdk.convertChildString()
    ::maxSdk.convertChildString()
    ::targetSdk.convertChildString()
    ::applicationId.convertChildString()
    ::namespace.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertKotlinSettings() = KotlinSettings().apply {
    ::languageVersion.convertChildString()
    ::apiVersion.convertChildString()

    ::allWarningsAsErrors.convertChildBoolean()
    ::suppressWarnings.convertChildBoolean()
    ::verbose.convertChildBoolean()
    ::debug.convertChildBoolean()
    ::progressiveMode.convertChildBoolean()

    ::freeCompilerArgs.convertChildScalarCollection { textValue }
    ::linkerOpts.convertChildScalarCollection { textValue }
    ::languageFeatures.convertChildScalarCollection { textValue }
    ::optIns.convertChildScalarCollection { textValue  }

    ::serialization.convertChild { value?.convertSerializationSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLValue.convertSerializationSettings() = when (this) {
    is YAMLScalar -> SerializationSettings().apply { ::engine.convertSelf { textValue } }
    is YAMLMapping -> SerializationSettings().apply { ::engine.convertChildString() }
    else -> null
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLValue.convertComposeSettings() = when (this) {
    is YAMLScalar -> ComposeSettings().apply { ::enabled.convertSelf { (textValue == "enabled") } }
    is YAMLMapping -> ComposeSettings().apply { ::enabled.convertChildBoolean() }
    else -> null
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertIosSettings() = IosSettings().apply {
    ::teamId.convertChildString()
    ::framework.convertChildValue { asMappingNode()?.convertIosFrameworkSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertIosFrameworkSettings() = IosFrameworkSettings().apply {
    ::basename.convertChildString()
    ::isStatic.convertChildBoolean()
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertPublishingSettings() = PublishingSettings().apply {
    ::group.convertChildString()
    ::version.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertKoverSettings() = KoverSettings().apply {
    ::enabled.convertChildBoolean()
    ::xml.convertChildValue { asMappingNode()?.convertKoverXmlSettings() }
    ::html.convertChildValue { asMappingNode()?.convertKoverHtmlSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertKoverXmlSettings() = KoverXmlSettings().apply {
    ::onCheck.convertChildBoolean()
    ::reportFile.convertChildScalar { asAbsolutePath() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertKoverHtmlSettings() = KoverHtmlSettings().apply {
    ::onCheck.convertChildBoolean()
    ::title.convertChildString()
    ::charset.convertChildString()
    ::reportDir.convertChildScalar { asAbsolutePath() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertNativeSettings() = NativeSettings().apply {
    ::entryPoint.convertChildString()
}