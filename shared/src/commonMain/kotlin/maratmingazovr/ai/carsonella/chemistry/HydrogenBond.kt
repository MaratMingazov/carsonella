package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.ChangeNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.ChangeSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.DeathNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentAware
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.LogWritable
import maratmingazovr.ai.carsonella.chemistry.behavior.LoggingSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsAware
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.OnDeathSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequestSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequester
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Водородная связь — МЕЖмолекулярное взаимодействие: тянет два атома РАЗНЫХ молекул, но новой молекулы не образует.
 */
class HydrogenBond(
    override val id: Long,
    energy: Float,
    val molecule1: Molecule,
    val localId1: Int,
    val molecule2: Molecule,
    val localId2: Int,
):
    Entity,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport(),
    ChangeNotifiable by ChangeSupport() {

    override var alive: Boolean = true
        private set
    override val protons: Int = 0
    override val electrons: Int = 0
    override val mass: Float = 0f
    var energy: Float = energy // Накопленная энергия (эВ). Дошла до HYDROGEN_BOND_ENERGY_EV — связь рвётся.
        set(value) { field = value.coerceAtLeast(0f); markChanged() }

    val atom1: MoleculeAtom get() = molecule1.atom(localId1)
    val atom2: MoleculeAtom get() = molecule2.atom(localId2)
    val restLength: Float get() = hydrogenBondRestLength(atom1, atom2) /** Длина покоя пружины: дальше — тянет к себе, ближе — расталкивает. */
    val length: Float get() = atom1.kinematics.position.distanceTo(atom2.kinematics.position)
    val center: Position get() {
        val p1 = atom1.kinematics.position
        val p2 = atom2.kinematics.position
        return Position((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    }

    override val displaySymbol: String get() = "${atom1.isotope.bareSymbol}···${atom2.isotope.bareSymbol}"
    override val energyLevels: List<Float> = emptyList() // ионизовать связь нельзя: электронов у неё нет
    override val saveKey: String = "HYDROGEN_BOND"
    
    override fun distanceToSurface(point: Position): Float = distanceToSegment(point, atom1.kinematics.position, atom2.kinematics.position) // Кликается: hit-test берёт расстояние до ОТРЕЗКА, иначе связь ловила бы курсор по всему холсту.
    override fun distanceSquareTo(point: Position): Float = point.distanceSquareTo(center) // «Далеко ли ты отсюда» для фильтров соседей — от середины связи.
    override fun forcePoints(): List<ForcePoint> = emptyList() // в полевых силах связь не участвует — она сама и есть сила

    override fun describe(): String {
        return """
            |Hydrogen bond ${atom1.isotope.bareSymbol}···${atom2.isotope.bareSymbol}
            |Length ${round(length)} (rest ${round(restLength)})
            |Energy ${round(energy * 100) / 100} / $HYDROGEN_BOND_ENERGY_EV eV
        """.trimMargin()
    }

    override fun step() {
        // Связь живёт, пока живы оба конца. Молекулы при реакциях не мутируют, а ЗАМЕНЯЮТСЯ
        // (рост убивает старую и рождает новую), поэтому любая химия рядом рвёт связь — так и надо.
        if (!molecule1.alive || !molecule2.alive) { destroy(); return }
        if (energy >= HYDROGEN_BOND_ENERGY_EV) { destroy(); return }
        // Сетка безопасности от дубля: обе молекулы могут инициировать связь в один тик. Умирает тот, у
        // кого id больше, — иначе оба убьют друг друга.
        if (hasOlderTwin()) { destroy(); return }
        applySpringForce()
    }

    override fun destroy() {
        if (!alive) return
        alive = false
        markChanged()
        notifyDeath()
    }

    /** Соединяет ли связь ту же пару атомов (в любом порядке концов). Правило спрашивает до создания связи. */
    fun connectsSamePair(moleculeId1: Long, localId1: Int, moleculeId2: Long, localId2: Int): Boolean =
        (molecule1.id == moleculeId1 && this.localId1 == localId1 && molecule2.id == moleculeId2 && this.localId2 == localId2) ||
        (molecule1.id == moleculeId2 && this.localId1 == localId2 && molecule2.id == moleculeId1 && this.localId2 == localId1)

    fun connectsSamePair(other: HydrogenBond): Boolean = connectsSamePair(other.molecule1.id, other.localId1, other.molecule2.id, other.localId2)

    private fun hasOlderTwin(): Boolean = getNeighbors()
        .filterIsInstance<HydrogenBond>()
        .any { it.alive && it.id < id && connectsSamePair(it) }

    // Пружина по образцу Molecule.applyInternalForces, только мягче: демпфер по относительной скорости
    // пары, иначе связь звенела бы вечно (reduceVelocity гасит абсолютную скорость, а не колебание).
    private fun applySpringForce() {
        val a = atom1
        val b = atom2
        val dx = b.kinematics.position.x - a.kinematics.position.x
        val dy = b.kinematics.position.y - a.kinematics.position.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < 1e-3f) return // атомы совпали — направление не определено

        val ux = dx / distance
        val uy = dy / distance
        val stretch = distance - restLength
        val relative = b.kinematics.direction * b.kinematics.velocity - a.kinematics.direction * a.kinematics.velocity
        val separationSpeed = relative.x * ux + relative.y * uy
        val reducedMass = a.mass * b.mass / (a.mass + b.mass)
        val magnitude = HYDROGEN_BOND_STIFFNESS * stretch + HYDROGEN_BOND_DAMPING * reducedMass * separationSpeed

        molecule1.applyForceToAtom(a, Vec2D(ux * magnitude, uy * magnitude))   // растянуто → тянет к соседу
        molecule2.applyForceToAtom(b, Vec2D(-ux * magnitude, -uy * magnitude)) // сжато → знак меняется, расталкивает
    }

    private fun distanceToSegment(point: Position, a: Position, b: Position): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSquare = abx * abx + aby * aby
        if (lengthSquare < 1e-6f) return point.distanceTo(a)
        val t = (((point.x - a.x) * abx + (point.y - a.y) * aby) / lengthSquare).coerceIn(0f, 1f)
        return point.distanceTo(Position(a.x + abx * t, a.y + aby * t))
    }
}

/**
 * Энергия водородной связи (эВ). Связь в димере воды ~21 кДж/моль ÷ 96.485 → 0.22 эВ, то есть в двадцать
 * раз слабее ковалентной O–H (4.80 эВ в [maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy]).
 * Отсюда и главный образовательный факт: ИК-фотон рвёт водородную связь, но ковалентную не берёт.
 */
const val HYDROGEN_BOND_ENERGY_EV = 0.22f

// Длина покоя как доля от суммы радиусов. В реальности H···O (190 pm) вдвое длиннее ковалентной O–H
// (96 pm); здесь пока 1.5 — при таких радиусах это ~112 px против ~100 px у ковалентной, то есть связь
// заметно короче настоящей. Оставлено намеренно: гейты запроса реакции пока рассчитаны на близкие пары.
private const val HYDROGEN_BOND_LENGTH_FACTOR = 1.5f

// Жёсткость: ковалентная пружина — 0.05 на ~4.8 эВ, значит на 0.22 эВ приходится ~1/24 от неё.
private const val HYDROGEN_BOND_STIFFNESS = 0.002f
private const val HYDROGEN_BOND_DAMPING = 0.2f // как PAIR_DAMPING у молекулы: держать < 1, иначе демпфер сам раскачивает пару

/** Длина покоя связи между этой парой атомов. Наружу — по ней же правило решает, достаточно ли они сблизились. */
fun hydrogenBondRestLength(a: MoleculeAtom, b: MoleculeAtom): Float = HYDROGEN_BOND_LENGTH_FACTOR * (a.radius + b.radius)