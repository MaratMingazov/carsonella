package maratmingazovr.ai.carsonella.chemistry.sub_atoms

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity

data class ProtonState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
) : SubAtomState<ProtonState> {

    override fun toString(): String {
        return """
            |Proton: $id
            |Position (${position.x.toInt()}, ${position.y.toInt()})
            |Velocity $velocity
        """.trimMargin()
    }

    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity)

}


class Proton(
    id: Long,
    position: Position,
    direction: Vec2D,
) : AbstractSubAtom<ProtonState>(
    initialState = ProtonState(
        id = id,
        element = Element.Proton,
        alive = true,
        position = position,
        direction = direction,
        velocity = 0f,
    )
) {

    private val stepMutex = Mutex()

    override suspend fun init() {

        writeLog("Появился протон")
        while (state.value.alive) {
            stepMutex.withLock {

                val neighbors = getNeighbors()
                val environment = getEnvironment()

                applyForce(calculateForce(neighbors))
                applyNewPosition()
                checkBorders(environment)

                neighbors
                    .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 1000f }
                    .takeIf { it.isNotEmpty() }
                    ?.let { requestReaction(listOf(this) + it) }


            }
            delay(10)
        }
    }

    override suspend fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }
}