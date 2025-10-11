package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import kotlin.math.round


data class StarState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
    override var energy: Float,
    override var environment: IEnvironment,
    override var subEnvironment: IEnvironment,
) : EntityState<StarState> {
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float, energy: Float, environment: IEnvironment, subEnvironment: IEnvironment) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity, energy = energy, environment = environment, subEnvironment = subEnvironment)
    override fun toString(): String {
        return """
            |${element.label}: $id
            |Position (${position.x.toInt()}, ${position.y.toInt()})
            |Velocity ${round(velocity * 100) / 100}
            |Energy ${round(energy * 100) / 100}
        """.trimMargin()
    }
}

class Star(
    id: Long,
    element: Element,
    position: Position,
    direction: Vec2D,
    velocity: Float,
    energy: Float,
    environment: IEnvironment,
):
    Entity<StarState>,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    LogWritable  by LoggingSupport()
{
    private var state = MutableStateFlow(
        StarState(
            id = id,
            element = element,
            alive = true,
            position = position,
            direction = direction,
            velocity = velocity,
            energy = energy,
            environment = environment,
            subEnvironment = Environment(position, element.radius, 1000000f),
        )
    )
    private val stepMutex = Mutex()

    override fun state() = state

    override suspend fun init() {

        while (state.value.alive) {
            stepMutex.withLock {

                val neighbors = getNeighbors()
                val environment = state.value.environment

                applyForce(calculateForce(neighbors))
                applyNewPosition()
                reduceVelocity()
                checkBorders(environment)

                neighbors
                    .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 10000f }
                    .takeIf { it.isNotEmpty() }
                    ?.let { requestReaction(listOf(this) + it) }

                //if (state.value.energy > 0) { requestReaction(listOf(this)) }
                requestReaction(listOf(this))

            }
            delay(10)
        }
    }



    override suspend fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}
