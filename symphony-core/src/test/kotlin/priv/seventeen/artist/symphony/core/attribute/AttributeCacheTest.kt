package priv.seventeen.artist.symphony.core.attribute

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.util.UUID

class AttributeCacheTest {

    private fun fresh() {
        AttributeCache.clear()
        AttributeRegistry.clear()
    }

    @Test
    fun `set and get round-trip`() {
        fresh()
        val u = UUID.randomUUID()
        AttributeCache.set(u, "atk", 12.5)
        assertEquals(12.5, AttributeCache.get(u, "atk"))
        assertNull(AttributeCache.get(u, "missing"))
    }

    @Test
    fun `setAll replaces map`() {
        fresh()
        val u = UUID.randomUUID()
        AttributeCache.setAll(u, mapOf("a" to 1.0, "b" to 2.0))
        AttributeCache.setAll(u, mapOf("c" to 3.0))
        assertNull(AttributeCache.get(u, "a"))
        assertEquals(3.0, AttributeCache.get(u, "c"))
    }

    @Test
    fun `full dirty marks everything`() {
        fresh()
        val u = UUID.randomUUID()
        AttributeCache.markDirty(u)
        assertTrue(AttributeCache.isDirty(u))
        assertNull(AttributeCache.getDirtyAttributes(u), "full dirty returns null")
    }

    @Test
    fun `partial dirty propagates via dependency index`() {
        fresh()
        AttributeRegistry.register(AttributeDefinition("base", "Base"))
        AttributeRegistry.register(AttributeDefinition("derived", "Derived", readonly = true, dependsOn = listOf("base")))
        AttributeRegistry.register(AttributeDefinition("top", "Top", readonly = true, dependsOn = listOf("derived")))

        val u = UUID.randomUUID()
        AttributeCache.markDirty(u, "base")
        val dirty = AttributeCache.getDirtyAttributes(u)
        assertEquals(setOf("base", "derived", "top"), dirty)
    }

    @Test
    fun `clearDirty resets state`() {
        fresh()
        val u = UUID.randomUUID()
        AttributeCache.markDirty(u, "x")
        AttributeCache.clearDirty(u)
        assertFalse(AttributeCache.isDirty(u))
    }

    @Test
    fun `full dirty wins over partial`() {
        fresh()
        val u = UUID.randomUUID()
        AttributeCache.markDirty(u)
        AttributeCache.markDirty(u, "x")
        assertNull(AttributeCache.getDirtyAttributes(u))
    }
}
