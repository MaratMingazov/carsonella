package maratmingazovr.ai.carsonella.chemistry.sub_atoms

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element


data class PhotonState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
) : SubAtomState<PhotonState> {
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity)
}


class Photon(
    id: Long,
    position: Position,
    direction: Vec2D,
) : AbstractSubAtom<PhotonState>(
    initialState = PhotonState(
        id = id,
        element = Element.Photon,
        alive = true,
        position = position,
        direction = direction,
        velocity = 40f,
    )
) {

    private val stepMutex = Mutex()

    override suspend fun init() {

        writeLog("К нам прилетел фотон")
        while (state.value.alive) {
            stepMutex.withLock {

                val environment = getEnvironment()
                applyNewPosition()
                if (state.value.position.x !in 0f..environment.getWorldWidth() ||
                    state.value.position.y !in 0f..environment.getWorldHeight()) {
                    destroy()
                }

            }
            delay(10)
        }
        writeLog("Фотон улетел")
    }

    override suspend fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }
}