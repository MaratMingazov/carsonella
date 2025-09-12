package maratmingazovr.ai.carsonella.world

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ChemicalReactionResolver
import maratmingazovr.ai.carsonella.world.generators.EntityGenerator
import maratmingazovr.ai.carsonella.world.generators.IdGenerator

class World(
    private val _scope: CoroutineScope,
) {

    private val _idGen: IdGenerator = IdGenerator()
    private val _requestsChannel =  Channel<ReactionRequest>(capacity = Channel.UNLIMITED)
    val environment = Environment(
        0f,
        0f,
        0.00000000000000000000000001f
    )
    val palette =  mutableStateListOf(Element.Photon, Element.Electron, Element.Proton, Element.H, Element.O, Element.O2)
    val entities =  mutableStateListOf<Entity<*>>()
    val logs =  mutableStateListOf<String>()
    val entityGenerator = EntityGenerator(_idGen, entities, _scope, _requestsChannel, environment, logs, palette)
    private val _worldMutex = Mutex()


    private val _chemicalReactionResolver = ChemicalReactionResolver(entityGenerator)

    fun start() {
        _scope.launch {
            for (reactinoRequest in _requestsChannel) {
                _worldMutex.withLock { runReaction(reactinoRequest) }
            }
        }
    }


    fun updateTemperatureGame(): Float {
        val currentTemperature = environment.getTemperature()

        if (entities.isEmpty()) {
            environment.setTemperature(0f)
            return 0f
        }
        var sum = 0f
        for (e in entities) {
            val m = e.state().value.element.mass
            val v = e.state().value.velocity
            sum += 0.5f * m * v * v
        }
        val actualTemperature = sum / entities.size
        val smoothTemperature = smoothEma(currentTemperature, actualTemperature)
        environment.setTemperature(smoothTemperature)
        return smoothTemperature
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



    suspend fun runReaction(reactionRequest: ReactionRequest) {
        val result = _chemicalReactionResolver.resolve(reactionRequest.reagents) ?: return
        logs += "${nowString()}: Произошла реакция между: ${result.consumed.map { it.state().value.element.name }.toList()}"

        result.consumed.forEach { it.destroy() }
        result.spawn.forEach { it() }
        result.updateState.forEach { it() }
    }
}




data class ReactionRequest(val reagents: List<Entity<*>>)

fun nowString(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val h = now.hour.toString().padStart(2, '0')
    val m = now.minute.toString().padStart(2, '0')
    val s = now.second.toString().padStart(2, '0')
    return "$h:$m:$s"
}
