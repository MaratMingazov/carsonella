package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.TranslatedText
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.ChangeNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.DeathNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentAware
import maratmingazovr.ai.carsonella.chemistry.behavior.LogWritable
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsAware
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequester

data class Kinematics(
    val position: Position,
    val direction: Vec2D,
    val velocity: Float,
)

data class ForcePoint(
    val position: Position,
    val radius: Float,
    val electrons: Int,
    val protons: Int,
)

sealed interface Entity :
    DeathNotifiable,
    NeighborsAware,
    ReactionRequester,
    IEnvironment, // каждая частица может являться средой для других частиц
    EnvironmentAware, // каждая частица сама находится в каком то среде
    LogWritable,
    ChangeNotifiable
{
    val id: Long
    val mass: Float
    val protons: Int
    val electrons: Int
    val displaySymbol: String // Как сущность подписана на экране: символ/формула плюс заряд.
    val saveKey: String // Ключ для сохранения
    val energyLevels: List<Float> // Энергетическая лестница (эВ): уровни возбуждения, последний = порог ионизации.
    val alive: Boolean

    fun step() // элемент делает свой ход
    fun destroy() // нужно, чтобы сообщить элементу, что он должен быть уничтожен
    fun describe(): String // Человекочитаемое описание для карточки Info.

    /**
     * Расстояние от [point] до ПОВЕРХНОСТИ сущности: 
     * `< 0` — точка внутри, 
     * `0` — на кромке,
     * `> 0` — снаружи.
     */
    fun distanceToSurface(point: Position): Float

    /**
     * Квадрат расстояния от [point] до сущности — «далеко ли ты отсюда». Спрашивают фильтры соседей
     * в каждом step: до кого имеет смысл проситься в реакцию.
     *
     * Именно ВОПРОС, а не поле `position`: у молекулы своей позиции нет, есть позиции её атомов, и она
     * отвечает по ближайшему. Квадрат — чтобы не считать корень на каждого соседа каждый тик.
     */
    fun distanceSquareTo(point: Position): Float

    // только те частицы, которые сами могут служить средой, будут переопределять эти методы
    override fun getEnvCenter(): Position = throw Exception("Not Supported")
    override fun getEnvRadius(): Float = throw Exception("Not Supported")
    override fun getEnvTemperature(): TemperatureMode = throw Exception("Not Supported")
    override fun getEnvChildren(): List<Entity> = throw Exception("Not Supported")
    override fun addEnvChild(entity: Entity) { throw Exception("Not Supported") }
    override fun removeEnvChild(entity: Entity) { throw Exception("Not Supported") }

    fun updateMyEnvironment(newEnvironment: IEnvironment) {
        this.getEnvironment().removeEnvChild(this)
        this.setEnvironment(newEnvironment)
        newEnvironment.addEnvChild(this)
    }

    // Точки, которыми сущность участвует в расчёте сил: у точечной одна, у молекулы по одной на атом.
    fun forcePoints(): List<ForcePoint>
    
    fun calculateForce(elements: List<Entity>): Vec2D {
        val others = elements.flatMap { it.forcePoints() }
        return forcePoints().fold(Vec2D(0f, 0f)) { sum, point -> sum + forceOn(point, others) }
    }

    // Сила на одну точку от списка чужих точек.
    fun forceOn(point: ForcePoint, others: List<ForcePoint>): Vec2D {
        var fx = 0f
        var fy = 0f
        others.forEach { other ->
            val force = forceBetween(point, other)
            fx += force.x
            fy += force.y
        }
        return Vec2D(fx, fy)
    }

    private fun forceBetween(
        entity1: ForcePoint,
        entity2: ForcePoint,
    ):  Vec2D {
        if (entity1.electrons == 0 && entity1.protons == 0) return Vec2D(0f, 0f) // нечем ни притягиваться, ни отталкиваться
        val rx = entity1.position.x - entity2.position.x
        val ry = entity1.position.y - entity2.position.y
        val distance2 = rx*rx + ry*ry // это квадрат расстояния между частицами


        val maxRadius2 = (entity1.radius + entity2.radius) * (entity1.radius + entity2.radius) * 1.7
        // Если элементы находятся дальше этого расстояния, то они не влияют друг на друга
        if (distance2 > maxRadius2) return  Vec2D(0f, 0f)// вне радиуса действия

        // Если электроны есть только у одного элемента, то эти элементы будут притягиваться
        // Если электроны есть у обоих элементов, то будут отталкиваться
        val fAttraction = if (entity1.electrons > 0) { // отлично, у меня есть электроны. Проверим электроны соседа
            if (entity2.electrons > 0) { (entity1.electrons + entity2.electrons) / (distance2 + 10f) }   // у него тоже есть электроны, тогда я буду от него отталкиваться
            else { 0f } // у него электронов нет, я ничего не буду делать, пусть он сам притянется если нужно
        } else { // у меня электронов нет. Проверим, есть ли у него электроны
            if (entity2.electrons > 0) { -2 * entity2.electrons / (distance2 + 10f) } // у него есть электроны, значит я притянусь к нему
            else { 0f } // у него тоже нет электроноа, никакой силы нет
        }

        //val gravityForce = -1 * myMass * elementMass / (distance2 + 10f)
        val gravityForce = 0

        // Но если элементы подлетят слишком близко друг к другу, то протоны начнут отталкивать друг друга.
        val fRepulsion =if (entity1.protons == 0 || entity2.protons == 0) {
            0f // если протоны есть только у одного из нас, то отталкивания не будет
        } else {
            if (distance2 < (entity1.radius + entity2.radius) * (entity1.radius + entity2.radius)) {
                (entity1.protons + entity2.protons + 1)/(distance2 + 50f)
            }
            else 0f // протоны есть у обоих, но мы слишком далеко друг от друга
        }

        val fScalar = fAttraction + fRepulsion + gravityForce
        return Vec2D(rx * fScalar, ry * fScalar)
    }

}

enum class ElementType { SubAtom, Atom, Star }

enum class Element(val title: TranslatedText, val description: TranslatedText) {
    // --- субатомные частицы ---
    PHOTON(TranslatedText(ru="Фотон", en="Photon"), TranslatedText(ru = "", en = "")),
    ELECTRON(TranslatedText(ru="Электрон", en="Electron"), TranslatedText(ru = "", en = "")),
    NEUTRON(TranslatedText(ru="Нейтрон", en="Neutron"), TranslatedText(ru = "", en = "")),
    POSITRON(TranslatedText(ru="Позитрон", en="Positron"), TranslatedText(ru = "", en = "")),

    // --- атомы ---
    HYDROGEN(TranslatedText(ru="Водород", en="Hydrogen"), TranslatedText(ru = "", en = "")),
    DEUTERIUM(TranslatedText(ru="Дейтерий", en="Deuterium"), TranslatedText(ru = "", en = "")),
    HELIUM_3(TranslatedText(ru="Гелий", en="Helium"), TranslatedText(ru = "", en = "")),
    HELIUM_4(TranslatedText(ru="Гелий", en="Helium"), TranslatedText(ru = "", en = "")),
    LITHIUM_7(TranslatedText(ru="Литий", en="Lithium"), TranslatedText(ru = "", en = "")),
    LITHIUM_8(TranslatedText(ru="Литий", en="Lithium"), TranslatedText(ru = "", en = "")),
    BERYLLIUM_7(TranslatedText(ru="Бериллий", en="Beryllium"), TranslatedText(ru = "", en = "")),
    BERYLLIUM_8(TranslatedText(ru="Бериллий", en="Beryllium"), TranslatedText(ru = "", en = "")),
    BORON_8(TranslatedText(ru="Бор", en="Boron"), TranslatedText(ru = "", en = "")),
    CARBON_12(TranslatedText(ru="Углерод", en="Carbon"), TranslatedText(ru = "", en = "")),
    CARBON_13(TranslatedText(ru="Углерод", en="Carbon"), TranslatedText(ru = "", en = "")),
    CARBON_14(TranslatedText(ru="Углерод", en="Carbon"), TranslatedText(ru = "", en = "")),
    NITROGEN_13(TranslatedText(ru="Азот", en="Nitrogen"), TranslatedText(ru = "", en = "")),
    NITROGEN_14(TranslatedText(ru="Азот", en="Nitrogen"), TranslatedText(ru = "", en = "")),
    NITROGEN_15(TranslatedText(ru="Азот", en="Nitrogen"), TranslatedText(ru = "", en = "")),
    OXYGEN_15(TranslatedText(ru="Кислород", en="Oxygen"), TranslatedText(ru = "", en = "")),
    OXYGEN_16(TranslatedText(ru="Кислород", en="Oxygen"), TranslatedText(ru = "", en = "")),
    OXYGEN_17(TranslatedText(ru="Кислород", en="Oxygen"), TranslatedText(ru = "", en = "")),
    OXYGEN_18(TranslatedText(ru="Кислород", en="Oxygen"), TranslatedText(ru = "", en = "")),
    FLUORINE_17(TranslatedText(ru="Фтор", en="Fluorine"), TranslatedText(ru = "", en = "")),
    FLUORINE_18(TranslatedText(ru="Фтор", en="Fluorine"), TranslatedText(ru = "", en = "")),
    FLUORINE_19(TranslatedText(ru="Фтор", en="Fluorine"), TranslatedText(ru = "", en = "")),
    NEON_20(TranslatedText(ru="Неон", en="Neon"), TranslatedText(ru = "", en = "")),
    NEON_21(TranslatedText(ru="Неон", en="Neon"), TranslatedText(ru = "", en = "")),
    NEON_22(TranslatedText(ru="Неон", en="Neon"), TranslatedText(ru = "", en = "")),
    SODIUM_21(TranslatedText(ru="Натрий", en="Sodium"), TranslatedText(ru = "", en = "")),
    SODIUM_22(TranslatedText(ru="Натрий", en="Sodium"), TranslatedText(ru = "", en = "")),
    SODIUM_23(TranslatedText(ru="Натрий", en="Sodium"), TranslatedText(ru = "", en = "")),
    MAGNESIUM_23(TranslatedText(ru="Магний", en="Magnesium"), TranslatedText(ru = "", en = "")),
    MAGNESIUM_24(TranslatedText(ru="Магний", en="Magnesium"), TranslatedText(ru = "", en = "")),
    MAGNESIUM_25(TranslatedText(ru="Магний", en="Magnesium"), TranslatedText(ru = "", en = "")),
    MAGNESIUM_26(TranslatedText(ru="Магний", en="Magnesium"), TranslatedText(ru = "", en = "")),
    ALUMINUM_25(TranslatedText(ru="Алюминий", en="Aluminum"), TranslatedText(ru = "", en = "")),
    ALUMINUM_26(TranslatedText(ru="Алюминий", en="Aluminum"), TranslatedText(ru = "", en = "")),
    ALUMINUM_27(TranslatedText(ru="Алюминий", en="Aluminum"), TranslatedText(ru = "", en = "")),
    SILICON_28(TranslatedText(ru="Кремний", en="Silicon"), TranslatedText(ru = "", en = "")),
    SILICON_29(TranslatedText(ru="Кремний", en="Silicon"), TranslatedText(ru = "", en = "")),
    SILICON_30(TranslatedText(ru="Кремний", en="Silicon"), TranslatedText(ru = "", en = "")),
    SILICON_31(TranslatedText(ru="Кремний", en="Silicon"), TranslatedText(ru = "", en = "")),
    PHOSPHORUS_31(TranslatedText(ru="Фосфор", en="Phosphorus"), TranslatedText(ru = "", en = "")),
    SULFUR_31(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    SULFUR_32(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    SULFUR_33(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    SULFUR_34(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    SULFUR_35(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    CHLORINE_35(TranslatedText(ru="Хлор", en="Chlorine"), TranslatedText(ru = "", en = "")),
    CHLORINE_36(TranslatedText(ru="Хлор", en="Chlorine"), TranslatedText(ru = "", en = "")),
    CHLORINE_37(TranslatedText(ru="Хлор", en="Chlorine"), TranslatedText(ru = "", en = "")),
    ARGON_36(TranslatedText(ru="Аргон", en="Argon"), TranslatedText(ru = "", en = "")),
    ARGON_37(TranslatedText(ru="Аргон", en="Argon"), TranslatedText(ru = "", en = "")),
    ARGON_38(TranslatedText(ru="Аргон", en="Argon"), TranslatedText(ru = "", en = "")),
    ARGON_39(TranslatedText(ru="Аргон", en="Argon"), TranslatedText(ru = "", en = "")),
    POTASSIUM_39(TranslatedText(ru="Калий", en="Potassium"), TranslatedText(ru = "", en = "")),
    POTASSIUM_40(TranslatedText(ru="Калий", en="Potassium"), TranslatedText(ru = "", en = "")),
    POTASSIUM_41(TranslatedText(ru="Калий", en="Potassium"), TranslatedText(ru = "", en = "")),
    CALCIUM_40(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_41(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_42(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_43(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_44(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_45(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    SCANDIUM_45(TranslatedText(ru="Скандий", en="Scandium"), TranslatedText(ru = "", en = "")),
    SCANDIUM_46(TranslatedText(ru="Скандий", en="Scandium"), TranslatedText(ru = "", en = "")),
    TITANIUM_44(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_46(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_47(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_48(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_49(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_50(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_51(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    VANADIUM_48(TranslatedText(ru="Ванадий", en="Vanadium"), TranslatedText(ru = "", en = "")),
    VANADIUM_51(TranslatedText(ru="Ванадий", en="Vanadium"), TranslatedText(ru = "", en = "")),
    VANADIUM_52(TranslatedText(ru="Ванадий", en="Vanadium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_48(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_52(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_53(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_54(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_55(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    MANGANESE_52(TranslatedText(ru="Марганец", en="Manganese"), TranslatedText(ru = "", en = "")),
    MANGANESE_55(TranslatedText(ru="Марганец", en="Manganese"), TranslatedText(ru = "", en = "")),
    MANGANESE_56(TranslatedText(ru="Марганец", en="Manganese"), TranslatedText(ru = "", en = "")),
    IRON_52(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    IRON_56(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    IRON_57(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    IRON_58(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    IRON_59(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    COBALT_56(TranslatedText(ru="Кобальт", en="Cobalt"), TranslatedText(ru = "", en = "")),
    COBALT_59(TranslatedText(ru="Кобальт", en="Cobalt"), TranslatedText(ru = "", en = "")),
    COBALT_60(TranslatedText(ru="Кобальт", en="Cobalt"), TranslatedText(ru = "", en = "")),
    NICKEL_56(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    NICKEL_60(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    NICKEL_61(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    NICKEL_62(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    NICKEL_63(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    COPPER_63(TranslatedText(ru="Медь", en="Copper"), TranslatedText(ru = "", en = "")),
    COPPER_64(TranslatedText(ru="Медь", en="Copper"), TranslatedText(ru = "", en = "")),
    ZINC_64(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_65(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_66(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_67(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_68(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_69(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    GALLIUM_69(TranslatedText(ru="Галлий", en="Gallium"), TranslatedText(ru = "", en = "")),
    GALLIUM_70(TranslatedText(ru="Галлий", en="Gallium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_70(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_71(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_72(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_73(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_74(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_75(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    ARSENIC_75(TranslatedText(ru="Мышьяк", en="Arsenic"), TranslatedText(ru = "", en = "")),
    ARSENIC_76(TranslatedText(ru="Мышьяк", en="Arsenic"), TranslatedText(ru = "", en = "")),
    SELENIUM_76(TranslatedText(ru="Селен", en="Selenium"), TranslatedText(ru = "", en = "")),
    SELENIUM_77(TranslatedText(ru="Селен", en="Selenium"), TranslatedText(ru = "", en = "")),
    SELENIUM_78(TranslatedText(ru="Селен", en="Selenium"), TranslatedText(ru = "", en = "")),
    SELENIUM_79(TranslatedText(ru="Селен", en="Selenium"), TranslatedText(ru = "", en = "")),
    BROMINE_79(TranslatedText(ru="Бром", en="Bromine"), TranslatedText(ru = "", en = "")),
    BROMINE_80(TranslatedText(ru="Бром", en="Bromine"), TranslatedText(ru = "", en = "")),
    KRYPTON_80(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_81(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_82(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_83(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_84(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_85(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    RUBIDIUM_85(TranslatedText(ru="Рубидий", en="Rubidium"), TranslatedText(ru = "", en = "")),
    RUBIDIUM_86(TranslatedText(ru="Рубидий", en="Rubidium"), TranslatedText(ru = "", en = "")),
    STRONTIUM_86(TranslatedText(ru="Стронций", en="Strontium"), TranslatedText(ru = "", en = "")),
    STRONTIUM_87(TranslatedText(ru="Стронций", en="Strontium"), TranslatedText(ru = "", en = "")),
    STRONTIUM_88(TranslatedText(ru="Стронций", en="Strontium"), TranslatedText(ru = "", en = "")),
    STRONTIUM_89(TranslatedText(ru="Стронций", en="Strontium"), TranslatedText(ru = "", en = "")),
    YTTRIUM_89(TranslatedText(ru="Иттрий", en="Yttrium"), TranslatedText(ru = "", en = "")),
    YTTRIUM_90(TranslatedText(ru="Иттрий", en="Yttrium"), TranslatedText(ru = "", en = "")),
    ZIRCONIUM_90(TranslatedText(ru="Цирконий", en="Zirconium"), TranslatedText(ru = "", en = "")),
    ZIRCONIUM_91(TranslatedText(ru="Цирконий", en="Zirconium"), TranslatedText(ru = "", en = "")),
    ZIRCONIUM_92(TranslatedText(ru="Цирконий", en="Zirconium"), TranslatedText(ru = "", en = "")),
    ZIRCONIUM_93(TranslatedText(ru="Цирконий", en="Zirconium"), TranslatedText(ru = "", en = "")),
    NIOBIUM_93(TranslatedText(ru="Ниобий", en="Niobium"), TranslatedText(ru = "", en = "")),
    NIOBIUM_94(TranslatedText(ru="Ниобий", en="Niobium"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_94(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_95(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_96(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_97(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_98(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_99(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    TECHNETIUM_99(TranslatedText(ru="Технеций", en="Technetium"), TranslatedText(ru = "", en = "")),
    TECHNETIUM_100(TranslatedText(ru="Технеций", en="Technetium"), TranslatedText(ru = "", en = "")),
    RUTHENIUM_100(TranslatedText(ru="Рутений", en="Ruthenium"), TranslatedText(ru = "", en = "")),
    RUTHENIUM_101(TranslatedText(ru="Рутений", en="Ruthenium"), TranslatedText(ru = "", en = "")),
    RUTHENIUM_102(TranslatedText(ru="Рутений", en="Ruthenium"), TranslatedText(ru = "", en = "")),
    RUTHENIUM_103(TranslatedText(ru="Рутений", en="Ruthenium"), TranslatedText(ru = "", en = "")),
    RHODIUM_103(TranslatedText(ru="Родий", en="Rhodium"), TranslatedText(ru = "", en = "")),
    RHODIUM_104(TranslatedText(ru="Родий", en="Rhodium"), TranslatedText(ru = "", en = "")),
    PALLADIUM_104(TranslatedText(ru="Палладий", en="Palladium"), TranslatedText(ru = "", en = "")),
    PALLADIUM_105(TranslatedText(ru="Палладий", en="Palladium"), TranslatedText(ru = "", en = "")),
    PALLADIUM_106(TranslatedText(ru="Палладий", en="Palladium"), TranslatedText(ru = "", en = "")),
    PALLADIUM_107(TranslatedText(ru="Палладий", en="Palladium"), TranslatedText(ru = "", en = "")),
    SILVER_107(TranslatedText(ru="Серебро", en="Silver"), TranslatedText(ru = "", en = "")),
    SILVER_108(TranslatedText(ru="Серебро", en="Silver"), TranslatedText(ru = "", en = "")),
    CADMIUM_108(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_109(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_110(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_111(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_112(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_113(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_114(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_115(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    INDIUM_115(TranslatedText(ru="Индий", en="Indium"), TranslatedText(ru = "", en = "")),
    INDIUM_116(TranslatedText(ru="Индий", en="Indium"), TranslatedText(ru = "", en = "")),
    TIN_116(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_117(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_118(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_119(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_120(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_121(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    ANTIMONY_121(TranslatedText(ru="Сурьма", en="Antimony"), TranslatedText(ru = "", en = "")),
    ANTIMONY_122(TranslatedText(ru="Сурьма", en="Antimony"), TranslatedText(ru = "", en = "")),
    TELLURIUM_122(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_123(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_124(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_125(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_126(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_127(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    IODINE_127(TranslatedText(ru="Йод", en="Iodine"), TranslatedText(ru = "", en = "")),
    IODINE_128(TranslatedText(ru="Йод", en="Iodine"), TranslatedText(ru = "", en = "")),
    XENON_128(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_129(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_130(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_131(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_132(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_133(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    CESIUM_133(TranslatedText(ru="Цезий", en="Cesium"), TranslatedText(ru = "", en = "")),
    CESIUM_134(TranslatedText(ru="Цезий", en="Cesium"), TranslatedText(ru = "", en = "")),
    BARIUM_134(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_135(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_136(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_137(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_138(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_139(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    LANTHANUM_139(TranslatedText(ru="Лантан", en="Lanthanum"), TranslatedText(ru = "", en = "")),
    LANTHANUM_140(TranslatedText(ru="Лантан", en="Lanthanum"), TranslatedText(ru = "", en = "")),
    CERIUM_140(TranslatedText(ru="Церий", en="Cerium"), TranslatedText(ru = "", en = "")),
    CERIUM_141(TranslatedText(ru="Церий", en="Cerium"), TranslatedText(ru = "", en = "")),
    PRASEODYMIUM_141(TranslatedText(ru="Празеодим", en="Praseodymium"), TranslatedText(ru = "", en = "")),
    PRASEODYMIUM_142(TranslatedText(ru="Празеодим", en="Praseodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_142(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_143(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_144(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_145(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_146(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_147(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    PROMETHIUM_147(TranslatedText(ru="Прометий", en="Promethium"), TranslatedText(ru = "", en = "")),
    PROMETHIUM_148(TranslatedText(ru="Прометий", en="Promethium"), TranslatedText(ru = "", en = "")),
    SAMARIUM_148(TranslatedText(ru="Самарий", en="Samarium"), TranslatedText(ru = "", en = "")),
    SAMARIUM_149(TranslatedText(ru="Самарий", en="Samarium"), TranslatedText(ru = "", en = "")),
    SAMARIUM_150(TranslatedText(ru="Самарий", en="Samarium"), TranslatedText(ru = "", en = "")),
    SAMARIUM_151(TranslatedText(ru="Самарий", en="Samarium"), TranslatedText(ru = "", en = "")),
    EUROPIUM_151(TranslatedText(ru="Европий", en="Europium"), TranslatedText(ru = "", en = "")),
    EUROPIUM_152(TranslatedText(ru="Европий", en="Europium"), TranslatedText(ru = "", en = "")),
    EUROPIUM_153(TranslatedText(ru="Европий", en="Europium"), TranslatedText(ru = "", en = "")),
    EUROPIUM_154(TranslatedText(ru="Европий", en="Europium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_154(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_155(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_156(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_157(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_158(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_159(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    TERBIUM_159(TranslatedText(ru="Тербий", en="Terbium"), TranslatedText(ru = "", en = "")),
    TERBIUM_160(TranslatedText(ru="Тербий", en="Terbium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_160(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_161(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_162(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_163(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_164(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_165(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    HOLMIUM_165(TranslatedText(ru="Гольмий", en="Holmium"), TranslatedText(ru = "", en = "")),
    HOLMIUM_166(TranslatedText(ru="Гольмий", en="Holmium"), TranslatedText(ru = "", en = "")),
    ERBIUM_166(TranslatedText(ru="Эрбий", en="Erbium"), TranslatedText(ru = "", en = "")),
    ERBIUM_167(TranslatedText(ru="Эрбий", en="Erbium"), TranslatedText(ru = "", en = "")),
    ERBIUM_168(TranslatedText(ru="Эрбий", en="Erbium"), TranslatedText(ru = "", en = "")),
    ERBIUM_169(TranslatedText(ru="Эрбий", en="Erbium"), TranslatedText(ru = "", en = "")),
    THULIUM_169(TranslatedText(ru="Тулий", en="Thulium"), TranslatedText(ru = "", en = "")),
    THULIUM_170(TranslatedText(ru="Тулий", en="Thulium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_170(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_171(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_172(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_173(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_174(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_175(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    LUTETIUM_175(TranslatedText(ru="Лютеций", en="Lutetium"), TranslatedText(ru = "", en = "")),
    LUTETIUM_176(TranslatedText(ru="Лютеций", en="Lutetium"), TranslatedText(ru = "", en = "")),
    LUTETIUM_177(TranslatedText(ru="Лютеций", en="Lutetium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_177(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_178(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_179(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_180(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_181(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    TANTALUM_181(TranslatedText(ru="Тантал", en="Tantalum"), TranslatedText(ru = "", en = "")),
    TANTALUM_182(TranslatedText(ru="Тантал", en="Tantalum"), TranslatedText(ru = "", en = "")),
    TUNGSTEN_182(TranslatedText(ru="Вольфрам", en="Tungsten"), TranslatedText(ru = "", en = "")),
    TUNGSTEN_183(TranslatedText(ru="Вольфрам", en="Tungsten"), TranslatedText(ru = "", en = "")),
    TUNGSTEN_184(TranslatedText(ru="Вольфрам", en="Tungsten"), TranslatedText(ru = "", en = "")),
    TUNGSTEN_185(TranslatedText(ru="Вольфрам", en="Tungsten"), TranslatedText(ru = "", en = "")),
    RHENIUM_185(TranslatedText(ru="Рений", en="Rhenium"), TranslatedText(ru = "", en = "")),
    RHENIUM_186(TranslatedText(ru="Рений", en="Rhenium"), TranslatedText(ru = "", en = "")),
    OSMIUM_186(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_187(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_188(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_189(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_190(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_191(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    IRIDIUM_191(TranslatedText(ru="Иридий", en="Iridium"), TranslatedText(ru = "", en = "")),
    IRIDIUM_192(TranslatedText(ru="Иридий", en="Iridium"), TranslatedText(ru = "", en = "")),
    PLATINUM_192(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_193(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_194(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_195(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_196(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_197(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    GOLD_197(TranslatedText(ru="Золото", en="Gold"), TranslatedText(ru = "", en = "")),
    GOLD_198(TranslatedText(ru="Золото", en="Gold"), TranslatedText(ru = "", en = "")),
    MERCURY_198(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_199(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_200(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_201(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_202(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_203(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    THALLIUM_203(TranslatedText(ru="Таллий", en="Thallium"), TranslatedText(ru = "", en = "")),
    THALLIUM_204(TranslatedText(ru="Таллий", en="Thallium"), TranslatedText(ru = "", en = "")),
    LEAD_204(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_205(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_206(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_207(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_208(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_209(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    BISMUTH_209(TranslatedText(ru="Висмут", en="Bismuth"), TranslatedText(ru = "", en = "")),
    BISMUTH_210(TranslatedText(ru="Висмут", en="Bismuth"), TranslatedText(ru = "", en = "")),
    POLONIUM_210(TranslatedText(ru="Полоний", en="Polonium"), TranslatedText(ru = "", en = "")),

    Star(TranslatedText(ru="Звезда", en="Star"), TranslatedText(ru = "", en = ""));

    val details: Details get() = detailsMap[this]!!

    // Символ/лейбл как функция от числа электронов (рефакторинг ионизации, шаг 2B).
    // Для атомов вычисляем заряд (p − electrons); у не-атомов символ/лейбл фиксированы — отдаём литерал.
    fun symbol(electrons: Int): String =
        if (details.type == ElementType.Atom) baseSymbolMap.getValue(this) + chargeSuffix(details.p - electrons)
        else details.symbol

    fun label(electrons: Int): String =
        if (details.type == ElementType.Atom) "${title.en} (${symbol(electrons)})"
        else details.label

    /**
     * Химический символ без масс-индекса и заряда: ²H→H, ¹²C→C, ³He→He. Изотопы схлопываются — этим
     * отличается от [symbol], который заряд показывает, и от baseSymbolMap, который срезает только его.
     * Нужен там, где важен ЭЛЕМЕНТ, а не изотоп: брутто-формула молекулы и подпись в кружке атома.
     */
    val bareSymbol: String get() = bareSymbolMap.getValue(this)

    // Энергетические уровни как функция от числа электронов (рефакторинг ионизации, 2C2b-4).
    // Зависят только от Z, не от N → одна лестница на элемент (atomEnergyLevelsByZ по details.p), общая
    // для всех изотопов и только для атомов. Голым/несуществующим состояниям — пустой список («нельзя ионизировать»).
    fun energyLevels(electrons: Int): List<Float> =
        if (details.type == ElementType.Atom) {
            atomEnergyLevelsByZ[details.p]?.getOrNull(electrons) ?: emptyList()
        } else {
            emptyList()
        }

    // Валентность = сколько ковалентных связей атом может образовать: заполнение оболочек (2/8/8)
    fun valence(electrons: Int): Int {
        if (details.p - electrons > MAX_BONDING_CHARGE) return 0   // если атом теряет больше 1 электрона, то в ковалентную связь он уже вступать не может
        var remaining = electrons   // сколько электронов ещё не разложено по оболочкам
        for (capacity in intArrayOf(2, 8, 8)) {
            if (remaining <= capacity) {
                return when {
                    remaining == capacity -> 0        // полная внешняя оболочка — благородный газ
                    remaining <= 4 -> remaining        // отдаёт/делит валентные электроны
                    else -> 8 - remaining              // достраивает до октета
                }
            }
            remaining -= capacity
        }
        return 0   // electrons > 18 — октет не применим → ковалентно не связывается
    }

    companion object {
        // Каталог Details вынесен в ElementDetails.kt. Делёж на light/heavy/heaviest — ради лимита JVM 64KB на байткод метода.
        private val detailsMap: Map<Element, Details> = elementDetails()

        // Энергетические лестницы ионизации по Z (одна на элемент, общая для изотопов). Опора energyLevels(electrons).
        private val atomEnergyLevelsByZ: Map<Int, List<List<Float>>> = atomEnergyLevelsTable()

        // База для symbol(e). Пока каталог не свёрнут — выводим из существующего symbol срезанием
        // (на шаге 2C станет хранимым полем изотопа). Считается один раз.
        private val baseSymbolMap: Map<Element, String> =
            entries.filter { it.details.type == ElementType.Atom }.associateWith { stripCharge(it.details.symbol) }

        // Опора bareSymbol. Без фильтра по типу, в отличие от соседей: подпись нужна и частицам, и звёздам.
        // Считается один раз — спрашивают на каждый атом каждый кадр (рендер) и на каждую формулу.
        private val bareSymbolMap: Map<Element, String> =
            entries.associateWith { element -> element.details.symbol.filter { it.isLetter() } }
    }

}

/**
 * Потолок заряда, при котором атом ещё ведёт ковалентную химию (см. [Element.valence]).
 * +1 — реальные однозарядные катионы: CH₃⁺, NH₄⁺, H₃O⁺. Двухзарядные молекулярные катионы существуют,
 * но это экзотика — обычно разлетаются кулоновским взрывом, а не связываются; выше +2 ковалентной химии нет.
 */
private const val MAX_BONDING_CHARGE = 1

private const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

const val MAX_VELOCITY = 10f

// Число → надстрочные цифры: 29 → "²⁹".
private fun sup(n: Int): String = n.toString().map { SUPERSCRIPT_DIGITS[it - '0'] }.joinToString("")

// Надстрочный заряд иона: 0 → "", 1 → "⁺", n≥2 → "ⁿ⁺" (конвенция: +1 без цифры).
// internal (не private): переиспользуется в Molecule.displaySymbol для заряда молекулы-иона.
internal fun chargeSuffix(charge: Int): String = when {
    charge <= 0 -> ""
    charge == 1 -> "⁺"
    else -> sup(charge) + "⁺"
}

// Базовый символ нуклида без заряда: срезаем хвостовой "⁺" и хвостовые надстрочные цифры заряда.
// Массовый индекс-префикс ("¹²C") не трогается — он стоит перед буквой элемента.
private fun stripCharge(symbol: String): String = if (symbol.endsWith("⁺")) symbol.dropLast(1).trimEnd { it in SUPERSCRIPT_DIGITS } else symbol

fun canGainElectron(element: Element, electrons: Int): Boolean = element.details.type == ElementType.Atom && electrons < element.details.p
fun Entity.isBareNucleus(of: Element): Boolean = this is Atom && element == of && electrons == 0

data class Details(
    val type: ElementType,
    val symbol: String,
    val label: String,
    val p: Int, // Количество протонов в элементе
    val n: Int, // Количество нейтронов в элементе
    val radius: Float = 40f,
    val covalentRadiusPm: Int? = null, // ковалентный: ½ длины связи — геометрия связанного атома в молекуле
    val vdwRadiusPm: Int? = null,      // ван-дер-ваальсов: ½ дистанции касания несвязанных — «размер» одинокого атома


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

    val alphaDecayResult: Element? = null, // α-распад. Ядро испускает ⁴He²⁺ (голое ядро гелия): A(Z) → A′(Z-2) + ⁴He. Замыкает свинцово-висмутовый цикл s-процесса: ²¹⁰Po → ²⁰⁶Pb + α. Generic — по образцу betaMinusDecayResult.
)


