// ABOUTME: Implements javax.lang.model.util.Types for KSP type operations.
// ABOUTME: Provides type comparison, erasure, subtyping, boxing, and declared type construction.
package org.mapstruct.ksp.adapter

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.NoType
import javax.lang.model.type.NullType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.WildcardType
import javax.lang.model.util.Types

class KspTypes(
    private val environment: SymbolProcessorEnvironment,
    private val resolver: Resolver,
) : Types {
    private val logger: KSPLogger = environment.logger
    override fun asElement(t: TypeMirror): Element? {
        return when (t) {
            is KspTypeMirror -> t.element
            is KspTypeVar -> throw IllegalArgumentException("Cannot get element from type variable")
            is KspNoType -> null // NoType (void, package, etc.) has no associated element
            else -> error("Unsupported TypeMirror type: ${t::class.simpleName}")
        }
    }

    override fun isSameType(
        t1: TypeMirror,
        t2: TypeMirror
    ): Boolean = when {
        t1 === t2 -> true
        t1 is KspNoType && t2 is KspNoType -> {
            t1.kind == t2.kind
        }
        t1 is KspPrimitiveType && t2 is KspPrimitiveType -> {
            t1.kind == t2.kind
        }
        // Cross-type primitive comparison
        t1 is KspPrimitiveType && t2 is PrimitiveType -> {
            t1.kind == t2.kind
        }
        t1 is PrimitiveType && t2 is KspPrimitiveType -> {
            t1.kind == t2.kind
        }
        // Array type comparison
        t1 is KspArrayType && t2 is KspArrayType -> {
            isSameType(t1.componentType, t2.componentType)
        }
        t1 is KspArrayType && t2 is ArrayType -> {
            isSameType(t1.componentType, t2.componentType)
        }
        t1 is ArrayType && t2 is KspArrayType -> {
            isSameType(t1.componentType, t2.componentType)
        }
        t1 is KspTypeMirror && t2 is KspTypeMirror -> {
            val qn1 = t1.element.qualifiedName?.toString()
            val qn2 = t2.element.qualifiedName?.toString()
            if (qn1 == null || qn2 == null || qn1 != qn2) {
                false
            } else {
                // Java generics are invariant: type arguments must match exactly
                val args1 = t1.typeArguments
                val args2 = t2.typeArguments
                args1.size == args2.size && args1.zip(args2).all { (a1, a2) -> isSameType(a1, a2) }
            }
        }
        // Cross-type declared type comparison
        t1 is KspTypeMirror && t2 is DeclaredType -> {
            val qn1 = t1.element.qualifiedName?.toString()
            val element2 = t2.asElement()
            val qn2 = when (element2) {
                is TypeElement -> element2.qualifiedName?.toString()
                else -> null
            }
            if (qn1 == null || qn2 == null || qn1 != qn2) {
                false
            } else {
                val args1 = t1.typeArguments
                val args2 = t2.typeArguments
                args1.size == args2.size && args1.zip(args2).all { (a1, a2) -> isSameType(a1, a2) }
            }
        }
        t1 is DeclaredType && t2 is KspTypeMirror -> {
            val element1 = t1.asElement()
            val qn1 = when (element1) {
                is TypeElement -> element1.qualifiedName?.toString()
                else -> null
            }
            val qn2 = t2.element.qualifiedName?.toString()
            if (qn1 == null || qn2 == null || qn1 != qn2) {
                false
            } else {
                val args1 = t1.typeArguments
                val args2 = t2.typeArguments
                args1.size == args2.size && args1.zip(args2).all { (a1, a2) -> isSameType(a1, a2) }
            }
        }
        t1 is KspTypeVar && t2 is KspTypeVar -> {
            t1.param.name.asString() == t2.param.name.asString()
        }
        else -> false
    }

    override fun isSubtype(
        t1: TypeMirror,
        t2: TypeMirror
    ): Boolean {
        return isSubtypeImpl(t1, t2)
    }

    private fun isSubtypeImpl(
        t1: TypeMirror,
        t2: TypeMirror
    ): Boolean = when {
        isSameType(t1, t2) -> true
        t1 is KspNoType || t2 is KspNoType -> {
            // NoType (VOID, NONE, etc.) is only a subtype of itself
            false
        }
        t1 is KspPrimitiveType || t2 is KspPrimitiveType -> {
            // Primitive types are only subtypes of themselves (handled by isSameType above)
            false
        }
        t1 is KspArrayType -> {
            // Arrays are subtypes of Object, Cloneable, and Serializable, but not Collection
            when {
                t2 is KspArrayType -> {
                    // Array subtyping: T[] <: S[] iff T <: S (for reference types)
                    // Primitive arrays are invariant: int[] is not a subtype of Object[]
                    val c1 = t1.componentType
                    val c2 = t2.componentType
                    if (c1 is KspPrimitiveType || c2 is KspPrimitiveType) {
                        isSameType(c1, c2)
                    } else {
                        isSubtypeImpl(c1, c2)
                    }
                }
                t2 is KspTypeMirror -> {
                    // All arrays are subtypes of Object, Cloneable, and Serializable
                    val t2Name = t2.element.qualifiedName?.toString()
                    t2Name == "java.lang.Object" || t2Name == "java.lang.Cloneable" || t2Name == "java.io.Serializable"
                }
                else -> false
            }
        }
        t2 is KspArrayType -> {
            // Non-array types are not subtypes of arrays (except null, which we don't handle here)
            false
        }
        t1 is KspTypeMirror && t2 is KspTypeMirror -> {
            // Check raw type compatibility first (star projection checks inheritance ignoring type args)
            val rawSubtype = t2.element.declaration.asStarProjectedType()
                .isAssignableFrom(t1.element.declaration.asStarProjectedType())
            // Then verify type arguments are compatible per Java's invariant generics
            rawSubtype && areTypeArgumentsCompatible(t1, t2)
        }
        t1 is KspTypeVar && t2 is KspTypeVar -> {
            isSameType(t1, t2)
        }
        t1 is KspTypeVar && t2 is KspTypeMirror -> {
            val upperBound = t1.param.bounds.firstOrNull()
            when {
                upperBound != null -> {
                    val qualifiedName = upperBound.resolve().declaration.qualifiedName?.asString()
                    when {
                        qualifiedName != null -> {
                            val upperBoundElement = resolver.getClassDeclarationByName(resolver.getKSNameFromString(qualifiedName))
                            when {
                                upperBoundElement != null -> {
                                    val upperBoundMirror = KspTypeMirror(KspClassTypeElement(upperBoundElement, resolver, logger), resolver, logger)
                                    isSubtypeImpl(upperBoundMirror, t2)
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                }
                else -> false
            }
        }
        else -> {
            error("Unsupported type subtype: ${t1::class.simpleName}:$t1 vs ${t2::class.simpleName}:$t2")
        }
    }

    override fun isAssignable(
        t1: TypeMirror,
        t2: TypeMirror
    ): Boolean {
        return when {
            isSameType(t1, t2) -> true
            // Handle primitive-to-boxed assignability (auto-boxing)
            t1 is KspPrimitiveType && t2 is KspTypeMirror -> {
                val boxedQualifiedName = when (t1.kind) {
                    TypeKind.BOOLEAN -> "java.lang.Boolean"
                    TypeKind.BYTE -> "java.lang.Byte"
                    TypeKind.SHORT -> "java.lang.Short"
                    TypeKind.INT -> "java.lang.Integer"
                    TypeKind.LONG -> "java.lang.Long"
                    TypeKind.CHAR -> "java.lang.Character"
                    TypeKind.FLOAT -> "java.lang.Float"
                    TypeKind.DOUBLE -> "java.lang.Double"
                    else -> null
                }
                boxedQualifiedName != null && t2.element.qualifiedName?.toString() == boxedQualifiedName
            }
            // Handle boxed-to-primitive assignability (auto-unboxing)
            t1 is KspTypeMirror && t2 is KspPrimitiveType -> {
                val boxedQualifiedName = when (t2.kind) {
                    TypeKind.BOOLEAN -> "java.lang.Boolean"
                    TypeKind.BYTE -> "java.lang.Byte"
                    TypeKind.SHORT -> "java.lang.Short"
                    TypeKind.INT -> "java.lang.Integer"
                    TypeKind.LONG -> "java.lang.Long"
                    TypeKind.CHAR -> "java.lang.Character"
                    TypeKind.FLOAT -> "java.lang.Float"
                    TypeKind.DOUBLE -> "java.lang.Double"
                    else -> null
                }
                boxedQualifiedName != null && t1.element.qualifiedName?.toString() == boxedQualifiedName
            }
            t1 is KspTypeMirror && t2 is KspTypeMirror -> {
                // Check raw type compatibility first (star projection checks inheritance ignoring type args)
                val rawAssignable = t2.element.declaration.asStarProjectedType()
                    .isAssignableFrom(t1.element.declaration.asStarProjectedType())
                // Then verify type arguments are compatible per Java's invariant generics
                rawAssignable && areTypeArgumentsCompatible(t1, t2)
            }
            else -> isSubtype(t1, t2)
        }
    }

    override fun contains(
        t1: TypeMirror,
        t2: TypeMirror
    ): Boolean {
        return isSameType(t1, t2) || isSubtype(t2, t1)
    }

    /**
     * Checks if type arguments of t1 are compatible with t2 following Java's invariant generics.
     * Assumes raw types have already been verified as compatible via star-projected check.
     */
    private fun areTypeArgumentsCompatible(t1: KspTypeMirror, t2: KspTypeMirror): Boolean {
        val args2 = t2.typeArguments

        // If target is raw (no type args or only unresolved type variables), always compatible
        if (args2.isEmpty() || args2.all { it is KspTypeVar }) return true

        // If same raw type, compare type arguments directly (invariant)
        if (t1.element == t2.element) {
            val args1 = t1.typeArguments
            if (args1.size != args2.size) return false
            // Raw source assigned to parameterized target: unchecked but allowed
            if (args1.isEmpty() || args1.all { it is KspTypeVar }) return true
            return args1.zip(args2).all { (a1, a2) -> isSameType(a1, a2) }
        }

        // Different raw types (e.g., ArrayList<String> vs List<String>):
        // Walk t1's supertypes to find the parameterization matching t2's raw type
        val t2QualifiedName = t2.element.qualifiedName?.toString() ?: return true
        val matchingSupertype = findSupertypeWithRawType(t1, t2QualifiedName)
        if (matchingSupertype == null) return true // Shouldn't happen since raw check passed

        val superArgs = matchingSupertype.typeArguments
        if (superArgs.isEmpty() || superArgs.all { it is KspTypeVar }) return true
        if (superArgs.size != args2.size) return true // Unexpected mismatch, fallback
        return superArgs.zip(args2).all { (a1, a2) -> isSameType(a1, a2) }
    }

    /**
     * Walks the supertype hierarchy of the given type to find a supertype whose raw type
     * matches the specified qualified name. Used to resolve type arguments through inheritance
     * (e.g., finding List<String> in the supertypes of ArrayList<String>).
     */
    private fun findSupertypeWithRawType(type: KspTypeMirror, targetQualifiedName: String): KspTypeMirror? {
        for (supertype in directSupertypes(type)) {
            if (supertype is KspTypeMirror) {
                if (supertype.element.qualifiedName?.toString() == targetQualifiedName) {
                    return supertype
                }
                val found = findSupertypeWithRawType(supertype, targetQualifiedName)
                if (found != null) return found
            }
        }
        return null
    }

    override fun isSubsignature(
        m1: ExecutableType,
        m2: ExecutableType
    ): Boolean {
        // JLS §8.4.2: m1 is a subsignature of m2 if either:
        // - m1 has the same signature as m2, or
        // - the signature of m1 is the same as the erasure of the signature of m2
        val params1 = m1.parameterTypes
        val params2 = m2.parameterTypes
        if (params1.size != params2.size) return false

        // Check exact match first
        val exactMatch = params1.zip(params2).all { (p1, p2) -> isSameType(p1, p2) }
        if (exactMatch) return true

        // Check if m1 matches erasure of m2
        return params1.zip(params2).all { (p1, p2) -> isSameType(p1, erasure(p2)) }
    }

    override fun directSupertypes(t: TypeMirror): List<TypeMirror> {
        return when (t) {
            is KspTypeMirror -> {
                val supertypes = mutableListOf<TypeMirror>()
                val declaration = t.element.declaration

                // Build type parameter substitution map: e.g., for List<Integer>,
                // maps "E" → KspTypeMirror(Integer) so supertypes like Collection<E>
                // become Collection<Integer>
                val actualTypeArgs = t.typeArguments
                val typeParams = declaration.typeParameters
                val typeParamMap = mutableMapOf<String, TypeMirror>()
                for (i in typeParams.indices) {
                    if (i < actualTypeArgs.size) {
                        typeParamMap[typeParams[i].name.asString()] = actualTypeArgs[i]
                    }
                }

                declaration.superTypes.forEach { superTypeRef ->
                    val superType = superTypeRef.resolve()
                    val superDeclaration = superType.declaration
                    if (superDeclaration is com.google.devtools.ksp.symbol.KSClassDeclaration) {
                        // Substitute type parameters in the supertype's arguments
                        val resolvedArgs = if (typeParamMap.isNotEmpty() && superType.arguments.isNotEmpty()) {
                            superType.arguments.mapNotNull { arg ->
                                val argType = arg.type?.resolve() ?: return@mapNotNull null
                                val argDecl = argType.declaration
                                if (argDecl is com.google.devtools.ksp.symbol.KSTypeParameter) {
                                    // Type parameter reference — substitute with actual argument
                                    typeParamMap[argDecl.name.asString()]
                                } else if (argDecl is com.google.devtools.ksp.symbol.KSClassDeclaration) {
                                    KspTypeMirror(
                                        KspClassTypeElement(argDecl, resolver, logger),
                                        resolver,
                                        logger,
                                        argType
                                    )
                                } else {
                                    null
                                }
                            }
                        } else {
                            null
                        }

                        supertypes.add(
                            KspTypeMirror(
                                KspClassTypeElement(superDeclaration, resolver, logger),
                                resolver,
                                logger,
                                superType,
                                typeArgs = resolvedArgs
                            )
                        )
                    }
                }
                supertypes
            }
            else -> emptyList()
        }
    }

    override fun erasure(t: TypeMirror): TypeMirror {
        return when (t) {
            is KspTypeMirror -> {
                // Java APT erasure strips type arguments: List<String> -> List
                // Create a new KspTypeMirror without the KSType so getTypeArguments() returns empty
                KspTypeMirror(t.element, resolver, logger)
            }
            is KspTypeVar -> {
                // Erasure of a type variable is the erasure of its upper bound
                val upperBound = t.upperBound
                if (upperBound != null) {
                    erasure(upperBound)
                } else {
                    // No bound means upper bound is Object
                    val objectDecl = resolver.getClassDeclarationByName(
                        resolver.getKSNameFromString("java.lang.Object")
                    )
                    if (objectDecl != null) {
                        KspTypeMirror(KspClassTypeElement(objectDecl, resolver, logger), resolver, logger)
                    } else {
                        t
                    }
                }
            }
            is KspArrayType -> {
                // Erasure of an array type is the array of the erasure of the component type
                KspArrayType(erasure(t.componentType))
            }
            is KspWildcardType -> {
                // Erasure of a wildcard is the erasure of its upper bound (extends bound)
                val extendsBound = t.extendsBound
                if (extendsBound != null) {
                    erasure(extendsBound)
                } else {
                    // Unbounded wildcard or super-bounded: erasure is Object
                    val objectDecl = resolver.getClassDeclarationByName(
                        resolver.getKSNameFromString("java.lang.Object")
                    )
                    if (objectDecl != null) {
                        KspTypeMirror(KspClassTypeElement(objectDecl, resolver, logger), resolver, logger)
                    } else {
                        t
                    }
                }
            }
            is KspPrimitiveType -> t
            is KspNullType -> t
            is KspNoType -> t
            else -> error("TypeMirror is not a KspTypeMirror: $t")
        }
    }

    override fun boxedClass(p: PrimitiveType): TypeElement {
        val boxedClassName = when (p.kind) {
            TypeKind.BOOLEAN -> "java.lang.Boolean"
            TypeKind.BYTE -> "java.lang.Byte"
            TypeKind.SHORT -> "java.lang.Short"
            TypeKind.INT -> "java.lang.Integer"
            TypeKind.LONG -> "java.lang.Long"
            TypeKind.CHAR -> "java.lang.Character"
            TypeKind.FLOAT -> "java.lang.Float"
            TypeKind.DOUBLE -> "java.lang.Double"
            else -> error("Not a primitive type: ${p.kind}")
        }
        val boxedDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(boxedClassName))
            ?: error("Could not find boxed class for $boxedClassName")
        return KspClassTypeElement(boxedDeclaration, resolver, logger)
    }

    override fun unboxedType(t: TypeMirror): PrimitiveType {
        if (t !is KspTypeMirror) {
            error("Cannot unbox non-KspTypeMirror: ${t::class.simpleName}")
        }
        val qualifiedName = t.element.qualifiedName?.toString()
        val primitiveKind = when (qualifiedName) {
            "java.lang.Boolean" -> TypeKind.BOOLEAN
            "java.lang.Byte" -> TypeKind.BYTE
            "java.lang.Short" -> TypeKind.SHORT
            "java.lang.Integer" -> TypeKind.INT
            "java.lang.Long" -> TypeKind.LONG
            "java.lang.Character" -> TypeKind.CHAR
            "java.lang.Float" -> TypeKind.FLOAT
            "java.lang.Double" -> TypeKind.DOUBLE
            else -> error("Not a boxed type: $qualifiedName")
        }
        return getPrimitiveType(primitiveKind)
    }

    override fun capture(t: TypeMirror): TypeMirror {
        return t
    }

    override fun getPrimitiveType(kind: TypeKind): PrimitiveType {
        return KspPrimitiveType(kind)
    }

    override fun getNullType(): NullType {
        return KspNullType()
    }

    override fun getNoType(kind: TypeKind): NoType {
        return KspNoType(kind)
    }

    override fun getArrayType(componentType: TypeMirror): ArrayType {
        return KspArrayType(componentType)
    }

    override fun getWildcardType(
        extendsBound: TypeMirror?,
        superBound: TypeMirror?
    ): WildcardType {
        return KspWildcardType(extendsBound, superBound)
    }

    override fun getDeclaredType(
        typeElem: TypeElement,
        vararg typeArgs: TypeMirror
    ): DeclaredType {
        if (typeElem !is KspClassTypeElement) {
            error("TypeElement must be KspClassTypeElement")
        }
        return if (typeArgs.isEmpty()) {
            KspTypeMirror(typeElem, resolver, logger)
        } else {
            KspTypeMirror(typeElem, resolver, logger, typeArgs = typeArgs.toList())
        }
    }

    override fun getDeclaredType(
        containing: DeclaredType,
        typeElem: TypeElement,
        vararg typeArgs: TypeMirror
    ): DeclaredType {
        return getDeclaredType(typeElem, *typeArgs)
    }

    override fun asMemberOf(
        containing: DeclaredType,
        element: Element
    ): TypeMirror {
        return when (element) {
            is KspClassTypeElement -> element.asType()
            is KspPropertyAccessorExecutableElement -> {
                // For property accessor executable elements, return an ExecutableType
                KspExecutableType(element, resolver, logger)
            }
            is KspExecutableElement -> {
                // For ExecutableElement, return an ExecutableType representing the method signature
                KspExecutableType(element, resolver, logger)
            }
            is KspVariableElement -> {
                // For VariableElement (fields/parameters), return the variable's type
                element.asType()
            }
            else -> error("asMemberOf not implemented for element type: ${element::class.simpleName}")
        }
    }

}
