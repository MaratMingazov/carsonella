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
    PHOTON, ELECTRON, Proton, Neutron, POSITRON,

    // --- атомы ---
    HYDROGEN,
    DEUTERIUM_ION,
    DEUTERIUM,
    HELIUM_3_ION_2, HELIUM_3_ION_1, HELIUM_3,
    HELIUM_4_ION_2, HELIUM_4_ION_1, HELIUM_4,
    LITHIUM_7_ION_3, LITHIUM_7_ION_2, LITHIUM_7_ION_1, LITHIUM_7,
    BERYLLIUM_7_ION_4, BERYLLIUM_7_ION_3, BERYLLIUM_7_ION_2, BERYLLIUM_7_ION_1, BERYLLIUM_7,
    BERYLLIUM_8_ION_4, BERYLLIUM_8_ION_3, BERYLLIUM_8_ION_2, BERYLLIUM_8_ION_1, BERYLLIUM_8,
    CARBON_12_ION_6, CARBON_12_ION_5, CARBON_12_ION_4, CARBON_12_ION_3, CARBON_12_ION_2, CARBON_12_ION_1, CARBON_12,
    CARBON_13_ION_6, CARBON_13_ION_5, CARBON_13_ION_4, CARBON_13_ION_3, CARBON_13_ION_2, CARBON_13_ION_1, CARBON_13,
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
    SODIUM_23_ION_11, SODIUM_23_ION_10, SODIUM_23_ION_9, SODIUM_23_ION_8, SODIUM_23_ION_7, SODIUM_23_ION_6, SODIUM_23_ION_5, SODIUM_23_ION_4, SODIUM_23_ION_3, SODIUM_23_ION_2, SODIUM_23_ION_1, SODIUM_23,
    MAGNESIUM_24_ION_12, MAGNESIUM_24_ION_11, MAGNESIUM_24_ION_10, MAGNESIUM_24_ION_9, MAGNESIUM_24_ION_8, MAGNESIUM_24_ION_7, MAGNESIUM_24_ION_6, MAGNESIUM_24_ION_5, MAGNESIUM_24_ION_4, MAGNESIUM_24_ION_3, MAGNESIUM_24_ION_2, MAGNESIUM_24_ION_1, MAGNESIUM_24,
    SILICON_28_ION_14, SILICON_28_ION_13, SILICON_28_ION_12, SILICON_28_ION_11, SILICON_28_ION_10, SILICON_28_ION_9, SILICON_28_ION_8, SILICON_28_ION_7, SILICON_28_ION_6, SILICON_28_ION_5, SILICON_28_ION_4, SILICON_28_ION_3, SILICON_28_ION_2, SILICON_28_ION_1, SILICON_28,
    PHOSPHORUS_31_ION_15, PHOSPHORUS_31_ION_14, PHOSPHORUS_31_ION_13, PHOSPHORUS_31_ION_12, PHOSPHORUS_31_ION_11, PHOSPHORUS_31_ION_10, PHOSPHORUS_31_ION_9, PHOSPHORUS_31_ION_8, PHOSPHORUS_31_ION_7, PHOSPHORUS_31_ION_6, PHOSPHORUS_31_ION_5, PHOSPHORUS_31_ION_4, PHOSPHORUS_31_ION_3, PHOSPHORUS_31_ION_2, PHOSPHORUS_31_ION_1, PHOSPHORUS_31,
    SULFUR_31_ION_16, SULFUR_31_ION_15, SULFUR_31_ION_14, SULFUR_31_ION_13, SULFUR_31_ION_12, SULFUR_31_ION_11, SULFUR_31_ION_10, SULFUR_31_ION_9, SULFUR_31_ION_8, SULFUR_31_ION_7, SULFUR_31_ION_6, SULFUR_31_ION_5, SULFUR_31_ION_4, SULFUR_31_ION_3, SULFUR_31_ION_2, SULFUR_31_ION_1, SULFUR_31,
    SULFUR_32_ION_16, SULFUR_32_ION_15, SULFUR_32_ION_14, SULFUR_32_ION_13, SULFUR_32_ION_12, SULFUR_32_ION_11, SULFUR_32_ION_10, SULFUR_32_ION_9, SULFUR_32_ION_8, SULFUR_32_ION_7, SULFUR_32_ION_6, SULFUR_32_ION_5, SULFUR_32_ION_4, SULFUR_32_ION_3, SULFUR_32_ION_2, SULFUR_32_ION_1, SULFUR_32,
    ARGON_36_ION_18, ARGON_36_ION_17, ARGON_36_ION_16, ARGON_36_ION_15, ARGON_36_ION_14, ARGON_36_ION_13, ARGON_36_ION_12, ARGON_36_ION_11, ARGON_36_ION_10, ARGON_36_ION_9, ARGON_36_ION_8, ARGON_36_ION_7, ARGON_36_ION_6, ARGON_36_ION_5, ARGON_36_ION_4, ARGON_36_ION_3, ARGON_36_ION_2, ARGON_36_ION_1, ARGON_36,
    CALCIUM_40_ION_20, CALCIUM_40_ION_19, CALCIUM_40_ION_18, CALCIUM_40_ION_17, CALCIUM_40_ION_16, CALCIUM_40_ION_15, CALCIUM_40_ION_14, CALCIUM_40_ION_13, CALCIUM_40_ION_12, CALCIUM_40_ION_11, CALCIUM_40_ION_10, CALCIUM_40_ION_9, CALCIUM_40_ION_8, CALCIUM_40_ION_7, CALCIUM_40_ION_6, CALCIUM_40_ION_5, CALCIUM_40_ION_4, CALCIUM_40_ION_3, CALCIUM_40_ION_2, CALCIUM_40_ION_1, CALCIUM_40,
    TITANIUM_44_ION_22, TITANIUM_44_ION_21, TITANIUM_44_ION_20, TITANIUM_44_ION_19, TITANIUM_44_ION_18, TITANIUM_44_ION_17, TITANIUM_44_ION_16, TITANIUM_44_ION_15, TITANIUM_44_ION_14, TITANIUM_44_ION_13, TITANIUM_44_ION_12, TITANIUM_44_ION_11, TITANIUM_44_ION_10, TITANIUM_44_ION_9, TITANIUM_44_ION_8, TITANIUM_44_ION_7, TITANIUM_44_ION_6, TITANIUM_44_ION_5, TITANIUM_44_ION_4, TITANIUM_44_ION_3, TITANIUM_44_ION_2, TITANIUM_44_ION_1, TITANIUM_44,
    TITANIUM_48_ION_22, TITANIUM_48_ION_21, TITANIUM_48_ION_20, TITANIUM_48_ION_19, TITANIUM_48_ION_18, TITANIUM_48_ION_17, TITANIUM_48_ION_16, TITANIUM_48_ION_15, TITANIUM_48_ION_14, TITANIUM_48_ION_13, TITANIUM_48_ION_12, TITANIUM_48_ION_11, TITANIUM_48_ION_10, TITANIUM_48_ION_9, TITANIUM_48_ION_8, TITANIUM_48_ION_7, TITANIUM_48_ION_6, TITANIUM_48_ION_5, TITANIUM_48_ION_4, TITANIUM_48_ION_3, TITANIUM_48_ION_2, TITANIUM_48_ION_1, TITANIUM_48,
    VANADIUM_48_ION_23,
    CHROMIUM_48_ION_24,
    CHROMIUM_52_ION_24,
    MANGANESE_52_ION_25,
    IRON_52_ION_26,
    IRON_56_ION_26,
    COBALT_56_ION_27,
    NICKEL_56_ION_28,


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
        private val detailsMap: Map<Element, Details> = mapOf(
            // --- субатомные частицы ---
            PHOTON      to Details (type = ElementType.SubAtom, symbol = "γ",  label = "Photon (γ)",       mass = 0f,      e = 0, p = 0, n = 0, radius = 5f),
            ELECTRON    to Details (type = ElementType.SubAtom, symbol = "e⁻", label = "Electron (e⁻)",    mass = 0.1f,    e = 1, p = 0, n = 0, radius = 5f),
            Proton      to Details (type = ElementType.SubAtom, symbol = "p⁺", label = "Proton (p⁺)",      mass = 1f,      e = 0, p = 1, n = 0, radius = 10f, recombinationElement = HYDROGEN),
            Neutron     to Details (type = ElementType.SubAtom, symbol = "n",  label = "Neutron (n)",      mass = 1f,      e = 0, p = 0, n = 1, radius = 10f),
            // Позитрон — фундаментальная античастица электрона; p = 1 здесь это маркер положительного единичного заряда (для calculateForce), а не «содержит протон».
            POSITRON    to Details (type = ElementType.SubAtom, symbol = "e⁺", label = "Positron (e⁺)",    mass = 0.1f,    e = 0, p = 1, n = 0, radius = 5f),

            // --- атомы ---
            HYDROGEN                to Details (type = ElementType.Atom, symbol = "H",         label = "Hydrogen (H)",         mass = 1f, e = 1, p = 1, n = 0,      description = "Водород",                                                energyLevels = listOf(10.2f, 12.09f, 13.6f),    ion = Proton),
            DEUTERIUM_ION           to Details (type = ElementType.Atom, symbol = "²H⁺",       label = "DEUTERIUM (²H⁺)",      mass = 2f, e = 0, p = 1, n = 1,      description = "Дейтерий",   recombinationElement = DEUTERIUM),
            DEUTERIUM               to Details (type = ElementType.Atom, symbol = "²H",        label = "DEUTERIUM (²H)",       mass = 2f, e = 1, p = 1, n = 1,      description = "Дейтерий",                                               energyLevels = listOf(10.2f,12.09f, 13.6f),     ion = DEUTERIUM_ION),
            HELIUM_3_ION_2          to Details (type = ElementType.Atom, symbol = "³He²⁺",     label = "Helium (³He²⁺)",       mass = 3f, e = 0, p = 2, n = 1,      description = "Гелий",      recombinationElement = HELIUM_3_ION_1,                                                          alphaGammaResult = BERYLLIUM_7_ION_4),
            HELIUM_3_ION_1          to Details (type = ElementType.Atom, symbol = "³He¹⁺",     label = "Helium (³He¹⁺)",       mass = 3f, e = 1, p = 2, n = 1,      description = "Гелий",      recombinationElement = HELIUM_3,            energyLevels = listOf(40.8f, 48.36f, 54.42f),   ion = HELIUM_3_ION_2),
            HELIUM_3                to Details (type = ElementType.Atom, symbol = "³He",       label = "Helium (³He)",         mass = 3f, e = 2, p = 2, n = 1,      description = "Гелий",                                                  energyLevels = listOf(21.22f, 23.09f, 24.59f),  ion = HELIUM_3_ION_1),
            HELIUM_4_ION_2          to Details (type = ElementType.Atom, symbol = "⁴He²⁺",     label = "Helium (⁴He²⁺)",       mass = 4f, e = 0, p = 2, n = 2,      description = "Гелий",      recombinationElement = HELIUM_4_ION_1,                                                              alphaGammaResult = BERYLLIUM_8_ION_4),
            HELIUM_4_ION_1          to Details (type = ElementType.Atom, symbol = "⁴He¹⁺",     label = "Helium (⁴He¹⁺)",       mass = 4f, e = 1, p = 2, n = 2,      description = "Гелий",      recombinationElement = HELIUM_4,            energyLevels = listOf(40.8f, 48.36f, 54.42f),   ion = HELIUM_4_ION_2),
            HELIUM_4                to Details (type = ElementType.Atom, symbol = "⁴He",       label = "Helium (⁴He)",         mass = 4f, e = 2, p = 2, n = 2,      description = "Гелий",                                                  energyLevels = listOf(21.22f, 23.09f, 24.59f),  ion = HELIUM_4_ION_1),
            LITHIUM_7_ION_3         to Details (type = ElementType.Atom, symbol = "⁷Li³⁺",     label = "Lithium (⁷Li³⁺)",      mass = 7f, e = 0, p = 3, n = 4,      description = "Литий",      recombinationElement = LITHIUM_7_ION_2),
            LITHIUM_7_ION_2         to Details (type = ElementType.Atom, symbol = "⁷Li²⁺",     label = "Lithium (⁷Li²⁺)",      mass = 7f, e = 1, p = 3, n = 4,      description = "Литий",      recombinationElement = LITHIUM_7_ION_1,     energyLevels = listOf(91.8f, 108.81f, 122.4f),  ion = LITHIUM_7_ION_3),
            LITHIUM_7_ION_1         to Details (type = ElementType.Atom, symbol = "⁷Li¹⁺",     label = "Lithium (⁷Li¹⁺)",      mass = 7f, e = 2, p = 3, n = 4,      description = "Литий",      recombinationElement = LITHIUM_7,           energyLevels = listOf(62.22f, 69.61f, 75.64f),  ion = LITHIUM_7_ION_2),
            LITHIUM_7               to Details (type = ElementType.Atom, symbol = "⁷Li",       label = "Lithium (⁷Li)",        mass = 7f, e = 3, p = 3, n = 4,      description = "Литий",                                                  energyLevels = listOf(1.85f, 3.83f, 5.39f),     ion = LITHIUM_7_ION_1),
            BERYLLIUM_7_ION_4       to Details (type = ElementType.Atom, symbol = "⁷Be⁴⁺",     label = "Beryllium (⁷Be⁴⁺)",    mass = 7f, e = 0, p = 4, n = 3,      description = "Бериллий",   recombinationElement = BERYLLIUM_7_ION_3),
            BERYLLIUM_7_ION_3       to Details (type = ElementType.Atom, symbol = "⁷Be³⁺",     label = "Beryllium (⁷Be³⁺)",    mass = 7f, e = 1, p = 4, n = 3,      description = "Бериллий",   recombinationElement = BERYLLIUM_7_ION_2,   energyLevels = listOf(163.2f, 193.44f, 217.6f), ion = BERYLLIUM_7_ION_4),
            BERYLLIUM_7_ION_2       to Details (type = ElementType.Atom, symbol = "⁷Be²⁺",     label = "Beryllium (⁷Be²⁺)",    mass = 7f, e = 2, p = 4, n = 3,      description = "Бериллий",   recombinationElement = BERYLLIUM_7_ION_1,   energyLevels = listOf(123.7f, 140.3f, 153.9f),  ion = BERYLLIUM_7_ION_3),
            BERYLLIUM_7_ION_1       to Details (type = ElementType.Atom, symbol = "⁷Be¹⁺",     label = "Beryllium (⁷Be¹⁺)",    mass = 7f, e = 3, p = 4, n = 3,      description = "Бериллий",   recombinationElement = BERYLLIUM_7,         energyLevels = listOf(3.96f, 11.96f, 18.21f),   ion = BERYLLIUM_7_ION_2),
            BERYLLIUM_7             to Details (type = ElementType.Atom, symbol = "⁷Be",       label = "Beryllium (⁷Be)",      mass = 7f, e = 4, p = 4, n = 3,      description = "Бериллий",                                               energyLevels = listOf(5.28f, 7.46f, 9.32f),     ion = BERYLLIUM_7_ION_1),
            BERYLLIUM_8_ION_4       to Details (type = ElementType.Atom, symbol = "⁸Be⁴⁺",     label = "Beryllium (⁸Be⁴⁺)",    mass = 8f, e = 0, p = 4, n = 4,      description = "Бериллий",   recombinationElement = BERYLLIUM_8_ION_3,                                                           alphaGammaResult = CARBON_12_ION_6),
            BERYLLIUM_8_ION_3       to Details (type = ElementType.Atom, symbol = "⁸Be³⁺",     label = "Beryllium (⁸Be³⁺)",    mass = 8f, e = 1, p = 4, n = 4,      description = "Бериллий",   recombinationElement = BERYLLIUM_8_ION_2,   energyLevels = listOf(163.2f, 193.44f, 217.6f), ion = BERYLLIUM_8_ION_4),
            BERYLLIUM_8_ION_2       to Details (type = ElementType.Atom, symbol = "⁸Be²⁺",     label = "Beryllium (⁸Be²⁺)",    mass = 8f, e = 2, p = 4, n = 4,      description = "Бериллий",   recombinationElement = BERYLLIUM_8_ION_1,   energyLevels = listOf(123.7f, 140.3f, 153.9f),  ion = BERYLLIUM_8_ION_3),
            BERYLLIUM_8_ION_1       to Details (type = ElementType.Atom, symbol = "⁸Be¹⁺",     label = "Beryllium (⁸Be¹⁺)",    mass = 8f, e = 3, p = 4, n = 4,      description = "Бериллий",   recombinationElement = BERYLLIUM_8,         energyLevels = listOf(3.96f, 11.96f, 18.21f),   ion = BERYLLIUM_8_ION_2),
            BERYLLIUM_8             to Details (type = ElementType.Atom, symbol = "⁸Be",       label = "Beryllium (⁸Be)",      mass = 8f, e = 4, p = 4, n = 4,      description = "Бериллий",                                               energyLevels = listOf(5.28f, 7.46f, 9.32f),     ion = BERYLLIUM_8_ION_1),
            CARBON_12_ION_6         to Details (type = ElementType.Atom, symbol = "¹²C⁶⁺",     label = "Carbon (¹²C⁶⁺)",       mass = 12f, e = 0, p = 6, n = 6,     description = "Углерод",    recombinationElement = CARBON_12_ION_5,                                                             alphaGammaResult = OXYGEN_16_ION_8),
            CARBON_12_ION_5         to Details (type = ElementType.Atom, symbol = "¹²C⁵⁺",     label = "Carbon (¹²C⁵⁺)",       mass = 12f, e = 1, p = 6, n = 6,     description = "Углерод",    recombinationElement = CARBON_12_ION_4,     energyLevels = listOf(367.2f, 435.24f, 489.6f), ion = CARBON_12_ION_6),
            CARBON_12_ION_4         to Details (type = ElementType.Atom, symbol = "¹²C⁴⁺",     label = "Carbon (¹²C⁴⁺)",       mass = 12f, e = 2, p = 6, n = 6,     description = "Углерод",    recombinationElement = CARBON_12_ION_3,     energyLevels = listOf(307.85f, 354.51f, 392.09f), ion = CARBON_12_ION_5),
            CARBON_12_ION_3         to Details (type = ElementType.Atom, symbol = "¹²C³⁺",     label = "Carbon (¹²C³⁺)",       mass = 12f, e = 3, p = 6, n = 6,     description = "Углерод",    recombinationElement = CARBON_12_ION_2,     energyLevels = listOf(8.01f, 37.6f, 64.49f),    ion = CARBON_12_ION_4),
            CARBON_12_ION_2         to Details (type = ElementType.Atom, symbol = "¹²C²⁺",     label = "Carbon (¹²C²⁺)",       mass = 12f, e = 4, p = 6, n = 6,     description = "Углерод",    recombinationElement = CARBON_12_ION_1,     energyLevels = listOf(12.69f, 32.10f, 47.89f),  ion = CARBON_12_ION_3),
            CARBON_12_ION_1         to Details (type = ElementType.Atom, symbol = "¹²C¹⁺",     label = "Carbon (¹²C¹⁺)",       mass = 12f, e = 5, p = 6, n = 6,     description = "Углерод",    recombinationElement = CARBON_12,           energyLevels = listOf(5.33f, 9.29f, 24.38f),    ion = CARBON_12_ION_2),
            CARBON_12               to Details (type = ElementType.Atom, symbol = "¹²C",       label = "Carbon (¹²C)",         mass = 12f, e = 6, p = 6, n = 6,     description = "Углерод",                                                energyLevels = listOf(1.26f, 7.48f, 11.26f),    ion = CARBON_12_ION_1),
            CARBON_13_ION_6         to Details (type = ElementType.Atom, symbol = "¹³C⁶⁺",     label = "Carbon (¹³C⁶⁺)",       mass = 13f, e = 0, p = 6, n = 7,     description = "Углерод",    recombinationElement = CARBON_13_ION_5),
            CARBON_13_ION_5         to Details (type = ElementType.Atom, symbol = "¹³C⁵⁺",     label = "Carbon (¹³C⁵⁺)",       mass = 13f, e = 1, p = 6, n = 7,     description = "Углерод",    recombinationElement = CARBON_13_ION_4,     energyLevels = listOf(367.2f, 435.24f, 489.6f), ion = CARBON_13_ION_6),
            CARBON_13_ION_4         to Details (type = ElementType.Atom, symbol = "¹³C⁴⁺",     label = "Carbon (¹³C⁴⁺)",       mass = 13f, e = 2, p = 6, n = 7,     description = "Углерод",    recombinationElement = CARBON_13_ION_3,     energyLevels = listOf(307.85f, 354.51f, 392.09f), ion = CARBON_13_ION_5),
            CARBON_13_ION_3         to Details (type = ElementType.Atom, symbol = "¹³C³⁺",     label = "Carbon (¹³C³⁺)",       mass = 13f, e = 3, p = 6, n = 7,     description = "Углерод",    recombinationElement = CARBON_13_ION_2,     energyLevels = listOf(8.01f, 37.6f, 64.49f),    ion = CARBON_13_ION_4),
            CARBON_13_ION_2         to Details (type = ElementType.Atom, symbol = "¹³C²⁺",     label = "Carbon (¹³C²⁺)",       mass = 13f, e = 4, p = 6, n = 7,     description = "Углерод",    recombinationElement = CARBON_13_ION_1,     energyLevels = listOf(12.69f, 32.10f, 47.89f),  ion = CARBON_13_ION_3),
            CARBON_13_ION_1         to Details (type = ElementType.Atom, symbol = "¹³C¹⁺",     label = "Carbon (¹³C¹⁺)",       mass = 13f, e = 5, p = 6, n = 7,     description = "Углерод",    recombinationElement = CARBON_13,           energyLevels = listOf(5.33f, 9.29f, 24.38f),    ion = CARBON_13_ION_2),
            CARBON_13               to Details (type = ElementType.Atom, symbol = "¹³C",       label = "Carbon (¹³C)",         mass = 13f, e = 6, p = 6, n = 7,     description = "Углерод",                                                energyLevels = listOf(1.26f, 7.48f, 11.26f),    ion = CARBON_13_ION_1),
            NITROGEN_13_ION_7       to Details (type = ElementType.Atom, symbol = "¹³N⁷⁺",     label = "Nitrogen (¹³N⁷⁺)",     mass = 13f, e = 0, p = 7, n = 6,     description = "Азот",       recombinationElement = NITROGEN_13_ION_6,                                                                               betaPlusDecayResult = CARBON_13_ION_6),
            NITROGEN_13_ION_6       to Details (type = ElementType.Atom, symbol = "¹³N⁶⁺",     label = "Nitrogen (¹³N⁶⁺)",     mass = 13f, e = 1, p = 7, n = 6,     description = "Азот",       recombinationElement = NITROGEN_13_ION_5,   energyLevels = listOf(499.8f, 592.36f, 666.4f), ion = NITROGEN_13_ION_7),
            NITROGEN_13_ION_5       to Details (type = ElementType.Atom, symbol = "¹³N⁵⁺",     label = "Nitrogen (¹³N⁵⁺)",     mass = 13f, e = 2, p = 7, n = 6,     description = "Азот",       recombinationElement = NITROGEN_13_ION_4,   energyLevels = listOf(430.6f, 500.3f, 552.07f), ion = NITROGEN_13_ION_6),
            NITROGEN_13_ION_4       to Details (type = ElementType.Atom, symbol = "¹³N⁴⁺",     label = "Nitrogen (¹³N⁴⁺)",     mass = 13f, e = 3, p = 7, n = 6,     description = "Азот",       recombinationElement = NITROGEN_13_ION_3,   energyLevels = listOf(10.01f, 77.24f, 97.89f),  ion = NITROGEN_13_ION_5),
            NITROGEN_13_ION_3       to Details (type = ElementType.Atom, symbol = "¹³N³⁺",     label = "Nitrogen (¹³N³⁺)",     mass = 13f, e = 4, p = 7, n = 6,     description = "Азот",       recombinationElement = NITROGEN_13_ION_2,   energyLevels = listOf(16.20f, 39.97f, 77.47f),  ion = NITROGEN_13_ION_4),
            NITROGEN_13_ION_2       to Details (type = ElementType.Atom, symbol = "¹³N²⁺",     label = "Nitrogen (¹³N²⁺)",     mass = 13f, e = 5, p = 7, n = 6,     description = "Азот",       recombinationElement = NITROGEN_13_ION_1,   energyLevels = listOf(6.98f, 12.29f, 47.45f),   ion = NITROGEN_13_ION_3),
            NITROGEN_13_ION_1       to Details (type = ElementType.Atom, symbol = "¹³N¹⁺",     label = "Nitrogen (¹³N¹⁺)",     mass = 13f, e = 6, p = 7, n = 6,     description = "Азот",       recombinationElement = NITROGEN_13,         energyLevels = listOf(1.899f, 11.28f, 29.60f),  ion = NITROGEN_13_ION_2),
            NITROGEN_13             to Details (type = ElementType.Atom, symbol = "¹³N",       label = "Nitrogen (¹³N)",       mass = 13f, e = 7, p = 7, n = 6,     description = "Азот",                                                   energyLevels = listOf(2.38f, 10.34f, 14.53f),   ion = NITROGEN_13_ION_1),
            NITROGEN_14_ION_7       to Details (type = ElementType.Atom, symbol = "¹⁴N⁷⁺",     label = "Nitrogen (¹⁴N⁷⁺)",     mass = 14f, e = 0, p = 7, n = 7,     description = "Азот",       recombinationElement = NITROGEN_14_ION_6,                                                                               alphaProtonResult = OXYGEN_17_ION_8),
            NITROGEN_14_ION_6       to Details (type = ElementType.Atom, symbol = "¹⁴N⁶⁺",     label = "Nitrogen (¹⁴N⁶⁺)",     mass = 14f, e = 1, p = 7, n = 7,     description = "Азот",       recombinationElement = NITROGEN_14_ION_5,   energyLevels = listOf(499.8f, 592.36f, 666.4f), ion = NITROGEN_14_ION_7,    alphaProtonResult = OXYGEN_17_ION_7),
            NITROGEN_14_ION_5       to Details (type = ElementType.Atom, symbol = "¹⁴N⁵⁺",     label = "Nitrogen (¹⁴N⁵⁺)",     mass = 14f, e = 2, p = 7, n = 7,     description = "Азот",       recombinationElement = NITROGEN_14_ION_4,   energyLevels = listOf(430.6f, 500.3f, 552.07f), ion = NITROGEN_14_ION_6),
            NITROGEN_14_ION_4       to Details (type = ElementType.Atom, symbol = "¹⁴N⁴⁺",     label = "Nitrogen (¹⁴N⁴⁺)",     mass = 14f, e = 3, p = 7, n = 7,     description = "Азот",       recombinationElement = NITROGEN_14_ION_3,   energyLevels = listOf(10.01f, 77.24f, 97.89f),  ion = NITROGEN_14_ION_5),
            NITROGEN_14_ION_3       to Details (type = ElementType.Atom, symbol = "¹⁴N³⁺",     label = "Nitrogen (¹⁴N³⁺)",     mass = 14f, e = 4, p = 7, n = 7,     description = "Азот",       recombinationElement = NITROGEN_14_ION_2,   energyLevels = listOf(16.20f, 39.97f, 77.47f),  ion = NITROGEN_14_ION_4),
            NITROGEN_14_ION_2       to Details (type = ElementType.Atom, symbol = "¹⁴N²⁺",     label = "Nitrogen (¹⁴N²⁺)",     mass = 14f, e = 5, p = 7, n = 7,     description = "Азот",       recombinationElement = NITROGEN_14_ION_1,   energyLevels = listOf(6.98f, 12.29f, 47.45f),   ion = NITROGEN_14_ION_3),
            NITROGEN_14_ION_1       to Details (type = ElementType.Atom, symbol = "¹⁴N¹⁺",     label = "Nitrogen (¹⁴N¹⁺)",     mass = 14f, e = 6, p = 7, n = 7,     description = "Азот",       recombinationElement = NITROGEN_14,         energyLevels = listOf(1.899f, 11.28f, 29.60f),  ion = NITROGEN_14_ION_2),
            NITROGEN_14             to Details (type = ElementType.Atom, symbol = "¹⁴N",       label = "Nitrogen (¹⁴N)",       mass = 14f, e = 7, p = 7, n = 7,     description = "Азот",                                                   energyLevels = listOf(2.38f, 10.34f, 14.53f),   ion = NITROGEN_14_ION_1),
            NITROGEN_15_ION_7       to Details (type = ElementType.Atom, symbol = "¹⁵N⁷⁺",     label = "Nitrogen (¹⁵N⁷⁺)",     mass = 15f, e = 0, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_6, alphaGammaResult = FLUORINE_19_ION_9),
            NITROGEN_15_ION_6       to Details (type = ElementType.Atom, symbol = "¹⁵N⁶⁺",     label = "Nitrogen (¹⁵N⁶⁺)",     mass = 15f, e = 1, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_5,   energyLevels = listOf(499.8f, 592.36f, 666.4f), ion = NITROGEN_15_ION_7),
            NITROGEN_15_ION_5       to Details (type = ElementType.Atom, symbol = "¹⁵N⁵⁺",     label = "Nitrogen (¹⁵N⁵⁺)",     mass = 15f, e = 2, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_4,   energyLevels = listOf(430.6f, 500.3f, 552.07f), ion = NITROGEN_15_ION_6),
            NITROGEN_15_ION_4       to Details (type = ElementType.Atom, symbol = "¹⁵N⁴⁺",     label = "Nitrogen (¹⁵N⁴⁺)",     mass = 15f, e = 3, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_3,   energyLevels = listOf(10.01f, 77.24f, 97.89f),  ion = NITROGEN_15_ION_5),
            NITROGEN_15_ION_3       to Details (type = ElementType.Atom, symbol = "¹⁵N³⁺",     label = "Nitrogen (¹⁵N³⁺)",     mass = 15f, e = 4, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_2,   energyLevels = listOf(16.20f, 39.97f, 77.47f),  ion = NITROGEN_15_ION_4),
            NITROGEN_15_ION_2       to Details (type = ElementType.Atom, symbol = "¹⁵N²⁺",     label = "Nitrogen (¹⁵N²⁺)",     mass = 15f, e = 5, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_1,   energyLevels = listOf(6.98f, 12.29f, 47.45f),   ion = NITROGEN_15_ION_3),
            NITROGEN_15_ION_1       to Details (type = ElementType.Atom, symbol = "¹⁵N¹⁺",     label = "Nitrogen (¹⁵N¹⁺)",     mass = 15f, e = 6, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15,         energyLevels = listOf(1.899f, 11.28f, 29.60f),  ion = NITROGEN_15_ION_2),
            NITROGEN_15             to Details (type = ElementType.Atom, symbol = "¹⁵N",       label = "Nitrogen (¹⁵N)",       mass = 15f, e = 7, p = 7, n = 8,     description = "Азот",                                                   energyLevels = listOf(2.38f, 10.34f, 14.53f),   ion = NITROGEN_15_ION_1),
            OXYGEN_15_ION_8         to Details (type = ElementType.Atom, symbol = "¹⁵O⁸⁺",     label = "Oxygen (¹⁵O⁸⁺)",       mass = 15f, e = 0, p = 8, n = 7,     description = "Кислород",   recombinationElement = OXYGEN_15_ION_7, betaPlusDecayResult = NITROGEN_15_ION_7),
            OXYGEN_15_ION_7         to Details (type = ElementType.Atom, symbol = "¹⁵O⁷⁺",     label = "Oxygen (¹⁵O⁷⁺)",       mass = 15f, e = 1, p = 8, n = 7,     description = "Кислород",   recombinationElement = OXYGEN_15_ION_6,     energyLevels = listOf(652.8f, 773.69f, 871.41f), ion = OXYGEN_15_ION_8),
            OXYGEN_15_ION_6         to Details (type = ElementType.Atom, symbol = "¹⁵O⁶⁺",     label = "Oxygen (¹⁵O⁶⁺)",       mass = 15f, e = 2, p = 8, n = 7,     description = "Кислород",   recombinationElement = OXYGEN_15_ION_5,     energyLevels = listOf(573.95f, 665.42f, 739.29f), ion = OXYGEN_15_ION_7),
            OXYGEN_15_ION_5         to Details (type = ElementType.Atom, symbol = "¹⁵O⁵⁺",     label = "Oxygen (¹⁵O⁵⁺)",       mass = 15f, e = 3, p = 8, n = 7,     description = "Кислород",   recombinationElement = OXYGEN_15_ION_4,     energyLevels = listOf(11.95f, 71.96f, 138.12f), ion = OXYGEN_15_ION_6),
            OXYGEN_15_ION_4         to Details (type = ElementType.Atom, symbol = "¹⁵O⁴⁺",     label = "Oxygen (¹⁵O⁴⁺)",       mass = 15f, e = 4, p = 8, n = 7,     description = "Кислород",   recombinationElement = OXYGEN_15_ION_3,     energyLevels = listOf(19.69f, 84.18f, 113.9f),  ion = OXYGEN_15_ION_5),
            OXYGEN_15_ION_3         to Details (type = ElementType.Atom, symbol = "¹⁵O³⁺",     label = "Oxygen (¹⁵O³⁺)",       mass = 15f, e = 5, p = 8, n = 7,     description = "Кислород",   recombinationElement = OXYGEN_15_ION_2,     energyLevels = listOf(8.86f, 22.4f, 77.41f),    ion = OXYGEN_15_ION_4),
            OXYGEN_15_ION_2         to Details (type = ElementType.Atom, symbol = "¹⁵O²⁺",     label = "Oxygen (¹⁵O²⁺)",       mass = 15f, e = 6, p = 8, n = 7,     description = "Кислород",   recombinationElement = OXYGEN_15_ION_1,     energyLevels = listOf(2.51f, 14.88f, 54.94f),   ion = OXYGEN_15_ION_3),
            OXYGEN_15_ION_1         to Details (type = ElementType.Atom, symbol = "¹⁵O¹⁺",     label = "Oxygen (¹⁵O¹⁺)",       mass = 15f, e = 7, p = 8, n = 7,     description = "Кислород",   recombinationElement = OXYGEN_15,           energyLevels = listOf(3.33f, 14.86f, 35.12f),   ion = OXYGEN_15_ION_2),
            OXYGEN_15               to Details (type = ElementType.Atom, symbol = "¹⁵O",       label = "Oxygen (¹⁵O)",         mass = 15f, e = 8, p = 8, n = 7,     description = "Кислород",                                               energyLevels = listOf(1.96f, 9.51f, 13.6f),     ion = OXYGEN_15_ION_1),
            OXYGEN_16_ION_8         to Details (type = ElementType.Atom, symbol = "¹⁶O⁸⁺",     label = "Oxygen (¹⁶O⁸⁺)",       mass = 16f, e = 0, p = 8, n = 8,     description = "Кислород",   recombinationElement = OXYGEN_16_ION_7, alphaGammaResult = NEON_20_ION_10),
            OXYGEN_16_ION_7         to Details (type = ElementType.Atom, symbol = "¹⁶O⁷⁺",     label = "Oxygen (¹⁶O⁷⁺)",       mass = 16f, e = 1, p = 8, n = 8,     description = "Кислород",   recombinationElement = OXYGEN_16_ION_6,     energyLevels = listOf(652.8f, 773.69f, 871.41f), ion = OXYGEN_16_ION_8),
            OXYGEN_16_ION_6         to Details (type = ElementType.Atom, symbol = "¹⁶O⁶⁺",     label = "Oxygen (¹⁶O⁶⁺)",       mass = 16f, e = 2, p = 8, n = 8,     description = "Кислород",   recombinationElement = OXYGEN_16_ION_5,     energyLevels = listOf(573.95f, 665.42f, 739.29f), ion = OXYGEN_16_ION_7),
            OXYGEN_16_ION_5         to Details (type = ElementType.Atom, symbol = "¹⁶O⁵⁺",     label = "Oxygen (¹⁶O⁵⁺)",       mass = 16f, e = 3, p = 8, n = 8,     description = "Кислород",   recombinationElement = OXYGEN_16_ION_4,     energyLevels = listOf(11.95f, 71.96f, 138.12f), ion = OXYGEN_16_ION_6),
            OXYGEN_16_ION_4         to Details (type = ElementType.Atom, symbol = "¹⁶O⁴⁺",     label = "Oxygen (¹⁶O⁴⁺)",       mass = 16f, e = 4, p = 8, n = 8,     description = "Кислород",   recombinationElement = OXYGEN_16_ION_3,     energyLevels = listOf(19.69f, 84.18f, 113.9f),  ion = OXYGEN_16_ION_5),
            OXYGEN_16_ION_3         to Details (type = ElementType.Atom, symbol = "¹⁶O³⁺",     label = "Oxygen (¹⁶O³⁺)",       mass = 16f, e = 5, p = 8, n = 8,     description = "Кислород",   recombinationElement = OXYGEN_16_ION_2,     energyLevels = listOf(8.86f, 22.4f, 77.41f),    ion = OXYGEN_16_ION_4),
            OXYGEN_16_ION_2         to Details (type = ElementType.Atom, symbol = "¹⁶O²⁺",     label = "Oxygen (¹⁶O²⁺)",       mass = 16f, e = 6, p = 8, n = 8,     description = "Кислород",   recombinationElement = OXYGEN_16_ION_1,     energyLevels = listOf(2.51f, 14.88f, 54.94f),   ion = OXYGEN_16_ION_3),
            OXYGEN_16_ION_1         to Details (type = ElementType.Atom, symbol = "¹⁶O¹⁺",     label = "Oxygen (¹⁶O¹⁺)",       mass = 16f, e = 7, p = 8, n = 8,     description = "Кислород",   recombinationElement = OXYGEN_16,           energyLevels = listOf(3.33f, 14.86f, 35.12f),   ion = OXYGEN_16_ION_2),
            OXYGEN_16               to Details (type = ElementType.Atom, symbol = "¹⁶O",       label = "Oxygen (¹⁶O)",         mass = 16f, e = 8, p = 8, n = 8,     description = "Кислород",                                               energyLevels = listOf(1.96f, 9.51f, 13.6f),     ion = OXYGEN_16_ION_1),
            OXYGEN_17_ION_8         to Details (type = ElementType.Atom, symbol = "¹⁷O⁸⁺",     label = "Oxygen (¹⁷O⁸⁺)",       mass = 17f, e = 0, p = 8, n = 9,     description = "Кислород",   recombinationElement = OXYGEN_17_ION_7),
            OXYGEN_17_ION_7         to Details (type = ElementType.Atom, symbol = "¹⁷O⁷⁺",     label = "Oxygen (¹⁷O⁷⁺)",       mass = 17f, e = 1, p = 8, n = 9,     description = "Кислород",   recombinationElement = OXYGEN_17_ION_6,     energyLevels = listOf(652.8f, 773.69f, 871.41f), ion = OXYGEN_17_ION_8),
            OXYGEN_17_ION_6         to Details (type = ElementType.Atom, symbol = "¹⁷O⁶⁺",     label = "Oxygen (¹⁷O⁶⁺)",       mass = 17f, e = 2, p = 8, n = 9,     description = "Кислород",   recombinationElement = OXYGEN_17_ION_5,     energyLevels = listOf(573.95f, 665.42f, 739.29f), ion = OXYGEN_17_ION_7),
            OXYGEN_17_ION_5         to Details (type = ElementType.Atom, symbol = "¹⁷O⁵⁺",     label = "Oxygen (¹⁷O⁵⁺)",       mass = 17f, e = 3, p = 8, n = 9,     description = "Кислород",   recombinationElement = OXYGEN_17_ION_4,     energyLevels = listOf(11.95f, 71.96f, 138.12f), ion = OXYGEN_17_ION_6),
            OXYGEN_17_ION_4         to Details (type = ElementType.Atom, symbol = "¹⁷O⁴⁺",     label = "Oxygen (¹⁷O⁴⁺)",       mass = 17f, e = 4, p = 8, n = 9,     description = "Кислород",   recombinationElement = OXYGEN_17_ION_3,     energyLevels = listOf(19.69f, 84.18f, 113.9f),  ion = OXYGEN_17_ION_5),
            OXYGEN_17_ION_3         to Details (type = ElementType.Atom, symbol = "¹⁷O³⁺",     label = "Oxygen (¹⁷O³⁺)",       mass = 17f, e = 5, p = 8, n = 9,     description = "Кислород",   recombinationElement = OXYGEN_17_ION_2,     energyLevels = listOf(8.86f, 22.4f, 77.41f),    ion = OXYGEN_17_ION_4),
            OXYGEN_17_ION_2         to Details (type = ElementType.Atom, symbol = "¹⁷O²⁺",     label = "Oxygen (¹⁷O²⁺)",       mass = 17f, e = 6, p = 8, n = 9,     description = "Кислород",   recombinationElement = OXYGEN_17_ION_1,     energyLevels = listOf(2.51f, 14.88f, 54.94f),   ion = OXYGEN_17_ION_3),
            OXYGEN_17_ION_1         to Details (type = ElementType.Atom, symbol = "¹⁷O¹⁺",     label = "Oxygen (¹⁷O¹⁺)",       mass = 17f, e = 7, p = 8, n = 9,     description = "Кислород",   recombinationElement = OXYGEN_17,           energyLevels = listOf(3.33f, 14.86f, 35.12f),   ion = OXYGEN_17_ION_2),
            OXYGEN_17               to Details (type = ElementType.Atom, symbol = "¹⁷O",       label = "Oxygen (¹⁷O)",         mass = 17f, e = 8, p = 8, n = 9,     description = "Кислород",                                               energyLevels = listOf(1.96f, 9.51f, 13.6f),     ion = OXYGEN_17_ION_1),
            OXYGEN_18_ION_8         to Details (type = ElementType.Atom, symbol = "¹⁸O⁸⁺",     label = "Oxygen (¹⁸O⁸⁺)",       mass = 18f, e = 0, p = 8, n = 10,    description = "Кислород",   recombinationElement = OXYGEN_18_ION_7),
            OXYGEN_18_ION_7         to Details (type = ElementType.Atom, symbol = "¹⁸O⁷⁺",     label = "Oxygen (¹⁸O⁷⁺)",       mass = 18f, e = 1, p = 8, n = 10,    description = "Кислород",   recombinationElement = OXYGEN_18_ION_6,     energyLevels = listOf(652.8f, 773.69f, 871.41f), ion = OXYGEN_18_ION_8),
            OXYGEN_18_ION_6         to Details (type = ElementType.Atom, symbol = "¹⁸O⁶⁺",     label = "Oxygen (¹⁸O⁶⁺)",       mass = 18f, e = 2, p = 8, n = 10,    description = "Кислород",   recombinationElement = OXYGEN_18_ION_5,     energyLevels = listOf(573.95f, 665.42f, 739.29f), ion = OXYGEN_18_ION_7),
            OXYGEN_18_ION_5         to Details (type = ElementType.Atom, symbol = "¹⁸O⁵⁺",     label = "Oxygen (¹⁸O⁵⁺)",       mass = 18f, e = 3, p = 8, n = 10,    description = "Кислород",   recombinationElement = OXYGEN_18_ION_4,     energyLevels = listOf(11.95f, 71.96f, 138.12f), ion = OXYGEN_18_ION_6),
            OXYGEN_18_ION_4         to Details (type = ElementType.Atom, symbol = "¹⁸O⁴⁺",     label = "Oxygen (¹⁸O⁴⁺)",       mass = 18f, e = 4, p = 8, n = 10,    description = "Кислород",   recombinationElement = OXYGEN_18_ION_3,     energyLevels = listOf(19.69f, 84.18f, 113.9f),  ion = OXYGEN_18_ION_5),
            OXYGEN_18_ION_3         to Details (type = ElementType.Atom, symbol = "¹⁸O³⁺",     label = "Oxygen (¹⁸O³⁺)",       mass = 18f, e = 5, p = 8, n = 10,    description = "Кислород",   recombinationElement = OXYGEN_18_ION_2,     energyLevels = listOf(8.86f, 22.4f, 77.41f),    ion = OXYGEN_18_ION_4),
            OXYGEN_18_ION_2         to Details (type = ElementType.Atom, symbol = "¹⁸O²⁺",     label = "Oxygen (¹⁸O²⁺)",       mass = 18f, e = 6, p = 8, n = 10,    description = "Кислород",   recombinationElement = OXYGEN_18_ION_1,     energyLevels = listOf(2.51f, 14.88f, 54.94f),   ion = OXYGEN_18_ION_3),
            OXYGEN_18_ION_1         to Details (type = ElementType.Atom, symbol = "¹⁸O¹⁺",     label = "Oxygen (¹⁸O¹⁺)",       mass = 18f, e = 7, p = 8, n = 10,    description = "Кислород",   recombinationElement = OXYGEN_18,           energyLevels = listOf(3.33f, 14.86f, 35.12f),   ion = OXYGEN_18_ION_2),
            OXYGEN_18               to Details (type = ElementType.Atom, symbol = "¹⁸O",       label = "Oxygen (¹⁸O)",         mass = 18f, e = 8, p = 8, n = 10,    description = "Кислород",                                               energyLevels = listOf(1.96f, 9.51f, 13.6f),     ion = OXYGEN_18_ION_1),
            FLUORINE_17_ION_9       to Details (type = ElementType.Atom, symbol = "¹⁷F⁹⁺",     label = "Fluorine (¹⁷F⁹⁺)",     mass = 17f, e = 0, p = 9, n = 8,     description = "Фтор",       recombinationElement = FLUORINE_17_ION_8, betaPlusDecayResult = OXYGEN_17_ION_8),
            FLUORINE_17_ION_8       to Details (type = ElementType.Atom, symbol = "¹⁷F⁸⁺",     label = "Fluorine (¹⁷F⁸⁺)",     mass = 17f, e = 1, p = 9, n = 8,     description = "Фтор",       recombinationElement = FLUORINE_17_ION_7, energyLevels = listOf(826.2f, 979.29f, 1101.6f), ion = FLUORINE_17_ION_9),
            FLUORINE_17_ION_7       to Details (type = ElementType.Atom, symbol = "¹⁷F⁷⁺",     label = "Fluorine (¹⁷F⁷⁺)",     mass = 17f, e = 2, p = 9, n = 8,     description = "Фтор",       recombinationElement = FLUORINE_17_ION_6, energyLevels = listOf(737.6f, 857.4f, 953.91f), ion = FLUORINE_17_ION_8),
            FLUORINE_17_ION_6       to Details (type = ElementType.Atom, symbol = "¹⁷F⁶⁺",     label = "Fluorine (¹⁷F⁶⁺)",     mass = 17f, e = 3, p = 9, n = 8,     description = "Фтор",       recombinationElement = FLUORINE_17_ION_5, energyLevels = listOf(14.04f, 111.2f, 185.19f), ion = FLUORINE_17_ION_7),
            FLUORINE_17_ION_5       to Details (type = ElementType.Atom, symbol = "¹⁷F⁵⁺",     label = "Fluorine (¹⁷F⁵⁺)",     mass = 17f, e = 4, p = 9, n = 8,     description = "Фтор",       recombinationElement = FLUORINE_17_ION_4, energyLevels = listOf(22.93f, 107.86f, 157.17f), ion = FLUORINE_17_ION_6),
            FLUORINE_17_ION_4       to Details (type = ElementType.Atom, symbol = "¹⁷F⁴⁺",     label = "Fluorine (¹⁷F⁴⁺)",     mass = 17f, e = 5, p = 9, n = 8,     description = "Фтор",       recombinationElement = FLUORINE_17_ION_3, energyLevels = listOf(11.16f, 27.28f, 114.25f), ion = FLUORINE_17_ION_5),
            FLUORINE_17_ION_3       to Details (type = ElementType.Atom, symbol = "¹⁷F³⁺",     label = "Fluorine (¹⁷F³⁺)",     mass = 17f, e = 6, p = 9, n = 8,     description = "Фтор",       recombinationElement = FLUORINE_17_ION_2, energyLevels = listOf(2.76f, 17.36f, 87.14f),   ion = FLUORINE_17_ION_4),
            FLUORINE_17_ION_2       to Details (type = ElementType.Atom, symbol = "¹⁷F²⁺",     label = "Fluorine (¹⁷F²⁺)",     mass = 17f, e = 7, p = 9, n = 8,     description = "Фтор",       recombinationElement = FLUORINE_17_ION_1, energyLevels = listOf(4.09f, 19.84f, 62.71f),   ion = FLUORINE_17_ION_3),
            FLUORINE_17_ION_1       to Details (type = ElementType.Atom, symbol = "¹⁷F¹⁺",     label = "Fluorine (¹⁷F¹⁺)",     mass = 17f, e = 8, p = 9, n = 8,     description = "Фтор",       recombinationElement = FLUORINE_17,       energyLevels = listOf(2.60f, 12.40f, 34.97f),   ion = FLUORINE_17_ION_2),
            FLUORINE_17             to Details (type = ElementType.Atom, symbol = "¹⁷F",       label = "Fluorine (¹⁷F)",       mass = 17f, e = 9, p = 9, n = 8,     description = "Фтор",                                                 energyLevels = listOf(12.65f, 14.51f, 17.42f),   ion = FLUORINE_17_ION_1),
            FLUORINE_18_ION_9       to Details (type = ElementType.Atom, symbol = "¹⁸F⁹⁺",     label = "Fluorine (¹⁸F⁹⁺)",     mass = 18f, e = 0, p = 9, n = 9,     description = "Фтор",       recombinationElement = FLUORINE_18_ION_8, betaPlusDecayResult = OXYGEN_18_ION_8),
            FLUORINE_18_ION_8       to Details (type = ElementType.Atom, symbol = "¹⁸F⁸⁺",     label = "Fluorine (¹⁸F⁸⁺)",     mass = 18f, e = 1, p = 9, n = 9,     description = "Фтор",       recombinationElement = FLUORINE_18_ION_7, energyLevels = listOf(826.2f, 979.29f, 1101.6f), ion = FLUORINE_18_ION_9),
            FLUORINE_18_ION_7       to Details (type = ElementType.Atom, symbol = "¹⁸F⁷⁺",     label = "Fluorine (¹⁸F⁷⁺)",     mass = 18f, e = 2, p = 9, n = 9,     description = "Фтор",       recombinationElement = FLUORINE_18_ION_6, energyLevels = listOf(737.6f, 857.4f, 953.91f), ion = FLUORINE_18_ION_8),
            FLUORINE_18_ION_6       to Details (type = ElementType.Atom, symbol = "¹⁸F⁶⁺",     label = "Fluorine (¹⁸F⁶⁺)",     mass = 18f, e = 3, p = 9, n = 9,     description = "Фтор",       recombinationElement = FLUORINE_18_ION_5, energyLevels = listOf(14.04f, 111.2f, 185.19f), ion = FLUORINE_18_ION_7),
            FLUORINE_18_ION_5       to Details (type = ElementType.Atom, symbol = "¹⁸F⁵⁺",     label = "Fluorine (¹⁸F⁵⁺)",     mass = 18f, e = 4, p = 9, n = 9,     description = "Фтор",       recombinationElement = FLUORINE_18_ION_4, energyLevels = listOf(22.93f, 107.86f, 157.17f), ion = FLUORINE_18_ION_6),
            FLUORINE_18_ION_4       to Details (type = ElementType.Atom, symbol = "¹⁸F⁴⁺",     label = "Fluorine (¹⁸F⁴⁺)",     mass = 18f, e = 5, p = 9, n = 9,     description = "Фтор",       recombinationElement = FLUORINE_18_ION_3, energyLevels = listOf(11.16f, 27.28f, 114.25f), ion = FLUORINE_18_ION_5),
            FLUORINE_18_ION_3       to Details (type = ElementType.Atom, symbol = "¹⁸F³⁺",     label = "Fluorine (¹⁸F³⁺)",     mass = 18f, e = 6, p = 9, n = 9,     description = "Фтор",       recombinationElement = FLUORINE_18_ION_2, energyLevels = listOf(2.76f, 17.36f, 87.14f),   ion = FLUORINE_18_ION_4),
            FLUORINE_18_ION_2       to Details (type = ElementType.Atom, symbol = "¹⁸F²⁺",     label = "Fluorine (¹⁸F²⁺)",     mass = 18f, e = 7, p = 9, n = 9,     description = "Фтор",       recombinationElement = FLUORINE_18_ION_1, energyLevels = listOf(4.09f, 19.84f, 62.71f),   ion = FLUORINE_18_ION_3),
            FLUORINE_18_ION_1       to Details (type = ElementType.Atom, symbol = "¹⁸F¹⁺",     label = "Fluorine (¹⁸F¹⁺)",     mass = 18f, e = 8, p = 9, n = 9,     description = "Фтор",       recombinationElement = FLUORINE_18,       energyLevels = listOf(2.60f, 12.40f, 34.97f),   ion = FLUORINE_18_ION_2),
            FLUORINE_18             to Details (type = ElementType.Atom, symbol = "¹⁸F",       label = "Fluorine (¹⁸F)",       mass = 18f, e = 9, p = 9, n = 9,     description = "Фтор",                                                 energyLevels = listOf(12.65f, 14.51f, 17.42f),   ion = FLUORINE_18_ION_1),
            FLUORINE_19_ION_9       to Details (type = ElementType.Atom, symbol = "¹⁹F⁹⁺",     label = "Fluorine (¹⁹F⁹⁺)",     mass = 19f, e = 0, p = 9, n = 10,    description = "Фтор",       recombinationElement = FLUORINE_19_ION_8),
            FLUORINE_19_ION_8       to Details (type = ElementType.Atom, symbol = "¹⁹F⁸⁺",     label = "Fluorine (¹⁹F⁸⁺)",     mass = 19f, e = 1, p = 9, n = 10,    description = "Фтор",       recombinationElement = FLUORINE_19_ION_7, energyLevels = listOf(826.2f, 979.29f, 1101.6f), ion = FLUORINE_19_ION_9),
            FLUORINE_19_ION_7       to Details (type = ElementType.Atom, symbol = "¹⁹F⁷⁺",     label = "Fluorine (¹⁹F⁷⁺)",     mass = 19f, e = 2, p = 9, n = 10,    description = "Фтор",       recombinationElement = FLUORINE_19_ION_6, energyLevels = listOf(737.6f, 857.4f, 953.91f), ion = FLUORINE_19_ION_8),
            FLUORINE_19_ION_6       to Details (type = ElementType.Atom, symbol = "¹⁹F⁶⁺",     label = "Fluorine (¹⁹F⁶⁺)",     mass = 19f, e = 3, p = 9, n = 10,    description = "Фтор",       recombinationElement = FLUORINE_19_ION_5, energyLevels = listOf(14.04f, 111.2f, 185.19f), ion = FLUORINE_19_ION_7),
            FLUORINE_19_ION_5       to Details (type = ElementType.Atom, symbol = "¹⁹F⁵⁺",     label = "Fluorine (¹⁹F⁵⁺)",     mass = 19f, e = 4, p = 9, n = 10,    description = "Фтор",       recombinationElement = FLUORINE_19_ION_4, energyLevels = listOf(22.93f, 107.86f, 157.17f), ion = FLUORINE_19_ION_6),
            FLUORINE_19_ION_4       to Details (type = ElementType.Atom, symbol = "¹⁹F⁴⁺",     label = "Fluorine (¹⁹F⁴⁺)",     mass = 19f, e = 5, p = 9, n = 10,    description = "Фтор",       recombinationElement = FLUORINE_19_ION_3, energyLevels = listOf(11.16f, 27.28f, 114.25f), ion = FLUORINE_19_ION_5),
            FLUORINE_19_ION_3       to Details (type = ElementType.Atom, symbol = "¹⁹F³⁺",     label = "Fluorine (¹⁹F³⁺)",     mass = 19f, e = 6, p = 9, n = 10,    description = "Фтор",       recombinationElement = FLUORINE_19_ION_2, energyLevels = listOf(2.76f, 17.36f, 87.14f),   ion = FLUORINE_19_ION_4),
            FLUORINE_19_ION_2       to Details (type = ElementType.Atom, symbol = "¹⁹F²⁺",     label = "Fluorine (¹⁹F²⁺)",     mass = 19f, e = 7, p = 9, n = 10,    description = "Фтор",       recombinationElement = FLUORINE_19_ION_1, energyLevels = listOf(4.09f, 19.84f, 62.71f),   ion = FLUORINE_19_ION_3),
            FLUORINE_19_ION_1       to Details (type = ElementType.Atom, symbol = "¹⁹F¹⁺",     label = "Fluorine (¹⁹F¹⁺)",     mass = 19f, e = 8, p = 9, n = 10,    description = "Фтор",       recombinationElement = FLUORINE_19,       energyLevels = listOf(2.60f, 12.40f, 34.97f),   ion = FLUORINE_19_ION_2),
            FLUORINE_19             to Details (type = ElementType.Atom, symbol = "¹⁹F",       label = "Fluorine (¹⁹F)",       mass = 19f, e = 9, p = 9, n = 10,    description = "Фтор",                                                 energyLevels = listOf(12.65f, 14.51f, 17.42f),   ion = FLUORINE_19_ION_1),
            NEON_20_ION_10          to Details (type = ElementType.Atom, symbol = "²⁰Ne¹⁰⁺",   label = "Neon (²⁰Ne¹⁰⁺)",       mass = 20f, e = 0, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20_ION_9, alphaGammaResult = MAGNESIUM_24_ION_12),
            NEON_20_ION_9           to Details (type = ElementType.Atom, symbol = "²⁰Ne⁹⁺",    label = "Neon (²⁰Ne⁹⁺)",        mass = 20f, e = 1, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20_ION_8, energyLevels = listOf(1020f, 1209f, 1360f),     ion = NEON_20_ION_10),
            NEON_20_ION_8           to Details (type = ElementType.Atom, symbol = "²⁰Ne⁸⁺",    label = "Neon (²⁰Ne⁸⁺)",        mass = 20f, e = 2, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20_ION_7, energyLevels = listOf(921.5f, 1073.4f, 1195.83f), ion = NEON_20_ION_9),
            NEON_20_ION_7           to Details (type = ElementType.Atom, symbol = "²⁰Ne⁷⁺",    label = "Neon (²⁰Ne⁷⁺)",        mass = 20f, e = 3, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20_ION_6, energyLevels = listOf(16.10f, 142.39f, 239.10f), ion = NEON_20_ION_8),
            NEON_20_ION_6           to Details (type = ElementType.Atom, symbol = "²⁰Ne⁶⁺",    label = "Neon (²⁰Ne⁶⁺)",        mass = 20f, e = 4, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20_ION_5, energyLevels = listOf(26.53f, 131.65f, 207.27f), ion = NEON_20_ION_7),
            NEON_20_ION_5           to Details (type = ElementType.Atom, symbol = "²⁰Ne⁵⁺",    label = "Neon (²⁰Ne⁵⁺)",        mass = 20f, e = 5, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20_ION_4, energyLevels = listOf(13.64f, 32.86f, 157.93f), ion = NEON_20_ION_6),
            NEON_20_ION_4           to Details (type = ElementType.Atom, symbol = "²⁰Ne⁴⁺",    label = "Neon (²⁰Ne⁴⁺)",        mass = 20f, e = 6, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20_ION_3, energyLevels = listOf(3.62f, 20.21f, 126.25f),  ion = NEON_20_ION_5),
            NEON_20_ION_3           to Details (type = ElementType.Atom, symbol = "²⁰Ne³⁺",    label = "Neon (²⁰Ne³⁺)",        mass = 20f, e = 7, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20_ION_2, energyLevels = listOf(5.08f, 24.79f, 97.19f),   ion = NEON_20_ION_4),
            NEON_20_ION_2           to Details (type = ElementType.Atom, symbol = "²⁰Ne²⁺",    label = "Neon (²⁰Ne²⁺)",        mass = 20f, e = 8, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20_ION_1, energyLevels = listOf(3.20f, 14.26f, 63.45f),   ion = NEON_20_ION_3),
            NEON_20_ION_1           to Details (type = ElementType.Atom, symbol = "²⁰Ne¹⁺",    label = "Neon (²⁰Ne¹⁺)",        mass = 20f, e = 9, p = 10, n = 10,   description = "Неон",       recombinationElement = NEON_20,       energyLevels = listOf(26.91f, 28.77f, 40.96f),  ion = NEON_20_ION_2),
            NEON_20                 to Details (type = ElementType.Atom, symbol = "²⁰Ne",      label = "Neon (²⁰Ne)",          mass = 20f, e = 10, p = 10, n = 10,  description = "Неон",                                             energyLevels = listOf(16.85f, 19.66f, 21.56f),   ion = NEON_20_ION_1),
            SODIUM_23_ION_11            to Details (type = ElementType.Atom, symbol = "²³Na¹¹⁺",   label = "Sodium (²³Na¹¹⁺)",     mass = 23f, e = 0, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_10),
            SODIUM_23_ION_10            to Details (type = ElementType.Atom, symbol = "²³Na¹⁰⁺",   label = "Sodium (²³Na¹⁰⁺)",     mass = 23f, e = 1, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_9,   energyLevels = listOf(1234.2f, 1462.89f, 1645.6f), ion = SODIUM_23_ION_11),
            SODIUM_23_ION_9             to Details (type = ElementType.Atom, symbol = "²³Na⁹⁺",    label = "Sodium (²³Na⁹⁺)",      mass = 23f, e = 2, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_8,   energyLevels = listOf(1125f, 1314f, 1465.12f),  ion = SODIUM_23_ION_10),
            SODIUM_23_ION_8             to Details (type = ElementType.Atom, symbol = "²³Na⁸⁺",    label = "Sodium (²³Na⁸⁺)",      mass = 23f, e = 3, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_7,   energyLevels = listOf(18.16f, 177.5f, 299.86f), ion = SODIUM_23_ION_9),
            SODIUM_23_ION_7             to Details (type = ElementType.Atom, symbol = "²³Na⁷⁺",    label = "Sodium (²³Na⁷⁺)",      mass = 23f, e = 4, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_6,   energyLevels = listOf(30.30f, 157.5f, 264.18f), ion = SODIUM_23_ION_8),
            SODIUM_23_ION_6             to Details (type = ElementType.Atom, symbol = "²³Na⁶⁺",    label = "Sodium (²³Na⁶⁺)",      mass = 23f, e = 5, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_5,   energyLevels = listOf(16.4f, 38.0f, 208.50f),   ion = SODIUM_23_ION_7),
            SODIUM_23_ION_5             to Details (type = ElementType.Atom, symbol = "²³Na⁵⁺",    label = "Sodium (²³Na⁵⁺)",      mass = 23f, e = 6, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_4,   energyLevels = listOf(4.27f, 23.8f, 172.18f),   ion = SODIUM_23_ION_6),
            SODIUM_23_ION_4             to Details (type = ElementType.Atom, symbol = "²³Na⁴⁺",    label = "Sodium (²³Na⁴⁺)",      mass = 23f, e = 7, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_3,   energyLevels = listOf(5.99f, 30.0f, 138.39f),   ion = SODIUM_23_ION_5),
            SODIUM_23_ION_3             to Details (type = ElementType.Atom, symbol = "²³Na³⁺",    label = "Sodium (²³Na³⁺)",      mass = 23f, e = 8, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_2,   energyLevels = listOf(3.79f, 17.4f, 98.94f),    ion = SODIUM_23_ION_4),
            SODIUM_23_ION_2             to Details (type = ElementType.Atom, symbol = "²³Na²⁺",    label = "Sodium (²³Na²⁺)",      mass = 23f, e = 9, p = 11, n = 12,   description = "Натрий",     recombinationElement = SODIUM_23_ION_1,   energyLevels = listOf(39.18f, 42.0f, 71.62f),   ion = SODIUM_23_ION_3),
            SODIUM_23_ION_1             to Details (type = ElementType.Atom, symbol = "²³Na¹⁺",    label = "Sodium (²³Na¹⁺)",      mass = 23f, e = 10, p = 11, n = 12,  description = "Натрий",     recombinationElement = SODIUM_23,         energyLevels = listOf(33.3f, 38.5f, 47.29f),    ion = SODIUM_23_ION_2),
            SODIUM_23                   to Details (type = ElementType.Atom, symbol = "²³Na",      label = "Sodium (²³Na)",        mass = 23f, e = 11, p = 11, n = 12,  description = "Натрий",                                               energyLevels = listOf(2.10f, 3.75f, 5.14f),     ion = SODIUM_23_ION_1),
            MAGNESIUM_24_ION_12     to Details (type = ElementType.Atom, symbol = "²⁴Mg¹²⁺",   label = "Magnesium (²⁴Mg¹²⁺)",  mass = 24f, e = 0, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_11, alphaGammaResult = SILICON_28_ION_14),
            MAGNESIUM_24_ION_11     to Details (type = ElementType.Atom, symbol = "²⁴Mg¹¹⁺",   label = "Magnesium (²⁴Mg¹¹⁺)",  mass = 24f, e = 1, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_10, energyLevels = listOf(1468.8f, 1740.96f, 1958.4f), ion = MAGNESIUM_24_ION_12),
            MAGNESIUM_24_ION_10     to Details (type = ElementType.Atom, symbol = "²⁴Mg¹⁰⁺",   label = "Magnesium (²⁴Mg¹⁰⁺)",  mass = 24f, e = 2, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_9,  energyLevels = listOf(1350.4f, 1579f, 1761.80f),  ion = MAGNESIUM_24_ION_11),
            MAGNESIUM_24_ION_9      to Details (type = ElementType.Atom, symbol = "²⁴Mg⁹⁺",    label = "Magnesium (²⁴Mg⁹⁺)",   mass = 24f, e = 3, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_8,  energyLevels = listOf(20.3f, 217f, 367.49f),    ion = MAGNESIUM_24_ION_10),
            MAGNESIUM_24_ION_8      to Details (type = ElementType.Atom, symbol = "²⁴Mg⁸⁺",    label = "Magnesium (²⁴Mg⁸⁺)",   mass = 24f, e = 4, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_7,  energyLevels = listOf(34f, 198f, 327.99f),      ion = MAGNESIUM_24_ION_9),
            MAGNESIUM_24_ION_7      to Details (type = ElementType.Atom, symbol = "²⁴Mg⁷⁺",    label = "Magnesium (²⁴Mg⁷⁺)",   mass = 24f, e = 5, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_6,  energyLevels = listOf(21f, 45f, 265.93f),       ion = MAGNESIUM_24_ION_8),
            MAGNESIUM_24_ION_6      to Details (type = ElementType.Atom, symbol = "²⁴Mg⁶⁺",    label = "Magnesium (²⁴Mg⁶⁺)",   mass = 24f, e = 6, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_5,  energyLevels = listOf(4.7f, 28f, 224.94f),      ion = MAGNESIUM_24_ION_7),
            MAGNESIUM_24_ION_5      to Details (type = ElementType.Atom, symbol = "²⁴Mg⁵⁺",    label = "Magnesium (²⁴Mg⁵⁺)",   mass = 24f, e = 7, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_4,  energyLevels = listOf(6.0f, 35f, 186.76f),      ion = MAGNESIUM_24_ION_6),
            MAGNESIUM_24_ION_4      to Details (type = ElementType.Atom, symbol = "²⁴Mg⁴⁺",    label = "Magnesium (²⁴Mg⁴⁺)",   mass = 24f, e = 8, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_3,  energyLevels = listOf(4.7f, 20f, 141.27f),      ion = MAGNESIUM_24_ION_5),
            MAGNESIUM_24_ION_3      to Details (type = ElementType.Atom, symbol = "²⁴Mg³⁺",    label = "Magnesium (²⁴Mg³⁺)",   mass = 24f, e = 9, p = 12, n = 12,   description = "Магний",     recombinationElement = MAGNESIUM_24_ION_2,  energyLevels = listOf(50f, 53f, 109.27f),       ion = MAGNESIUM_24_ION_4),
            MAGNESIUM_24_ION_2      to Details (type = ElementType.Atom, symbol = "²⁴Mg²⁺",    label = "Magnesium (²⁴Mg²⁺)",   mass = 24f, e = 10, p = 12, n = 12,  description = "Магний",     recombinationElement = MAGNESIUM_24_ION_1,  energyLevels = listOf(50f, 60f, 80.14f),        ion = MAGNESIUM_24_ION_3),
            MAGNESIUM_24_ION_1      to Details (type = ElementType.Atom, symbol = "²⁴Mg¹⁺",    label = "Magnesium (²⁴Mg¹⁺)",   mass = 24f, e = 11, p = 12, n = 12,  description = "Магний",     recombinationElement = MAGNESIUM_24,        energyLevels = listOf(4.43f, 9.9f, 15.03f),     ion = MAGNESIUM_24_ION_2),
            MAGNESIUM_24            to Details (type = ElementType.Atom, symbol = "²⁴Mg",      label = "Magnesium (²⁴Mg)",     mass = 24f, e = 12, p = 12, n = 12,  description = "Магний",                                                 energyLevels = listOf(4.35f, 6.43f, 7.65f),     ion = MAGNESIUM_24_ION_1),
            SILICON_28_ION_14       to Details (type = ElementType.Atom, symbol = "²⁸Si¹⁴⁺",   label = "Silicon (²⁸Si¹⁴⁺)",    mass = 28f, e = 0, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_13,   alphaGammaResult = SULFUR_32_ION_16),
            SILICON_28_ION_13       to Details (type = ElementType.Atom, symbol = "²⁸Si¹³⁺",   label = "Silicon (²⁸Si¹³⁺)",    mass = 28f, e = 1, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_12,   energyLevels = listOf(1999.2f, 2369.64f, 2665.6f), ion = SILICON_28_ION_14),
            SILICON_28_ION_12       to Details (type = ElementType.Atom, symbol = "²⁸Si¹²⁺",   label = "Silicon (²⁸Si¹²⁺)",    mass = 28f, e = 2, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_11,   energyLevels = listOf(1863.8f, 2183f, 2438.40f), ion = SILICON_28_ION_13),
            SILICON_28_ION_11       to Details (type = ElementType.Atom, symbol = "²⁸Si¹¹⁺",   label = "Silicon (²⁸Si¹¹⁺)",    mass = 28f, e = 3, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_10,   energyLevels = listOf(24f, 318f, 523.45f),      ion = SILICON_28_ION_12),
            SILICON_28_ION_10       to Details (type = ElementType.Atom, symbol = "²⁸Si¹⁰⁺",   label = "Silicon (²⁸Si¹⁰⁺)",    mass = 28f, e = 4, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_9,    energyLevels = listOf(43f, 290f, 476.07f),      ion = SILICON_28_ION_11),
            SILICON_28_ION_9        to Details (type = ElementType.Atom, symbol = "²⁸Si⁹⁺",    label = "Silicon (²⁸Si⁹⁺)",     mass = 28f, e = 5, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_8,    energyLevels = listOf(30f, 60f, 401.4f),        ion = SILICON_28_ION_10),
            SILICON_28_ION_8        to Details (type = ElementType.Atom, symbol = "²⁸Si⁸⁺",    label = "Silicon (²⁸Si⁸⁺)",     mass = 28f, e = 6, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_7,    energyLevels = listOf(6.4f, 35f, 351.10f),      ion = SILICON_28_ION_9),
            SILICON_28_ION_7        to Details (type = ElementType.Atom, symbol = "²⁸Si⁷⁺",    label = "Silicon (²⁸Si⁷⁺)",     mass = 28f, e = 7, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_6,    energyLevels = listOf(9f, 50f, 303.17f),        ion = SILICON_28_ION_8),
            SILICON_28_ION_6        to Details (type = ElementType.Atom, symbol = "²⁸Si⁶⁺",    label = "Silicon (²⁸Si⁶⁺)",     mass = 28f, e = 8, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_5,    energyLevels = listOf(6.9f, 24f, 246.32f),      ion = SILICON_28_ION_7),
            SILICON_28_ION_5        to Details (type = ElementType.Atom, symbol = "²⁸Si⁵⁺",    label = "Silicon (²⁸Si⁵⁺)",     mass = 28f, e = 9, p = 14, n = 14,   description = "Кремний",    recombinationElement = SILICON_28_ION_4,    energyLevels = listOf(75f, 78f, 205.27f),       ion = SILICON_28_ION_6),
            SILICON_28_ION_4        to Details (type = ElementType.Atom, symbol = "²⁸Si⁴⁺",    label = "Silicon (²⁸Si⁴⁺)",     mass = 28f, e = 10, p = 14, n = 14,  description = "Кремний",    recombinationElement = SILICON_28_ION_3,    energyLevels = listOf(85f, 100f, 166.77f),      ion = SILICON_28_ION_5),
            SILICON_28_ION_3        to Details (type = ElementType.Atom, symbol = "²⁸Si³⁺",    label = "Silicon (²⁸Si³⁺)",     mass = 28f, e = 11, p = 14, n = 14,  description = "Кремний",    recombinationElement = SILICON_28_ION_2,    energyLevels = listOf(8.81f, 27f, 45.14f),      ion = SILICON_28_ION_4),
            SILICON_28_ION_2        to Details (type = ElementType.Atom, symbol = "²⁸Si²⁺",    label = "Silicon (²⁸Si²⁺)",     mass = 28f, e = 12, p = 14, n = 14,  description = "Кремний",    recombinationElement = SILICON_28_ION_1,    energyLevels = listOf(10.28f, 19f, 33.49f),     ion = SILICON_28_ION_3),
            SILICON_28_ION_1        to Details (type = ElementType.Atom, symbol = "²⁸Si¹⁺",    label = "Silicon (²⁸Si¹⁺)",     mass = 28f, e = 13, p = 14, n = 14,  description = "Кремний",    recombinationElement = SILICON_28,          energyLevels = listOf(5.31f, 8.12f, 16.35f),    ion = SILICON_28_ION_2),
            SILICON_28              to Details (type = ElementType.Atom, symbol = "²⁸Si",      label = "Silicon (²⁸Si)",       mass = 28f, e = 14, p = 14, n = 14,  description = "Кремний",                                                  energyLevels = listOf(0.78f, 4.92f, 8.15f),     ion = SILICON_28_ION_1),
            PHOSPHORUS_31_ION_15    to Details (type = ElementType.Atom, symbol = "³¹P¹⁵⁺",    label = "Phosphorus (³¹P¹⁵⁺)",  mass = 31f, e = 0, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_14),
            PHOSPHORUS_31_ION_14    to Details (type = ElementType.Atom, symbol = "³¹P¹⁴⁺",    label = "Phosphorus (³¹P¹⁴⁺)",  mass = 31f, e = 1, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_13, energyLevels = listOf(2295f, 2720.25f, 3060f),   ion = PHOSPHORUS_31_ION_15),
            PHOSPHORUS_31_ION_13    to Details (type = ElementType.Atom, symbol = "³¹P¹³⁺",    label = "Phosphorus (³¹P¹³⁺)",  mass = 31f, e = 2, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_12, energyLevels = listOf(2150.5f, 2520.7f, 2816.91f), ion = PHOSPHORUS_31_ION_14),
            PHOSPHORUS_31_ION_12    to Details (type = ElementType.Atom, symbol = "³¹P¹²⁺",    label = "Phosphorus (³¹P¹²⁺)",  mass = 31f, e = 3, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_11, energyLevels = listOf(26.0f, 356.4f, 611.74f),   ion = PHOSPHORUS_31_ION_13),
            PHOSPHORUS_31_ION_11    to Details (type = ElementType.Atom, symbol = "³¹P¹¹⁺",    label = "Phosphorus (³¹P¹¹⁺)",  mass = 31f, e = 4, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_10, energyLevels = listOf(48f, 330f, 560.42f),       ion = PHOSPHORUS_31_ION_12),
            PHOSPHORUS_31_ION_10    to Details (type = ElementType.Atom, symbol = "³¹P¹⁰⁺",    label = "Phosphorus (³¹P¹⁰⁺)",  mass = 31f, e = 5, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_9,  energyLevels = listOf(35f, 70f, 479.41f),        ion = PHOSPHORUS_31_ION_11),
            PHOSPHORUS_31_ION_9     to Details (type = ElementType.Atom, symbol = "³¹P⁹⁺",     label = "Phosphorus (³¹P⁹⁺)",   mass = 31f, e = 6, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_8,  energyLevels = listOf(7.2f, 42f, 424.4f),        ion = PHOSPHORUS_31_ION_10),
            PHOSPHORUS_31_ION_8     to Details (type = ElementType.Atom, symbol = "³¹P⁸⁺",     label = "Phosphorus (³¹P⁸⁺)",   mass = 31f, e = 7, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_7,  energyLevels = listOf(10f, 58f, 372.13f),        ion = PHOSPHORUS_31_ION_9),
            PHOSPHORUS_31_ION_7     to Details (type = ElementType.Atom, symbol = "³¹P⁷⁺",     label = "Phosphorus (³¹P⁷⁺)",   mass = 31f, e = 8, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_6,  energyLevels = listOf(7.5f, 28f, 309.6f),        ion = PHOSPHORUS_31_ION_8),
            PHOSPHORUS_31_ION_6     to Details (type = ElementType.Atom, symbol = "³¹P⁶⁺",     label = "Phosphorus (³¹P⁶⁺)",   mass = 31f, e = 9, p = 15, n = 16,   description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_5,  energyLevels = listOf(88f, 92f, 263.57f),        ion = PHOSPHORUS_31_ION_7),
            PHOSPHORUS_31_ION_5     to Details (type = ElementType.Atom, symbol = "³¹P⁵⁺",     label = "Phosphorus (³¹P⁵⁺)",   mass = 31f, e = 10, p = 15, n = 16,  description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_4,  energyLevels = listOf(100f, 120f, 220.43f),      ion = PHOSPHORUS_31_ION_6),
            PHOSPHORUS_31_ION_4     to Details (type = ElementType.Atom, symbol = "³¹P⁴⁺",     label = "Phosphorus (³¹P⁴⁺)",   mass = 31f, e = 11, p = 15, n = 16,  description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_3,  energyLevels = listOf(11.09f, 35f, 65.03f),      ion = PHOSPHORUS_31_ION_5),
            PHOSPHORUS_31_ION_3     to Details (type = ElementType.Atom, symbol = "³¹P³⁺",     label = "Phosphorus (³¹P³⁺)",   mass = 31f, e = 12, p = 15, n = 16,  description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_2,  energyLevels = listOf(13.05f, 24f, 51.44f),      ion = PHOSPHORUS_31_ION_4),
            PHOSPHORUS_31_ION_2     to Details (type = ElementType.Atom, symbol = "³¹P²⁺",     label = "Phosphorus (³¹P²⁺)",   mass = 31f, e = 13, p = 15, n = 16,  description = "Фосфор",     recombinationElement = PHOSPHORUS_31_ION_1,  energyLevels = listOf(8.7f, 11.78f, 30.20f),     ion = PHOSPHORUS_31_ION_3),
            PHOSPHORUS_31_ION_1     to Details (type = ElementType.Atom, symbol = "³¹P¹⁺",     label = "Phosphorus (³¹P¹⁺)",   mass = 31f, e = 14, p = 15, n = 16,  description = "Фосфор",     recombinationElement = PHOSPHORUS_31,        energyLevels = listOf(1.10f, 7.07f, 19.77f),     ion = PHOSPHORUS_31_ION_2),
            PHOSPHORUS_31           to Details (type = ElementType.Atom, symbol = "³¹P",       label = "Phosphorus (³¹P)",     mass = 31f, e = 15, p = 15, n = 16,  description = "Фосфор",                                                  energyLevels = listOf(1.41f, 6.94f, 10.49f),     ion = PHOSPHORUS_31_ION_1),
            SULFUR_31_ION_16        to Details (type = ElementType.Atom, symbol = "³¹S¹⁶⁺",    label = "Sulfur (³¹S¹⁶⁺)",      mass = 31f, e = 0, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_15, betaPlusDecayResult = PHOSPHORUS_31_ION_15),
            SULFUR_31_ION_15        to Details (type = ElementType.Atom, symbol = "³¹S¹⁵⁺",    label = "Sulfur (³¹S¹⁵⁺)",      mass = 31f, e = 1, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_14, energyLevels = listOf(2611.2f, 3095.04f, 3481.6f), ion = SULFUR_31_ION_16),
            SULFUR_31_ION_14        to Details (type = ElementType.Atom, symbol = "³¹S¹⁴⁺",    label = "Sulfur (³¹S¹⁴⁺)",      mass = 31f, e = 2, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_13, energyLevels = listOf(2458.78f, 2883.78f, 3223.78f), ion = SULFUR_31_ION_15),
            SULFUR_31_ION_13        to Details (type = ElementType.Atom, symbol = "³¹S¹³⁺",    label = "Sulfur (³¹S¹³⁺)",      mass = 31f, e = 3, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_12, energyLevels = listOf(28f, 410.8f, 707.01f),     ion = SULFUR_31_ION_14),
            SULFUR_31_ION_12        to Details (type = ElementType.Atom, symbol = "³¹S¹²⁺",    label = "Sulfur (³¹S¹²⁺)",      mass = 31f, e = 4, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_11, energyLevels = listOf(52f, 380f, 652.20f),       ion = SULFUR_31_ION_13),
            SULFUR_31_ION_11        to Details (type = ElementType.Atom, symbol = "³¹S¹¹⁺",    label = "Sulfur (³¹S¹¹⁺)",      mass = 31f, e = 5, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_10, energyLevels = listOf(40f, 80f, 564.66f),        ion = SULFUR_31_ION_12),
            SULFUR_31_ION_10        to Details (type = ElementType.Atom, symbol = "³¹S¹⁰⁺",    label = "Sulfur (³¹S¹⁰⁺)",      mass = 31f, e = 6, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_9,  energyLevels = listOf(8f, 50f, 504.55f),         ion = SULFUR_31_ION_11),
            SULFUR_31_ION_9         to Details (type = ElementType.Atom, symbol = "³¹S⁹⁺",     label = "Sulfur (³¹S⁹⁺)",       mass = 31f, e = 7, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_8,  energyLevels = listOf(11f, 67f, 446.7f),         ion = SULFUR_31_ION_10),
            SULFUR_31_ION_8         to Details (type = ElementType.Atom, symbol = "³¹S⁸⁺",     label = "Sulfur (³¹S⁸⁺)",       mass = 31f, e = 8, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_7,  energyLevels = listOf(8.5f, 32f, 379.84f),       ion = SULFUR_31_ION_9),
            SULFUR_31_ION_7         to Details (type = ElementType.Atom, symbol = "³¹S⁷⁺",     label = "Sulfur (³¹S⁷⁺)",       mass = 31f, e = 9, p = 16, n = 15,   description = "Сера",       recombinationElement = SULFUR_31_ION_6,  energyLevels = listOf(100f, 105f, 328.79f),      ion = SULFUR_31_ION_8),
            SULFUR_31_ION_6         to Details (type = ElementType.Atom, symbol = "³¹S⁶⁺",     label = "Sulfur (³¹S⁶⁺)",       mass = 31f, e = 10, p = 16, n = 15,  description = "Сера",       recombinationElement = SULFUR_31_ION_5,  energyLevels = listOf(115f, 140f, 280.95f),      ion = SULFUR_31_ION_7),
            SULFUR_31_ION_5         to Details (type = ElementType.Atom, symbol = "³¹S⁵⁺",     label = "Sulfur (³¹S⁵⁺)",       mass = 31f, e = 11, p = 16, n = 15,  description = "Сера",       recombinationElement = SULFUR_31_ION_4,  energyLevels = listOf(13.28f, 45f, 88.05f),      ion = SULFUR_31_ION_6),
            SULFUR_31_ION_4         to Details (type = ElementType.Atom, symbol = "³¹S⁴⁺",     label = "Sulfur (³¹S⁴⁺)",       mass = 31f, e = 12, p = 16, n = 15,  description = "Сера",       recombinationElement = SULFUR_31_ION_3,  energyLevels = listOf(15.76f, 28f, 72.59f),      ion = SULFUR_31_ION_5),
            SULFUR_31_ION_3         to Details (type = ElementType.Atom, symbol = "³¹S³⁺",     label = "Sulfur (³¹S³⁺)",       mass = 31f, e = 13, p = 16, n = 15,  description = "Сера",       recombinationElement = SULFUR_31_ION_2,  energyLevels = listOf(8.79f, 11.28f, 47.22f),    ion = SULFUR_31_ION_4),
            SULFUR_31_ION_2         to Details (type = ElementType.Atom, symbol = "³¹S²⁺",     label = "Sulfur (³¹S²⁺)",       mass = 31f, e = 14, p = 16, n = 15,  description = "Сера",       recombinationElement = SULFUR_31_ION_1,  energyLevels = listOf(1.40f, 8.89f, 34.86f),     ion = SULFUR_31_ION_3),
            SULFUR_31_ION_1         to Details (type = ElementType.Atom, symbol = "³¹S¹⁺",     label = "Sulfur (³¹S¹⁺)",       mass = 31f, e = 15, p = 16, n = 15,  description = "Сера",       recombinationElement = SULFUR_31,        energyLevels = listOf(1.84f, 9.17f, 23.34f),     ion = SULFUR_31_ION_2),
            SULFUR_31               to Details (type = ElementType.Atom, symbol = "³¹S",      label = "Sulfur (³¹S)",         mass = 31f, e = 16, p = 16, n = 15,  description = "Сера",                                                  energyLevels = listOf(1.145f, 6.45f, 10.36f),    ion = SULFUR_31_ION_1),
            SULFUR_32_ION_16        to Details (type = ElementType.Atom, symbol = "³²S¹⁶⁺",    label = "Sulfur (³²S¹⁶⁺)",      mass = 32f, e = 0, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_15, alphaGammaResult = ARGON_36_ION_18),
            SULFUR_32_ION_15        to Details (type = ElementType.Atom, symbol = "³²S¹⁵⁺",    label = "Sulfur (³²S¹⁵⁺)",      mass = 32f, e = 1, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_14, energyLevels = listOf(2611.2f, 3095.04f, 3481.6f), ion = SULFUR_32_ION_16),
            SULFUR_32_ION_14        to Details (type = ElementType.Atom, symbol = "³²S¹⁴⁺",    label = "Sulfur (³²S¹⁴⁺)",      mass = 32f, e = 2, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_13, energyLevels = listOf(2458.78f, 2883.78f, 3223.78f), ion = SULFUR_32_ION_15),
            SULFUR_32_ION_13        to Details (type = ElementType.Atom, symbol = "³²S¹³⁺",    label = "Sulfur (³²S¹³⁺)",      mass = 32f, e = 3, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_12, energyLevels = listOf(28f, 410.8f, 707.01f),     ion = SULFUR_32_ION_14),
            SULFUR_32_ION_12        to Details (type = ElementType.Atom, symbol = "³²S¹²⁺",    label = "Sulfur (³²S¹²⁺)",      mass = 32f, e = 4, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_11, energyLevels = listOf(52f, 380f, 652.20f),       ion = SULFUR_32_ION_13),
            SULFUR_32_ION_11        to Details (type = ElementType.Atom, symbol = "³²S¹¹⁺",    label = "Sulfur (³²S¹¹⁺)",      mass = 32f, e = 5, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_10, energyLevels = listOf(40f, 80f, 564.66f),        ion = SULFUR_32_ION_12),
            SULFUR_32_ION_10        to Details (type = ElementType.Atom, symbol = "³²S¹⁰⁺",    label = "Sulfur (³²S¹⁰⁺)",      mass = 32f, e = 6, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_9,  energyLevels = listOf(8f, 50f, 504.55f),         ion = SULFUR_32_ION_11),
            SULFUR_32_ION_9         to Details (type = ElementType.Atom, symbol = "³²S⁹⁺",     label = "Sulfur (³²S⁹⁺)",       mass = 32f, e = 7, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_8,  energyLevels = listOf(11f, 67f, 446.7f),         ion = SULFUR_32_ION_10),
            SULFUR_32_ION_8         to Details (type = ElementType.Atom, symbol = "³²S⁸⁺",     label = "Sulfur (³²S⁸⁺)",       mass = 32f, e = 8, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_7,  energyLevels = listOf(8.5f, 32f, 379.84f),       ion = SULFUR_32_ION_9),
            SULFUR_32_ION_7         to Details (type = ElementType.Atom, symbol = "³²S⁷⁺",     label = "Sulfur (³²S⁷⁺)",       mass = 32f, e = 9, p = 16, n = 16,   description = "Сера",       recombinationElement = SULFUR_32_ION_6,  energyLevels = listOf(100f, 105f, 328.79f),      ion = SULFUR_32_ION_8),
            SULFUR_32_ION_6         to Details (type = ElementType.Atom, symbol = "³²S⁶⁺",     label = "Sulfur (³²S⁶⁺)",       mass = 32f, e = 10, p = 16, n = 16,  description = "Сера",       recombinationElement = SULFUR_32_ION_5,  energyLevels = listOf(115f, 140f, 280.95f),      ion = SULFUR_32_ION_7),
            SULFUR_32_ION_5         to Details (type = ElementType.Atom, symbol = "³²S⁵⁺",     label = "Sulfur (³²S⁵⁺)",       mass = 32f, e = 11, p = 16, n = 16,  description = "Сера",       recombinationElement = SULFUR_32_ION_4,  energyLevels = listOf(13.28f, 45f, 88.05f),      ion = SULFUR_32_ION_6),
            SULFUR_32_ION_4         to Details (type = ElementType.Atom, symbol = "³²S⁴⁺",     label = "Sulfur (³²S⁴⁺)",       mass = 32f, e = 12, p = 16, n = 16,  description = "Сера",       recombinationElement = SULFUR_32_ION_3,  energyLevels = listOf(15.76f, 28f, 72.59f),      ion = SULFUR_32_ION_5),
            SULFUR_32_ION_3         to Details (type = ElementType.Atom, symbol = "³²S³⁺",     label = "Sulfur (³²S³⁺)",       mass = 32f, e = 13, p = 16, n = 16,  description = "Сера",       recombinationElement = SULFUR_32_ION_2,  energyLevels = listOf(8.79f, 11.28f, 47.22f),    ion = SULFUR_32_ION_4),
            SULFUR_32_ION_2         to Details (type = ElementType.Atom, symbol = "³²S²⁺",     label = "Sulfur (³²S²⁺)",       mass = 32f, e = 14, p = 16, n = 16,  description = "Сера",       recombinationElement = SULFUR_32_ION_1,  energyLevels = listOf(1.40f, 8.89f, 34.86f),     ion = SULFUR_32_ION_3),
            SULFUR_32_ION_1         to Details (type = ElementType.Atom, symbol = "³²S¹⁺",     label = "Sulfur (³²S¹⁺)",       mass = 32f, e = 15, p = 16, n = 16,  description = "Сера",       recombinationElement = SULFUR_32,        energyLevels = listOf(1.84f, 9.17f, 23.34f),     ion = SULFUR_32_ION_2),
            SULFUR_32               to Details (type = ElementType.Atom, symbol = "³²S",      label = "Sulfur (³²S)",         mass = 32f, e = 16, p = 16, n = 16,  description = "Сера",                                                  energyLevels = listOf(1.145f, 6.45f, 10.36f),    ion = SULFUR_32_ION_1),
            ARGON_36_ION_18         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹⁸⁺",   label = "Argon (³⁶Ar¹⁸⁺)",      mass = 36f, e = 0, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_17, alphaGammaResult = CALCIUM_40_ION_20),
            ARGON_36_ION_17         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹⁷⁺",   label = "Argon (³⁶Ar¹⁷⁺)",      mass = 36f, e = 1, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_16, energyLevels = listOf(3304.8f, 3917.16f, 4406.4f), ion = ARGON_36_ION_18),
            ARGON_36_ION_16         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹⁶⁺",   label = "Argon (³⁶Ar¹⁶⁺)",      mass = 36f, e = 2, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_15, energyLevels = listOf(3138.06f, 3683.95f, 4120.66f), ion = ARGON_36_ION_17),
            ARGON_36_ION_15         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹⁵⁺",   label = "Argon (³⁶Ar¹⁵⁺)",      mass = 36f, e = 3, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_14, energyLevels = listOf(32f, 531.6f, 918.375f),   ion = ARGON_36_ION_16),
            ARGON_36_ION_14         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹⁴⁺",   label = "Argon (³⁶Ar¹⁴⁺)",      mass = 36f, e = 4, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_13, energyLevels = listOf(62f, 440f, 854.77f),      ion = ARGON_36_ION_15),
            ARGON_36_ION_13         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹³⁺",   label = "Argon (³⁶Ar¹³⁺)",      mass = 36f, e = 5, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_12, energyLevels = listOf(45f, 90f, 755.74f),       ion = ARGON_36_ION_14),
            ARGON_36_ION_12         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹²⁺",   label = "Argon (³⁶Ar¹²⁺)",      mass = 36f, e = 6, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_11, energyLevels = listOf(9f, 60f, 686.10f),        ion = ARGON_36_ION_13),
            ARGON_36_ION_11         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹¹⁺",   label = "Argon (³⁶Ar¹¹⁺)",      mass = 36f, e = 7, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_10, energyLevels = listOf(12f, 75f, 618.26f),       ion = ARGON_36_ION_12),
            ARGON_36_ION_10         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹⁰⁺",   label = "Argon (³⁶Ar¹⁰⁺)",      mass = 36f, e = 8, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_9,  energyLevels = listOf(10f, 38f, 539.0f),        ion = ARGON_36_ION_11),
            ARGON_36_ION_9          to Details (type = ElementType.Atom, symbol = "³⁶Ar⁹⁺",    label = "Argon (³⁶Ar⁹⁺)",       mass = 36f, e = 9, p = 18, n = 18,   description = "Аргон",      recombinationElement = ARGON_36_ION_8,  energyLevels = listOf(115f, 120f, 478.69f),     ion = ARGON_36_ION_10),
            ARGON_36_ION_8          to Details (type = ElementType.Atom, symbol = "³⁶Ar⁸⁺",    label = "Argon (³⁶Ar⁸⁺)",       mass = 36f, e = 10, p = 18, n = 18,  description = "Аргон",      recombinationElement = ARGON_36_ION_7,  energyLevels = listOf(140f, 170f, 422.45f),     ion = ARGON_36_ION_9),
            ARGON_36_ION_7          to Details (type = ElementType.Atom, symbol = "³⁶Ar⁷⁺",    label = "Argon (³⁶Ar⁷⁺)",       mass = 36f, e = 11, p = 18, n = 18,  description = "Аргон",      recombinationElement = ARGON_36_ION_6,  energyLevels = listOf(17.71f, 90f, 143.46f),    ion = ARGON_36_ION_8),
            ARGON_36_ION_6          to Details (type = ElementType.Atom, symbol = "³⁶Ar⁶⁺",    label = "Argon (³⁶Ar⁶⁺)",       mass = 36f, e = 12, p = 18, n = 18,  description = "Аргон",      recombinationElement = ARGON_36_ION_5,  energyLevels = listOf(21.20f, 75f, 124.41f),    ion = ARGON_36_ION_7),
            ARGON_36_ION_5          to Details (type = ElementType.Atom, symbol = "³⁶Ar⁵⁺",    label = "Argon (³⁶Ar⁵⁺)",       mass = 36f, e = 13, p = 18, n = 18,  description = "Аргон",      recombinationElement = ARGON_36_ION_4,  energyLevels = listOf(14f, 18f, 91.29f),        ion = ARGON_36_ION_6),
            ARGON_36_ION_4          to Details (type = ElementType.Atom, symbol = "³⁶Ar⁴⁺",    label = "Argon (³⁶Ar⁴⁺)",       mass = 36f, e = 14, p = 18, n = 18,  description = "Аргон",      recombinationElement = ARGON_36_ION_3,  energyLevels = listOf(1.93f, 22f, 74.84f),      ion = ARGON_36_ION_5),
            ARGON_36_ION_3          to Details (type = ElementType.Atom, symbol = "³⁶Ar³⁺",    label = "Argon (³⁶Ar³⁺)",       mass = 36f, e = 15, p = 18, n = 18,  description = "Аргон",      recombinationElement = ARGON_36_ION_2,  energyLevels = listOf(2.62f, 20.80f, 59.58f),   ion = ARGON_36_ION_4),
            ARGON_36_ION_2          to Details (type = ElementType.Atom, symbol = "³⁶Ar²⁺",    label = "Argon (³⁶Ar²⁺)",       mass = 36f, e = 16, p = 18, n = 18,  description = "Аргон",      recombinationElement = ARGON_36_ION_1,  energyLevels = listOf(1.74f, 16.1f, 40.74f),    ion = ARGON_36_ION_3),
            ARGON_36_ION_1          to Details (type = ElementType.Atom, symbol = "³⁶Ar¹⁺",    label = "Argon (³⁶Ar¹⁺)",       mass = 36f, e = 17, p = 18, n = 18,  description = "Аргон",      recombinationElement = ARGON_36,        energyLevels = listOf(16f, 18f, 27.63f),        ion = ARGON_36_ION_2),
            ARGON_36                to Details (type = ElementType.Atom, symbol = "³⁶Ar",     label = "Argon (³⁶Ar)",         mass = 36f, e = 18, p = 18, n = 18,  description = "Аргон",                                                  energyLevels = listOf(11.83f, 14.09f, 15.76f),  ion = ARGON_36_ION_1),
            CALCIUM_40_ION_20       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca²⁰⁺",   label = "Calcium (⁴⁰Ca²⁰⁺)",    mass = 40f, e = 0, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_19, alphaGammaResult = TITANIUM_44_ION_22),
            CALCIUM_40_ION_19       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹⁹⁺",   label = "Calcium (⁴⁰Ca¹⁹⁺)",    mass = 40f, e = 1, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_18, energyLevels = listOf(4080f, 4836f, 5440f),     ion = CALCIUM_40_ION_20),
            CALCIUM_40_ION_18       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹⁸⁺",   label = "Calcium (⁴⁰Ca¹⁸⁺)",    mass = 40f, e = 2, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_17, energyLevels = listOf(3901.45f, 4583.35f, 5128.85f), ion = CALCIUM_40_ION_19),
            CALCIUM_40_ION_17       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹⁷⁺",   label = "Calcium (⁴⁰Ca¹⁷⁺)",    mass = 40f, e = 3, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_16, energyLevels = listOf(36f, 668.13f, 1157.73f),  ion = CALCIUM_40_ION_18),
            CALCIUM_40_ION_16       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹⁶⁺",   label = "Calcium (⁴⁰Ca¹⁶⁺)",    mass = 40f, e = 4, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_15, energyLevels = listOf(72f, 510f, 1087.0f),      ion = CALCIUM_40_ION_17),
            CALCIUM_40_ION_15       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹⁵⁺",   label = "Calcium (⁴⁰Ca¹⁵⁺)",    mass = 40f, e = 5, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_14, energyLevels = listOf(55f, 110f, 974.0f),       ion = CALCIUM_40_ION_16),
            CALCIUM_40_ION_14       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹⁴⁺",   label = "Calcium (⁴⁰Ca¹⁴⁺)",    mass = 40f, e = 6, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_13, energyLevels = listOf(10f, 70f, 894.5f),        ion = CALCIUM_40_ION_15),
            CALCIUM_40_ION_13       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹³⁺",   label = "Calcium (⁴⁰Ca¹³⁺)",    mass = 40f, e = 7, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_12, energyLevels = listOf(14f, 85f, 817.6f),        ion = CALCIUM_40_ION_14),
            CALCIUM_40_ION_12       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹²⁺",   label = "Calcium (⁴⁰Ca¹²⁺)",    mass = 40f, e = 8, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_11, energyLevels = listOf(12f, 45f, 726.6f),        ion = CALCIUM_40_ION_13),
            CALCIUM_40_ION_11       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹¹⁺",   label = "Calcium (⁴⁰Ca¹¹⁺)",    mass = 40f, e = 9, p = 20, n = 20,   description = "Кальций",    recombinationElement = CALCIUM_40_ION_10, energyLevels = listOf(135f, 140f, 657.2f),      ion = CALCIUM_40_ION_12),
            CALCIUM_40_ION_10       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹⁰⁺",   label = "Calcium (⁴⁰Ca¹⁰⁺)",    mass = 40f, e = 10, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40_ION_9,  energyLevels = listOf(170f, 200f, 591.6f),      ion = CALCIUM_40_ION_11),
            CALCIUM_40_ION_9        to Details (type = ElementType.Atom, symbol = "⁴⁰Ca⁹⁺",    label = "Calcium (⁴⁰Ca⁹⁺)",     mass = 40f, e = 11, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40_ION_8,  energyLevels = listOf(22.26f, 110f, 211.28f),   ion = CALCIUM_40_ION_10),
            CALCIUM_40_ION_8        to Details (type = ElementType.Atom, symbol = "⁴⁰Ca⁸⁺",    label = "Calcium (⁴⁰Ca⁸⁺)",     mass = 40f, e = 12, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40_ION_7,  energyLevels = listOf(26f, 90f, 188.54f),       ion = CALCIUM_40_ION_9),
            CALCIUM_40_ION_7        to Details (type = ElementType.Atom, symbol = "⁴⁰Ca⁷⁺",    label = "Calcium (⁴⁰Ca⁷⁺)",     mass = 40f, e = 13, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40_ION_6,  energyLevels = listOf(16f, 22f, 147.24f),       ion = CALCIUM_40_ION_8),
            CALCIUM_40_ION_6        to Details (type = ElementType.Atom, symbol = "⁴⁰Ca⁶⁺",    label = "Calcium (⁴⁰Ca⁶⁺)",     mass = 40f, e = 14, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40_ION_5,  energyLevels = listOf(2.3f, 26f, 127.21f),      ion = CALCIUM_40_ION_7),
            CALCIUM_40_ION_5        to Details (type = ElementType.Atom, symbol = "⁴⁰Ca⁵⁺",    label = "Calcium (⁴⁰Ca⁵⁺)",     mass = 40f, e = 15, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40_ION_4,  energyLevels = listOf(3.4f, 25f, 108.78f),      ion = CALCIUM_40_ION_6),
            CALCIUM_40_ION_4        to Details (type = ElementType.Atom, symbol = "⁴⁰Ca⁴⁺",    label = "Calcium (⁴⁰Ca⁴⁺)",     mass = 40f, e = 16, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40_ION_3,  energyLevels = listOf(2.0f, 20f, 84.50f),       ion = CALCIUM_40_ION_5),
            CALCIUM_40_ION_3        to Details (type = ElementType.Atom, symbol = "⁴⁰Ca³⁺",    label = "Calcium (⁴⁰Ca³⁺)",     mass = 40f, e = 17, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40_ION_2,  energyLevels = listOf(22f, 25f, 67.27f),        ion = CALCIUM_40_ION_4),
            CALCIUM_40_ION_2        to Details (type = ElementType.Atom, symbol = "⁴⁰Ca²⁺",    label = "Calcium (⁴⁰Ca²⁺)",     mass = 40f, e = 18, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40_ION_1,  energyLevels = listOf(24.8f, 31.5f, 50.91f),    ion = CALCIUM_40_ION_3),
            CALCIUM_40_ION_1        to Details (type = ElementType.Atom, symbol = "⁴⁰Ca¹⁺",    label = "Calcium (⁴⁰Ca¹⁺)",     mass = 40f, e = 19, p = 20, n = 20,  description = "Кальций",    recombinationElement = CALCIUM_40,        energyLevels = listOf(1.69f, 3.15f, 11.87f),    ion = CALCIUM_40_ION_2),
            CALCIUM_40              to Details (type = ElementType.Atom, symbol = "⁴⁰Ca",     label = "Calcium (⁴⁰Ca)",       mass = 40f, e = 20, p = 20, n = 20,  description = "Кальций",                                                  energyLevels = listOf(2.93f, 4.55f, 6.11f),     ion = CALCIUM_40_ION_1),
            TITANIUM_44_ION_22      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti²²⁺",   label = "Titanium (⁴⁴Ti²²⁺)",   mass = 44f, e = 0, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_21, alphaGammaResult = CHROMIUM_48_ION_24),
            TITANIUM_44_ION_21      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti²¹⁺",   label = "Titanium (⁴⁴Ti²¹⁺)",   mass = 44f, e = 1, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_20, energyLevels = listOf(4936.8f, 5851.56f, 6582.4f), ion = TITANIUM_44_ION_22),
            TITANIUM_44_ION_20      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti²⁰⁺",   label = "Titanium (⁴⁴Ti²⁰⁺)",   mass = 44f, e = 2, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_19, energyLevels = listOf(4749.6f, 5582.6f, 6249.0f),  ion = TITANIUM_44_ION_21),
            TITANIUM_44_ION_19      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹⁹⁺",   label = "Titanium (⁴⁴Ti¹⁹⁺)",   mass = 44f, e = 3, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_18, energyLevels = listOf(40f, 820.6f, 1425.45f),   ion = TITANIUM_44_ION_20),
            TITANIUM_44_ION_18      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹⁸⁺",   label = "Titanium (⁴⁴Ti¹⁸⁺)",   mass = 44f, e = 4, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_17, energyLevels = listOf(82f, 590f, 1346.6f),      ion = TITANIUM_44_ION_19),
            TITANIUM_44_ION_17      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹⁷⁺",   label = "Titanium (⁴⁴Ti¹⁷⁺)",   mass = 44f, e = 5, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_16, energyLevels = listOf(65f, 130f, 1221.4f),      ion = TITANIUM_44_ION_18),
            TITANIUM_44_ION_16      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹⁶⁺",   label = "Titanium (⁴⁴Ti¹⁶⁺)",   mass = 44f, e = 6, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_15, energyLevels = listOf(12f, 85f, 1131.0f),       ion = TITANIUM_44_ION_17),
            TITANIUM_44_ION_15      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹⁵⁺",   label = "Titanium (⁴⁴Ti¹⁵⁺)",   mass = 44f, e = 7, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_14, energyLevels = listOf(17f, 100f, 1043.9f),      ion = TITANIUM_44_ION_16),
            TITANIUM_44_ION_14      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹⁴⁺",   label = "Titanium (⁴⁴Ti¹⁴⁺)",   mass = 44f, e = 8, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_13, energyLevels = listOf(15f, 55f, 940.36f),       ion = TITANIUM_44_ION_15),
            TITANIUM_44_ION_13      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹³⁺",   label = "Titanium (⁴⁴Ti¹³⁺)",   mass = 44f, e = 9, p = 22, n = 22,   description = "Титан",      recombinationElement = TITANIUM_44_ION_12, energyLevels = listOf(165f, 170f, 863.1f),      ion = TITANIUM_44_ION_14),
            TITANIUM_44_ION_12      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹²⁺",   label = "Titanium (⁴⁴Ti¹²⁺)",   mass = 44f, e = 10, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_11, energyLevels = listOf(215f, 250f, 787.84f),     ion = TITANIUM_44_ION_13),
            TITANIUM_44_ION_11      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹¹⁺",   label = "Titanium (⁴⁴Ti¹¹⁺)",   mass = 44f, e = 11, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_10, energyLevels = listOf(28f, 130f, 291.50f),      ion = TITANIUM_44_ION_12),
            TITANIUM_44_ION_10      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹⁰⁺",   label = "Titanium (⁴⁴Ti¹⁰⁺)",   mass = 44f, e = 12, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_9,  energyLevels = listOf(32f, 110f, 265.07f),      ion = TITANIUM_44_ION_11),
            TITANIUM_44_ION_9       to Details (type = ElementType.Atom, symbol = "⁴⁴Ti⁹⁺",    label = "Titanium (⁴⁴Ti⁹⁺)",    mass = 44f, e = 13, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_8,  energyLevels = listOf(19f, 27f, 215.92f),       ion = TITANIUM_44_ION_10),
            TITANIUM_44_ION_8       to Details (type = ElementType.Atom, symbol = "⁴⁴Ti⁸⁺",    label = "Titanium (⁴⁴Ti⁸⁺)",    mass = 44f, e = 14, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_7,  energyLevels = listOf(2.8f, 32f, 192.1f),       ion = TITANIUM_44_ION_9),
            TITANIUM_44_ION_7       to Details (type = ElementType.Atom, symbol = "⁴⁴Ti⁷⁺",    label = "Titanium (⁴⁴Ti⁷⁺)",    mass = 44f, e = 15, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_6,  energyLevels = listOf(4.0f, 30f, 170.4f),       ion = TITANIUM_44_ION_8),
            TITANIUM_44_ION_6       to Details (type = ElementType.Atom, symbol = "⁴⁴Ti⁶⁺",    label = "Titanium (⁴⁴Ti⁶⁺)",    mass = 44f, e = 16, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_5,  energyLevels = listOf(2.6f, 25f, 140.8f),       ion = TITANIUM_44_ION_7),
            TITANIUM_44_ION_5       to Details (type = ElementType.Atom, symbol = "⁴⁴Ti⁵⁺",    label = "Titanium (⁴⁴Ti⁵⁺)",    mass = 44f, e = 17, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_4,  energyLevels = listOf(28f, 33f, 119.5f),        ion = TITANIUM_44_ION_6),
            TITANIUM_44_ION_4       to Details (type = ElementType.Atom, symbol = "⁴⁴Ti⁴⁺",    label = "Titanium (⁴⁴Ti⁴⁺)",    mass = 44f, e = 18, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_3,  energyLevels = listOf(38f, 50f, 99.30f),        ion = TITANIUM_44_ION_5),
            TITANIUM_44_ION_3       to Details (type = ElementType.Atom, symbol = "⁴⁴Ti³⁺",    label = "Titanium (⁴⁴Ti³⁺)",    mass = 44f, e = 19, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_2,  energyLevels = listOf(0.05f, 9.0f, 43.27f),     ion = TITANIUM_44_ION_4),
            TITANIUM_44_ION_2       to Details (type = ElementType.Atom, symbol = "⁴⁴Ti²⁺",    label = "Titanium (⁴⁴Ti²⁺)",    mass = 44f, e = 20, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44_ION_1,  energyLevels = listOf(2.4f, 8.0f, 27.49f),      ion = TITANIUM_44_ION_3),
            TITANIUM_44_ION_1       to Details (type = ElementType.Atom, symbol = "⁴⁴Ti¹⁺",    label = "Titanium (⁴⁴Ti¹⁺)",    mass = 44f, e = 21, p = 22, n = 22,  description = "Титан",      recombinationElement = TITANIUM_44,        energyLevels = listOf(0.05f, 2.72f, 13.58f),    ion = TITANIUM_44_ION_2),
            TITANIUM_44             to Details (type = ElementType.Atom, symbol = "⁴⁴Ti",     label = "Titanium (⁴⁴Ti)",      mass = 44f, e = 22, p = 22, n = 22,  description = "Титан",                                                  energyLevels = listOf(0.05f, 3.4f, 6.83f),      ion = TITANIUM_44_ION_1),
            TITANIUM_48_ION_22      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti²²⁺",   label = "Titanium (⁴⁸Ti²²⁺)",   mass = 48f, e = 0, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_21),
            TITANIUM_48_ION_21      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti²¹⁺",   label = "Titanium (⁴⁸Ti²¹⁺)",   mass = 48f, e = 1, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_20, energyLevels = listOf(4936.8f, 5851.56f, 6582.4f), ion = TITANIUM_48_ION_22),
            TITANIUM_48_ION_20      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti²⁰⁺",   label = "Titanium (⁴⁸Ti²⁰⁺)",   mass = 48f, e = 2, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_19, energyLevels = listOf(4749.6f, 5582.6f, 6249.0f),  ion = TITANIUM_48_ION_21),
            TITANIUM_48_ION_19      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹⁹⁺",   label = "Titanium (⁴⁸Ti¹⁹⁺)",   mass = 48f, e = 3, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_18, energyLevels = listOf(40f, 820.6f, 1425.45f),   ion = TITANIUM_48_ION_20),
            TITANIUM_48_ION_18      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹⁸⁺",   label = "Titanium (⁴⁸Ti¹⁸⁺)",   mass = 48f, e = 4, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_17, energyLevels = listOf(82f, 590f, 1346.6f),      ion = TITANIUM_48_ION_19),
            TITANIUM_48_ION_17      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹⁷⁺",   label = "Titanium (⁴⁸Ti¹⁷⁺)",   mass = 48f, e = 5, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_16, energyLevels = listOf(65f, 130f, 1221.4f),      ion = TITANIUM_48_ION_18),
            TITANIUM_48_ION_16      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹⁶⁺",   label = "Titanium (⁴⁸Ti¹⁶⁺)",   mass = 48f, e = 6, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_15, energyLevels = listOf(12f, 85f, 1131.0f),       ion = TITANIUM_48_ION_17),
            TITANIUM_48_ION_15      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹⁵⁺",   label = "Titanium (⁴⁸Ti¹⁵⁺)",   mass = 48f, e = 7, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_14, energyLevels = listOf(17f, 100f, 1043.9f),      ion = TITANIUM_48_ION_16),
            TITANIUM_48_ION_14      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹⁴⁺",   label = "Titanium (⁴⁸Ti¹⁴⁺)",   mass = 48f, e = 8, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_13, energyLevels = listOf(15f, 55f, 940.36f),       ion = TITANIUM_48_ION_15),
            TITANIUM_48_ION_13      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹³⁺",   label = "Titanium (⁴⁸Ti¹³⁺)",   mass = 48f, e = 9, p = 22, n = 26,   description = "Титан",      recombinationElement = TITANIUM_48_ION_12, energyLevels = listOf(165f, 170f, 863.1f),      ion = TITANIUM_48_ION_14),
            TITANIUM_48_ION_12      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹²⁺",   label = "Titanium (⁴⁸Ti¹²⁺)",   mass = 48f, e = 10, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_11, energyLevels = listOf(215f, 250f, 787.84f),     ion = TITANIUM_48_ION_13),
            TITANIUM_48_ION_11      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹¹⁺",   label = "Titanium (⁴⁸Ti¹¹⁺)",   mass = 48f, e = 11, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_10, energyLevels = listOf(28f, 130f, 291.50f),      ion = TITANIUM_48_ION_12),
            TITANIUM_48_ION_10      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹⁰⁺",   label = "Titanium (⁴⁸Ti¹⁰⁺)",   mass = 48f, e = 12, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_9,  energyLevels = listOf(32f, 110f, 265.07f),      ion = TITANIUM_48_ION_11),
            TITANIUM_48_ION_9       to Details (type = ElementType.Atom, symbol = "⁴⁸Ti⁹⁺",    label = "Titanium (⁴⁸Ti⁹⁺)",    mass = 48f, e = 13, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_8,  energyLevels = listOf(19f, 27f, 215.92f),       ion = TITANIUM_48_ION_10),
            TITANIUM_48_ION_8       to Details (type = ElementType.Atom, symbol = "⁴⁸Ti⁸⁺",    label = "Titanium (⁴⁸Ti⁸⁺)",    mass = 48f, e = 14, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_7,  energyLevels = listOf(2.8f, 32f, 192.1f),       ion = TITANIUM_48_ION_9),
            TITANIUM_48_ION_7       to Details (type = ElementType.Atom, symbol = "⁴⁸Ti⁷⁺",    label = "Titanium (⁴⁸Ti⁷⁺)",    mass = 48f, e = 15, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_6,  energyLevels = listOf(4.0f, 30f, 170.4f),       ion = TITANIUM_48_ION_8),
            TITANIUM_48_ION_6       to Details (type = ElementType.Atom, symbol = "⁴⁸Ti⁶⁺",    label = "Titanium (⁴⁸Ti⁶⁺)",    mass = 48f, e = 16, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_5,  energyLevels = listOf(2.6f, 25f, 140.8f),       ion = TITANIUM_48_ION_7),
            TITANIUM_48_ION_5       to Details (type = ElementType.Atom, symbol = "⁴⁸Ti⁵⁺",    label = "Titanium (⁴⁸Ti⁵⁺)",    mass = 48f, e = 17, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_4,  energyLevels = listOf(28f, 33f, 119.5f),        ion = TITANIUM_48_ION_6),
            TITANIUM_48_ION_4       to Details (type = ElementType.Atom, symbol = "⁴⁸Ti⁴⁺",    label = "Titanium (⁴⁸Ti⁴⁺)",    mass = 48f, e = 18, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_3,  energyLevels = listOf(38f, 50f, 99.30f),        ion = TITANIUM_48_ION_5),
            TITANIUM_48_ION_3       to Details (type = ElementType.Atom, symbol = "⁴⁸Ti³⁺",    label = "Titanium (⁴⁸Ti³⁺)",    mass = 48f, e = 19, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_2,  energyLevels = listOf(0.05f, 9.0f, 43.27f),     ion = TITANIUM_48_ION_4),
            TITANIUM_48_ION_2       to Details (type = ElementType.Atom, symbol = "⁴⁸Ti²⁺",    label = "Titanium (⁴⁸Ti²⁺)",    mass = 48f, e = 20, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48_ION_1,  energyLevels = listOf(2.4f, 8.0f, 27.49f),      ion = TITANIUM_48_ION_3),
            TITANIUM_48_ION_1       to Details (type = ElementType.Atom, symbol = "⁴⁸Ti¹⁺",    label = "Titanium (⁴⁸Ti¹⁺)",    mass = 48f, e = 21, p = 22, n = 26,  description = "Титан",      recombinationElement = TITANIUM_48,        energyLevels = listOf(0.05f, 2.72f, 13.58f),    ion = TITANIUM_48_ION_2),
            TITANIUM_48             to Details (type = ElementType.Atom, symbol = "⁴⁸Ti",     label = "Titanium (⁴⁸Ti)",      mass = 48f, e = 22, p = 22, n = 26,  description = "Титан",                                                   energyLevels = listOf(0.05f, 3.4f, 6.83f),      ion = TITANIUM_48_ION_1),
            VANADIUM_48_ION_23      to Details (type = ElementType.Atom, symbol = "⁴⁸V²³⁺",    label = "Vanadium (⁴⁸V²³⁺)",    mass = 48f, e = 0, p = 23, n = 25,   description = "Ванадий",    betaPlusDecayResult = TITANIUM_48_ION_22),
            CHROMIUM_48_ION_24      to Details (type = ElementType.Atom, symbol = "⁴⁸Cr²⁴⁺",   label = "Chromium (⁴⁸Cr²⁴⁺)",   mass = 48f, e = 0, p = 24, n = 24,   description = "Хром",       betaPlusDecayResult = VANADIUM_48_ION_23, alphaGammaResult = IRON_52_ION_26),
            CHROMIUM_52_ION_24      to Details (type = ElementType.Atom, symbol = "⁵²Cr²⁴⁺",   label = "Chromium (⁵²Cr²⁴⁺)",   mass = 52f, e = 0, p = 24, n = 28,   description = "Хром"),
            MANGANESE_52_ION_25     to Details (type = ElementType.Atom, symbol = "⁵²Mn²⁵⁺",   label = "Manganese (⁵²Mn²⁵⁺)",  mass = 52f, e = 0, p = 25, n = 27,   description = "Марганец",   betaPlusDecayResult = CHROMIUM_52_ION_24),
            IRON_52_ION_26          to Details (type = ElementType.Atom, symbol = "⁵²Fe²⁶⁺",   label = "Iron (⁵²Fe²⁶⁺)",       mass = 52f, e = 0, p = 26, n = 26,   description = "Железо",     betaPlusDecayResult = MANGANESE_52_ION_25, alphaGammaResult = NICKEL_56_ION_28),
            IRON_56_ION_26          to Details (type = ElementType.Atom, symbol = "⁵⁶Fe²⁶⁺",   label = "Iron (⁵⁶Fe²⁶⁺)",       mass = 56f, e = 0, p = 26, n = 30,   description = "Железо"),
            COBALT_56_ION_27        to Details (type = ElementType.Atom, symbol = "⁵⁶Co²⁷⁺",   label = "Cobalt (⁵⁶Co²⁷⁺)",     mass = 56f, e = 0, p = 27, n = 29,   description = "Кобальт",    betaPlusDecayResult = IRON_56_ION_26),
            NICKEL_56_ION_28        to Details (type = ElementType.Atom, symbol = "⁵⁶Ni²⁸⁺",   label = "Nickel (⁵⁶Ni²⁸⁺)",     mass = 56f, e = 0, p = 28, n = 28,   description = "Никель",     betaPlusDecayResult = COBALT_56_ION_27),

            Star                    to Details (type = ElementType.Star,                symbol = "Star",    label = "Star",         mass = 1f, e = 1, p = 1, n = 0, radius = 100f),
            SPACE_MODULE            to Details (type = ElementType.SpaceModule,         symbol = ".",       label = "SpaceModule",  mass = 1f, e = 1, p = 1, n = 0, radius = 30f),
            RECOMBINATION_MODULE    to Details (type = ElementType.RecombinationModule, symbol = ".",       label = "SpaceModule",  mass = 1f, e = 1, p = 1, n = 0, radius = 30f),

            // Молекулы
            C2_H6_O_ETHANOL         to Details (type = ElementType.Molecule, symbol = "C₂H₅OH", label = "Ethanol (C₂H₅OH)", mass = 46f, e = 26, p = 26, n = 20, description = "Этиловый спирт. Основной компонент водки."),
            C2_H6_O_DIMETHYL_ETHER  to Details (type = ElementType.Molecule, symbol = "CH₃OCH₃", label = "Dimethyl Ether (CH₃OCH₃)", mass = 46f, e = 26, p = 26, n = 20, description = "Диметиловый Эфир."),

            C_H4                    to Details (type = ElementType.Molecule, symbol = "CH₄", label = "Methane (CH₄)", mass = 16f, e = 10, p = 10, n = 6, description = "Метан. Основной компонент природного газа."),

            O2                      to Details (type = ElementType.Molecule, symbol = "O₂", label = "Oxygen (O₂)", mass = 32f, e = 16, p = 16, n = 16),
            H2O                     to Details (type = ElementType.Molecule, symbol = "H₂O", label = "Water (H₂O)", mass = 18f, e = 10, p = 10, n = 8),
            H2                      to Details (type = ElementType.Molecule, symbol = "H₂", label = "DiHydrogen (H₂)", mass = 2f, e = 2, p = 2, n = 2, energyBondDissociation = 4.5f, dissociationElements = listOf(Element.HYDROGEN, Element.HYDROGEN)),
        )
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

    val betaPlusDecayResult: Element? = null, // β⁺-распад. Протон-избыточное ядро превращает протон в нейтрон с испусканием позитрона: p → n + e⁺ + νₑ (нейтрино опускаем). Если поле выставлено — элемент сам по себе нестабилен и распадается в указанный.
)

