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
    suspend fun init()
    suspend fun destroy() // нужно, чтобы сообщить атому, что он должен быть уничтожен

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

    fun addVelocity(moreVelocity: Float) {
        state().value = state().value.copyWith(velocity = state().value.velocity + moreVelocity)
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
        val myElectronsCount = state().value.element.e
        val myProtonsCount = state().value.element.p
        val myRadius = state().value.element.radius
        val myMass = state().value.element.mass
        if (myElectronsCount == 0 && myProtonsCount == 0) {return fVector}

        elements.forEach { element ->
            val elementPosition = element.state().value.position
            val rx = state().value.position.x - elementPosition.x
            val ry = state().value.position.y - elementPosition.y
            val distance2 = rx*rx + ry*ry // это квадрат расстояния между частицами

            val elementRadius = element.state().value.element.radius
            val elementMass = element.state().value.element.mass
            val maxRadius2 = (myRadius + elementRadius) * (myRadius + elementRadius) * 1.7
            // Если элементы находятся дальше этого расстояния, то они не влияют друг на друга
            if (distance2 > maxRadius2) return@forEach // вне радиуса действия

            // Если электроны есть только у одного элемента, то эти элементы будут притягиваться
            // Если электроны есть у обоих элементов, то будут отталкиваться
            val elementElectronsCount = element.state().value.element.e
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
            val elementProtonsCount = element.state().value.element.e
            val fRepulsion = if (distance2 < (myRadius + elementRadius) * (myRadius + elementRadius)) { (myProtonsCount + elementProtonsCount + 1)/(distance2 + 50f) } else 0f

            val fScalar = fAttraction + fRepulsion + gravityForce
            fVector.x += rx * fScalar
            fVector.y += ry * fScalar
        }
        return fVector
    }
}

enum class ElementType { SubAtom, Atom, Molecule, Star, SpaceModule }

enum class Element(
    val type: ElementType,
    val symbol: String,
    val label: String,
    val mass: Float,
    val e: Int, // Количество электронов в элементе
    val p: Int, // Количество протонов в элементе
    val n: Int, // Количество нейтронов в элементе
    val radius: Float = 20f,
    val description: String = "",

    val energyLevels: List<Float> = listOf(), // Энергетические уровни атома. Атом может принимать только такие кванты энергии
    val ion: Element? = null, // Ион, который образуется, когда мы выбиваем электрон у элемента

    val energyBondDissociation: Float? = null, // Энергия диссоциации. Сколько нужно энергии, чтобы разорвать химическую связь.
    val dissociationElements: List<Element> = listOf(), // Элементы, которые получаются в результате диссоциации

    val alphaReactionResult: Element? = null // Альфа захват. Процесс в недрах звезд. Когда ион захватывает альфа частицу (ион Гелия-4) и получается более тяжелый элемент
) {
    // --- субатомные частицы ---
    Neutron     (type = ElementType.SubAtom, symbol = "n",  label = "Neutron (n)",      mass = 1f,      e = 0, p = 0, n = 1, radius = 10f),
    Proton      (type = ElementType.SubAtom, symbol = "p⁺", label = "Proton (p⁺)",      mass = 1f,      e = 0, p = 1, n = 0, radius = 10f),
    Electron    (type = ElementType.SubAtom, symbol = "e⁻", label = "Electron (e⁻)",    mass = 0.1f,    e = 1, p = 0, n = 0, radius = 5f),
    Photon      (type = ElementType.SubAtom, symbol = "γ",  label = "Photon (γ)",       mass = 0f,      e = 0, p = 0, n = 0, radius = 5f),

    // --- атомы ---
    Ni (type = ElementType.Atom, symbol = "Ni", label = "Nikel (O)", mass = 58f, e = 28, p = 28, n = 30),
    O (type = ElementType.Atom, symbol = "O", label = "Oxygen (O)", mass = 16f, e = 8, p = 8, n = 8),
    C (type = ElementType.Atom, symbol = "C", label = "Carbon (C)", mass = 12f, e = 6, p = 6, n = 6),

    NICKEL_56_ION_28        (type = ElementType.Atom, symbol = "Ni²⁸⁺",     label = "Nickel (⁵⁶Ni²⁸⁺)",     mass = 56f, e = 0, p = 28, n = 28, description = "Никель"),
    IRON_52_ION_26          (type = ElementType.Atom, symbol = "Fe²⁶⁺",     label = "Iron (⁵²Fe²⁶⁺)",       mass = 52f, e = 0, p = 26, n = 26, description = "Железо", alphaReactionResult = NICKEL_56_ION_28),
    CHROMIUM_48_ION_24      (type = ElementType.Atom, symbol = "Cr²⁴⁺",     label = "Chromium (⁴⁸Cr²⁴⁺)",   mass = 48f, e = 0, p = 24, n = 24, description = "Хром", alphaReactionResult = IRON_52_ION_26),
    TITANIUM_44_ION_22      (type = ElementType.Atom, symbol = "Ti²²⁺",     label = "Titanium (⁴⁴Ti²²⁺)",   mass = 44f, e = 0, p = 22, n = 22, description = "Титан", alphaReactionResult = CHROMIUM_48_ION_24),
    CALCIUM_40_ION_20       (type = ElementType.Atom, symbol = "Ca²⁰⁺",     label = "Calcium (⁴⁰Ca²⁰⁺)",    mass = 40f, e = 0, p = 20, n = 20, description = "Кальций", alphaReactionResult = TITANIUM_44_ION_22),
    ARGON_36_ION_18         (type = ElementType.Atom, symbol = "Ar¹⁸⁺",     label = "Argon (³⁶Ar¹⁸⁺)",      mass = 36f, e = 0, p = 18, n = 18, description = "Аргон", alphaReactionResult = CALCIUM_40_ION_20),
    SULFUR_32_ION_16        (type = ElementType.Atom, symbol = "S¹⁶⁺",      label = "Sulfur (³²S¹⁶⁺)",      mass = 32f, e = 0, p = 16, n = 16, description = "Сера", alphaReactionResult = ARGON_36_ION_18),
    SULFUR_31_ION_16        (type = ElementType.Atom, symbol = "³¹S¹⁶⁺",    label = "Sulfur (³¹S¹⁶⁺)",      mass = 31f, e = 0, p = 16, n = 15, description = "Сера"),
    PHOSPHORUS_31_ION_15    (type = ElementType.Atom, symbol = "P¹⁵⁺",      label = "Phosphorus (³¹P¹⁵⁺)",  mass = 31f, e = 0, p = 15, n = 16, description = "Фосфор"),
    SILICON_28_ION_14       (type = ElementType.Atom, symbol = "Si¹⁴⁺",     label = "Silicon (²⁸Mg¹⁴⁺)",    mass = 28f, e = 0, p = 14, n = 14, description = "Кремний", alphaReactionResult = SULFUR_32_ION_16),
    MG_24_ION_12            (type = ElementType.Atom, symbol = "Mg¹²⁺",     label = "Magnesium (²⁴Mg¹²⁺)",  mass = 24f, e = 0, p = 12, n = 12),
    NA_23_ION_11 (type = ElementType.Atom, symbol = "Na¹¹⁺", label = "Sodium (²³Na¹¹⁺)", mass = 23f, e = 0, p = 11, n = 12),
    NE_20_ION_10 (type = ElementType.Atom, symbol = "Ne¹⁰⁺", label = "Neon (²⁰Ne¹⁰⁺)", mass = 20f, e = 0, p = 10, n = 10),
    O_16_ION_8 (type = ElementType.Atom, symbol = "O⁸⁺", label = "Oxygen (¹⁶O⁸⁺)", mass = 16f, e = 0, p = 8, n = 8),
    C_12_ION_6 (type = ElementType.Atom, symbol = "C⁶⁺", label = "Carbon (¹²C⁶⁺)", mass = 12f, e = 0, p = 6, n = 6, alphaReactionResult = O_16_ION_8),
    BE_8_ION_4 (type = ElementType.Atom, symbol = "⁸Be⁴⁺", label = "Beryllium (⁸Be⁴⁺)", mass = 8f, e = 0, p = 4, n = 4),
    HE_4_ION_2 (type = ElementType.Atom, symbol = "He²⁺", label = "Helium (⁴He²⁺)", mass = 4f, e = 0, p = 2, n = 2),
    HE_3_ION_2 (type = ElementType.Atom, symbol = "³He²⁺", label = "Helium (³He²⁺)", mass = 3f, e = 0, p = 2, n = 1),
    DEUTERIUM (type = ElementType.Atom, symbol = "²H", label = "DEUTERIUM (²H)", mass = 2f, e = 1, p = 1, n = 1, description = "Дейтерий"),
    DEUTERIUM_ION           (type = ElementType.Atom, symbol = "²H⁺",       label = "DEUTERIUM (²H⁺)",      mass = 2f, e = 0, p = 1, n = 1, description = "Ион Дейтерия"),
    HYDROGEN                (type = ElementType.Atom, symbol = "H",         label = "Hydrogen (H)",         mass = 1f, e = 1, p = 1, n = 0, energyLevels = listOf(10.2f, 12.09f, 13.6f), ion = Proton),

    Star (type = ElementType.Star, symbol = "Star", label = "Star", mass = 1f, e = 1, p = 1, n = 0, radius = 100f),
    SPACE_MODULE (type = ElementType.SpaceModule, symbol = ".", label = "SpaceModule", mass = 1f, e = 1, p = 1, n = 0, radius = 10f),

    // Молекулы
    C2_H6_O_ETHANOL (type = ElementType.Molecule, symbol = "C₂H₅OH", label = "Ethanol (C₂H₅OH)", mass = 46f, e = 26, p = 26, n = 20, description = "Этиловый спирт. Основной компонент водки."),
    C2_H6_O_DIMETHYL_ETHER (type = ElementType.Molecule, symbol = "CH₃OCH₃", label = "Dimethyl Ether (CH₃OCH₃)", mass = 46f, e = 26, p = 26, n = 20, description = "Диметиловый Эфир."),

    C_H4 (type = ElementType.Molecule, symbol = "CH₄", label = "Methane (CH₄)", mass = 16f, e = 10, p = 10, n = 6, description = "Метан. Основной компонент природного газа."),

    O2 (type = ElementType.Molecule, symbol = "O₂", label = "Oxygen (O₂)", mass = 32f, e = 16, p = 16, n = 16),
    H2O (type = ElementType.Molecule, symbol = "H₂O", label = "Water (H₂O)", mass = 18f, e = 10, p = 10, n = 8),
    H2 (type = ElementType.Molecule, symbol = "H₂", label = "DiHydrogen (H₂)", mass = 2f, e = 2, p = 2, n = 2, energyBondDissociation = 4.5f, dissociationElements = listOf(HYDROGEN, HYDROGEN)),




}