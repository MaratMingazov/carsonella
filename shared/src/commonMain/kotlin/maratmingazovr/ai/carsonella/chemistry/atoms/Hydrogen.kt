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
 * Радиус атома водорода. Это расстояние между протоном и электроном.
 * В игре я использую это при визуализации атома водорода в игровом мире.
 */
const val HYDROGEN_ATOM_RADIUS = 53f

/**
 * Ковалентный радиус атома.
 * На такую дистанцию должны сблизиться атом с другим атомом,
 * чтобы атомы смогли образовать ковалентную связь.
 */
const val HYDROGEN_COVALENT_RADIUS = 32f

const val HYDROGEN_ELECTRONEGATIVITY = 2.2 // шкала Полинга

/**
 * Масса водорода в атомных единицах
 * 1.008 * 1.66054 * 10^{-27} кг = 1.67 * 10^{-27} кг
 */
const val HYDROGEN_MASS_AMU = 1.008f


// --- ФИЗКОНСТАНТЫ ---
private const val K_B = 1.380_649e-23      // Постоянная Больцмана Дж/Кельвин Дж = (кг * метр^2)/cек^2
private const val AMU_TO_KG = 1.660_539_066_60e-27 // атомную единицу переводим в кг

class HydrogenState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
) : AtomState<HydrogenState> {
    override fun covalentRadius() = HYDROGEN_COVALENT_RADIUS
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

        println("Атом Водорода (H) id:${state.value.id} создан")
        writeLog("Атом Водорода (H) id:${state.value.id} создан")
        while (state.value.alive) {
            stepMutex.withLock {


               // _state.value = _state.value.copy(temp = _state.value.temp + 1)



//                val environment = getEnvironment()
//                val temperature = environment.getTemperature()
//
//                // определили с какой скоростью должен двигаться атом водорода [пикометр/сек]
//                val targetVelocity = calculateAtomVelocity(temperature, HYDROGEN_MASS_AMU)
//
//                // это его текущий вектор скорости
//                val currentVelocity = _state.value.velocity
//
//                val lerp = 0.2
//                val target = currentVelocity.length() * (1.0 - lerp) + targetVelocity * lerp
//
//
//                println("targetVelocity = $targetVelocity")
//                println("currentVelocity_1 = $currentVelocity")
//
//                currentVelocity.scaleTo(target.toFloat())
//
//                println("currentVelocity_2 = $currentVelocity")
//                println("")
//
//                /**
//                 * Атом водорода просто колеблется.
//                 */
//                var newPosition = _state.value.position()
//                    .addVelocity(_state.value.velocity)
//                var newVelocity = _state.value.velocity
//                if (newPosition.x !in 0f..environment.getWorldWidth()) {
//                    newPosition = newPosition.copy(x = newPosition.x.coerceIn(0f, getEnvironment().getWorldWidth()))
//                    newVelocity = newVelocity.copy(x = -newVelocity.x)
//                }
//                if (newPosition.y !in 0f..environment.getWorldHeight()) {
//                    newPosition = newPosition.copy(y = newPosition.y.coerceIn(0f, getEnvironment().getWorldHeight()))
//                    newVelocity = newVelocity.copy(y = -newVelocity.y)
//                }
//
//                _state.value = _state.value.copy(position = newPosition, velocity = newVelocity)
//
//
//                /**
//                 * Мы будем проверять какие другие атомы находятся возле нашего атома водорода
//                 * Если нашли такие атомы, то отправляем запрос на химическую реакцию
//                 *
//                 */
//                getNeighbors()
//                    .filter { entity -> _state.value.position.distanceTo(entity.state().value.position()) < 100f }
//                    .takeIf { it.isNotEmpty() }
//                    ?.let { requestReaction(listOf(this) + it) }

            }
            delay(50)
        }
        println("Атом Водорода (H) id:${state.value.id} разрушен")
    }

    override suspend fun destroy() {
        state.value.updateAlive(false)
        notifyDeath()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Hydrogen) return false
        return state.value.id == other.state.value.id
    }

    override fun hashCode(): Int = state.value.id.hashCode()

}

/**
 * Вычисляем скорость движения частицы (м/c)
 * На вход:
 *  T_kelvin : Температура окружающей среды
 *  massAmu : Масса частицы в атомных единицах
 * Выход: скорость движения частицы в пикометр/cек
 */
fun calculateAtomVelocity(T_kelvin: Float, massAmu: Float): Float {
    val massaKg = massAmu * AMU_TO_KG // масса частицы в килограммах
    val factor = 10.0.pow(12).toFloat() // 1 метр = 10^12 пикометров
    return sqrt(3.0 * K_B * T_kelvin / massaKg).toFloat() * factor
}

