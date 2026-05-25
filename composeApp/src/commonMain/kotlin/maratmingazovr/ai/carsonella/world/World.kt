package maratmingazovr.ai.carsonella.world

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.RecombinationModule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ChemicalReactionResolver
import maratmingazovr.ai.carsonella.randomDirection
import maratmingazovr.ai.carsonella.world.generators.EntityGenerator
import maratmingazovr.ai.carsonella.world.generators.IdGenerator

class World(
    private val _scope: CoroutineScope,
) {

    private val _idGen: IdGenerator = IdGenerator()
    private val _pendingRequests = mutableListOf<ReactionRequest>()
    private val _seed = 1L
    val random = kotlin.random.Random(_seed)
    val environment = Environment(Position(800f, 400f), 500f, TemperatureMode.Space)
    val palette =  mutableStateListOf(
        Element.PHOTON, Element.ELECTRON, Element.Proton,
        Element.HYDROGEN, Element.OXYGEN_16,
        Element.H2, Element.OXYGEN_16_ION_8,
        Element.Star, Element.SPACE_MODULE
    )
    val entities =  mutableStateListOf<Entity<*>>()
    val logs =  mutableStateListOf<String>()
    val entityGenerator = EntityGenerator(_idGen, entities, _pendingRequests, logs, palette, environment, random)



    private val _chemicalReactionResolver = ChemicalReactionResolver(entityGenerator)

    fun start() {
        entityGenerator.createEntity(element = Element.Star, position = Position(800f, 400f),  direction = randomDirection(random), velocity = 0f, energy = 0f)
        //val recombinationModule1 = entityGenerator.createEntity(element = Element.RECOMBINATION_MODULE, position = Position(300f, 250f),  direction = randomDirection(random), velocity = 0f, energy = 0f) as RecombinationModule

//        val module1 = entityGenerator.createEntity(element = Element.SPACE_MODULE, position = Position(300f, 300f),  direction = randomDirection(), velocity = 0f, energy = 0f) as SpaceModule
//        module1.setReagent1Element(Element.HYDROGEN)
//        module1.setReagent2Element(Element.HYDROGEN)




//        entityGenerator.createEntity(element = Element.Photon, position = Position(100f, 150f),  direction = randomDirection(), velocity = 0f, energy = 10.2f)
//        entityGenerator.createEntity(element = Element.H2, position = Position(100f, 350f),  direction = randomDirection(), velocity = 0f, energy = 0f)
//        entityGenerator.createEntity(element = Element.Photon, position = Position(100f, 200f),  direction = randomDirection(), velocity = 0f, energy = 1.89f)
//        entityGenerator.createEntity(element = Element.Photon, position = Position(100f, 250f),  direction = randomDirection(), velocity = 0f, energy = 1.51f)
//        entityGenerator.createEntity(element = Element.Photon, position = Position(100f, 300f),  direction = randomDirection(), velocity = 0f, energy = 12.09f)

        _scope.launch {
            val tickMs = 16L
            while (true) {

                // снимок, чтобы не падать на ConcurrentModificationException
                // если кто-то добавит сущность во время шага
                val snapshot = entities.toList()
                snapshot.forEach { entity ->
                    if (entity.state().value.alive) entity.step()
                }

                // Resolve phase — обрабатываем все запросы, накопленные за tick
                // - toList() — снимок, чтобы быть устойчивыми, если runReaction сам положит новый запрос (сейчас не кладёт, но защищаемся).
                val requests = _pendingRequests.toList()
                _pendingRequests.clear()
                requests.forEach { runReaction(it) }

                delay(tickMs)
            }
        }

    }

    fun applyForceToEntity(entityId: Long, force: Vec2D) {
        entities.find { it.state().value.id == entityId }?.run { applyForce(force) }
    }

    /**
     * Экспоненциальное сглаживание: новый = α*текущее + (1-α)*предыдущее
     * alpha: 0.05..0.3 — мягкое сглаживание; 0.5 — более «живое».
     */
    fun smoothEma(prev: Float, current: Float, alpha: Float = 0.2f): Float =
        alpha * current + (1f - alpha) * prev



    fun runReaction(reactionRequest: ReactionRequest) {
        val result = _chemicalReactionResolver.resolve(reactionRequest.reagents) ?: return
        if (result.description.isNotEmpty()) logs += "${currentTime()}: ${result.description}"

        result.consumed.forEach { it.destroy() }
        result.spawn.forEach { it() }
        result.updateState.forEach { it() }
    }
}




data class ReactionRequest(val reagents: List<Entity<*>>)

fun currentTime(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val h = now.hour.toString().padStart(2, '0')
    val m = now.minute.toString().padStart(2, '0')
    val s = now.second.toString().padStart(2, '0')
    return "$h:$m:$s"
}
