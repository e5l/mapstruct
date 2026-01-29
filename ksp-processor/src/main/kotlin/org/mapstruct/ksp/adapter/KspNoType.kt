// ABOUTME: Represents javax.lang.model NoType for void, package, and none type kinds.
// ABOUTME: Used as a return type placeholder for void methods and missing types.
package org.mapstruct.ksp.adapter

import javax.lang.model.type.NoType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeVisitor

class KspNoType(private val noTypeKind: TypeKind) : AbstractKspAnnotatedConstruct(), NoType {

    override fun getKind(): TypeKind = noTypeKind

    override fun <R : Any?, P : Any?> accept(v: TypeVisitor<R?, P?>?, p: P?): R? {
        return v?.visitNoType(this, p)
    }

    override fun toString(): String = "KspNoType[$noTypeKind]"
}
