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
    OXYGEN_15_ION_8,
    OXYGEN_16_ION_8, OXYGEN_16_ION_7, OXYGEN_16_ION_6, OXYGEN_16_ION_5, OXYGEN_16_ION_4, OXYGEN_16_ION_3, OXYGEN_16_ION_2, OXYGEN_16_ION_1, OXYGEN_16,
    OXYGEN_17_ION_8, OXYGEN_17_ION_7,
    OXYGEN_18_ION_8,
    FLUORINE_17_ION_9,
    FLUORINE_18_ION_9,
    FLUORINE_19_ION_9,
    NEON_20_ION_10,
    NA_23_ION_11,
    MG_24_ION_12,
    SILICON_28_ION_14,
    PHOSPHORUS_31_ION_15,
    SULFUR_31_ION_16,
    SULFUR_32_ION_16,
    ARGON_36_ION_18,
    CALCIUM_40_ION_20,
    TITANIUM_44_ION_22,
    TITANIUM_48_ION_22,
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
            NITROGEN_15_ION_6       to Details (type = ElementType.Atom, symbol = "¹⁵N⁶⁺",     label = "Nitrogen (¹⁵N⁶⁺)",     mass = 15f, e = 1, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_5, energyLevels = listOf(499.8f, 592.36f, 666.4f), ion = NITROGEN_15_ION_7),
            NITROGEN_15_ION_5       to Details (type = ElementType.Atom, symbol = "¹⁵N⁵⁺",     label = "Nitrogen (¹⁵N⁵⁺)",     mass = 15f, e = 2, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_4, energyLevels = listOf(430.6f, 500.3f, 552.07f), ion = NITROGEN_15_ION_6),
            NITROGEN_15_ION_4       to Details (type = ElementType.Atom, symbol = "¹⁵N⁴⁺",     label = "Nitrogen (¹⁵N⁴⁺)",     mass = 15f, e = 3, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_3, energyLevels = listOf(10.01f, 77.24f, 97.89f),  ion = NITROGEN_15_ION_5),
            NITROGEN_15_ION_3       to Details (type = ElementType.Atom, symbol = "¹⁵N³⁺",     label = "Nitrogen (¹⁵N³⁺)",     mass = 15f, e = 4, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_2, energyLevels = listOf(16.20f, 39.97f, 77.47f),  ion = NITROGEN_15_ION_4),
            NITROGEN_15_ION_2       to Details (type = ElementType.Atom, symbol = "¹⁵N²⁺",     label = "Nitrogen (¹⁵N²⁺)",     mass = 15f, e = 5, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15_ION_1, energyLevels = listOf(6.98f, 12.29f, 47.45f),   ion = NITROGEN_15_ION_3),
            NITROGEN_15_ION_1       to Details (type = ElementType.Atom, symbol = "¹⁵N¹⁺",     label = "Nitrogen (¹⁵N¹⁺)",     mass = 15f, e = 6, p = 7, n = 8,     description = "Азот",       recombinationElement = NITROGEN_15,       energyLevels = listOf(1.899f, 11.28f, 29.60f),  ion = NITROGEN_15_ION_2),
            NITROGEN_15             to Details (type = ElementType.Atom, symbol = "¹⁵N",       label = "Nitrogen (¹⁵N)",       mass = 15f, e = 7, p = 7, n = 8,     description = "Азот",                                                 energyLevels = listOf(2.38f, 10.34f, 14.53f),   ion = NITROGEN_15_ION_1),
            OXYGEN_15_ION_8         to Details (type = ElementType.Atom, symbol = "¹⁵O⁸⁺",     label = "Oxygen (¹⁵O⁸⁺)",       mass = 15f, e = 0, p = 8, n = 7,     description = "Кислород",   betaPlusDecayResult = NITROGEN_15_ION_7),
            OXYGEN_16_ION_8         to Details (type = ElementType.Atom, symbol = "¹⁶O⁸⁺",       label = "Oxygen (¹⁶O⁸⁺)",       mass = 16f, e = 0, p = 8, n = 8,     description = "Кислород", recombinationElement = OXYGEN_16_ION_7, alphaGammaResult = NEON_20_ION_10),
            OXYGEN_16_ION_7         to Details (type = ElementType.Atom, symbol = "¹⁶O⁷⁺",       label = "Oxygen (¹⁶O⁷⁺)",       mass = 16f, e = 1, p = 8, n = 8,     description = "Кислород", recombinationElement = OXYGEN_16_ION_6,     energyLevels = listOf(652.8f, 773.69f, 871.41f), ion = OXYGEN_16_ION_8),
            OXYGEN_16_ION_6         to Details (type = ElementType.Atom, symbol = "¹⁶O⁶⁺",       label = "Oxygen (¹⁶O⁶⁺)",       mass = 16f, e = 2, p = 8, n = 8,     description = "Кислород", recombinationElement = OXYGEN_16_ION_5,     energyLevels = listOf(573.95f, 665.42f, 739.29f), ion = OXYGEN_16_ION_7),
            OXYGEN_16_ION_5         to Details (type = ElementType.Atom, symbol = "¹⁶O⁵⁺",       label = "Oxygen (¹⁶O⁵⁺)",       mass = 16f, e = 3, p = 8, n = 8,     description = "Кислород", recombinationElement = OXYGEN_16_ION_4,     energyLevels = listOf(11.95f, 71.96f, 138.12f), ion = OXYGEN_16_ION_6),
            OXYGEN_16_ION_4         to Details (type = ElementType.Atom, symbol = "¹⁶O⁴⁺",       label = "Oxygen (¹⁶O⁴⁺)",       mass = 16f, e = 4, p = 8, n = 8,     description = "Кислород", recombinationElement = OXYGEN_16_ION_3,     energyLevels = listOf(19.69f, 84.18f, 113.9f),  ion = OXYGEN_16_ION_5),
            OXYGEN_16_ION_3         to Details (type = ElementType.Atom, symbol = "¹⁶O³⁺",       label = "Oxygen (¹⁶O³⁺)",       mass = 16f, e = 5, p = 8, n = 8,     description = "Кислород", recombinationElement = OXYGEN_16_ION_2,     energyLevels = listOf(8.86f, 22.4f, 77.41f),    ion = OXYGEN_16_ION_4),
            OXYGEN_16_ION_2         to Details (type = ElementType.Atom, symbol = "¹⁶O²⁺",       label = "Oxygen (¹⁶O²⁺)",       mass = 16f, e = 6, p = 8, n = 8,     description = "Кислород", recombinationElement = OXYGEN_16_ION_1,     energyLevels = listOf(2.51f, 14.88f, 54.94f),   ion = OXYGEN_16_ION_3),
            OXYGEN_16_ION_1         to Details (type = ElementType.Atom, symbol = "¹⁶O¹⁺",       label = "Oxygen (¹⁶O¹⁺)",       mass = 16f, e = 7, p = 8, n = 8,     description = "Кислород", recombinationElement = OXYGEN_16,           energyLevels = listOf(3.33f, 14.86f, 35.12f),   ion = OXYGEN_16_ION_2),
            OXYGEN_16               to Details (type = ElementType.Atom, symbol = "¹⁶O",         label = "Oxygen (¹⁶O)",         mass = 16f, e = 8, p = 8, n = 8,     description = "Кислород",                                             energyLevels = listOf(1.96f, 9.51f, 13.6f),     ion = OXYGEN_16_ION_1),
            OXYGEN_17_ION_8         to Details (type = ElementType.Atom, symbol = "¹⁷O⁸⁺",     label = "Oxygen (¹⁷O⁸⁺)",       mass = 17f, e = 0, p = 8, n = 9,     description = "Кислород", recombinationElement = OXYGEN_17_ION_7),
            OXYGEN_17_ION_7         to Details (type = ElementType.Atom, symbol = "¹⁷O⁷⁺",     label = "Oxygen (¹⁷O⁷⁺)",       mass = 17f, e = 1, p = 8, n = 9,     description = "Кислород",                                               energyLevels = listOf(652.8f, 773.69f, 871.41f), ion = OXYGEN_17_ION_8),
            OXYGEN_18_ION_8         to Details (type = ElementType.Atom, symbol = "¹⁸O⁸⁺",     label = "Oxygen (¹⁸O⁸⁺)",       mass = 18f, e = 0, p = 8, n = 10,    description = "Кислород"),
            FLUORINE_17_ION_9       to Details (type = ElementType.Atom, symbol = "¹⁷F⁹⁺",     label = "Fluorine (¹⁷F⁹⁺)",     mass = 17f, e = 0, p = 9, n = 8,     description = "Фтор", betaPlusDecayResult = OXYGEN_17_ION_8),
            FLUORINE_18_ION_9       to Details (type = ElementType.Atom, symbol = "¹⁸F⁹⁺",     label = "Fluorine (¹⁸F⁹⁺)",     mass = 18f, e = 0, p = 9, n = 9,     description = "Фтор", betaPlusDecayResult = OXYGEN_18_ION_8),
            FLUORINE_19_ION_9       to Details (type = ElementType.Atom, symbol = "¹⁹F⁹⁺",     label = "Fluorine (¹⁹F⁹⁺)",     mass = 19f, e = 0, p = 9, n = 10,    description = "Фтор"),
            NEON_20_ION_10          to Details (type = ElementType.Atom, symbol = "²⁰Ne¹⁰⁺",     label = "Neon (²⁰Ne¹⁰⁺)",       mass = 20f, e = 0, p = 10, n = 10,   description = "Неон",        alphaGammaResult = MG_24_ION_12),
            NA_23_ION_11            to Details (type = ElementType.Atom, symbol = "²³Na¹¹⁺",     label = "Sodium (²³Na¹¹⁺)",     mass = 23f, e = 0, p = 11, n = 12),
            MG_24_ION_12            to Details (type = ElementType.Atom, symbol = "²⁴Mg¹²⁺",     label = "Magnesium (²⁴Mg¹²⁺)",  mass = 24f, e = 0, p = 12, n = 12,   description = "Магний",     alphaGammaResult = SILICON_28_ION_14),
            SILICON_28_ION_14       to Details (type = ElementType.Atom, symbol = "²⁸Si¹⁴⁺",     label = "Silicon (²⁸Si¹⁴⁺)",    mass = 28f, e = 0, p = 14, n = 14,   description = "Кремний",    alphaGammaResult = SULFUR_32_ION_16),
            PHOSPHORUS_31_ION_15    to Details (type = ElementType.Atom, symbol = "³¹P¹⁵⁺",      label = "Phosphorus (³¹P¹⁵⁺)",  mass = 31f, e = 0, p = 15, n = 16,   description = "Фосфор"),
            SULFUR_31_ION_16        to Details (type = ElementType.Atom, symbol = "³¹S¹⁶⁺",    label = "Sulfur (³¹S¹⁶⁺)",      mass = 31f, e = 0, p = 16, n = 15,   description = "Сера",       betaPlusDecayResult = PHOSPHORUS_31_ION_15),
            SULFUR_32_ION_16        to Details (type = ElementType.Atom, symbol = "S¹⁶⁺",      label = "Sulfur (³²S¹⁶⁺)",      mass = 32f, e = 0, p = 16, n = 16,   description = "Сера",       alphaGammaResult = ARGON_36_ION_18),
            ARGON_36_ION_18         to Details (type = ElementType.Atom, symbol = "³⁶Ar¹⁸⁺",     label = "Argon (³⁶Ar¹⁸⁺)",      mass = 36f, e = 0, p = 18, n = 18,   description = "Аргон",      alphaGammaResult = CALCIUM_40_ION_20),
            CALCIUM_40_ION_20       to Details (type = ElementType.Atom, symbol = "⁴⁰Ca²⁰⁺",     label = "Calcium (⁴⁰Ca²⁰⁺)",    mass = 40f, e = 0, p = 20, n = 20,   description = "Кальций",    alphaGammaResult = TITANIUM_44_ION_22),
            TITANIUM_44_ION_22      to Details (type = ElementType.Atom, symbol = "⁴⁴Ti²²⁺",     label = "Titanium (⁴⁴Ti²²⁺)",   mass = 44f, e = 0, p = 22, n = 22,   description = "Титан",      alphaGammaResult = CHROMIUM_48_ION_24),
            TITANIUM_48_ION_22      to Details (type = ElementType.Atom, symbol = "⁴⁸Ti²²⁺",   label = "Titanium (⁴⁸Ti²²⁺)",   mass = 48f, e = 0, p = 22, n = 26,   description = "Титан"),
            VANADIUM_48_ION_23      to Details (type = ElementType.Atom, symbol = "⁴⁸V²³⁺",    label = "Vanadium (⁴⁸V²³⁺)",    mass = 48f, e = 0, p = 23, n = 25,   description = "Ванадий",    betaPlusDecayResult = TITANIUM_48_ION_22),
            CHROMIUM_48_ION_24      to Details (type = ElementType.Atom, symbol = "⁴⁸Cr²⁴⁺",   label = "Chromium (⁴⁸Cr²⁴⁺)",   mass = 48f, e = 0, p = 24, n = 24,   description = "Хром",       betaPlusDecayResult = VANADIUM_48_ION_23, alphaGammaResult = IRON_52_ION_26),
            CHROMIUM_52_ION_24      to Details (type = ElementType.Atom, symbol = "⁵²Cr²⁴⁺",   label = "Chromium (⁵²Cr²⁴⁺)",   mass = 52f, e = 0, p = 24, n = 28,   description = "Хром"),
            MANGANESE_52_ION_25     to Details (type = ElementType.Atom, symbol = "⁵²Mn²⁵⁺",   label = "Manganese (⁵²Mn²⁵⁺)",  mass = 52f, e = 0, p = 25, n = 27,   description = "Марганец",   betaPlusDecayResult = CHROMIUM_52_ION_24),
            IRON_52_ION_26          to Details (type = ElementType.Atom, symbol = "⁵²Fe²⁶⁺",   label = "Iron (⁵²Fe²⁶⁺)",       mass = 52f, e = 0, p = 26, n = 26,   description = "Железо",     betaPlusDecayResult = MANGANESE_52_ION_25, alphaGammaResult = NICKEL_56_ION_28),
            IRON_56_ION_26          to Details (type = ElementType.Atom, symbol = "⁵⁶Fe²⁶⁺",   label = "Iron (⁵⁶Fe²⁶⁺)",       mass = 56f, e = 0, p = 26, n = 30,   description = "Железо"),
            COBALT_56_ION_27        to Details (type = ElementType.Atom, symbol = "⁵⁶Co²⁷⁺",   label = "Cobalt (⁵⁶Co²⁷⁺)",     mass = 56f, e = 0, p = 27, n = 29,   description = "Кобальт",    betaPlusDecayResult = IRON_56_ION_26),
            NICKEL_56_ION_28        to Details (type = ElementType.Atom, symbol = "⁵⁶Ni²⁸⁺",     label = "Nickel (⁵⁶Ni²⁸⁺)",     mass = 56f, e = 0, p = 28, n = 28,   description = "Никель",     betaPlusDecayResult = COBALT_56_ION_27),

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

