// ABOUTME: Shared utility functions for KSP type handling and TypeMirror creation
// ABOUTME: Centralizes Kotlin-to-Java type conversion logic used across adapter classes
package org.mapstruct.ksp.adapter

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * Shared utilities for creating TypeMirror instances from KSP types.
 */
object KspTypeUtils {

    // Cache primitive type instances for reuse across all adapter classes
    private val primitiveTypeCache = mutableMapOf<TypeKind, KspPrimitiveType>()

    /**
     * Creates appropriate TypeMirror for a KSType, handling primitive types and void.
     * Kotlin primitive types (Int, Long, etc.) are represented as their Java boxed equivalents
     * in KSP, but MapStruct needs primitive types for non-nullable Kotlin primitives.
     * Nullable Kotlin primitives (Boolean?, Int?, etc.) must be boxed in Java.
     * Kotlin Unit is mapped to Java void.
     *
     * @param ksType The KSP type to convert
     * @param resolver The KSP resolver for built-in type access
     * @param logger The KSP logger for debugging
     * @return A TypeMirror representing the Java equivalent of the Kotlin type
     */
    fun createTypeMirrorForType(
        ksType: KSType,
        resolver: Resolver,
        logger: KSPLogger
    ): TypeMirror {
        val decl = ksType.declaration

        if (decl !is KSClassDeclaration) {
            return KspNoType(TypeKind.NONE)
        }

        // Check if this is a Kotlin built-in primitive type or Unit
        // BUT: Only use primitive if NOT nullable (nullable types must be boxed in Java)
        val builtins = resolver.builtIns
        val starProjectedType = decl.asStarProjectedType()
        val isNullable = ksType.isMarkedNullable

        // Handle Unit -> void mapping (Unit is never nullable in the meaningful sense)
        if (starProjectedType == builtins.unitType) {
            return KspNoType(TypeKind.VOID)
        }

        val primitiveKind = if (!isNullable) {
            when (starProjectedType) {
                builtins.booleanType -> TypeKind.BOOLEAN
                builtins.byteType -> TypeKind.BYTE
                builtins.shortType -> TypeKind.SHORT
                builtins.intType -> TypeKind.INT
                builtins.longType -> TypeKind.LONG
                builtins.charType -> TypeKind.CHAR
                builtins.floatType -> TypeKind.FLOAT
                builtins.doubleType -> TypeKind.DOUBLE
                else -> null
            }
        } else {
            null // Nullable types are always boxed
        }

        return if (primitiveKind != null) {
            // Return cached instance to ensure identity equality
            primitiveTypeCache.getOrPut(primitiveKind) { KspPrimitiveType(primitiveKind) }
        } else {
            KspTypeMirror(
                KspClassTypeElement(decl, resolver, logger),
                resolver,
                logger,
                ksType  // Pass the KSType to preserve type arguments
            )
        }
    }
}
