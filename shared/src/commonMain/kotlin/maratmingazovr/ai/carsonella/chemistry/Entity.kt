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

    fun reduceVelocity() {
        if (state().value.velocity < 0.1f) {
            state().value = state().value.copyWith(velocity = 0f)
        } else {
            state().value = state().value.copyWith(velocity = state().value.velocity * 0.99f)
        }
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

    fun applyForce(force: Vec2D) {

        if (state().value.element.mass < 0.001f) return
        val a = force.div(state().value.element.mass)
        val newVelocityVector = state().value.direction.times(state().value.velocity).plus(a)
        val newVelocity = newVelocityVector.length()
        val newDirection = if (newVelocity > 0) newVelocityVector.div(newVelocity) else state().value.direction

        state().value = state().value.copyWith(direction = newDirection, velocity = newVelocity)
    }

    /**
     * Здесь мы вычисляем с какой силой два элемента притягиваются друг к другу
     */
    fun calculateForce(elements: List<Entity<*>>): Vec2D {
        val fVector = Vec2D(0f, 0f)

        elements.forEach { element ->
            val elementPosition = element.state().value.position
            val rx = state().value.position.x - elementPosition.x
            val ry = state().value.position.y - elementPosition.y
            val distance2 = rx*rx + ry*ry // это квадрат расстояния между частицами

            val myRadius = state().value.element.radius
            val elementRadius = element.state().value.element.radius
            val maxRadius2 = (myRadius + elementRadius) * (myRadius + elementRadius) * 1.2
            // Если элементы находятся дальше этого расстояния, то они не влияют друг на друга
            if (distance2 > maxRadius2) return@forEach    // вне радиуса действия

            // Если электроны есть только у одного элемента, то эти элементы будут притягиваться
            // Если электроны есть у обоих элементов, то будут отталкиваться
            val myElectronsCount = state().value.element.electronsCount
            val elementElectronsCount = element.state().value.element.electronsCount
            val fAttraction = if (myElectronsCount > 0) { // отлично, у меня есть электроны. Проверим электроны соседа
                if (elementElectronsCount > 0) { (myElectronsCount+elementElectronsCount) / (distance2 + 10f) }   // у него тоже есть электроны, тогда я буду от него отталкиваться
                else { 0f } // у него электронов нет, я ничего не буду делать, пусть он сам притянется если нужно
            } else { // у меня электронов нет. Проверим, есть ли у него электроны
                if (elementElectronsCount > 0) { -1 * elementElectronsCount / (distance2 + 10f) } // у него есть электроны, значит я притянусь к нему
                else { 0f } // у него тоже нет электроноа, никакой силы нет
            }

            // Но если элементы подлетят слишком близко друг к другу, то протоны начнут отталкивать друг друга.
            val myProtonsCount = state().value.element.protonsCount
            val elementProtonsCount = element.state().value.element.electronsCount
            val fRepulsion = if (distance2 < (myRadius + elementRadius) * (myRadius + elementRadius)) {
                (myProtonsCount + elementProtonsCount + 1)/(distance2 + 50f)
            } else 0f

            val fScalar = fAttraction + fRepulsion
            fVector.x += rx * fScalar
            fVector.y += ry * fScalar
        }
        return fVector
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
    val electronsCount: Int,
    val protonsCount: Int,
    val radius: Float, // в пикометрах
) {
    // --- субатомные частицы ---
    Photon(ElementType.SubAtom, "γ", "Photon (γ)", mass = 0f, electronsCount = 0, protonsCount = 0, radius = 1f),
    Electron(ElementType.SubAtom, "e⁻", "Electron (e⁻)", mass = 0.0005f, electronsCount = 1, protonsCount = 0, radius = 1f),
    Proton(ElementType.SubAtom, "p⁺", "Proton (p⁺)", mass = 1f, electronsCount = 0, protonsCount = 1, radius = 10f),

    // --- атомы ---
    H(ElementType.Atom, "H", "Hydrogen (H)", mass = 1f, electronsCount = 1, protonsCount = 1, radius = 53f),
    O(ElementType.Atom, "O", "Oxygen (O)", mass = 16f, electronsCount = 8, protonsCount = 8, radius = 70f),

    // --- молекулы ---
    H2(ElementType.Molecule, "H₂", "DiHydrogen (H₂)", mass = 2f, electronsCount = 2, protonsCount = 2, radius = 100f),
    H2O(ElementType.Molecule, "H₂O", "Water (H₂O)", mass = 18f, electronsCount = 10, protonsCount = 10, radius = 140f),;
}