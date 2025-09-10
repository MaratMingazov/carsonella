package maratmingazovr.ai.carsonella.chemistry.sub_atoms

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element


data class ElectronState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
    override var energy: Float,
    var liveTime: Float,
) : SubAtomState<ElectronState> {

    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float, energy: Float) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity, energy = energy)
}


class Electron(
    id: Long,
    position: Position,
    direction: Vec2D,
) : AbstractSubAtom<ElectronState>(
    initialState = ElectronState(
        id = id,
        element = Element.Electron,
        alive = true,
        position = position,
        direction = direction,
        velocity = 40f,
        liveTime = 500f,
        energy = 0f,
    )
) {

    private val stepMutex = Mutex()

    override suspend fun init() {


        writeLog("Появился электрон")
        while (state.value.alive) {
            stepMutex.withLock {

                val environment = getEnvironment()
                applyNewPosition()
                checkBorders(environment)

                state.value = state.value.copy(liveTime = state.value.liveTime - 1)

                if (state.value.liveTime < 1) { destroy() }

            }
            delay(10)
        }
    }

    override suspend fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }
}