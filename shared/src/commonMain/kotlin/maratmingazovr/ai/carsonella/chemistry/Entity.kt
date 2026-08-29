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
import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement

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
            if (entity2.electrons > 0) { (entity1.electrons + entity2.electrons) / (distance2) }   // у него тоже есть электроны, тогда я буду от него отталкиваться
            else { 0f } // у него электронов нет, я ничего не буду делать, пусть он сам притянется если нужно
        } else { // у меня электронов нет. Проверим, есть ли у него электроны
            if (entity2.electrons > 0) { -2 * entity2.electrons / (distance2) } // у него есть электроны, значит я притянусь к нему
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



const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

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


fun canGainElectron(element: AtomElement, electrons: Int): Boolean = element.details.type == ElementType.Atom && electrons < element.details.p
fun Entity.isBareNucleus(of: AtomElement): Boolean = this is Atom && element == of && electrons == 0




