/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ksp.test.kotlin

import org.junit.jupiter.api.Test
import org.mapstruct.ksp.test.pluginTest

/**
 * Tests for Kotlin-specific features that the KSP processor must handle correctly,
 * including companion objects, inner classes, and sealed hierarchies.
 */
class KotlinFeaturesTest {

    @Test
    fun shouldHandleSourceClassWithCompanionObject() = pluginTest("""
        import org.mapstruct.Mapper

        data class Source(val name: String, val code: Int) {
            companion object {
                fun create(name: String): Source = Source(name, 0)
            }
        }

        data class Target(val name: String, val code: Int)

        @Mapper
        interface CompanionMapper {
            fun map(source: Source): Target
        }

        fun test() {
            val mapper = CompanionMapperImpl()
            val source = Source.create("test")
            val target = mapper.map(source)

            assert(target.name == "test") { "Expected name 'test' but was '${'$'}{target.name}'" }
            assert(target.code == 0) { "Expected code 0 but was ${'$'}{target.code}" }
        }
    """)

    @Test
    fun shouldHandleTargetClassWithCompanionObject() = pluginTest("""
        import org.mapstruct.Mapper

        data class Source(val value: String)

        data class Target(val value: String) {
            companion object {
                const val DEFAULT_VALUE = "default"
            }
        }

        @Mapper
        interface TargetCompanionMapper {
            fun map(source: Source): Target
        }

        fun test() {
            val mapper = TargetCompanionMapperImpl()
            val source = Source("hello")
            val target = mapper.map(source)

            assert(target.value == "hello") { "Expected 'hello' but was '${'$'}{target.value}'" }
        }
    """)

    @Test
    fun shouldMapClassWithInnerEnum() = pluginTest("""
        import org.mapstruct.Mapper

        class Source {
            var status: String = ""

            enum class Priority { LOW, MEDIUM, HIGH }
        }

        data class Target(val status: String)

        @Mapper
        interface InnerEnumMapper {
            fun map(source: Source): Target
        }

        fun test() {
            val mapper = InnerEnumMapperImpl()
            val source = Source()
            source.status = "active"
            val target = mapper.map(source)

            assert(target.status == "active") { "Expected 'active' but was '${'$'}{target.status}'" }
        }
    """)

    @Test
    fun shouldMapDataClassWithDefaultValues() = pluginTest("""
        import org.mapstruct.Mapper

        data class Source(val name: String, val age: Int)
        data class Target(val name: String = "unknown", val age: Int = 0)

        @Mapper
        interface DefaultValueMapper {
            fun map(source: Source): Target
        }

        fun test() {
            val mapper = DefaultValueMapperImpl()
            val source = Source("Alice", 30)
            val target = mapper.map(source)

            assert(target.name == "Alice") { "Expected 'Alice' but was '${'$'}{target.name}'" }
            assert(target.age == 30) { "Expected 30 but was ${'$'}{target.age}" }
        }
    """)

    @Test
    fun shouldMapClassWithNullableProperties() = pluginTest("""
        import org.mapstruct.Mapper

        data class Source(val name: String?, val count: Int?)
        data class Target(val name: String?, val count: Int?)

        @Mapper
        interface NullableMapper {
            fun map(source: Source): Target
        }

        fun test() {
            val mapper = NullableMapperImpl()

            // With values
            val source1 = Source("test", 42)
            val target1 = mapper.map(source1)
            assert(target1.name == "test") { "Expected 'test'" }
            assert(target1.count == 42) { "Expected 42" }

            // With nulls
            val source2 = Source(null, null)
            val target2 = mapper.map(source2)
            assert(target2.name == null) { "Expected null name" }
            assert(target2.count == null) { "Expected null count" }
        }
    """)
}
