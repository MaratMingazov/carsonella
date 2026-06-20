package maratmingazovr.ai.carsonella.world.save

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldSnapshotSerializationTest {

    private fun sampleSnapshot() = WorldSnapshotDto(
        tick = 1234L,
        seed = 1L,
        idGenNext = 42L,
        environment = EnvironmentDto(centerX = 800f, centerY = 400f, radius = 500f, temperature = "Space"),
        entities = listOf(
            EntityDto(id = 0, element = "Star", alive = true, x = 800f, y = 400f, dirX = 1f, dirY = 0f, velocity = 0f, energy = 0f, electrons = 1, parentId = null),
            EntityDto(id = 1, element = "Proton", alive = true, x = 810f, y = 405f, dirX = 0f, dirY = 1f, velocity = 2f, energy = 0f, electrons = 0, parentId = 0),
            EntityDto(id = 2, element = "HYDROGEN", alive = false, x = 100f, y = 200f, dirX = -1f, dirY = 0f, velocity = 1f, energy = 3.5f, electrons = 1, parentId = null),
        ),
        summary = mapOf("Star" to 1, "Proton" to 1),
    )

    @Test
    fun roundTripPreservesData() {
        val original = sampleSnapshot()
        val json = WorldJson.encode(original)
        val restored = WorldJson.decode(json)
        assertEquals(original, restored)
    }

    @Test
    fun jsonHasReadableElementNames() {
        // Имена элементов в JSON должны быть человекочитаемыми — это нужно для анализа файла.
        val json = WorldJson.encode(sampleSnapshot())
        assertTrue(json.contains("\"Star\""), "ожидали имя элемента Star в JSON")
        assertTrue(json.contains("\"Proton\""), "ожидали имя элемента Proton в JSON")
        assertTrue(json.contains("\"summary\""), "ожидали блок summary в JSON")
    }

    @Test
    fun parentIdNullIsPreserved() {
        // Сущности в корневом Environment имеют parentId = null — он не должен теряться при round-trip.
        val restored = WorldJson.decode(WorldJson.encode(sampleSnapshot()))
        assertEquals(null, restored.entities.first { it.id == 0L }.parentId)
        assertEquals(0L, restored.entities.first { it.id == 1L }.parentId)
    }
}