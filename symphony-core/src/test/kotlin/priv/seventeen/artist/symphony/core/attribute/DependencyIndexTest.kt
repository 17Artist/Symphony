package priv.seventeen.artist.symphony.core.attribute

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest

class DependencyIndexTest {

    @BeforeTest
    fun setup() {
        DependencyIndex.clear()
    }

    @Test
    fun `single level deps`() {
        DependencyIndex.rebuild(listOf(
            AttributeDefinition("a", "A"),
            AttributeDefinition("b", "B", dependsOn = listOf("a"))
        ))
        assertEquals(setOf("b"), DependencyIndex.dependents("a"))
        assertTrue(DependencyIndex.dependents("b").isEmpty())
    }

    @Test
    fun `transitive closure`() {
        DependencyIndex.rebuild(listOf(
            AttributeDefinition("a", "A"),
            AttributeDefinition("b", "B", dependsOn = listOf("a")),
            AttributeDefinition("c", "C", dependsOn = listOf("b")),
            AttributeDefinition("d", "D", dependsOn = listOf("c", "a"))
        ))
        assertEquals(setOf("b", "c", "d"), DependencyIndex.dependents("a"))
        assertEquals(setOf("c", "d"), DependencyIndex.dependents("b"))
    }

    @Test
    fun `diamond shape no duplicates`() {
        // a -> b -> d
        // a -> c -> d
        DependencyIndex.rebuild(listOf(
            AttributeDefinition("a", "A"),
            AttributeDefinition("b", "B", dependsOn = listOf("a")),
            AttributeDefinition("c", "C", dependsOn = listOf("a")),
            AttributeDefinition("d", "D", dependsOn = listOf("b", "c"))
        ))
        assertEquals(setOf("b", "c", "d"), DependencyIndex.dependents("a"))
    }

    @Test
    fun `cycle does not loop forever`() {
        // a -> b -> a (self-cycle through chain)
        DependencyIndex.rebuild(listOf(
            AttributeDefinition("a", "A", dependsOn = listOf("b")),
            AttributeDefinition("b", "B", dependsOn = listOf("a"))
        ))
        // 反向闭包应包含两者，且不会栈溢出
        val depsOfA = DependencyIndex.dependents("a")
        assertTrue(depsOfA.contains("b"))
    }

    @Test
    fun `unknown key returns empty`() {
        DependencyIndex.rebuild(emptyList())
        assertTrue(DependencyIndex.dependents("nope").isEmpty())
    }
}
