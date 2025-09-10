package maratmingazovr.ai.carsonella.chemistry.atoms

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.math.pow
import kotlin.math.sqrt


/**
 * Ковалентный радиус атома.
 * На такую дистанцию должны сблизиться атом с другим атомом,
 * чтобы атомы смогли образовать ковалентную связь.
 */
const val HYDROGEN_COVALENT_RADIUS = 32f

const val HYDROGEN_ELECTRONEGATIVITY = 2.2 // шкала Полинга


// --- ФИЗКОНСТАНТЫ ---
private const val K_B = 1.380_649e-23      // Постоянная Больцмана Дж/Кельвин Дж = (кг * метр^2)/cек^2
private const val AMU_TO_KG = 1.660_539_066_60e-27 // атомную единицу переводим в кг

data class HydrogenState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
) : AtomState<HydrogenState> {
    override fun covalentRadius() = HYDROGEN_COVALENT_RADIUS
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity)

    override fun toString(): String {
        return """
            |Hydrogen: $id
            |Position (${position.x.toInt()}, ${position.y.toInt()})
            |Velocity $velocity
        """.trimMargin()
    }
}

class Hydrogen(
    id: Long,
    position: Position,
    direction: Vec2D,
    velocity: Float,
) : AbstractAtom<HydrogenState>(
        initialState = HydrogenState(
            id = id,
            element = Element.H,
            alive = true,
            position = position,
            direction = direction,
            velocity = velocity,
        )
) {

    /**
     * Валентность Водорода = 1
     * Определяет сколько связей Водород может образовывать с другими атомами
     * Атом водорода может образовать только одну связь с другими атомами
     */
    private val _valence = 1f


    private val stepMutex = Mutex()

    override suspend fun init() {

        writeLog("Атом Водорода (H) id:${state.value.id} создан")
        while (state.value.alive) {
            stepMutex.withLock {

                val neighbors = getNeighbors()
                val environment = getEnvironment()

                applyForce(calculateForce(neighbors))
                applyNewPosition()
                reduceVelocity()
                checkBorders(environment)

                neighbors
                    .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 10000f }
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

