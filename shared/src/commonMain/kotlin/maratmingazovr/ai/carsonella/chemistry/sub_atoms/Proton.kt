package maratmingazovr.ai.carsonella.chemistry.sub_atoms

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity

class ProtonState(
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
        while (state.value.alive()) {
            stepMutex.withLock {


                val neighbors = getNeighbors()
                val environment = getEnvironment()

                applyCoulombForce(neighbors)
                applyNewPosition()
                checkBorders(environment)
            }
            delay(10)
        }
    }

    override suspend fun destroy() {
        state.value.updateAlive(false)
        notifyDeath()
    }

    private fun applyCoulombForce(neighbors: List<Entity<*>>) {
        val protons = neighbors.filterIsInstance<Proton>()
        if (protons.isEmpty()) return
        val f = calculateCoulombForce(protons)
        val a = f.div(state.value.element().mass)

        val newVelocityVector = state.value.direction().times(state.value.velocity()).plus(a)
        val newVelocity = newVelocityVector.length()
        val newDirection = if (newVelocity > 0) newVelocityVector.div(newVelocity) else state.value.direction()

        state.value.updateDirection(newDirection)
        state.value.updateVelocity(newVelocity)
    }


    /**
     * Вычисляем с какой силой соседние протоны влияют на наш протон
     * Будем вычислять по закону Кулоновского взаимодействия
     * F = k * q² / r²
     * k [Постоянная Кулона] = 8.98755 × 10⁹ Н·м²/Кл²
     * q [Заряд частицы по модулю] = 1.602 × 10⁻¹⁹ Кл у протона
     * r [Расстояние между частицами]
     */
    private fun calculateCoulombForce(protons: List<Proton>): Vec2D {

        // Суммарная сила от всех соседних протонов
        val fVector = Vec2D(0f, 0f)

        protons.forEach { neighbor ->

            val nPosition = neighbor.state().value.position()
            val rx = state.value.position().x - nPosition.x
            val ry = state.value.position().y - nPosition.y
            val distance2 = rx*rx + ry*ry // это квадрат расстояния между частицами

            val maxRadius2 = 2000f        // радиус действия (px) Если протон дальше, то не оказывает никакого влияния
            if (distance2 > maxRadius2) return@forEach    // вне радиуса действия

            /**
             * Так как мы тут пока рассматриваем только протоны, То мы можем заранее вычислить:
             * kqq = 23e-29f
             * Так как вместо метров мы тут оперируем пикселями. МЫ пока уберем степень и оставим просто
             * kqq = 23f
             * Также мы введем
             * eps = 10f чтобы при маленьких расстояниях сила не уходила в бесконечность
             */
            val fScalar = 23f / (distance2 + 10f)

            fVector.x += rx * fScalar
            fVector.y += ry * fScalar

        }
        return fVector
    }
}