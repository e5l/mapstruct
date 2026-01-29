// ABOUTME: Represents Java primitive types (boolean, int, etc.) as javax.lang.model PrimitiveType.
// ABOUTME: Maps non-nullable Kotlin built-in types to their Java primitive equivalents.
package org.mapstruct.ksp.adapter

import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeVisitor

class KspPrimitiveType(private val primitiveKind: TypeKind) : AbstractKspAnnotatedConstruct(), PrimitiveType {

    override fun getKind(): TypeKind = primitiveKind

    override fun <R, P> accept(v: TypeVisitor<R, P>, p: P): R {
        return v.visitPrimitive(this, p)
    }

    override fun toString(): String = "KspPrimitiveType[$primitiveKind]"
}
