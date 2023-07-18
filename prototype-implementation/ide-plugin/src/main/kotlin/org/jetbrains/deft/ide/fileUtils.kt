package org.jetbrains.deft.ide

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.yaml.psi.YAMLFile

private const val TEMPLATE_FILE_SUFFIX = ".Pot-template.yaml"
private const val POT_FILE_NAME = "Pot.yaml"

internal fun VirtualFile.isPot(): Boolean = name == POT_FILE_NAME

internal fun VirtualFile.isPotTemplate(): Boolean = name.endsWith(TEMPLATE_FILE_SUFFIX)

internal fun String.stripTemplateSuffix(): String = removeSuffix(TEMPLATE_FILE_SUFFIX)

internal fun VirtualFile.isDeftFile(): Boolean = isPot() || isPotTemplate()

internal fun PsiElement.isPot(): Boolean {
    if (this !is YAMLFile) return false
    return CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result(this.virtualFile.isPot(), this)
    }
}

internal fun PsiElement.isPotTemplate(): Boolean {
    if (this !is YAMLFile) return false
    return CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result(this.virtualFile.isPotTemplate(), this)
    }
}