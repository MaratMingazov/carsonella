package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D

interface EntityState<State : EntityState<State>> {

    val id: Long
    val element: Element
    var alive: Boolean
    var position: Position
    var direction: Vec2D
    var velocity: Float

    fun copyWith(
        alive: Boolean = this.alive,
        position: Position = this.position,
        direction: Vec2D = this.direction,
        velocity: Float = this.velocity
    ): State

}

interface Entity<State : EntityState<State>> {
    fun state(): MutableStateFlow<State>
    suspend fun init()
    suspend fun destroy() // нужно, чтобы сообщить атому, что он должен быть уничтожен

    fun applyNewPosition() {
        state().value = state().value.copyWith(position =
            Position(
                x = state().value.position.x + state().value.direction.x * state().value.velocity,
                y = state().value.position.y + state().value.direction.y * state().value.velocity
            )
        )
    }

    fun checkBorders(env: IEnvironment) {

        var position = state().value.position
        var direction = state().value.direction

        if (position.x !in 0f..env.getWorldWidth()) {
            position = position.copy(x = position.x.coerceIn(0f, env.getWorldWidth()))
            direction = direction.copy(x = -direction.x)
        }
        if (position.y !in 0f..env.getWorldHeight()) {
            position = position.copy(y = position.y.coerceIn(0f, env.getWorldHeight()))
            direction = direction.copy(y = -direction.y)
        }
        state().value = state().value.copyWith(position = position, direction = direction)

    }
}

enum class ElementType {
    SubAtom,  // субатомные частицы
    Atom,     // атомы
    Molecule  // молекулы
}

enum class Element(
    val type: ElementType,
    val symbol: String,
    val label: String,
    val mass: Float,
) {
    // --- субатомные частицы ---
    Photon(ElementType.SubAtom, "γ", "Photon (γ)", mass = 0f),
    Electron(ElementType.SubAtom, "e⁻", "Electron (e⁻)", mass = 0.0005f),
    Proton(ElementType.SubAtom, "p⁺", "Proton (p⁺)", mass = 1f),

    // --- атомы ---
    H(ElementType.Atom, "H", "Hydrogen (H)", mass = 1f),
    O(ElementType.Atom, "O", "Oxygen (O)", mass = 16f),

    // --- молекулы ---
    H2(ElementType.Molecule, "H₂", "Hydrogen (H₂)", mass = 2f),
    H2O(ElementType.Molecule, "H₂O", "Water (H₂O)", mass = 18f);
}