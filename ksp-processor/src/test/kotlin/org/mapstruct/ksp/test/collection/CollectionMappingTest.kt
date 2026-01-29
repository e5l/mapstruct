/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ksp.test.collection

import org.junit.jupiter.api.Test
import org.mapstruct.ksp.test.pluginTest

/**
 * Tests for collection type mapping with KSP processor.
 */
class CollectionMappingTest {

    @Test
    fun shouldMapListToList() = pluginTest("""
        import org.mapstruct.Mapper

        data class Source(val items: List<String>)
        data class Target(val items: List<String>)

        @Mapper
        interface CollectionMapper {
            fun map(source: Source): Target
        }

        fun test() {
            val mapper = CollectionMapperImpl()
            val source = Source(listOf("x", "y", "z"))
            val target = mapper.map(source)

            assert(target.items == listOf("x", "y", "z")) {
                "Expected items to be ['x', 'y', 'z'] but was '${'$'}{target.items}'"
            }
        }
    """)

    @Test
    fun shouldMapSetToSet() = pluginTest("""
        import org.mapstruct.Mapper

        data class Source(val tags: Set<String>)
        data class Target(val tags: Set<String>)

        @Mapper
        interface SetMapper {
            fun map(source: Source): Target
        }

        fun test() {
            val mapper = SetMapperImpl()
            val source = Source(setOf("a", "b", "c"))
            val target = mapper.map(source)

            assert(target.tags.size == 3) {
                "Expected 3 tags but was ${'$'}{target.tags.size}"
            }
            assert(target.tags.containsAll(listOf("a", "b", "c"))) {
                "Expected tags to contain a, b, c but was ${'$'}{target.tags}"
            }
        }
    """)

    @Test
    fun shouldMapListWithTypeConversion() = pluginTest("""
        import org.mapstruct.Mapper

        data class Source(val values: List<Int>)
        data class Target(val values: List<String>)

        @Mapper
        interface ListConversionMapper {
            fun map(source: Source): Target
        }

        fun test() {
            val mapper = ListConversionMapperImpl()
            val source = Source(listOf(1, 2, 3))
            val target = mapper.map(source)

            assert(target.values == listOf("1", "2", "3")) {
                "Expected ['1', '2', '3'] but was ${'$'}{target.values}"
            }
        }
    """)

    @Test
    fun shouldMapEmptyCollection() = pluginTest("""
        import org.mapstruct.Mapper

        data class Source(val items: List<String>)
        data class Target(val items: List<String>)

        @Mapper
        interface EmptyCollectionMapper {
            fun map(source: Source): Target
        }

        fun test() {
            val mapper = EmptyCollectionMapperImpl()
            val source = Source(emptyList())
            val target = mapper.map(source)

            assert(target.items.isEmpty()) {
                "Expected empty list but was ${'$'}{target.items}"
            }
        }
    """)
}
