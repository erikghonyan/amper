/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.SchemaBase
import org.jetbrains.amper.frontend.api.ValueBase
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.superclasses

/**
 * A class to traverse schema.
 */
interface SchemaVisitor<CustomT : Any> {

    /**
     * Visit schema class.
     */
    fun visitClas(klass: KClass<*>)

    /**
     * Visit property with type of [SchemaBase], possibly with
     * modifiers ([Map] typed with values of [SchemaBase] type)
     *
     * [types] - collection of types, that can be assigned to this field
     */
    fun visitTyped(
        prop: KProperty<*>,
        type: KType,
        types: Collection<KClass<*>>,
    )

    /**
     * Visit collection property with type of [SchemaBase].
     *
     * [types] - collection of types, that can be assigned as this collection elements
     */
    fun visitCollectionTyped(
        prop: KProperty<*>,
        type: KType,
        types: Collection<KClass<*>>,
    )

    /**
     * Visit map property with value type of [SchemaBase].
     * Assert that key type is string.
     *
     * [types] - collection of types, that can be assigned as this map value elements
     * [modifierAware] - if this element can have "@" modifiers
     */
    fun visitMapTyped(
        prop: KProperty<*>,
        type: KType,
        types: Collection<KClass<*>>,
        modifierAware: Boolean,
    )

    /**
     * Visit non typed property.
     *
     * [default] - default value for this scalar
     */
    fun visitCommon(
        prop: KProperty<*>,
        type: KType,
        default: Any?,
    )

    /**
     * Visit custom marked property.
     *
     * [custom] - custom info, specific to visitor
     */
    fun visitCustom(
        prop: KProperty<*>,
        custom: CustomT,
    )
}

/**
 * Visitor, that visits all schema tree elements depth first (except custom).
 */
abstract class RecurringVisitor<T : Any>(
    private val detectCustom: ((KProperty<*>) -> T?)? = null
) : SchemaVisitor<T> {
    override fun visitClas(klass: KClass<*>) =
        visitSchema(klass, this, detectCustom)

    override fun visitTyped(prop: KProperty<*>, type: KType, types: Collection<KClass<*>>) =
        types.forEach { visitClas(it) }

    override fun visitCollectionTyped(prop: KProperty<*>, type: KType, types: Collection<KClass<*>>) =
        types.forEach { visitClas(it) }

    override fun visitMapTyped(
        prop: KProperty<*>,
        type: KType,
        types: Collection<KClass<*>>,
        modifierAware: Boolean
    ) = types.forEach { visitClas(it) }

    override fun visitCustom(prop: KProperty<*>, custom: T) {
        // no-op
    }
}

/**
 * Perform schema visiting using specified visitor.
 *
 * [detectCustom] - a way to treat some properties differently, based
 * on visitor specific info (say, annotations, or other markers)
 */
internal fun <CustomT : Any> visitSchema(
    root: KClass<*>,
    visitor: SchemaVisitor<CustomT>,
    detectCustom: ((KProperty<*>) -> CustomT?)? = null
) {
    val noArgCtor = root.constructors.firstOrNull { it.parameters.isEmpty() }
        ?: error("Non compatible schema type declaration: ${root.simpleName}") // TODO Add reporting

    val rootInstance = noArgCtor.call()

    // Careful about CCE.
    root.schemaDeclaredMemberProperties()
        .forEach {
            with(visitor) {
                val unwrappedType = it.unwrapValueTypeArg ?: return@forEach // TODO Handle non KClass return type.
                val customData = detectCustom?.invoke(it)
                val propertyValue = it.get(rootInstance)
                val defaultValue = propertyValue.default
                val modifiersAware = it.annotations.any { ModifierAware::class.isInstance(it) }

                when {
                    customData != null -> visitCustom(
                        it,
                        customData
                    )

                    unwrappedType.isCollection && unwrappedType.collectionType.isSchemaElement -> visitCollectionTyped(
                        it,
                        unwrappedType,
                        unwrappedType.collectionType.possibleTypes,
                    )

                    unwrappedType.isMap && unwrappedType.mapValueType.isSchemaElement -> visitMapTyped(
                        it,
                        unwrappedType,
                        unwrappedType.mapValueType.possibleTypes,
                        modifiersAware,
                    )

                    unwrappedType.isSchemaElement -> visitTyped(
                        it,
                        unwrappedType,
                        unwrappedType.possibleTypes
                    )

                    else -> visitCommon(
                        it,
                        unwrappedType,
                        defaultValue
                    )
                }
            }
        }
}

/**
 * Get all declared member properties in class hierarchy, limiting by [SchemaBase].
 */
fun KClass<*>.schemaDeclaredMemberProperties(): Sequence<KProperty1<Any, ValueBase<*>>> {
    val schemaClasses = generateSequence(listOf(this)) { roots ->
        roots.flatMap { root ->
            root.superclasses
                .filter { it.isSubclassOf(SchemaBase::class) }
                .filter { it != SchemaBase::class }
        }.takeIf { it.isNotEmpty() }
    }
    return schemaClasses
        .flatten()
        .flatMap { it.declaredMemberProperties }
        .filterIsInstance<KProperty1<Any, ValueBase<*>>>()
}

val KProperty<*>.unwrapValueTypeArg: KType?
    get() {
        // TODO Handle non KClass classifier.
        val kClassClassifier = returnType.classifier as? KClass<*> ?: return null
        return if (kClassClassifier.supertypes.map { it.classifier }.contains(ValueBase::class)) {
            // We have either [SchemaValue] or [NullableSchemaValue] wrapper.
            returnType.arguments.first().type
        } else {
            // Some other type, currently unsupported.
            error("Not supported type: $kClassClassifier in property ${this.name}")
        }
    }

// TODO For now we will use sealed subclasses, but later
// maybe some registry need to be introduced.
val KType.possibleTypes
    get() = unwrapKClass.sealedSubclasses.takeIf { it.isNotEmpty() }
        ?: listOf(unwrapKClass)

inline val KType.unwrapKClassOrNull get() = classifier as? KClass<*>
inline val KType.unwrapKClass get() = classifier as KClass<*>

val KType.isSchemaElement get() = unwrapKClassOrNull?.isSubclassOf(SchemaBase::class) == true

val KType.isEnum get() = unwrapKClassOrNull?.isSubclassOf(Enum::class) == true
val KType.isString get() = unwrapKClassOrNull?.isSubclassOf(String::class) == true
val KType.isBoolean get() = unwrapKClassOrNull?.isSubclassOf(Boolean::class) == true
val KType.isInt get() = unwrapKClassOrNull?.isSubclassOf(Int::class) == true
val KType.isPath get() = unwrapKClassOrNull?.isSubclassOf(Path::class) == true
val KType.isScalar get() = isEnum || isString || isBoolean || isInt || isPath

// FiXME Here we assume that collection type will have only one type argument, that
// generally is not true. Maybe need to add constraints of value<Type> methods.
val KType.isCollection get() = (classifier as? KClass<*>)?.isSubclassOf(Collection::class) ?: false
val KType.collectionType get() = arguments.first().type!!
val KType.collectionTypeOrNull get() = arguments.firstOrNull()?.type

// FiXME Here we assume that map type will have only two type arguments, that
// generally is not true. Maybe need to add constraints of value<Type> methods.
val KType.isMap get() = (classifier as? KClass<*>)?.isSubclassOf(Map::class) ?: false
val KType.mapValueType get() = arguments[1].type!!
val KType.mapValueTypeOrNull get() = arguments.getOrNull(1)?.type

/**
 * Tries to extract schema type from collection, map or just property.
 */
val KProperty<*>.unwrapSchemaTypeOrNull: KClass<out Any>?
    get() {
        val type = unwrapValueTypeArg ?: return null
        return when {
            type.isMap && type.mapValueType.isSchemaElement -> type.mapValueType.unwrapKClassOrNull
            type.isCollection && type.collectionType.isSchemaElement -> type.collectionType.unwrapKClassOrNull
            type.isSchemaElement -> type.unwrapKClass
            else -> null
        }
    }
