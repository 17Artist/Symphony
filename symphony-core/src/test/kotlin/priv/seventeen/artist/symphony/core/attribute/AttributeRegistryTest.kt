package priv.seventeen.artist.symphony.core.attribute

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.BeforeTest

class AttributeRegistryTest {

    @BeforeTest
    fun setup() {
        AttributeRegistry.clear()
    }

    @Test
    fun `register stores and exposes`() {
        AttributeRegistry.register(AttributeDefinition("atk", "Attack", category = "combat"))
        assertTrue(AttributeRegistry.exists("atk"))
        assertEquals("Attack", AttributeRegistry.get("atk")?.displayName)
        assertEquals(setOf("atk"), AttributeRegistry.ids())
    }

    @Test
    fun `unregister removes from index and dependency graph`() {
        AttributeRegistry.register(AttributeDefinition("a", "A"))
        AttributeRegistry.register(AttributeDefinition("b", "B", dependsOn = listOf("a")))
        AttributeRegistry.unregister("b")
        assertFalse(AttributeRegistry.exists("b"))
        assertTrue(DependencyIndex.dependents("a").isEmpty(), "a 的依赖者应清空")
    }

    @Test
    fun `category and tag lookup`() {
        AttributeRegistry.register(AttributeDefinition("hp", "HP", category = "vital", tags = listOf("defensive")))
        AttributeRegistry.register(AttributeDefinition("atk", "Attack", category = "combat", tags = listOf("offensive")))
        AttributeRegistry.register(AttributeDefinition("def", "Defense", category = "vital", tags = listOf("defensive")))

        assertEquals(setOf("hp", "def"), AttributeRegistry.getByCategory("vital").map { it.id }.toSet())
        assertEquals(setOf("hp", "def"), AttributeRegistry.getByTag("defensive").map { it.id }.toSet())
        assertEquals(setOf("atk"), AttributeRegistry.getByTag("offensive").map { it.id }.toSet())
    }

    @Test
    fun `register triggers dependency rebuild`() {
        AttributeRegistry.register(AttributeDefinition("a", "A"))
        AttributeRegistry.register(AttributeDefinition("b", "B", dependsOn = listOf("a")))
        AttributeRegistry.register(AttributeDefinition("c", "C", dependsOn = listOf("b")))
        assertEquals(setOf("b", "c"), DependencyIndex.dependents("a"))
    }

    @Test
    fun `clear empties everything`() {
        AttributeRegistry.register(AttributeDefinition("x", "X"))
        AttributeRegistry.clear()
        assertNull(AttributeRegistry.get("x"))
        assertTrue(AttributeRegistry.ids().isEmpty())
        assertTrue(DependencyIndex.dependents("x").isEmpty())
    }
}
