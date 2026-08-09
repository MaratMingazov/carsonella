package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
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

data class MoleculeRingCandidate(
    val localId1: Int,
    val localId2: Int,
    val ringSize: Int, // Сколько атомов окажется в цикле, если пару связать. Считается по графу (длина пути)
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
        atoms = bondedAtoms(atom1, atom2),
        energy = atom1.energy + atom2.energy,
        electrons = atom1.electrons + atom2.electrons,
    )

    constructor(id: Long, molecule1: Molecule, atom1: MoleculeAtom, molecule2: Molecule, atom2: MoleculeAtom) : this(
        id = id,
        graph = mergedGraph(
            molecule1.graph, atom1.localId,
            molecule2.graph, atom2.localId,
        ),
        atoms = mergedAtoms(molecule1, molecule2, molecule1.graph.mergeOffset()),
        energy = molecule1.energy + molecule2.energy,
        electrons = molecule1.electrons + molecule2.electrons,
    )

    /** Молекула растет путен добавления нового атома */
    constructor(id: Long, molecule: Molecule, atom: MoleculeAtom, newAtom: Atom) : this(
        id = id,
        graph = mergedGraph(
            molecule.graph, atom.localId,
            MoleculeGraph(nodes = listOf(AtomNode(0, newAtom.element)), bonds = emptyList()), 0,
        ),
        atoms = grownAtoms(molecule, newAtom, molecule.graph.mergeOffset()),
        energy = molecule.energy + newAtom.energy,
        electrons = molecule.electrons + newAtom.electrons,
    )

    constructor(id: Long, shape: MoleculeShape, energy: Float, electrons: Int) : this(
        id = id,
        graph = shape.toGraph(),
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
            var totalMass = 0f
            var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
            for (atom in atomsById.values) {
                val k = atom.kinematics
                val m = atom.mass
                totalMass += m
                x += k.position.x * m; y += k.position.y * m
                vx += k.direction.x * k.velocity * m; vy += k.direction.y * k.velocity * m
            }
            val velocityVector = Vec2D(vx / totalMass, vy / totalMass)
            val velocity = velocityVector.length()
            // У покоящейся молекулы направления нет — берём у первого атома: там лежит то же, что раньше в поле.
            val direction = if (velocity > 1e-6f) velocityVector.div(velocity) else atomsById.values.first().kinematics.direction
            return Kinematics(Position(x / totalMass, y / totalMass), direction, velocity)
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
            atom.kinematics = k.copy(velocity = if (k.velocity < 0.1f) 0f else k.velocity * 0.99f)
        }
        markChanged()
    }

    override fun checkBorders(env: IEnvironment) {
        val envCenter = env.getEnvCenter()
        val envRadius = env.getEnvRadius()
        var corrected = false
        for (atom in atomsById.values) {
            val k = atom.kinematics
            val dx = k.position.x - envCenter.x
            val dy = k.position.y - envCenter.y
            if (dx * dx + dy * dy <= envRadius * envRadius) continue // этот атом внутри
            val dist = sqrt(dx * dx + dy * dy)
            val nx = dx / dist
            val ny = dy / dist
            val dot = k.direction.x * nx + k.direction.y * ny
            atom.kinematics = k.copy(
                position = Position(envCenter.x + nx * envRadius, envCenter.y + ny * envRadius),
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
    // Внутренняя (колебательная) энергия — квазинепрерывная, в отличие от дискретных уровней атома.
    var energy: Float = energy
        set(value) { field = value.coerceAtLeast(0f); markChanged() }
    override val radius: Float = MOLECULE_RADIUS
    override val displaySymbol: String get() = graph.formulaPretty + chargeSuffix(graph.protons - electrons)
    override val energyLevels: List<Float> get() = graph.energyLevels
    override val saveKey: String get() = graph.formula

    override fun distanceToSurface(point: Position): Float = atoms.minOf { it.kinematics.position.distanceTo(point) - it.radius } // Молекула не кружок: берём ближайший АТОМ.
    override fun distanceSquareTo(point: Position): Float = atomsById.values.minOf { it.kinematics.position.distanceSquareTo(point) } // Своей позиции у молекулы нет — отвечает ближайший атом.
    override fun describe(): String {
        val known = MoleculeRegistry.lookup(graph.canonical)
        val lines = mutableListOf(
            if (known != null) "${known.nameEn} (${graph.formulaPretty})" else graph.formulaPretty,
        )
        if (known != null) lines += known.nameRu
        if (known != null && known.structuralFormula.isNotEmpty()) lines += known.structuralFormula
        if (known != null && known.description.isNotEmpty()) lines += known.description
        lines += "Energy ${round(energy * 100) / 100}"
        dissociationEnergy?.let { energy ->
            lines += "Weakest bond ${round(energy * 100) / 100} eV"
        }
        return lines.joinToString("\n")
    }
    override fun step() {
        val neighbors = getNeighbors()
        val environment = getEnvironment()

        // ВРЕМЕННО: молекула неподвижна КАК ЦЕЛОЕ — внешние силы и границу ещё не переписали на атомы.
//        applyForce(calculateForce(neighbors))
//        applyNewPosition()
//        reduceVelocity()
//        checkBorders(environment)

        // Но внутри она уже живая: пружины связей растаскивают атомы на длину покоя, несвязанные
        // расталкиваются. Импульс от этих сил нулевой, так что с места молекула не двинется.
        applyInternalForces()
        var moved = false
        for (atom in atomsById.values) if (atom.dampAndMove()) moved = true
        if (moved) markChanged() // успокоилась — перестаём будить рендер

        neighbors
            .filter { entity -> atomsById.values.any { atom -> entity.distanceSquareTo(atom.kinematics.position) < 10000f } }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }

        if (energy > 0f) {
            requestReaction(listOf(this))
        } // Спонтанный сброс внутренней энергии

        // В звезде (TemperatureMode.Star) молекула термически распадается — зовёт себя, StarDissociation
        // рвёт слабейшую связь (зеркало StarThermalIonization у атома). Зов безусловный: даже насыщенная
        // молекула (у неё strengthenableBonds пусто) обязана распасться в звезде.
        if (environment.getEnvTemperature() == TemperatureMode.Star) { requestReaction(listOf(this)) }
    }
    override fun destroy() {
        if (!alive) return
        alive = false
        markChanged()
        notifyDeath()
    }

    ////////////////////////////////////////////////////////////
    // ФУНКЦИИ - ТОЛЬКО НА ОСНОВНЕ ГРАФА. ДАННЫЕ ЗАКЭШИРОВАНЫ //
    val hasFreeValence: Boolean get() = graph.hasFreeValence
    val canCloseRing: Boolean get() = graph.ringClosureCandidates.isNotEmpty() // Есть ли пара атомов, между которыми можно замкнуть цикл. Дешёвая проверка: кандидаты кеширует граф.
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
    val ringClosureCandidates: List<MoleculeRingCandidate> get() =
        graph.ringClosureCandidates.map { MoleculeRingCandidate(it.atom1, it.atom2, it.ringSize) }
    // Пары атомов, которые можно связать в кольцо, — поставленные в мир. Какую выбрать, решает правило.
    fun atom(localId: Int): MoleculeAtom = atomsById.getValue(localId) // Атом по номеру узла: им адресуют концы MoleculeBond и MoleculeRingCandidate.
    fun freeValence(atom: MoleculeAtom): Int = graph.freeValence(atom.localId) // Сколько связей атом ещё может образовать или усилить В ЭТОЙ молекуле. Живёт на графе, а не на атоме: меняется при усилении связи и замыкании кольца, хранимое поле протухло бы.
    val freeValenceAtoms: List<MoleculeAtom> get() = atoms.filter { freeValence(it) > 0 }
    fun split(bond: MoleculeBond): List<MoleculeShape> = graph.split(bond.localId1, bond.localId2).map { fragment -> createMoleculeShape(fragment) }

    override fun forcePoints(): List<ForcePoint> = atoms.map { atom ->
        val neutral = atom.isotope.details.p
        ForcePoint(atom.kinematics.position, atom.radius, electrons = neutral, protons = neutral)
    } // Молекула участвует в силах  каждым атомом

    /**
     * Силы между атомами ОДНОЙ молекулы — то, что держит её форму теперь, когда атом двигается сам.
     *
     * Связь ведёт себя как пружина с длиной покоя из [MoleculeGeometry] — той же, по которой атомы
     * расставляются при рождении, поэтому только что родившаяся молекула стоит на месте, а не дёргается.
     * Несвязанные атомы одной молекулы друг друга ОТТАЛКИВАЮТ, но не притягивают: без этого цепочку
     * ничто не держит от складывания, а угловых связей мы не моделируем.
     *
     * Силы парные и противоположные, поэтому суммарный импульс нулевой: от собственных пружин молекула
     * с места не сдвинется.
     */
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

                val magnitude = BOND_STIFFNESS * stretch
                val ux = dx / distance
                val uy = dy / distance
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
    // Затухание и шаг одним движением: внутреннее движение должно затихать, иначе молекула
    // будет звенеть на пружинах бесконечно.
    // Возвращает, сдвинулся ли атом на самом деле: по этому молекула решает, будить ли рендер. Без порога
    // она не замолчит никогда — на успокоившейся молекуле остаточная сила даёт скорость порядка 1e-5.
    private fun MoleculeAtom.dampAndMove(): Boolean {
        val velocity = if (kinematics.velocity < INTERNAL_VELOCITY_EPS) 0f else kinematics.velocity * INTERNAL_DAMPING
        kinematics = kinematics.copy(
            position = Position(
                kinematics.position.x + kinematics.direction.x * velocity,
                kinematics.position.y + kinematics.direction.y * velocity,
            ),
            velocity = velocity,
        )
        return velocity > 0f
    }

    private fun createMoleculeShape(graph: MoleculeGraph): MoleculeShape =
        MoleculeShape(createMoleculeAtoms(graph), createMoleculeBonds(graph, graph.bonds))
    private fun createMoleculeAtoms(graph: MoleculeGraph): List<MoleculeAtom> =
        graph.nodes.map { node -> atomsById.getValue(node.localId) }
    private fun createMoleculeBonds(graph: MoleculeGraph, bonds: List<Bond>): List<MoleculeBond> =
        bonds.map {
            MoleculeBond(it.atom1, it.atom2, it.order, graph.energyOf(it), graph.isRingBond(it))
        }


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





private fun MoleculeShape.toGraph(): MoleculeGraph = MoleculeGraph(
    nodes = atoms.map { AtomNode(it.localId, it.isotope) },
    bonds = bonds.map { Bond(it.localId1, it.localId2, it.order) },
)
private fun mergedGraph(graph1: MoleculeGraph, node1: Int, graph2: MoleculeGraph, node2: Int): MoleculeGraph {
    require(graph1.freeValence(node1) > 0) { "Узел $node1 в ${graph1.formula} насыщен: связь образовать нечем" }
    require(graph2.freeValence(node2) > 0) { "Узел $node2 в ${graph2.formula} насыщен: связь образовать нечем" }
    return graph1.merge(graph2, thisNode = node1, otherNode = node2, bondOrder = 1)
}

private fun bondedAtoms(atom1: Atom, atom2: Atom): List<MoleculeAtom> = listOf(
    MoleculeAtom(0, atom1.element, atom1.kinematics),
    MoleculeAtom(1, atom2.element, atom2.kinematics),
)
// Слияние двух молекул: узлы второй переезжают на mergeOffset вверх — ровно так же, как их двигает merge.
private fun mergedAtoms(molecule1: Molecule, molecule2: Molecule, offset: Int): List<MoleculeAtom> =
    molecule1.atoms.map { MoleculeAtom(it.localId, it.isotope, it.kinematics) } +
            molecule2.atoms.map { MoleculeAtom(it.localId + offset, it.isotope, it.kinematics) }
// Рост: у новичка единственный узел 0, значит после сдвига он становится offset.
private fun grownAtoms(molecule: Molecule, newAtom: Atom, offset: Int): List<MoleculeAtom> =
    molecule.atoms.map { MoleculeAtom(it.localId, it.isotope, it.kinematics) } +
            MoleculeAtom(offset, newAtom.element, newAtom.kinematics)
internal const val MOLECULE_RADIUS = 20f

// Жёсткость связи-пружины: во сколько превращается пиксель отклонения от длины покоя. Держать НИЗКОЙ —
// шаг интегрирования у нас один тик, и на большой жёсткости лёгкий водород (масса 1) улетит за один ход.
private const val BOND_STIFFNESS = 0.02f
// Затухание внутреннего движения. Заметно сильнее общего reduceVelocity (0.99): там гасится полёт молекулы
// по миру, а здесь — звон пружин, и звенеть он должен недолго.
private const val INTERNAL_DAMPING = 0.9f
private const val INTERNAL_VELOCITY_EPS = 0.01f // ниже этого скорость атома считаем нулевой, иначе молекула никогда не «успокоится»
