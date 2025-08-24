package maratmingazovr.ai.carsonella.chemistry.atoms

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element

data class OxygenState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
) : AtomState<OxygenState> {
    override fun covalentRadius() = HYDROGEN_COVALENT_RADIUS
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity)
}

//class Oxygen(
//    id: Long,
//    position: Position,
//) : AbstractAtom<HydrogenState>(
//    initialState = HydrogenState(
//        id = id,
//        alive = true,
//        position = position,
//        element = Element.O,
//    )
//) {
//
//    override suspend fun init() {}
//
//    override suspend fun destroy() {
//        _state.value = _state.value.copy(alive = false)
//        notifyDeath()
//    }
//
//}