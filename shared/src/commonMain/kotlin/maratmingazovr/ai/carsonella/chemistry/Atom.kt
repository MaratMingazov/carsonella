package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.DeathNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentAware
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.LogWritable
import maratmingazovr.ai.carsonella.chemistry.behavior.LoggingSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsAware
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.OnDeathSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequestSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequester
import kotlin.math.round

data class AtomState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
    override var energy: Float,
) : EntityState<AtomState> {
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float, energy: Float) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity, energy = energy)
    override fun toString(): String {
        return """
            |${element.label}: $id
            |Position (${position.x.toInt()}, ${position.y.toInt()})
            |Velocity ${round(velocity * 100) / 100}
            |Energy ${round(energy * 100) / 100}
        """.trimMargin()
    }
}

class Atom(
    id: Long,
    element: Element,
    position: Position,
    direction: Vec2D,
    velocity: Float,
    energy: Float,
):
    Entity<AtomState>,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport()
{
    private var state = MutableStateFlow(
        AtomState(
            id = id,
            element = element,
            alive = true,
            position = position,
            direction = direction,
            velocity = velocity,
            energy = energy,
        )
    )
    private val stepMutex = Mutex()

    override fun state() = state

    override suspend fun init() {
        //writeLog("Появился ${state.value.element.label}: ${state.value.id}")
        while (state.value.alive) {
            stepMutex.withLock {

                val neighbors = getNeighbors()
                val environment = getEnvironment()

                applyForce(calculateForce(neighbors))
                applyNewPosition()
                reduceVelocity()
                checkBorders(environment)

                neighbors
                    .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 10000f }
                    .takeIf { it.isNotEmpty() }
                    ?.let { requestReaction(listOf(this) + it) }

                if (state.value.energy > 0) { requestReaction(listOf(this)) }

            }
            delay(10)
        }
    }


    override suspend fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}


