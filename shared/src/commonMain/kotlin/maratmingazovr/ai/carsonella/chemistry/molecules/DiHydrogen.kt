package maratmingazovr.ai.carsonella.chemistry.molecules

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element


data class DiHydrogenState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
) : MoleculeState<DiHydrogenState> {
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity)
}

class DiHydrogen(
    id: Long,
    position: Position,
    direction: Vec2D,
    velocity: Float,
) : AbstractMolecule<DiHydrogenState>(
    initialState = DiHydrogenState(
        id = id,
        element = Element.H2,
        alive = true,
        position = position,
        direction = direction,
        velocity = velocity,
    )
) {

    private val stepMutex = Mutex()

    override suspend fun init() {

        writeLog("Молекула Водорода (H2) id:${state.value.id} создана")
        while (state.value.alive) {
            stepMutex.withLock {

                val neighbors = getNeighbors()
                val environment = getEnvironment()

                applyForce(calculateForce(neighbors))
                applyNewPosition()
                checkBorders(environment)

//                neighbors
//                    .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 10000f }
//                    .takeIf { it.isNotEmpty() }
//                    ?.let { requestReaction(listOf(this) + it) }
            }
            delay(10)
        }
    }

    override suspend fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}