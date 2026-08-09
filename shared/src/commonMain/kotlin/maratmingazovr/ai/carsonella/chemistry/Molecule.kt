package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry
import kotlin.math.round
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.Float


class MoleculeAtom(
    val localId: Int,
    val isotope: Element,
    var kinematics: Kinematics,
) {
    val radius: Float = isotope.details.radius
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
    kinematics: Kinematics,
    energy: Float,
    electrons: Int,
):
    Entity,
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
        kinematics = mergedKinematics(atom1, atom2),
        energy = atom1.energy + atom2.energy,
        electrons = atom1.electrons + atom2.electrons,
    )

    constructor(id: Long, molecule1: Molecule, atom1: MoleculeAtom, molecule2: Molecule, atom2: MoleculeAtom) : this(
        id = id,
        graph = mergedGraph(
            molecule1.graph, atom1.localId,
            molecule2.graph, atom2.localId,
        ),
        kinematics = mergedKinematics(molecule1, molecule2),
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
        kinematics = mergedKinematics(molecule, newAtom),
        energy = molecule.energy + newAtom.energy,
        electrons = molecule.electrons + newAtom.electrons,
    )

    constructor(id: Long, shape: MoleculeShape, kinematics: Kinematics, energy: Float, electrons: Int) : this(
        id = id,
        graph = shape.toGraph(),
        kinematics = kinematics,
        energy = energy,
        electrons = electrons,
    )

    // Кинематика МОЛЕКУЛЫ — это кинематика её центра. Атомы пока едут за центром жёстко: сеттер
    // сдвигает каждый на ту же дельту. Собственное движение атомов (колебания на связях) появится
    // здесь же, когда заведём силы связей, — тогда центр станет ведомым, а не ведущим.
    override var kinematics: Kinematics = kinematics
        set(value) {
            if (field == value) return
            val dx = value.position.x - field.position.x
            val dy = value.position.y - field.position.y
            field = value
            for (atom in atomsById.values) {
                atom.kinematics = value.copy(position = Position(atom.kinematics.position.x + dx, atom.kinematics.position.y + dy))
            }
            markChanged()
        }
    // Раскладка графа нужна ровно один раз — при рождении. Дальше атом сам себе хозяин: правка графа
    // его не пересаживает, геометрию будут держать силы между атомами молекулы (отдельный метод, позже).
    private val atomsById: Map<Int, MoleculeAtom> = graph.nodes.associate { node ->
        node.localId to MoleculeAtom(node.localId, node.isotope, kinematics.copy(position = kinematics.position + graph.atomOffset(node.localId)))
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

        // ВРЕМЕННО: молекула неподвижна.
//        applyForce(calculateForce(neighbors))
//        applyNewPosition()
//        reduceVelocity()
//        checkBorders(environment)

        neighbors
            .filter { entity -> kinematics.position.distanceSquareTo(entity.kinematics.position) < 10000f }
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
    val weakestBond: MoleculeBond? get() = graph.weakestBondAndEnergy?.let { (bond, _) -> createMoleculeBonds(graph, listOf(bond)).single() } // Слабейшая связь — она рвётся первой при распаде.
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
private fun mergedKinematics(entity1: Entity, entity2: Entity): Kinematics {
    val k1 = entity1.kinematics
    val k2 = entity2.kinematics

    val impulse = k1.direction * k1.velocity * entity1.mass + k2.direction * k2.velocity * entity2.mass
    val velocityVector = impulse.div(entity1.mass + entity2.mass)
    val velocity = velocityVector.length()

    return Kinematics(
        position = Position((k1.position.x + k2.position.x) / 2f, (k1.position.y + k2.position.y) / 2f),
        direction = if (velocity > 1e-6f) velocityVector.div(velocity) else Vec2D(1f, 0f),
        velocity = velocity.coerceAtMost(MAX_VELOCITY),
    )
}
internal const val MOLECULE_RADIUS = 20f
