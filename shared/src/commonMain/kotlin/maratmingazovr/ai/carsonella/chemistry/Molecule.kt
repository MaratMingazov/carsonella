package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.KnownMolecule
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry
import kotlin.math.round
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGeometry
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.Float
import kotlin.math.sqrt


class MoleculeAtom(
    val localId: Int,
    val isotope: Element,
    var kinematics: Kinematics,
) {
    val radius: Float = isotope.details.radius
    val mass: Float = (isotope.details.p + isotope.details.n).toFloat() // нуклоны, как у Atom.mass

    fun forcePoint(): ForcePoint { return ForcePoint(kinematics.position, radius, electrons = isotope.details.p, protons = isotope.details.p) }
}

data class MoleculeBond(
    val localId1: Int,
    val localId2: Int,
    val order: Int,
    val energy: Float?, // Энергия связи (эВ) — сколько нужно, чтобы её разорвать. null — тип связи не в каталоге
    val inRing: Boolean, // Лежит в цикле: разрыв раскроет кольцо, а не развалит молекулу на осколки
)

data class MoleculeShape(
    val atoms: List<MoleculeAtom>,
    val bonds: List<MoleculeBond>,
)

class Molecule private constructor(
    override val id: Long,
    private var graph: MoleculeGraph,
    atoms: List<MoleculeAtom>, // уже расставленные: позиции приходят от источников, раскладка графа не нужна
    energy: Float,
    electrons: Int,
):
    Entity,
    Movable,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport(),
    ChangeNotifiable by ChangeSupport()
{

    constructor(id: Long, atom1: Atom, atom2: Atom) : this(
        id = id,
        graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, atom1.element), AtomNode(1, atom2.element)),
            bonds = listOf(Bond(0, 1, order = 1)),
        ),
        atoms = listOf(MoleculeAtom(0, atom1.element, atom1.kinematics), MoleculeAtom(1, atom2.element, atom2.kinematics)),
        energy = atom1.energy + atom2.energy,
        electrons = atom1.electrons + atom2.electrons,
    )

    constructor(id: Long, molecule1: Molecule, atom1: MoleculeAtom, molecule2: Molecule, atom2: MoleculeAtom) : this(
        id = id,
        graph = molecule1.graph.merge(molecule2.graph, thisNode = atom1.localId, otherNode = atom2.localId, bondOrder = 1),
        atoms = molecule1.atoms.map { MoleculeAtom(it.localId, it.isotope, it.kinematics) } + molecule2.atoms.map { MoleculeAtom(it.localId + molecule1.graph.mergeOffset(), it.isotope, it.kinematics) },
        energy = molecule1.energy + molecule2.energy,
        electrons = molecule1.electrons + molecule2.electrons,
    )

    /** Молекула растет путен добавления нового атома */
    constructor(id: Long, molecule: Molecule, atom: MoleculeAtom, newAtom: Atom) : this(
        id = id,
        graph = molecule.graph.merge( MoleculeGraph(nodes = listOf(AtomNode(0, newAtom.element)), bonds = emptyList()), thisNode = atom.localId, otherNode = 0, bondOrder = 1),
        atoms = molecule.atoms.map { MoleculeAtom(it.localId, it.isotope, it.kinematics) } + MoleculeAtom(molecule.graph.mergeOffset(), newAtom.element, newAtom.kinematics),
        energy = molecule.energy + newAtom.energy,
        electrons = molecule.electrons + newAtom.electrons,
    )


    constructor(id: Long, shape: MoleculeShape, energy: Float, electrons: Int) : this(
        id = id,
        graph = MoleculeGraph(
            nodes = shape.atoms.map { AtomNode(it.localId, it.isotope) },
            bonds = shape.bonds.map { Bond(it.localId1, it.localId2, it.order) },
        ),
        atoms = shape.atoms.map { MoleculeAtom(it.localId, it.isotope, it.kinematics) },
        energy = energy,
        electrons = electrons,
    )

    private val atomsById: Map<Int, MoleculeAtom> = atoms.associateBy { it.localId }

    init {
        // Страховка на перенумерацию: merge сдвигает номера узлов второго графа, и промахнуться тут
        // легко, а промах вылез бы далеко от места ошибки — на первом же getValue в рендере.
        require(atomsById.keys == graph.nodes.mapTo(HashSet()) { it.localId }) {
            "Атомы и узлы графа разошлись: атомы ${atomsById.keys.sorted()}, узлы ${graph.nodes.map { it.localId }.sorted()}"
        }
    }

    override val kinematics: Kinematics
        get() {
            val first = atomsById.getValue(atomsById.keys.min())   // наименьший localId — не зависит от порядка обхода map
            return Kinematics(first.kinematics.position, Vec2D(0f, 0f), 0f)
        }

    // ДВИЖЕНИЕ. Пятёрка Movable разложена по атомам: сеттера кинематики у молекулы нет, потому что
    // «поставить молекуле скорость» мимо её атомов — бессмыслица. Внешнее воздействие приходит на тело
    // целиком и раздаётся всем поровну; собственное движение атомов (пружины) оно не трогает.
    override fun applyNewPosition() {
        if (atomsById.values.none { it.kinematics.velocity > 0f }) return
        for (atom in atomsById.values) {
            val k = atom.kinematics
            atom.kinematics = k.copy(position = Position(
                k.position.x + k.direction.x * k.velocity,
                k.position.y + k.direction.y * k.velocity,
            ))
        }
        markChanged()
    }
    override fun moveBy(delta: Vec2D) {
        for (atom in atomsById.values) {
            val k = atom.kinematics
            atom.kinematics = k.copy(position = k.position.addVelocity(delta), velocity = 0f)
        }
        markChanged()
    } // Игрок «берёт и кладёт»: все атомы едут одним сдвигом (форма цела), скорость гасится

    override fun reduceVelocity() {
        if (atomsById.values.none { it.kinematics.velocity > 0f }) return
        for (atom in atomsById.values) {
            val k = atom.kinematics
            atom.kinematics = k.copy(velocity = if (k.velocity < INTERNAL_VELOCITY_EPS) 0f else k.velocity * 0.99f)
        }
        markChanged()
    }

    override fun checkBorders(env: IEnvironment) {
        val envCenter = env.getEnvCenter()
        val radiusX = env.getEnvRadius()
        val radiusY = env.getEnvRadiusY()
        if (radiusX <= 0f || radiusY <= 0f) return   // границы ещё не заданы (канва не измерена)
        var corrected = false
        for (atom in atomsById.values) {
            val k = atom.kinematics
            val dx = k.position.x - envCenter.x
            val dy = k.position.y - envCenter.y
            val outside = (dx / radiusX) * (dx / radiusX) + (dy / radiusY) * (dy / radiusY)
            if (outside <= 1f) continue // этот атом внутри
            val scale = sqrt(outside)
            var nx = dx / (radiusX * radiusX)    // нормаль к эллипсу; у окружности — тот же радиус-вектор
            var ny = dy / (radiusY * radiusY)
            val normalLength = sqrt(nx * nx + ny * ny)
            if (normalLength > 1e-6f) { nx /= normalLength; ny /= normalLength }
            val dot = k.direction.x * nx + k.direction.y * ny
            atom.kinematics = k.copy(
                position = Position(envCenter.x + dx / scale, envCenter.y + dy / scale),
                direction = Vec2D(k.direction.x - 2 * dot * nx, k.direction.y - 2 * dot * ny),
            )
            corrected = true
        }
        if (corrected) markChanged()
    }

    override fun applyForce(force: Vec2D) {
        if (mass < 0.001f) return
        val a = force.div(mass) // сила приложена к телу целиком → ускорение у всех атомов одинаковое
        for (atom in atomsById.values) {
            val k = atom.kinematics
            val velocityVector = k.direction.times(k.velocity).plus(a)
            val velocity = velocityVector.length()
            atom.kinematics = k.copy(
                direction = if (velocity > 1e-6f) velocityVector.div(velocity) else k.direction,
                velocity = velocity,
            )
        }
        markChanged()
    }

    override var alive: Boolean = true
        private set
    override val mass: Float get() = graph.mass
    override val protons: Int get() = graph.protons
    override var electrons: Int = electrons
        set(value) { field = value; markChanged() }
    var energy: Float = energy // Внутренняя (колебательная) энергия — квазинепрерывная, в отличие от дискретных уровней атома.
        set(value) { field = value.coerceAtLeast(0f); markChanged() }
    override val displaySymbol: String get() = graph.formulaPretty + chargeSuffix(graph.protons - electrons)
    override val energyLevels: List<Float> get() = graph.energyLevels
    override val saveKey: String get() = graph.formula

    override fun distanceToSurface(point: Position): Float = atoms.minOf { it.kinematics.position.distanceTo(point) - it.radius } // Молекула не кружок: берём ближайший АТОМ.
    override fun distanceSquareTo(point: Position): Float = atomsById.values.minOf { it.kinematics.position.distanceSquareTo(point) } // Своей позиции у молекулы нет — отвечает ближайший атом.
    val known: KnownMolecule? get() = MoleculeRegistry.lookup(graph.canonical) // Запись реестра, если молекула известна. Безымянная (её нет в реестре) — это норма, а не ошибка.

    override fun describe(): String {
        val known = known   // локальная копия: у свойства свой геттер, без неё нет смарт-каста
        val lines = mutableListOf(
            if (known != null) "${known.nameEn} (${graph.formulaPretty})" else graph.formulaPretty,
        )
        if (known != null) lines += known.nameRu
        if (known != null && known.structuralFormula.isNotEmpty()) lines += known.structuralFormula
        //if (known != null && known.description.isNotEmpty()) lines += known.description
        lines += "Energy ${round(energy * 100) / 100}"
        dissociationEnergy?.let { energy ->
            lines += "Weakest bond ${round(energy * 100) / 100} eV"
        }
        return lines.joinToString("\n")
    }
    override fun step() {
        val neighbors = getNeighbors()
        val environment = getEnvironment()

        if (neighbors.isNotEmpty()) {
            val others = neighbors.flatMap { it.forcePoints() }
            for (atom in atomsById.values) atom.applyForce(forceOn(atom.forcePoint(), others))
        }
        applyInternalForces()
        reduceVelocity()

        var moved = false
        for (atom in atomsById.values) if (atom.move()) moved = true
        if (moved) markChanged() // успокоилась — перестаём будить рендер
        checkBorders(environment)

        neighbors
            .filter { entity -> atomsById.values.any { atom -> entity.distanceSquareTo(atom.kinematics.position) < 10000f } }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }

        if (energy > 0f) {
            requestReaction(listOf(this))
        } // Спонтанный сброс внутренней энергии

        if (environment.getEnvTemperature() == TemperatureMode.Star) { requestReaction(listOf(this)) } // В звезде (TemperatureMode.Star) молекула термически распадается
    }
    override fun destroy() {
        if (!alive) return
        alive = false
        markChanged()
        notifyDeath()
    }
    override fun forcePoints(): List<ForcePoint> = atoms.map { it.forcePoint() } // Молекула участвует в силах  каждым атомом

    ////////////////////////////////////////////////////////////
    // ФУНКЦИИ - ТОЛЬКО НА ОСНОВНЕ ГРАФА. ДАННЫЕ ЗАКЭШИРОВАНЫ //
    val hasFreeValence: Boolean get() = graph.hasFreeValence
    val canCloseRing: Boolean get() = graph.hasRingClosureCandidate // Есть ли пара атомов, между которыми можно замкнуть цикл. Дешёвая проверка: кандидаты кеширует граф.
    val dissociationEnergy: Float? get() = graph.weakestBondAndEnergy?.second // ПОРОГ ДИССОЦИАЦИИ (эВ) — энергия слабейшей связи

    ///////////////////////////////////////////////////////////////
    // ФУНКЦИИ - ГРАФ + КИНЕМАТИКА. ДАННЫЕ ВЫЧИСЛЯЮТСЯ КАЖДЫЙ РАЗ //
    val shape: MoleculeShape get() = createMoleculeShape(graph)
    val atoms: List<MoleculeAtom> get() = createMoleculeAtoms(graph)
    val bonds: List<MoleculeBond> get() = createMoleculeBonds(graph, graph.bonds) // Все связи, поставленные в мир. У живой молекулы их всегда ≥ 1: атомов ≥ 2 и граф связен.
    val weakestBond: MoleculeBond? get() = graph.weakestBondAndEnergy?.let { (bond, _) -> createMoleculeBonds(graph, listOf(bond)).single() } // Слабейшая связь — она рвётся первой при распаде.
    fun weakestBondAt(atom: MoleculeAtom): Pair<MoleculeBond, Float>? = graph.bonds
        .filter { it.atom1 == atom.localId || it.atom2 == atom.localId }
        .mapNotNull { bond -> graph.energyOf(bond)?.let { energy -> bond to energy } }
        .minByOrNull { (_, energy) -> energy }
        ?.let { (bond, energy) -> createMoleculeBonds(graph, listOf(bond)).single() to energy }
    val strengthenableBonds: List<MoleculeBond> get() = createMoleculeBonds(graph, graph.strengthenableBonds) // Связи, которые можно усилить (кратность +1).
    fun canCloseRing(localId1: Int, localId2: Int): Boolean = graph.canCloseRing(localId1, localId2) // Можно ли замкнуть кольцо между ЭТОЙ парой атомов
    fun atom(localId: Int): MoleculeAtom = atomsById.getValue(localId) // Атом по номеру узла: им адресуют концы MoleculeBond.
    fun freeValence(atom: MoleculeAtom): Int = graph.freeValence(atom.localId) // Сколько связей атом ещё может образовать или усилить В ЭТОЙ молекуле. Живёт на графе, а не на атоме: меняется при усилении связи и замыкании кольца, хранимое поле протухло бы.
    val freeValenceAtoms: List<MoleculeAtom> get() = atoms.filter { freeValence(it) > 0 }
    fun split(bond: MoleculeBond): List<MoleculeShape> = graph.split(bond.localId1, bond.localId2).map { fragment -> createMoleculeShape(fragment) }



    private fun applyInternalForces() {
        val all = atoms
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                val a = all[i]
                val b = all[j]
                val order = bondOrderBetween(a.localId, b.localId)
                val restLength = MoleculeGeometry.bondLengthPx(a.isotope, b.isotope, order ?: 1)

                val dx = b.kinematics.position.x - a.kinematics.position.x
                val dy = b.kinematics.position.y - a.kinematics.position.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance < 1e-3f) continue // атомы совпали — направление не определено

                val stretch = distance - restLength
                if (order == null && stretch >= 0f) continue // несвязанные только расталкиваются

                val ux = dx / distance
                val uy = dy / distance
                // Скорость расхождения пары (вдоль их оси) и демпфер по ней: c = PAIR_DAMPING · m_прив.
                val relative = b.kinematics.direction * b.kinematics.velocity - a.kinematics.direction * a.kinematics.velocity
                val separationSpeed = relative.x * ux + relative.y * uy
                val reducedMass = a.mass * b.mass / (a.mass + b.mass)
                val magnitude = BOND_STIFFNESS * stretch + PAIR_DAMPING * reducedMass * separationSpeed
                a.applyForce(Vec2D(ux * magnitude, uy * magnitude))   // растянуто → тянет к соседу
                b.applyForce(Vec2D(-ux * magnitude, -uy * magnitude)) // сжато → знак меняется, расталкивает
            }
        }
    }
    private fun bondOrderBetween(localId1: Int, localId2: Int): Int? = graph.bonds
        .firstOrNull { (it.atom1 == localId1 && it.atom2 == localId2) || (it.atom1 == localId2 && it.atom2 == localId1) }
        ?.order
    private fun MoleculeAtom.applyForce(force: Vec2D) {
        if (mass < 0.001f) return
        val velocityVector = kinematics.direction.times(kinematics.velocity).plus(force.div(mass))
        val velocity = velocityVector.length().coerceAtMost(MAX_VELOCITY)
        kinematics = kinematics.copy(
            direction = if (velocity > 1e-6f) velocityVector.normalized() else kinematics.direction,
            velocity = velocity,
        )
    }
    private fun MoleculeAtom.move(): Boolean {
        val velocity = kinematics.velocity
        if (velocity == 0f) return false
        kinematics = kinematics.copy(
            position = Position(
                kinematics.position.x + kinematics.direction.x * velocity,
                kinematics.position.y + kinematics.direction.y * velocity,
            ),
        )
        return true
    }

    private fun createMoleculeShape(graph: MoleculeGraph): MoleculeShape = MoleculeShape(createMoleculeAtoms(graph), createMoleculeBonds(graph, graph.bonds))
    private fun createMoleculeAtoms(graph: MoleculeGraph): List<MoleculeAtom> = graph.nodes.map { node -> atomsById.getValue(node.localId) }
    private fun createMoleculeBonds(graph: MoleculeGraph, bonds: List<Bond>): List<MoleculeBond> = bonds.map { MoleculeBond(it.atom1, it.atom2, it.order, graph.energyOf(it), graph.isRingBond(it)) }


    ////////////////////////////////
    // ФУНКЦИИ - ОБНОВЛЕНИЕ ГРАФА //
    fun strengthenBond(bond: MoleculeBond) {
        val id1 = bond.localId1
        val id2 = bond.localId2
        require(graph.freeValence(id1) > 0 && graph.freeValence(id2) > 0) {
            "Связь $id1–$id2 в ${graph.formula} не усилить: у конца нет свободного слота"
        }
        graph = graph.strengthenBond(id1, id2)
        markChanged()
    } // Усиливаем связь bond: её кратность растёт на 1 (O–O → O=O, N=N → N≡N).
    fun closeRing(localId1: Int, localId2: Int) {
        require(graph.freeValence(localId1) > 0 && graph.freeValence(localId2) > 0) {
            "Кольцо $localId1–$localId2 в ${graph.formula} не замкнуть: у конца нет свободного слота"
        }
        graph = graph.closeRing(localId1, localId2)
        markChanged()
    } // Замыкание кольца: связываем два НЕСОСЕДНИХ атома молекулы → цикл (C–C–C–C–C → циклопентан).
    fun openRing(bond: MoleculeBond) {
        graph = graph.removeRingBond(bond.localId1, bond.localId2)
        markChanged()
    } // Раскрытие кольца: рвём связь, лежащую в цикле → цикл разворачивается в цепь


    ////////////////////////////////




}

private const val BOND_STIFFNESS = 0.05f // Жёсткость связи-пружины. Насколько быстро расходятся атомы
private const val PAIR_DAMPING = 0.2f // Демпфер пружины Держать < 1: на 1 и выше демпфер перелетает через ноль и сам раскачивает пару.
private const val INTERNAL_VELOCITY_EPS = 0.01f // ниже этого скорость атома считаем нулевой, иначе молекула никогда не «успокоится»
