// ABOUTME: Represents javax.lang.model NullType for the null literal type.
// ABOUTME: Implements the null type required by the javax.lang.model type system.
package org.mapstruct.ksp.adapter

import javax.lang.model.type.NullType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeVisitor

class KspNullType : AbstractKspAnnotatedConstruct(), NullType {

    override fun getKind(): TypeKind = TypeKind.NULL

    override fun <R : Any?, P : Any?> accept(v: TypeVisitor<R?, P?>?, p: P?): R? {
        return v?.visitNull(this, p)
    }

    override fun toString(): String = "KspNullType"
}
