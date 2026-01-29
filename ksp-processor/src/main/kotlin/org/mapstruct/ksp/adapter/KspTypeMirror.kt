// ABOUTME: Adapter wrapping KSP class declarations as javax.lang.model DeclaredType.
// ABOUTME: Provides type argument resolution and element access for MapStruct's type system.
package org.mapstruct.ksp.adapter

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVisitor

class KspTypeMirror(
    val element: KspClassTypeElement,
    private val resolver: Resolver,
    private val logger: KSPLogger,
    private val ksType: KSType? = null,
    private val typeArgs: List<TypeMirror>? = null
) : DeclaredType {

    override fun getKind(): TypeKind {
        // KspTypeMirror represents DeclaredType (class/interface types), not primitives
        // Even for Kotlin's Boolean/Int/etc., when accessed as java.lang.Boolean/java.lang.Integer,
        // they should be treated as DECLARED types (wrapper types), not primitives
        return TypeKind.DECLARED
    }

    override fun asElement(): Element = element

    override fun getEnclosingType(): TypeMirror? {
        TODO("Not yet implemented")
    }

    override fun getTypeArguments(): List<TypeMirror> {
        // If explicit type arguments were provided (e.g., from getDeclaredType()), use those
        if (typeArgs != null) {
            return typeArgs
        }
        // If we have the actual KSType with resolved type arguments, use those
        if (ksType != null && ksType.arguments.isNotEmpty()) {
            return ksType.arguments.mapNotNull { typeArg ->
                val resolvedType = typeArg.type?.resolve() ?: return@mapNotNull null
                val typeDecl = resolvedType.declaration
                when (typeDecl) {
                    is KSClassDeclaration -> KspTypeMirror(
                        KspClassTypeElement(typeDecl, resolver, logger),
                        resolver,
                        logger,
                        resolvedType
                    )
                    is KSTypeParameter -> KspTypeVar(typeDecl, resolver, logger)
                    else -> null
                }
            }
        }
        // Fallback to type parameters from declaration (for backward compatibility)
        return element.declaration.typeParameters.map { typeParameter: KSTypeParameter ->
            KspTypeVar(typeParameter, resolver, logger)
        }
    }

    override fun getAnnotationMirrors(): List<AnnotationMirror?>? {
        TODO("Not yet implemented")
    }

    override fun <A : Annotation?> getAnnotation(annotationType: Class<A?>?): A? {
        TODO("Not yet implemented")
    }

    override fun <A : Annotation?> getAnnotationsByType(annotationType: Class<A?>?): Array<out A?>? {
        TODO("Not yet implemented")
    }

    override fun <R, P> accept(v: TypeVisitor<R, P>, p: P): R {
        return v.visitDeclared(this, p)
    }

    override fun equals(other: Any?): Boolean {
        // Use element.equals() which compares by qualified name, not reference equality.
        // Type argument comparison is handled by KspTypes.isSameType() which is the
        // correct place for full type equality checks per javax.lang.model conventions.
        return other is KspTypeMirror && element == other.element
    }

    override fun hashCode(): Int = element.hashCode()

    override fun toString(): String {
        // The gem library compares annotation types using toString(), expecting the qualified name
        val qualifiedName = element.qualifiedName?.toString()
        return qualifiedName ?: "KspTypeMirror[${element.declaration}]"
    }
}

