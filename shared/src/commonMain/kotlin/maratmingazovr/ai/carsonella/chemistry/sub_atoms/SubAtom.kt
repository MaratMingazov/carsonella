package maratmingazovr.ai.carsonella.chemistry.sub_atoms

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.Photon
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.EntityState
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
import kotlin.compareTo


//interface SubAtomState<State: SubAtomState<State>> : EntityState<State>

//interface SubAtom<State: SubAtomState<State>> :
//    Entity<State>,
//    DeathNotifiable,
//    NeighborsAware,
//    ReactionRequester,
//    EnvironmentAware,
//    LogWritable

//abstract class AbstractSubAtom<State : SubAtomState<State>>(
//    initialState: State,
//) : SubAtom<State>,
//    DeathNotifiable by OnDeathSupport(), // теперь атомы во время смерти могут оповещаться мир об этом
//    NeighborsAware by NeighborsSupport(),
//    ReactionRequester by ReactionRequestSupport(),
//    EnvironmentAware by EnvironmentSupport(),
//    LogWritable by LoggingSupport()
//{
//
//    protected var state = MutableStateFlow(initialState)
//    override fun state() = state
//
//}

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
            |Velocity $velocity
        """.trimMargin()
    }
}

class SubAtom(
    id: Long,
    element: Element,
    position: Position,
    direction: Vec2D,
    velocity: Float,
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
            energy = 0f,
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
                    else -> initProton(environment, neighbors)
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

    private suspend fun initProton(environment: IEnvironment, neighbors: List<Entity<*>>) {
        applyForce(calculateForce(neighbors))
        reduceVelocity()
        applyNewPosition()
        checkBorders(environment)

        neighbors
            .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 1000f }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }
    }

    override suspend fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}