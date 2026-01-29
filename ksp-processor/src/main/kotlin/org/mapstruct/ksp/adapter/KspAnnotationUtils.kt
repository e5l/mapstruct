// ABOUTME: Utility functions for converting KSP annotations to Java annotation mirrors
// ABOUTME: Provides centralized annotation handling to ensure consistency across adapter classes
package org.mapstruct.ksp.adapter

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import javax.lang.model.element.AnnotationMirror

private const val JAVA_REPEATABLE_FQN = "java.lang.annotation.Repeatable"
private const val KOTLIN_REPEATABLE_FQN = "kotlin.annotation.Repeatable"

/**
 * Converts a sequence of KSP annotations to a list of AnnotationMirror instances.
 * This is the standard conversion used by getAnnotationMirrors() implementations.
 *
 * In Java annotation processing, when multiple repeatable annotations (like @Mapping) are present,
 * they are wrapped in a container annotation (like @Mappings) at the source level. KSP doesn't
 * do this automatically, so we need to detect repeatable annotations and wrap them ourselves
 * to match Java APT behavior.
 *
 * @param annotations the KSP annotation sequence to convert
 * @param resolver the KSP resolver for type resolution
 * @param logger the KSP logger for diagnostics
 * @return an immutable list of AnnotationMirror instances
 */
fun toAnnotationMirrors(
    annotations: List<KSAnnotation>, resolver: Resolver, logger: KSPLogger
): List<AnnotationMirror> {
    // Group annotations by their fully qualified type name
    val groupedByType = annotations.groupBy { anno ->
        anno.annotationType.resolve().declaration.qualifiedName?.asString() ?: ""
    }

    val result = mutableListOf<AnnotationMirror>()

    for ((typeName, annotationsOfType) in groupedByType) {
        if (typeName.isEmpty()) continue

        if (annotationsOfType.size > 1) {
            // Multiple annotations of the same type - check if it's a repeatable annotation
            val containerType = findRepeatableContainerType(annotationsOfType.first(), resolver)
            if (containerType != null) {
                // Wrap all instances in a container annotation
                result.add(RepeatableAnnotation(containerType, annotationsOfType, resolver, logger))
            } else {
                // Not repeatable, add all individually
                annotationsOfType.forEach { result.add(KspAnnotationMirror(it, resolver, logger)) }
            }
        } else {
            // Single annotation - add directly
            result.add(KspAnnotationMirror(annotationsOfType.first(), resolver, logger))
        }
    }

    return result
}

/**
 * Finds the container annotation type for a repeatable annotation by looking for
 * the @Repeatable meta-annotation on the annotation class.
 *
 * @param annotation the annotation to check
 * @param resolver the KSP resolver
 * @return the container annotation type, or null if not a repeatable annotation
 */
private fun findRepeatableContainerType(annotation: KSAnnotation, resolver: Resolver): KSType? {
    val annoType = annotation.annotationType.resolve()
    val annoDecl = annoType.declaration as? KSClassDeclaration ?: return null

    // Check for @java.lang.annotation.Repeatable (also covers @kotlin.jvm.JvmRepeatable which is a typealias)
    val javaRepeatableAnno = annoDecl.annotations.firstOrNull { anno ->
        anno.annotationType.resolve().declaration.qualifiedName?.asString() == JAVA_REPEATABLE_FQN
    }
    if (javaRepeatableAnno != null) {
        // The value of @Repeatable is the container annotation class
        return javaRepeatableAnno.arguments.firstOrNull()?.value as? KSType
    }

    // Check for @kotlin.annotation.Repeatable (auto-generated container named "Container")
    val kotlinRepeatableAnno = annoDecl.annotations.firstOrNull { anno ->
        anno.annotationType.resolve().declaration.qualifiedName?.asString() == KOTLIN_REPEATABLE_FQN
    }
    if (kotlinRepeatableAnno != null) {
        // Kotlin auto-generates a container as an inner class named "Container"
        val annoQName = annoDecl.qualifiedName?.asString() ?: return null
        val containerDecl = resolver.getClassDeclarationByName("$annoQName.Container")
        return containerDecl?.asStarProjectedType()
    }

    return null
}
