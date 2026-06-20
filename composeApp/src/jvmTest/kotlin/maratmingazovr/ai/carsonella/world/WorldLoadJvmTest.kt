package maratmingazovr.ai.carsonella.world

import kotlinx.coroutines.CoroutineScope
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.world.save.EntityDto
import maratmingazovr.ai.carsonella.world.save.EnvironmentDto
import maratmingazovr.ai.carsonella.world.save.WorldSnapshotDto
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorldLoadJvmTest {

    // Мир без start() — фоновый цикл не запускаем, applySnapshot зовём напрямую.
    private fun freshWorld() = World(CoroutineScope(EmptyCoroutineContext))

    private fun snapshot() = WorldSnapshotDto(
        tick = 100L,
        seed = 1L,
        idGenNext = 3L,
        environment = EnvironmentDto(centerX = 800f, centerY = 400f, radius = 500f, temperature = "Space"),
        entities = listOf(
            // звезда — сама себе среда
            EntityDto(id = 0, element = "Star", alive = true, x = 800f, y = 400f, dirX = 1f, dirY = 0f, velocity = 0f, energy = 0f, electrons = 1, parentId = null),
            // протон внутри звезды
            EntityDto(id = 1, element = "Proton", alive = true, x = 805f, y = 402f, dirX = 0f, dirY = 1f, velocity = 1f, energy = 0f, electrons = 0, parentId = 0),
            // водород в корневой среде
            EntityDto(id = 2, element = "HYDROGEN", alive = true, x = 100f, y = 200f, dirX = -1f, dirY = 0f, velocity = 2f, energy = 0f, electrons = 1, parentId = null),
        ),
        summary = mapOf("Star" to 1, "Proton" to 1, "HYDROGEN" to 1),
    )

    private fun World.entityById(id: Long): Entity<*> = entities.first { it.state().value.id == id }

    @Test
    fun restoresAllEntitiesAndTick() {
        val world = freshWorld()
        world.applySnapshot(snapshot())

        assertEquals(3, world.entities.size)
        assertEquals(100L, world.tick)
        assertEquals(setOf(0L, 1L, 2L), world.entities.mapTo(mutableSetOf()) { it.state().value.id })
    }

    @Test
    fun restoresEnvironmentTree() {
        val world = freshWorld()
        world.applySnapshot(snapshot())

        val star = world.entityById(0)
        val proton = world.entityById(1)
        val hydrogen = world.entityById(2)

        // протон должен сидеть внутри звезды
        assertSame(star, proton.getEnvironment(), "родитель протона — звезда")
        assertTrue(star.getEnvChildren().any { it.state().value.id == 1L }, "звезда содержит протон")

        // водород — в корневой среде, не внутри звезды
        assertSame(world.environment, hydrogen.getEnvironment(), "водород в корневой среде")
        assertTrue(world.environment.getEnvChildren().any { it.state().value.id == 2L })
        assertTrue(star.getEnvChildren().none { it.state().value.id == 2L }, "водорода в звезде быть не должно")
    }

    @Test
    fun restoresEntityState() {
        val world = freshWorld()
        world.applySnapshot(snapshot())

        val proton = world.entityById(1).state().value
        assertEquals(805f, proton.position.x)
        assertEquals(402f, proton.position.y)
        assertEquals(1f, proton.velocity)
        assertEquals(0, proton.electrons)
    }

    @Test
    fun restoresIdGeneratorSoNewIdsDoNotCollide() {
        val world = freshWorld()
        world.applySnapshot(snapshot())

        // следующая созданная сущность должна получить id = idGenNext (3), а не пересечься с загруженными
        val created = world.entityGenerator.createEntity(
            element = maratmingazovr.ai.carsonella.chemistry.Element.HYDROGEN,
            position = maratmingazovr.ai.carsonella.Position(0f, 0f),
            direction = maratmingazovr.ai.carsonella.Vec2D(1f, 0f),
            velocity = 0f, energy = 0f, environment = world.environment,
        )
        assertEquals(3L, created.state().value.id)
    }

    @Test
    fun loadReplacesPreviousWorld() {
        val world = freshWorld()
        // первый слепок
        world.applySnapshot(snapshot())
        // второй слепок — один атом; должен полностью заменить предыдущий мир
        world.applySnapshot(
            snapshot().copy(
                entities = listOf(
                    EntityDto(id = 0, element = "OXYGEN_16", alive = true, x = 1f, y = 2f, dirX = 1f, dirY = 0f, velocity = 0f, energy = 0f, electrons = 8, parentId = null),
                ),
                summary = mapOf("OXYGEN_16" to 1),
            )
        )
        assertEquals(1, world.entities.size)
        assertEquals("OXYGEN_16", world.entities.first().state().value.element.name)
    }
}