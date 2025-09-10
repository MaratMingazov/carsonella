package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Element.Photon
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import kotlin.math.round


data class SubAtomState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
    override var energy: Float,
) : EntityState<SubAtomState> {
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float, energy: Float) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity, energy = energy)
    override fun toString(): String {
        return """
            |${element.label}: $id
            |Position (${position.x.toInt()}, ${position.y.toInt()})
            |Velocity ${round(velocity * 100) / 100}
            ||Energy ${round(energy * 100) / 100}
        """.trimMargin()
    }
}

class SubAtom(
    id: Long,
    element: Element,
    position: Position,
    direction: Vec2D,
    velocity: Float,
    energy: Float,
):
    Entity<SubAtomState>,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport()
{
    private var state = MutableStateFlow(
        SubAtomState(
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
        writeLog("Появился ${state.value.element.label}: ${state.value.id}")
        while (state.value.alive) {
            stepMutex.withLock {

                val neighbors = getNeighbors()
                val environment = getEnvironment()

                when (state.value.element) {
                    Photon -> initPhoton(environment)
                    Electron -> initPhoton(environment)
                    Proton -> initProton(environment, neighbors)
                    else -> NotImplementedError()
                }

            }
            delay(10)
        }
    }

    private suspend fun initPhoton(environment: IEnvironment) {
        applyNewPosition()
        if (state.value.element in listOf(Element.Photon, Element.Electron)) {
            if (state.value.position.x !in 0f..environment.getWorldWidth() ||
                state.value.position.y !in 0f..environment.getWorldHeight()) {
                destroy()
            }
        }
    }

    private fun initProton(environment: IEnvironment, neighbors: List<Entity<*>>) {
        reduceVelocity()
        applyForce(calculateForce(neighbors))
        applyNewPosition()
        checkBorders(environment)

        neighbors
            .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 5000f }
            .takeIf { it.isNotEmpty() }
            ?.let {requestReaction(listOf(this) + it) }
    }

    override suspend fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}