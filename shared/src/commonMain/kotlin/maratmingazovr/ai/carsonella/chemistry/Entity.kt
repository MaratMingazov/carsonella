package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.DeathNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentAware
import maratmingazovr.ai.carsonella.chemistry.behavior.LogWritable
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsAware
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequester
import kotlin.math.sqrt

interface EntityState<State : EntityState<State>> {

    val id: Long
    val element: Element
    var alive: Boolean
    var position: Position
    var direction: Vec2D
    var velocity: Float
    var energy: Float

    fun copyWith(
        alive: Boolean = this.alive,
        position: Position = this.position,
        direction: Vec2D = this.direction,
        velocity: Float = this.velocity,
        energy: Float = this.energy
    ): State

}

interface Entity<State : EntityState<State>> :
    DeathNotifiable,
    NeighborsAware,
    ReactionRequester,
    IEnvironment, // каждая частица может являться средой для других частиц
    EnvironmentAware, // каждая частица сама находится в каком то среде
    LogWritable
{
    fun state(): MutableStateFlow<State>
    fun step() // элемент делает свой ход
    fun destroy() // нужно, чтобы сообщить элементу, что он должен быть уничтожен

    // только те частицы, которые сами могут служить средой, будут переопределять эти методы
    override fun getEnvCenter(): Position = throw Exception("Not Supported")
    override fun getEnvRadius(): Float = throw Exception("Not Supported")
    override fun getEnvTemperature(): TemperatureMode = throw Exception("Not Supported")
    override fun getEnvChildren(): List<Entity<*>> = throw Exception("Not Supported")
    override fun addEnvChild(entity: Entity<*>) { throw Exception("Not Supported") }
    override fun removeEnvChild(entity: Entity<*>) { throw Exception("Not Supported") }

    fun updateMyEnvironment(newEnvironment: IEnvironment) {
        this.getEnvironment().removeEnvChild(this)
        this.setEnvironment(newEnvironment)
        newEnvironment.addEnvChild(this)
    }

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
        val center = env.getEnvCenter()
        val radius = env.getEnvRadius()

        // Вектор от центра круга к объекту
        val dx = position.x - center.x
        val dy = position.y - center.y


        if (dx * dx + dy * dy > radius * radius) {
            // Расстояние от центра
            val dist = sqrt(dx * dx + dy * dy)
            // Если снаружи — нормализуем вектор и перемещаем на границу круга
            val nx = dx / dist
            val ny = dy / dist
            position =  Position(x = center.x + nx * radius, y = center.y + ny * radius)

            // Отразить направление относительно нормали
            val dot = direction.x * nx + direction.y * ny
            direction = direction.copy(x = direction.x - 2 * dot * nx, y = direction.y - 2 * dot * ny)
        }

//        if (position.x !in left..right) {
//            position = position.copy(x = position.x.coerceIn(left, right))
//            direction = direction.copy(x = -direction.x)
//        }
//        if (position.y !in bottom..top) {
//            position = position.copy(y = position.y.coerceIn(bottom, top))
//            direction = direction.copy(y = -direction.y)
//        }
        state().value = state().value.copyWith(position = position, direction = direction)
    }

    fun addEnergy(energy: Float) {
        var updatedEnergy =  state().value.energy + energy
        if (updatedEnergy < 0f) { updatedEnergy = 0f }
        state().value = state().value.copyWith(energy = updatedEnergy)
    }

    // Записывает энергию напрямую через copyWith — без арифметики, чтобы избежать float-дрейфа.
    // Используется когда нужно «снапнуть» энергию ровно на один из energyLevels из таблицы Details.
    fun setEnergy(energy: Float) {
        val clamped = if (energy < 0f) 0f else energy
        state().value = state().value.copyWith(energy = clamped)
    }

    fun addVelocity(moreVelocity: Float) {
        state().value = state().value.copyWith(velocity = state().value.velocity + moreVelocity)
    }

    fun applyForce(force: Vec2D) {

        if (state().value.element.details.mass < 0.001f) return
        val a = force.div(state().value.element.details.mass)
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
        val myElectronsCount = state().value.element.details.e
        val myProtonsCount = state().value.element.details.p
        val myRadius = state().value.element.details.radius
        val myMass = state().value.element.details.mass
        if (myElectronsCount == 0 && myProtonsCount == 0) {return fVector}

        elements.forEach { element ->
            val elementPosition = element.state().value.position
            val rx = state().value.position.x - elementPosition.x
            val ry = state().value.position.y - elementPosition.y
            val distance2 = rx*rx + ry*ry // это квадрат расстояния между частицами

            val elementRadius = element.state().value.element.details.radius
            val elementMass = element.state().value.element.details.mass
            val maxRadius2 = (myRadius + elementRadius) * (myRadius + elementRadius) * 1.7
            // Если элементы находятся дальше этого расстояния, то они не влияют друг на друга
            if (distance2 > maxRadius2) return@forEach // вне радиуса действия

            // Если электроны есть только у одного элемента, то эти элементы будут притягиваться
            // Если электроны есть у обоих элементов, то будут отталкиваться
            val elementElectronsCount = element.state().value.element.details.e
            val fAttraction = if (myElectronsCount > 0) { // отлично, у меня есть электроны. Проверим электроны соседа
                if (elementElectronsCount > 0) { (myElectronsCount+elementElectronsCount) / (distance2 + 10f) }   // у него тоже есть электроны, тогда я буду от него отталкиваться
                else { 0f } // у него электронов нет, я ничего не буду делать, пусть он сам притянется если нужно
            } else { // у меня электронов нет. Проверим, есть ли у него электроны
                if (elementElectronsCount > 0) { -2 * elementElectronsCount / (distance2 + 10f) } // у него есть электроны, значит я притянусь к нему
                else { 0f } // у него тоже нет электроноа, никакой силы нет
            }

            //val gravityForce = -1 * myMass * elementMass / (distance2 + 10f)
            val gravityForce = 0

            // Но если элементы подлетят слишком близко друг к другу, то протоны начнут отталкивать друг друга.
            val elementProtonsCount = element.state().value.element.details.e
            val fRepulsion = if (distance2 < (myRadius + elementRadius) * (myRadius + elementRadius)) { (myProtonsCount + elementProtonsCount + 1)/(distance2 + 50f) } else 0f

            val fScalar = fAttraction + fRepulsion + gravityForce
            fVector.x += rx * fScalar
            fVector.y += ry * fScalar
        }
        return fVector
    }
}

enum class ElementType { SubAtom, Atom, Molecule, Star, SpaceModule, RecombinationModule }

enum class Element() {
    // --- субатомные частицы ---
    PHOTON, ELECTRON, Proton, NEUTRON, POSITRON,

    // --- атомы ---
    HYDROGEN,
    DEUTERIUM_ION,
    DEUTERIUM,
    HELIUM_3_ION_2, HELIUM_3_ION_1, HELIUM_3,
    HELIUM_4_ION_2, HELIUM_4_ION_1, HELIUM_4,
    LITHIUM_7_ION_3, LITHIUM_7_ION_2, LITHIUM_7_ION_1, LITHIUM_7,
    BERYLLIUM_7_ION_4, BERYLLIUM_7_ION_3, BERYLLIUM_7_ION_2, BERYLLIUM_7_ION_1, BERYLLIUM_7,
    BERYLLIUM_8_ION_4, BERYLLIUM_8_ION_3, BERYLLIUM_8_ION_2, BERYLLIUM_8_ION_1, BERYLLIUM_8,
    BORON_8_ION_5, BORON_8_ION_4, BORON_8_ION_3, BORON_8_ION_2, BORON_8_ION_1, BORON_8,
    CARBON_12_ION_6, CARBON_12_ION_5, CARBON_12_ION_4, CARBON_12_ION_3, CARBON_12_ION_2, CARBON_12_ION_1, CARBON_12,
    CARBON_13_ION_6, CARBON_13_ION_5, CARBON_13_ION_4, CARBON_13_ION_3, CARBON_13_ION_2, CARBON_13_ION_1, CARBON_13,
    CARBON_14_ION_6, CARBON_14_ION_5, CARBON_14_ION_4, CARBON_14_ION_3, CARBON_14_ION_2, CARBON_14_ION_1, CARBON_14,
    NITROGEN_13_ION_7, NITROGEN_13_ION_6, NITROGEN_13_ION_5, NITROGEN_13_ION_4, NITROGEN_13_ION_3, NITROGEN_13_ION_2, NITROGEN_13_ION_1, NITROGEN_13,
    NITROGEN_14_ION_7, NITROGEN_14_ION_6, NITROGEN_14_ION_5, NITROGEN_14_ION_4, NITROGEN_14_ION_3, NITROGEN_14_ION_2, NITROGEN_14_ION_1, NITROGEN_14,
    NITROGEN_15_ION_7, NITROGEN_15_ION_6, NITROGEN_15_ION_5, NITROGEN_15_ION_4, NITROGEN_15_ION_3, NITROGEN_15_ION_2, NITROGEN_15_ION_1, NITROGEN_15,
    OXYGEN_15_ION_8, OXYGEN_15_ION_7, OXYGEN_15_ION_6, OXYGEN_15_ION_5, OXYGEN_15_ION_4, OXYGEN_15_ION_3, OXYGEN_15_ION_2, OXYGEN_15_ION_1, OXYGEN_15,
    OXYGEN_16_ION_8, OXYGEN_16_ION_7, OXYGEN_16_ION_6, OXYGEN_16_ION_5, OXYGEN_16_ION_4, OXYGEN_16_ION_3, OXYGEN_16_ION_2, OXYGEN_16_ION_1, OXYGEN_16,
    OXYGEN_17_ION_8, OXYGEN_17_ION_7, OXYGEN_17_ION_6, OXYGEN_17_ION_5, OXYGEN_17_ION_4, OXYGEN_17_ION_3, OXYGEN_17_ION_2, OXYGEN_17_ION_1, OXYGEN_17,
    OXYGEN_18_ION_8, OXYGEN_18_ION_7, OXYGEN_18_ION_6, OXYGEN_18_ION_5, OXYGEN_18_ION_4, OXYGEN_18_ION_3, OXYGEN_18_ION_2, OXYGEN_18_ION_1, OXYGEN_18,
    FLUORINE_17_ION_9, FLUORINE_17_ION_8, FLUORINE_17_ION_7, FLUORINE_17_ION_6, FLUORINE_17_ION_5, FLUORINE_17_ION_4, FLUORINE_17_ION_3, FLUORINE_17_ION_2, FLUORINE_17_ION_1, FLUORINE_17,
    FLUORINE_18_ION_9, FLUORINE_18_ION_8, FLUORINE_18_ION_7, FLUORINE_18_ION_6, FLUORINE_18_ION_5, FLUORINE_18_ION_4, FLUORINE_18_ION_3, FLUORINE_18_ION_2, FLUORINE_18_ION_1, FLUORINE_18,
    FLUORINE_19_ION_9, FLUORINE_19_ION_8, FLUORINE_19_ION_7, FLUORINE_19_ION_6, FLUORINE_19_ION_5, FLUORINE_19_ION_4, FLUORINE_19_ION_3, FLUORINE_19_ION_2, FLUORINE_19_ION_1, FLUORINE_19,
    NEON_20_ION_10, NEON_20_ION_9, NEON_20_ION_8, NEON_20_ION_7, NEON_20_ION_6, NEON_20_ION_5, NEON_20_ION_4, NEON_20_ION_3, NEON_20_ION_2, NEON_20_ION_1, NEON_20,
    NEON_21_ION_10, NEON_21_ION_9, NEON_21_ION_8, NEON_21_ION_7, NEON_21_ION_6, NEON_21_ION_5, NEON_21_ION_4, NEON_21_ION_3, NEON_21_ION_2, NEON_21_ION_1, NEON_21,
    NEON_22_ION_10, NEON_22_ION_9, NEON_22_ION_8, NEON_22_ION_7, NEON_22_ION_6, NEON_22_ION_5, NEON_22_ION_4, NEON_22_ION_3, NEON_22_ION_2, NEON_22_ION_1, NEON_22,
    SODIUM_21_ION_11, SODIUM_21_ION_10, SODIUM_21_ION_9, SODIUM_21_ION_8, SODIUM_21_ION_7, SODIUM_21_ION_6, SODIUM_21_ION_5, SODIUM_21_ION_4, SODIUM_21_ION_3, SODIUM_21_ION_2, SODIUM_21_ION_1, SODIUM_21,
    SODIUM_22_ION_11, SODIUM_22_ION_10, SODIUM_22_ION_9, SODIUM_22_ION_8, SODIUM_22_ION_7, SODIUM_22_ION_6, SODIUM_22_ION_5, SODIUM_22_ION_4, SODIUM_22_ION_3, SODIUM_22_ION_2, SODIUM_22_ION_1, SODIUM_22,
    SODIUM_23_ION_11, SODIUM_23_ION_10, SODIUM_23_ION_9, SODIUM_23_ION_8, SODIUM_23_ION_7, SODIUM_23_ION_6, SODIUM_23_ION_5, SODIUM_23_ION_4, SODIUM_23_ION_3, SODIUM_23_ION_2, SODIUM_23_ION_1, SODIUM_23,
    MAGNESIUM_23_ION_12, MAGNESIUM_23_ION_11, MAGNESIUM_23_ION_10, MAGNESIUM_23_ION_9, MAGNESIUM_23_ION_8, MAGNESIUM_23_ION_7, MAGNESIUM_23_ION_6, MAGNESIUM_23_ION_5, MAGNESIUM_23_ION_4, MAGNESIUM_23_ION_3, MAGNESIUM_23_ION_2, MAGNESIUM_23_ION_1, MAGNESIUM_23,
    MAGNESIUM_24_ION_12, MAGNESIUM_24_ION_11, MAGNESIUM_24_ION_10, MAGNESIUM_24_ION_9, MAGNESIUM_24_ION_8, MAGNESIUM_24_ION_7, MAGNESIUM_24_ION_6, MAGNESIUM_24_ION_5, MAGNESIUM_24_ION_4, MAGNESIUM_24_ION_3, MAGNESIUM_24_ION_2, MAGNESIUM_24_ION_1, MAGNESIUM_24,
    MAGNESIUM_25_ION_12, MAGNESIUM_25_ION_11, MAGNESIUM_25_ION_10, MAGNESIUM_25_ION_9, MAGNESIUM_25_ION_8, MAGNESIUM_25_ION_7, MAGNESIUM_25_ION_6, MAGNESIUM_25_ION_5, MAGNESIUM_25_ION_4, MAGNESIUM_25_ION_3, MAGNESIUM_25_ION_2, MAGNESIUM_25_ION_1, MAGNESIUM_25,
    MAGNESIUM_26_ION_12, MAGNESIUM_26_ION_11, MAGNESIUM_26_ION_10, MAGNESIUM_26_ION_9, MAGNESIUM_26_ION_8, MAGNESIUM_26_ION_7, MAGNESIUM_26_ION_6, MAGNESIUM_26_ION_5, MAGNESIUM_26_ION_4, MAGNESIUM_26_ION_3, MAGNESIUM_26_ION_2, MAGNESIUM_26_ION_1, MAGNESIUM_26,
    ALUMINUM_25_ION_13, ALUMINUM_25_ION_12, ALUMINUM_25_ION_11, ALUMINUM_25_ION_10, ALUMINUM_25_ION_9, ALUMINUM_25_ION_8, ALUMINUM_25_ION_7, ALUMINUM_25_ION_6, ALUMINUM_25_ION_5, ALUMINUM_25_ION_4, ALUMINUM_25_ION_3, ALUMINUM_25_ION_2, ALUMINUM_25_ION_1, ALUMINUM_25,
    ALUMINUM_26_ION_13, ALUMINUM_26_ION_12, ALUMINUM_26_ION_11, ALUMINUM_26_ION_10, ALUMINUM_26_ION_9, ALUMINUM_26_ION_8, ALUMINUM_26_ION_7, ALUMINUM_26_ION_6, ALUMINUM_26_ION_5, ALUMINUM_26_ION_4, ALUMINUM_26_ION_3, ALUMINUM_26_ION_2, ALUMINUM_26_ION_1, ALUMINUM_26,
    ALUMINUM_27_ION_13, ALUMINUM_27_ION_12, ALUMINUM_27_ION_11, ALUMINUM_27_ION_10, ALUMINUM_27_ION_9, ALUMINUM_27_ION_8, ALUMINUM_27_ION_7, ALUMINUM_27_ION_6, ALUMINUM_27_ION_5, ALUMINUM_27_ION_4, ALUMINUM_27_ION_3, ALUMINUM_27_ION_2, ALUMINUM_27_ION_1, ALUMINUM_27,
    SILICON_28_ION_14, SILICON_28_ION_13, SILICON_28_ION_12, SILICON_28_ION_11, SILICON_28_ION_10, SILICON_28_ION_9, SILICON_28_ION_8, SILICON_28_ION_7, SILICON_28_ION_6, SILICON_28_ION_5, SILICON_28_ION_4, SILICON_28_ION_3, SILICON_28_ION_2, SILICON_28_ION_1, SILICON_28,
    SILICON_29_ION_14, SILICON_29_ION_13, SILICON_29_ION_12, SILICON_29_ION_11, SILICON_29_ION_10, SILICON_29_ION_9, SILICON_29_ION_8, SILICON_29_ION_7, SILICON_29_ION_6, SILICON_29_ION_5, SILICON_29_ION_4, SILICON_29_ION_3, SILICON_29_ION_2, SILICON_29_ION_1, SILICON_29,
    SILICON_30_ION_14, SILICON_30_ION_13, SILICON_30_ION_12, SILICON_30_ION_11, SILICON_30_ION_10, SILICON_30_ION_9, SILICON_30_ION_8, SILICON_30_ION_7, SILICON_30_ION_6, SILICON_30_ION_5, SILICON_30_ION_4, SILICON_30_ION_3, SILICON_30_ION_2, SILICON_30_ION_1, SILICON_30,
    SILICON_31_ION_14, SILICON_31_ION_13, SILICON_31_ION_12, SILICON_31_ION_11, SILICON_31_ION_10, SILICON_31_ION_9, SILICON_31_ION_8, SILICON_31_ION_7, SILICON_31_ION_6, SILICON_31_ION_5, SILICON_31_ION_4, SILICON_31_ION_3, SILICON_31_ION_2, SILICON_31_ION_1, SILICON_31,
    PHOSPHORUS_31_ION_15, PHOSPHORUS_31_ION_14, PHOSPHORUS_31_ION_13, PHOSPHORUS_31_ION_12, PHOSPHORUS_31_ION_11, PHOSPHORUS_31_ION_10, PHOSPHORUS_31_ION_9, PHOSPHORUS_31_ION_8, PHOSPHORUS_31_ION_7, PHOSPHORUS_31_ION_6, PHOSPHORUS_31_ION_5, PHOSPHORUS_31_ION_4, PHOSPHORUS_31_ION_3, PHOSPHORUS_31_ION_2, PHOSPHORUS_31_ION_1, PHOSPHORUS_31,
    SULFUR_31_ION_16, SULFUR_31_ION_15, SULFUR_31_ION_14, SULFUR_31_ION_13, SULFUR_31_ION_12, SULFUR_31_ION_11, SULFUR_31_ION_10, SULFUR_31_ION_9, SULFUR_31_ION_8, SULFUR_31_ION_7, SULFUR_31_ION_6, SULFUR_31_ION_5, SULFUR_31_ION_4, SULFUR_31_ION_3, SULFUR_31_ION_2, SULFUR_31_ION_1, SULFUR_31,
    SULFUR_32_ION_16, SULFUR_32_ION_15, SULFUR_32_ION_14, SULFUR_32_ION_13, SULFUR_32_ION_12, SULFUR_32_ION_11, SULFUR_32_ION_10, SULFUR_32_ION_9, SULFUR_32_ION_8, SULFUR_32_ION_7, SULFUR_32_ION_6, SULFUR_32_ION_5, SULFUR_32_ION_4, SULFUR_32_ION_3, SULFUR_32_ION_2, SULFUR_32_ION_1, SULFUR_32,
    ARGON_36_ION_18, ARGON_36_ION_17, ARGON_36_ION_16, ARGON_36_ION_15, ARGON_36_ION_14, ARGON_36_ION_13, ARGON_36_ION_12, ARGON_36_ION_11, ARGON_36_ION_10, ARGON_36_ION_9, ARGON_36_ION_8, ARGON_36_ION_7, ARGON_36_ION_6, ARGON_36_ION_5, ARGON_36_ION_4, ARGON_36_ION_3, ARGON_36_ION_2, ARGON_36_ION_1, ARGON_36,
    CALCIUM_40_ION_20, CALCIUM_40_ION_19, CALCIUM_40_ION_18, CALCIUM_40_ION_17, CALCIUM_40_ION_16, CALCIUM_40_ION_15, CALCIUM_40_ION_14, CALCIUM_40_ION_13, CALCIUM_40_ION_12, CALCIUM_40_ION_11, CALCIUM_40_ION_10, CALCIUM_40_ION_9, CALCIUM_40_ION_8, CALCIUM_40_ION_7, CALCIUM_40_ION_6, CALCIUM_40_ION_5, CALCIUM_40_ION_4, CALCIUM_40_ION_3, CALCIUM_40_ION_2, CALCIUM_40_ION_1, CALCIUM_40,
    TITANIUM_44_ION_22, TITANIUM_44_ION_21, TITANIUM_44_ION_20, TITANIUM_44_ION_19, TITANIUM_44_ION_18, TITANIUM_44_ION_17, TITANIUM_44_ION_16, TITANIUM_44_ION_15, TITANIUM_44_ION_14, TITANIUM_44_ION_13, TITANIUM_44_ION_12, TITANIUM_44_ION_11, TITANIUM_44_ION_10, TITANIUM_44_ION_9, TITANIUM_44_ION_8, TITANIUM_44_ION_7, TITANIUM_44_ION_6, TITANIUM_44_ION_5, TITANIUM_44_ION_4, TITANIUM_44_ION_3, TITANIUM_44_ION_2, TITANIUM_44_ION_1, TITANIUM_44,
    TITANIUM_48_ION_22, TITANIUM_48_ION_21, TITANIUM_48_ION_20, TITANIUM_48_ION_19, TITANIUM_48_ION_18, TITANIUM_48_ION_17, TITANIUM_48_ION_16, TITANIUM_48_ION_15, TITANIUM_48_ION_14, TITANIUM_48_ION_13, TITANIUM_48_ION_12, TITANIUM_48_ION_11, TITANIUM_48_ION_10, TITANIUM_48_ION_9, TITANIUM_48_ION_8, TITANIUM_48_ION_7, TITANIUM_48_ION_6, TITANIUM_48_ION_5, TITANIUM_48_ION_4, TITANIUM_48_ION_3, TITANIUM_48_ION_2, TITANIUM_48_ION_1, TITANIUM_48,
    VANADIUM_48_ION_23, VANADIUM_48_ION_22, VANADIUM_48_ION_21, VANADIUM_48_ION_20, VANADIUM_48_ION_19, VANADIUM_48_ION_18, VANADIUM_48_ION_17, VANADIUM_48_ION_16, VANADIUM_48_ION_15, VANADIUM_48_ION_14, VANADIUM_48_ION_13, VANADIUM_48_ION_12, VANADIUM_48_ION_11, VANADIUM_48_ION_10, VANADIUM_48_ION_9, VANADIUM_48_ION_8, VANADIUM_48_ION_7, VANADIUM_48_ION_6, VANADIUM_48_ION_5, VANADIUM_48_ION_4, VANADIUM_48_ION_3, VANADIUM_48_ION_2, VANADIUM_48_ION_1, VANADIUM_48,
    CHROMIUM_48_ION_24, CHROMIUM_48_ION_23, CHROMIUM_48_ION_22, CHROMIUM_48_ION_21, CHROMIUM_48_ION_20, CHROMIUM_48_ION_19, CHROMIUM_48_ION_18, CHROMIUM_48_ION_17, CHROMIUM_48_ION_16, CHROMIUM_48_ION_15, CHROMIUM_48_ION_14, CHROMIUM_48_ION_13, CHROMIUM_48_ION_12, CHROMIUM_48_ION_11, CHROMIUM_48_ION_10, CHROMIUM_48_ION_9, CHROMIUM_48_ION_8, CHROMIUM_48_ION_7, CHROMIUM_48_ION_6, CHROMIUM_48_ION_5, CHROMIUM_48_ION_4, CHROMIUM_48_ION_3, CHROMIUM_48_ION_2, CHROMIUM_48_ION_1, CHROMIUM_48,
    CHROMIUM_52_ION_24, CHROMIUM_52_ION_23, CHROMIUM_52_ION_22, CHROMIUM_52_ION_21, CHROMIUM_52_ION_20, CHROMIUM_52_ION_19, CHROMIUM_52_ION_18, CHROMIUM_52_ION_17, CHROMIUM_52_ION_16, CHROMIUM_52_ION_15, CHROMIUM_52_ION_14, CHROMIUM_52_ION_13, CHROMIUM_52_ION_12, CHROMIUM_52_ION_11, CHROMIUM_52_ION_10, CHROMIUM_52_ION_9, CHROMIUM_52_ION_8, CHROMIUM_52_ION_7, CHROMIUM_52_ION_6, CHROMIUM_52_ION_5, CHROMIUM_52_ION_4, CHROMIUM_52_ION_3, CHROMIUM_52_ION_2, CHROMIUM_52_ION_1, CHROMIUM_52,
    MANGANESE_52_ION_25, MANGANESE_52_ION_24, MANGANESE_52_ION_23, MANGANESE_52_ION_22, MANGANESE_52_ION_21, MANGANESE_52_ION_20, MANGANESE_52_ION_19, MANGANESE_52_ION_18, MANGANESE_52_ION_17, MANGANESE_52_ION_16, MANGANESE_52_ION_15, MANGANESE_52_ION_14, MANGANESE_52_ION_13, MANGANESE_52_ION_12, MANGANESE_52_ION_11, MANGANESE_52_ION_10, MANGANESE_52_ION_9, MANGANESE_52_ION_8, MANGANESE_52_ION_7, MANGANESE_52_ION_6, MANGANESE_52_ION_5, MANGANESE_52_ION_4, MANGANESE_52_ION_3, MANGANESE_52_ION_2, MANGANESE_52_ION_1, MANGANESE_52,
    IRON_52_ION_26, IRON_52_ION_25, IRON_52_ION_24, IRON_52_ION_23, IRON_52_ION_22, IRON_52_ION_21, IRON_52_ION_20, IRON_52_ION_19, IRON_52_ION_18, IRON_52_ION_17, IRON_52_ION_16, IRON_52_ION_15, IRON_52_ION_14, IRON_52_ION_13, IRON_52_ION_12, IRON_52_ION_11, IRON_52_ION_10, IRON_52_ION_9, IRON_52_ION_8, IRON_52_ION_7, IRON_52_ION_6, IRON_52_ION_5, IRON_52_ION_4, IRON_52_ION_3, IRON_52_ION_2, IRON_52_ION_1, IRON_52,
    IRON_56_ION_26, IRON_56_ION_25, IRON_56_ION_24, IRON_56_ION_23, IRON_56_ION_22, IRON_56_ION_21, IRON_56_ION_20, IRON_56_ION_19, IRON_56_ION_18, IRON_56_ION_17, IRON_56_ION_16, IRON_56_ION_15, IRON_56_ION_14, IRON_56_ION_13, IRON_56_ION_12, IRON_56_ION_11, IRON_56_ION_10, IRON_56_ION_9, IRON_56_ION_8, IRON_56_ION_7, IRON_56_ION_6, IRON_56_ION_5, IRON_56_ION_4, IRON_56_ION_3, IRON_56_ION_2, IRON_56_ION_1, IRON_56,
    IRON_57_ION_26, IRON_57_ION_25, IRON_57_ION_24, IRON_57_ION_23, IRON_57_ION_22, IRON_57_ION_21, IRON_57_ION_20, IRON_57_ION_19, IRON_57_ION_18, IRON_57_ION_17, IRON_57_ION_16, IRON_57_ION_15, IRON_57_ION_14, IRON_57_ION_13, IRON_57_ION_12, IRON_57_ION_11, IRON_57_ION_10, IRON_57_ION_9, IRON_57_ION_8, IRON_57_ION_7, IRON_57_ION_6, IRON_57_ION_5, IRON_57_ION_4, IRON_57_ION_3, IRON_57_ION_2, IRON_57_ION_1, IRON_57,
    IRON_58_ION_26, IRON_58_ION_25, IRON_58_ION_24, IRON_58_ION_23, IRON_58_ION_22, IRON_58_ION_21, IRON_58_ION_20, IRON_58_ION_19, IRON_58_ION_18, IRON_58_ION_17, IRON_58_ION_16, IRON_58_ION_15, IRON_58_ION_14, IRON_58_ION_13, IRON_58_ION_12, IRON_58_ION_11, IRON_58_ION_10, IRON_58_ION_9, IRON_58_ION_8, IRON_58_ION_7, IRON_58_ION_6, IRON_58_ION_5, IRON_58_ION_4, IRON_58_ION_3, IRON_58_ION_2, IRON_58_ION_1, IRON_58,
    IRON_59_ION_26, IRON_59_ION_25, IRON_59_ION_24, IRON_59_ION_23, IRON_59_ION_22, IRON_59_ION_21, IRON_59_ION_20, IRON_59_ION_19, IRON_59_ION_18, IRON_59_ION_17, IRON_59_ION_16, IRON_59_ION_15, IRON_59_ION_14, IRON_59_ION_13, IRON_59_ION_12, IRON_59_ION_11, IRON_59_ION_10, IRON_59_ION_9, IRON_59_ION_8, IRON_59_ION_7, IRON_59_ION_6, IRON_59_ION_5, IRON_59_ION_4, IRON_59_ION_3, IRON_59_ION_2, IRON_59_ION_1, IRON_59,
    COBALT_56_ION_27, COBALT_56_ION_26, COBALT_56_ION_25, COBALT_56_ION_24, COBALT_56_ION_23, COBALT_56_ION_22, COBALT_56_ION_21, COBALT_56_ION_20, COBALT_56_ION_19, COBALT_56_ION_18, COBALT_56_ION_17, COBALT_56_ION_16, COBALT_56_ION_15, COBALT_56_ION_14, COBALT_56_ION_13, COBALT_56_ION_12, COBALT_56_ION_11, COBALT_56_ION_10, COBALT_56_ION_9, COBALT_56_ION_8, COBALT_56_ION_7, COBALT_56_ION_6, COBALT_56_ION_5, COBALT_56_ION_4, COBALT_56_ION_3, COBALT_56_ION_2, COBALT_56_ION_1, COBALT_56,
    COBALT_59_ION_27, COBALT_59_ION_26, COBALT_59_ION_25, COBALT_59_ION_24, COBALT_59_ION_23, COBALT_59_ION_22, COBALT_59_ION_21, COBALT_59_ION_20, COBALT_59_ION_19, COBALT_59_ION_18, COBALT_59_ION_17, COBALT_59_ION_16, COBALT_59_ION_15, COBALT_59_ION_14, COBALT_59_ION_13, COBALT_59_ION_12, COBALT_59_ION_11, COBALT_59_ION_10, COBALT_59_ION_9, COBALT_59_ION_8, COBALT_59_ION_7, COBALT_59_ION_6, COBALT_59_ION_5, COBALT_59_ION_4, COBALT_59_ION_3, COBALT_59_ION_2, COBALT_59_ION_1, COBALT_59,
    COBALT_60_ION_27, COBALT_60_ION_26, COBALT_60_ION_25, COBALT_60_ION_24, COBALT_60_ION_23, COBALT_60_ION_22, COBALT_60_ION_21, COBALT_60_ION_20, COBALT_60_ION_19, COBALT_60_ION_18, COBALT_60_ION_17, COBALT_60_ION_16, COBALT_60_ION_15, COBALT_60_ION_14, COBALT_60_ION_13, COBALT_60_ION_12, COBALT_60_ION_11, COBALT_60_ION_10, COBALT_60_ION_9, COBALT_60_ION_8, COBALT_60_ION_7, COBALT_60_ION_6, COBALT_60_ION_5, COBALT_60_ION_4, COBALT_60_ION_3, COBALT_60_ION_2, COBALT_60_ION_1, COBALT_60,
    NICKEL_56_ION_28, NICKEL_56_ION_27, NICKEL_56_ION_26, NICKEL_56_ION_25, NICKEL_56_ION_24, NICKEL_56_ION_23, NICKEL_56_ION_22, NICKEL_56_ION_21, NICKEL_56_ION_20, NICKEL_56_ION_19, NICKEL_56_ION_18, NICKEL_56_ION_17, NICKEL_56_ION_16, NICKEL_56_ION_15, NICKEL_56_ION_14, NICKEL_56_ION_13, NICKEL_56_ION_12, NICKEL_56_ION_11, NICKEL_56_ION_10, NICKEL_56_ION_9, NICKEL_56_ION_8, NICKEL_56_ION_7, NICKEL_56_ION_6, NICKEL_56_ION_5, NICKEL_56_ION_4, NICKEL_56_ION_3, NICKEL_56_ION_2, NICKEL_56_ION_1, NICKEL_56,
    NICKEL_60_ION_28, NICKEL_60_ION_27, NICKEL_60_ION_26, NICKEL_60_ION_25, NICKEL_60_ION_24, NICKEL_60_ION_23, NICKEL_60_ION_22, NICKEL_60_ION_21, NICKEL_60_ION_20, NICKEL_60_ION_19, NICKEL_60_ION_18, NICKEL_60_ION_17, NICKEL_60_ION_16, NICKEL_60_ION_15, NICKEL_60_ION_14, NICKEL_60_ION_13, NICKEL_60_ION_12, NICKEL_60_ION_11, NICKEL_60_ION_10, NICKEL_60_ION_9, NICKEL_60_ION_8, NICKEL_60_ION_7, NICKEL_60_ION_6, NICKEL_60_ION_5, NICKEL_60_ION_4, NICKEL_60_ION_3, NICKEL_60_ION_2, NICKEL_60_ION_1, NICKEL_60,


    Star,
    SPACE_MODULE,
    RECOMBINATION_MODULE,

    // Молекулы
    C2_H6_O_ETHANOL,
    C2_H6_O_DIMETHYL_ETHER,

    C_H4,

    O2,
    H2O,
    H2;

    val details: Details get() = detailsMap[this]!!

    companion object {
        // Каталог Details вынесен в ElementDetails.kt. Делёж на light/heavy — ради лимита JVM 64KB на байткод метода.
        private val detailsMap: Map<Element, Details> = lightElementsDetails() + heavyElementsDetails()
    }

}

data class Details(
    val type: ElementType,
    val symbol: String,
    val label: String,
    val mass: Float,
    val e: Int, // Количество электронов в элементе
    val p: Int, // Количество протонов в элементе
    val n: Int, // Количество нейтронов в элементе
    val radius: Float = 20f,
    val description: String = "",

    val energyLevels: List<Float> = listOf(), // Столько энергии нужно, чтобы выбить электрон у элемента. Энергетические уровни атома. Атом может принимать только такие кванты энергии
    val ion: Element? = null, // Если этот параметр указан, значит элемент может отдавать электрон. Положительно заряженный ион, который образуется, когда мы выбиваем электрон у элемента. Ионизация.
    val recombinationElement: Element? = null,  // Такой элемент получится, если элемент получит электрон
    val recombinationEnergy: Float? = null, // Столько энергии выделится, если элемент получит электрон

    val energyBondDissociation: Float? = null, // Энергия диссоциации. Сколько нужно энергии, чтобы разорвать химическую связь.
    val dissociationElements: List<Element> = listOf(), // Элементы, которые получаются в результате диссоциации

    val alphaGammaResult: Element? = null, // Альфа захват. Процесс в недрах звезд. Когда ион захватывает альфа частицу (ион Гелия-4) и получается более тяжелый элемент

    val alphaProtonResult: Element? = null, // (α,p) реакция. Ядро ловит ⁴He, выбрасывает протон: A + ⁴He → A′ + p (Z→Z+1, A→A+3). Историческая ¹⁴N+α→¹⁷O+p (Резерфорд, 1919). У нас работает только в TemperatureMode.Space — аналог «лабораторного» режима.

    val alphaNeutronResult: Element? = null, // (α,n) реакция. Ядро ловит ⁴He, выбрасывает нейтрон: A + ⁴He → A′ + n (Z→Z+2, A→A+3). Главный нейтронный источник для s-процесса: ¹⁸O→²¹Ne, ²²Ne→²⁵Mg (weak s-process в массивных звёздах), ²⁵Mg→²⁸Si. Работает в TemperatureMode.Star — He-burning ядро AGB и массивных звёзд.

    val protonGammaResult: Element? = null, // (p,γ) реакция. Ядро ловит протон с радиативным переходом: A + p → A′ + γ (Z→Z+1, A→A+1). Тип реакции, встречающийся в CNO/NeNa/MgAl-циклах (²⁰Ne+p→²¹Na, ²⁴Mg+p→²⁵Al и т.п.), pp-III (⁷Be+p→⁸B) и hot CNO breakouts. Работает в TemperatureMode.Star.

    val protonAlphaResult: Element? = null, // (p,α) реакция. Ядро ловит протон, выбрасывает ⁴He: A + p → A′ + ⁴He (Z→Z-1, A→A-3). Главные применения — замыкания циклов горения водорода: ¹⁵N+p→¹²C+α (CNO-I), ¹⁷O/¹⁸O+p→¹⁴N/¹⁵N+α (CNO-II/III), ²³Na+p→²⁰Ne+α (NeNa), ²⁷Al+p→²⁴Mg+α (MgAl). Работает в TemperatureMode.Star.

    val protonNeutronResult: Element? = null, // (p,n) реакция. Ядро ловит протон, выбрасывает нейтрон: A + p → A′ + n (Z→Z+1, A→A). Превращает изотоп в изобарный сосед с большим Z. В основном эндотермические — нужны высокие T (HotStar условия). Главный пример: ⁷Li(p,n)⁷Be (Q=-1.64 МэВ).

    val neutronGammaResult: Element? = null, // (n,γ) реакция. Ядро ловит нейтрон с радиативным переходом: A + n → A′ + γ (Z→Z, A→A+1). Главный механизм s-процесса — через цепочку (n,γ) рождаются все элементы тяжелее железа. Нет кулоновского барьера — идёт при любых T где есть свободные нейтроны. Цикл воспроизводства нейтронов: ¹²C(n,γ)¹³C(α,n)¹⁶O.

    val neutronProtonResult: Element? = null, // (n,p) реакция. Ядро ловит нейтрон, выбрасывает протон: A + n → A′ + p (Z→Z-1, A→A). Изобарный сосед с меньшим Z. Главный пример: ¹⁴N(n,p)¹⁴C — космогенный источник ¹⁴C (радиоуглеродное датирование). ¹⁴C β⁻-нестабилен → замыкает петлю ¹⁴N(n,p)¹⁴C(β⁻)¹⁴N.

    val neutronAlphaResult: Element? = null, // (n,α) реакция. Ядро ловит нейтрон, выбрасывает α (⁴He): A + n → A′ + ⁴He (Z→Z-2, A→A-3). Падение сразу на два Z. Пример: ¹⁷O(n,α)¹⁴C — кормит ту же радиоуглеродную петлю, что и (n,p). Прочие (¹⁰B(n,α)⁷Li, ⁶Li(n,α)³H) ждут target-ядер ¹⁰B/⁶Li.

    val betaPlusDecayResult: Element? = null, // β⁺-распад. Протон-избыточное ядро превращает протон в нейтрон с испусканием позитрона: p → n + e⁺ + νₑ (нейтрино опускаем). Если поле выставлено — элемент сам по себе нестабилен и распадается в указанный.

    val betaMinusDecayResult: Element? = null, // β⁻-распад. Нейтрон-избыточное ядро превращает нейтрон в протон с испусканием электрона: n → p + e⁻ + ν̄ₑ (антинейтрино опускаем). Z→Z+1, A не меняется. Зеркало betaPlusDecayResult — толкает s-процесс вверх по таблице (нейтрон-избыточный продукт (n,γ) распадается в следующий элемент). Первый пример: ³¹Si→³¹P.
)

