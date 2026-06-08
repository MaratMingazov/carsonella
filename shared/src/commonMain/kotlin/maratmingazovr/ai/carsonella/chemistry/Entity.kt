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
    // Число электронов как динамическое состояние (рефакторинг «ионизация → состояние»): задаёт заряд,
    // символ и energyLevels; цикл ионизации/рекомбинации крутит его. Дефолт при создании — details.e.
    var electrons: Int

    fun copyWith(
        alive: Boolean = this.alive,
        position: Position = this.position,
        direction: Vec2D = this.direction,
        velocity: Float = this.velocity,
        energy: Float = this.energy,
        electrons: Int = this.electrons,
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

    // Зеркало setEnergy для числа электронов. Используется циклом ионизации/рекомбинации
    // (PhotoIonization/RecombinationReaction) через updateState — меняет заряд, не подменяя Element.
    fun setElectrons(electrons: Int) {
        state().value = state().value.copyWith(electrons = electrons)
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
        val myElectronsCount = state().value.electrons
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
            val elementElectronsCount = element.state().value.electrons
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
            val elementProtonsCount = element.state().value.electrons // NB: исторически читает электроны (был details.e), не протоны — поведение сохранено; fix на details.p отдельным шагом
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
    DEUTERIUM,
    HELIUM_3,
    HELIUM_4,
    LITHIUM_7,
    BERYLLIUM_7,
    BERYLLIUM_8,
    BORON_8,
    CARBON_12,
    CARBON_13,
    CARBON_14,
    NITROGEN_13,
    NITROGEN_14,
    NITROGEN_15,
    OXYGEN_15,
    OXYGEN_16,
    OXYGEN_17,
    OXYGEN_18,
    FLUORINE_17,
    FLUORINE_18,
    FLUORINE_19,
    NEON_20,
    NEON_21,
    NEON_22,
    SODIUM_21,
    SODIUM_22,
    SODIUM_23,
    MAGNESIUM_23,
    MAGNESIUM_24,
    MAGNESIUM_25,
    MAGNESIUM_26,
    ALUMINUM_25,
    ALUMINUM_26,
    ALUMINUM_27,
    SILICON_28,
    SILICON_29,
    SILICON_30,
    SILICON_31,
    PHOSPHORUS_31,
    SULFUR_31,
    SULFUR_32,
    ARGON_36,
    CALCIUM_40,
    TITANIUM_44,
    TITANIUM_48,
    VANADIUM_48,
    CHROMIUM_48,
    CHROMIUM_52,
    MANGANESE_52,
    IRON_52,
    IRON_56,
    IRON_57,
    IRON_58,
    IRON_59,
    COBALT_56,
    COBALT_59,
    COBALT_60,
    NICKEL_56,
    NICKEL_60,
    NICKEL_61,
    NICKEL_62,
    NICKEL_63,
    COPPER_63,
    COPPER_64,
    ZINC_64,
    ZINC_65,
    ZINC_66,
    ZINC_67,
    ZINC_68,
    ZINC_69,
    GALLIUM_69,
    GALLIUM_70,
    GERMANIUM_70,
    GERMANIUM_71,
    GERMANIUM_72,
    GERMANIUM_73,
    GERMANIUM_74,
    GERMANIUM_75,
    ARSENIC_75,
    ARSENIC_76,
    SELENIUM_76,
    SELENIUM_77,
    SELENIUM_78,
    SELENIUM_79,
    BROMINE_79,
    BROMINE_80,
    KRYPTON_80,
    KRYPTON_81,
    KRYPTON_82,
    KRYPTON_83,
    KRYPTON_84,
    KRYPTON_85,
    RUBIDIUM_85,
    RUBIDIUM_86,
    STRONTIUM_86,
    STRONTIUM_87,
    STRONTIUM_88,
    STRONTIUM_89,
    YTTRIUM_89,
    YTTRIUM_90,
    ZIRCONIUM_90,
    ZIRCONIUM_91,
    ZIRCONIUM_92,
    ZIRCONIUM_93,
    NIOBIUM_93,
    NIOBIUM_94,
    MOLYBDENUM_94,
    MOLYBDENUM_95,
    MOLYBDENUM_96,
    MOLYBDENUM_97,
    MOLYBDENUM_98,
    MOLYBDENUM_99,
    TECHNETIUM_99,
    TECHNETIUM_100,
    RUTHENIUM_100,
    RUTHENIUM_101,
    RUTHENIUM_102,
    RUTHENIUM_103,
    RHODIUM_103,
    RHODIUM_104,
    PALLADIUM_104,
    PALLADIUM_105,
    PALLADIUM_106,
    PALLADIUM_107,
    SILVER_107,
    SILVER_108,
    CADMIUM_108,
    CADMIUM_109,
    CADMIUM_110,
    CADMIUM_111,
    CADMIUM_112,
    CADMIUM_113,
    CADMIUM_114,
    CADMIUM_115,
    INDIUM_115,
    INDIUM_116,
    TIN_116,
    TIN_117,
    TIN_118,
    TIN_119,
    TIN_120,
    TIN_121,
    ANTIMONY_121,
    ANTIMONY_122,
    TELLURIUM_122,
    TELLURIUM_123,
    TELLURIUM_124,
    TELLURIUM_125,
    TELLURIUM_126,
    TELLURIUM_127,
    IODINE_127,
    IODINE_128,
    XENON_128,
    XENON_129,
    XENON_130,
    XENON_131,
    XENON_132,
    XENON_133,
    CESIUM_133,
    CESIUM_134,
    BARIUM_134,
    BARIUM_135,
    BARIUM_136,
    BARIUM_137,
    BARIUM_138,
    BARIUM_139,
    LANTHANUM_139,
    LANTHANUM_140,
    CERIUM_140,
    CERIUM_141,
    PRASEODYMIUM_141,
    PRASEODYMIUM_142,
    NEODYMIUM_142,
    NEODYMIUM_143,
    NEODYMIUM_144,
    NEODYMIUM_145,
    NEODYMIUM_146,
    NEODYMIUM_147,
    PROMETHIUM_147,
    PROMETHIUM_148,
    SAMARIUM_148,
    SAMARIUM_149,
    SAMARIUM_150,
    SAMARIUM_151,
    EUROPIUM_151,
    EUROPIUM_152,
    EUROPIUM_153,
    EUROPIUM_154,
    GADOLINIUM_154,
    GADOLINIUM_155,
    GADOLINIUM_156,
    GADOLINIUM_157,
    GADOLINIUM_158,
    GADOLINIUM_159,
    TERBIUM_159,
    TERBIUM_160,
    DYSPROSIUM_160,
    DYSPROSIUM_161,
    DYSPROSIUM_162,
    DYSPROSIUM_163,
    DYSPROSIUM_164,
    DYSPROSIUM_165,
    HOLMIUM_165,
    HOLMIUM_166,
    ERBIUM_166,
    ERBIUM_167,
    ERBIUM_168,
    ERBIUM_169,
    THULIUM_169,
    THULIUM_170,
    YTTERBIUM_170,
    YTTERBIUM_171,
    YTTERBIUM_172,
    YTTERBIUM_173,
    YTTERBIUM_174,
    YTTERBIUM_175,
    LUTETIUM_175,
    LUTETIUM_176,
    LUTETIUM_177,
    HAFNIUM_177,
    HAFNIUM_178,
    HAFNIUM_179,
    HAFNIUM_180,
    HAFNIUM_181,
    TANTALUM_181,
    TANTALUM_182,
    TUNGSTEN_182,
    TUNGSTEN_183,
    TUNGSTEN_184,
    TUNGSTEN_185,
    RHENIUM_185,
    RHENIUM_186,
    OSMIUM_186,
    OSMIUM_187,
    OSMIUM_188,
    OSMIUM_189,
    OSMIUM_190,
    OSMIUM_191,
    IRIDIUM_191,
    IRIDIUM_192,
    PLATINUM_192,
    PLATINUM_193,
    PLATINUM_194,
    PLATINUM_195,
    PLATINUM_196,
    PLATINUM_197,
    GOLD_197,
    GOLD_198,
    MERCURY_198,
    MERCURY_199,
    MERCURY_200,
    MERCURY_201,
    MERCURY_202,
    MERCURY_203,
    THALLIUM_203,
    THALLIUM_204,
    LEAD_204,
    LEAD_205,
    LEAD_206,
    LEAD_207,
    LEAD_208,


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

    // Символ/лейбл как функция от числа электронов (рефакторинг ионизации, шаг 2B).
    // Для атомов вычисляем заряд (p − electrons); у не-атомов символ/лейбл фиксированы — отдаём литерал.
    fun symbol(electrons: Int): String =
        if (details.type == ElementType.Atom) baseSymbolMap.getValue(this) + chargeSuffix(details.p - electrons)
        else details.symbol

    fun label(electrons: Int): String =
        if (details.type == ElementType.Atom) "${nameMap.getValue(this)} (${symbol(electrons)})"
        else details.label

    // Энергетические уровни как функция от числа электронов (рефакторинг ионизации, 2C2b-4).
    // Зависят только от Z, не от N → одна лестница на элемент (energyLevelsByZ по details.p), общая
    // для всех изотопов. Голым/несуществующим состояниям — пустой список («нельзя ионизировать»).
    fun energyLevels(electrons: Int): List<Float> =
        energyLevelsByZ[details.p]?.getOrNull(electrons) ?: emptyList()

    companion object {
        // Каталог Details вынесен в ElementDetails.kt. Делёж на light/heavy/heaviest — ради лимита JVM 64KB на байткод метода.
        private val detailsMap: Map<Element, Details> = elementDetails()

        // Энергетические лестницы ионизации по Z (одна на элемент, общая для изотопов). Опора energyLevels(electrons).
        private val energyLevelsByZ: Map<Int, List<List<Float>>> = energyLevelsTable()

        // База для symbol(e)/label(e). Пока каталог не свёрнут — выводим из существующих symbol/label
        // срезанием (на шаге 2C станут хранимыми полями изотопа). Считаются один раз.
        private val baseSymbolMap: Map<Element, String> =
            entries.filter { it.details.type == ElementType.Atom }.associateWith { stripCharge(it.details.symbol) }
        private val nameMap: Map<Element, String> =
            entries.filter { it.details.type == ElementType.Atom }.associateWith { it.details.label.substringBefore(" (") }
    }

}

private const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

// Число → надстрочные цифры: 29 → "²⁹".
private fun sup(n: Int): String = n.toString().map { SUPERSCRIPT_DIGITS[it - '0'] }.joinToString("")

// Надстрочный заряд иона: 0 → "", 1 → "⁺", n≥2 → "ⁿ⁺" (конвенция: +1 без цифры).
private fun chargeSuffix(charge: Int): String = when {
    charge <= 0 -> ""
    charge == 1 -> "⁺"
    else -> sup(charge) + "⁺"
}

// Базовый символ нуклида без заряда: срезаем хвостовой "⁺" и хвостовые надстрочные цифры заряда.
// Массовый индекс-префикс ("¹²C") не трогается — он стоит перед буквой элемента.
private fun stripCharge(symbol: String): String =
    if (symbol.endsWith("⁺")) symbol.dropLast(1).trimEnd { it in SUPERSCRIPT_DIGITS } else symbol

// Может ли частица принять ещё электрон (рекомбинация): протон → H, либо атом с электронами меньше Z.
// Анионов в модели нет — потолок electrons == p (нейтраль). Опора рефакторинга ионизации (2C).
fun canGainElectron(element: Element, electrons: Int): Boolean =
    element == Element.Proton || (element.details.type == ElementType.Atom && electrons < element.details.p)

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

