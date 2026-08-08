package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
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


data class MoleculeAtomStructure(
    val localId: Int,
    val isotope: Element,
    val freeValence: Int, // Свободная валентность: сколько связей атом ещё может образовать или усилить В ЭТОЙ молекуле.
) {
    val radius: Float = isotope.details.radius
}

data class MoleculeAtom(
    val structure: MoleculeAtomStructure,
    val kinematics: Kinematics,
)

data class MoleculeBond(
    val atom1: MoleculeAtom,
    val atom2: MoleculeAtom,
    val order: Int,
    val energy: Float?, // Энергия связи (эВ) — сколько нужно, чтобы её разорвать. null — тип связи не в каталоге
    val inRing: Boolean, // Лежит в цикле: разрыв раскроет кольцо, а не развалит молекулу на осколки
)

data class MoleculeShape(
    val atoms: List<MoleculeAtom>,
    val bonds: List<MoleculeBond>,
)

data class MoleculeRingCandidate(
    val atom1: MoleculeAtom,
    val atom2: MoleculeAtom,
    val ringSize: Int, // Сколько атомов окажется в цикле, если пару связать. Считается по графу (длина пути)
)

class Molecule(
    override val id: Long,
    graph: MoleculeGraph,
    kinematics: Kinematics,
    energy: Float,
    electrons: Int,
):
    Entity,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport()
{

    constructor(id: Long, atom1: Atom, atom2: Atom) : this(
        id = id,
        graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, atom1.element), AtomNode(1, atom2.element)),
            bonds = listOf(Bond(0, 1, order = 1)),
        ),
        kinematics = mergedKinematics(atom1, atom2),
        energy = atom1.state().value.energy + atom2.state().value.energy,
        electrons = atom1.state().value.electrons + atom2.state().value.electrons,
    )

    constructor(id: Long, molecule1: Molecule, atom1: MoleculeAtom, molecule2: Molecule, atom2: MoleculeAtom) : this(
        id = id,
        graph = mergedGraph(
            molecule1.graph, atom1.structure.localId,
            molecule2.graph, atom2.structure.localId,
        ),
        kinematics = mergedKinematics(molecule1, molecule2),
        energy = molecule1.state().value.energy + molecule2.state().value.energy,
        electrons = molecule1.state().value.electrons + molecule2.state().value.electrons,
    )

    /** Молекула растет путен добавления нового атома */
    constructor(id: Long, molecule: Molecule, atom: MoleculeAtom, newAtom: Atom) : this(
        id = id,
        graph = mergedGraph(
            molecule.graph, atom.structure.localId,
            MoleculeGraph(nodes = listOf(AtomNode(0, newAtom.element)), bonds = emptyList()), 0,
        ),
        kinematics = mergedKinematics(molecule, newAtom),
        energy = molecule.state().value.energy + newAtom.state().value.energy,
        electrons = molecule.state().value.electrons + newAtom.state().value.electrons,
    )

    /**
     * Молекула из ФОРМЫ — так рождаются осколки распада ([split]). Топология восстанавливается из формы
     * полностью, а вот координаты атомов в ней задают только состав: где встанет сущность, говорит
     * [kinematics], а внутри молекула всё равно раскладывает атомы от своего центра ([MoleculeGeometry]).
     * Это изменится, когда позиции атомов станут состоянием (шаг 3b дока) — тогда форма понесёт их сама.
     */
    constructor(id: Long, shape: MoleculeShape, kinematics: Kinematics, energy: Float, electrons: Int) : this(
        id = id,
        graph = shape.toGraph(),
        kinematics = kinematics,
        energy = energy,
        electrons = electrons,
    )

    private var state = MutableStateFlow(
        EntityState(
            alive = true,
            kinematics = kinematics,
            energy = energy,
            electrons = electrons,
        )
    )

    override fun state() = state

    private var graph: MoleculeGraph = graph
    override val mass: Float get() = graph.mass
    override val protons: Int get() = graph.protons
    override val radius: Float = MOLECULE_RADIUS

    val shape: MoleculeShape get() = placeShape(graph)
    private val atoms: List<MoleculeAtom> get() = placeAtoms(graph)

    /**
     * Граф ПАРАМЕТРОМ, а не `this.graph`: так же ставится в мир осколок, чей граф молекуле ещё не
     * принадлежит (см. [split]). Свойство [atoms] здесь не подходит — оно всегда от `this.graph`, и
     * форма осколка получила бы атомы целой молекулы с досплитовыми `freeValence`/`inRing`.
     */
    private fun placeShape(graph: MoleculeGraph): MoleculeShape {
        val placed = placeAtoms(graph)
        return MoleculeShape(placed, placeBonds(graph, graph.bonds, placed))
    }
    private fun placeAtoms(graph: MoleculeGraph): List<MoleculeAtom> =
        graph.nodes.map { node ->
            MoleculeAtom(
                structure = MoleculeAtomStructure(localId = node.localId, isotope = node.isotope, freeValence = graph.freeValence(node.localId)),
                kinematics = ownKinematics(node.localId),
            )
        }
    private fun placeBonds(graph: MoleculeGraph, bonds: List<Bond>, atoms: List<MoleculeAtom>): List<MoleculeBond> {
        val byId = atoms.associateBy { it.structure.localId }
        return bonds.map {
            MoleculeBond(byId.getValue(it.atom1), byId.getValue(it.atom2), it.order, graph.energyOf(it), graph.isRingBond(it.atom1, it.atom2))
        }
    }


    /** Кинематика атома [localId] в ЭТОЙ молекуле: центр сущности плюс смещение из раскладки графа. */
    private fun ownKinematics(localId: Int): Kinematics {
        val kinematics = state.value.kinematics
        return kinematics.copy(position = kinematics.position + graph.atomOffset(localId))
    }

    /** Связи, которые можно усилить (кратность +1) — поставленные в мир. */
    val strengthenableBonds: List<MoleculeBond> get() = place(graph.strengthenableBonds, atoms)

    /** Есть ли пара атомов, между которыми можно замкнуть цикл. Дешёвая проверка: кандидаты кеширует граф. */
    val canCloseRing: Boolean get() = graph.ringClosureCandidates.isNotEmpty()

    /** Пары атомов, которые можно связать в кольцо, — поставленные в мир. Какую выбрать, решает правило. */
    val ringClosureCandidates: List<MoleculeRingCandidate> get() {
        val candidates = graph.ringClosureCandidates
        if (candidates.isEmpty()) return emptyList()
        val byId = atoms.associateBy { it.structure.localId }
        return candidates.map { MoleculeRingCandidate(byId.getValue(it.atom1), byId.getValue(it.atom2), it.ringSize) }
    }

    /** Слабейшая связь — она рвётся первой при распаде. */
    val weakestBond: MoleculeBond? get() = graph.weakestBondAndEnergy?.let { (bond, _) -> place(listOf(bond), atoms).single() }

    /**
     * ПОРОГ ДИССОЦИАЦИИ (эВ) — энергия слабейшей связи, то же, что `weakestBond?.energy`. Отдельно, потому
     * что это одно чтение кеша графа, а [weakestBond] ставит связь в мир (строит все атомы). Правила
     * спрашивают порог в matches/weight, то есть на каждый тик — им нужен дешёвый путь.
     */
    val dissociationEnergy: Float? get() = graph.weakestBondAndEnergy?.second

    private fun place(bonds: List<Bond>, atoms: List<MoleculeAtom>): List<MoleculeBond> = placeBonds(graph, bonds, atoms)

    /**
     * Разрыв связи-МОСТА: молекула распадается, и каждый осколок описывается формой — топологией с
     * координатами, из которой вызывающий строит новую сущность (сама молекула при этом обречена, её
     * хоронит правило). Кольцевую связь сюда не носят: там граф остаётся связным, это [openRing].
     *
     * ДВА разных источника, и путать их нельзя:
     *  - СТРУКТУРА осколка (`freeValence`, `inRing`, энергия связи) считается по ЕГО СОБСТВЕННОМУ
     *    подграфу. У концов разорванной связи свободная валентность выросла на 1, а связи разорванного
     *    цикла перестали быть кольцевыми — отфильтруй мы вместо этого готовые [MoleculeAtom] исходной
     *    молекулы по компонентам, значения были бы досплитовые, то есть тихо неверные;
     *  - КООРДИНАТЫ берутся из раскладки ЭТОЙ молекулы ([ownKinematics]) — атом остаётся там, где был.
     *    Работает это потому, что номера узлов осколок наследует ([MoleculeGraph.split] их не
     *    перенумеровывает) и что разрез — операция ЧИСТАЯ: `graph` тут ещё прежний, молекулу хоронит
     *    правило и уже после. Станет `split` мутацией (идея «личность остаётся у наибольшего осколка»)
     *    — координаты придётся снимать в снимок ДО разреза, иначе [ownKinematics] прочтёт новый граф.
     */
    fun split(bond: MoleculeBond): List<MoleculeShape> = graph
        .split(bond.atom1.structure.localId, bond.atom2.structure.localId)
        .map { fragment -> placeShape(fragment) }

    override fun distanceToSurface(point: Position): Float = atoms.minOf { it.kinematics.position.distanceTo(point) - it.structure.radius } // Молекула не кружок: берём ближайший АТОМ.
    override val displaySymbol: String get() = graph.formulaPretty + chargeSuffix(graph.protons - state().value.electrons)
    override val energyLevels: List<Float> get() = graph.energyLevels
    override val saveKey: String get() = graph.formula

    /**
     * Первый атом со свободной валентностью — ПОСТАВЛЕННЫЙ в мир (структура + координаты), а не одна
     * структура: именно такой атом ждут конструкторы слияния [Molecule], им нужна кинематика конца связи.
     * null — молекула насыщена, расти/усиливать нечем.
     */
    fun firstFreeValenceAtom(): MoleculeAtom? {
        val node = graph.firstFreeValenceAtomNode ?: return null
        return atoms.first { it.structure.localId == node.localId }
    }
    val hasFreeValence: Boolean get() = graph.hasFreeValence

    /**
     * Усиливаем связь bond: её кратность растёт на 1 (O–O → O=O, N=N → N≡N). Меняем граф
     */
    fun strengthenBond(bond: MoleculeBond) {
        val atom1 = bond.atom1.structure.localId
        val atom2 = bond.atom2.structure.localId
        require(graph.freeValence(atom1) > 0 && graph.freeValence(atom2) > 0) {
            "Связь $atom1–$atom2 в ${graph.formula} не усилить: у конца нет свободного слота"
        }
        graph = graph.strengthenBond(atom1, atom2)
        nudgeAfterRebuild()
    }

    /**
     * Замыкание кольца: связываем два НЕСОСЕДНИХ атома молекулы → цикл (C–C–C–C–C → циклопентан). Меняем граф
     */
    fun closeRing(atom1: MoleculeAtom, atom2: MoleculeAtom) {
        val id1 = atom1.structure.localId
        val id2 = atom2.structure.localId
        require(graph.freeValence(id1) > 0 && graph.freeValence(id2) > 0) {
            "Кольцо $id1–$id2 в ${graph.formula} не замкнуть: у конца нет свободного слота"
        }
        graph = graph.closeRing(id1, id2)
        nudgeAfterRebuild()
    }

    /**
     * Раскрытие кольца: рвём связь, лежащую в цикле → цикл разворачивается в цепь, а молекула НЕ
     * распадается. Атомы те же, состав тот же — значит это та же сущность, как усиление и замыкание
     * (id и выделение игрока живут). Разрыв связи-МОСТА — другое дело: там молекула гибнет, а осколки
     * рождаются заново, это делают правила распада через [split].
     *
     * Своего `require` здесь нет: что связь кольцевая (а значит снимок [MoleculeBond.inRing] не протух),
     * проверяет по живому графу сам [MoleculeGraph.removeRingBond].
     */
    fun openRing(bond: MoleculeBond) {
        graph = graph.removeRingBond(bond.atom1.structure.localId, bond.atom2.structure.localId)
        nudgeAfterRebuild()
    }

    /**
     * Сдвиг молекулы на пиксель после перестройки графа. ЗАТЫЧКА, но по делу: новая связь обязана
     * ПРИТЯНУТЬ свои атомы друг к другу, а притягивать пока нечего — своих координат у атомов нет, offset
     */
    private fun nudgeAfterRebuild() {
        val kinematics = state.value.kinematics
        state.value = state.value.copyWith(kinematics = kinematics.copy(position = kinematics.position + Position(1f, 0f)))
    }

    override fun describe(): String {
        val known = MoleculeRegistry.lookup(graph.canonical)
        val lines = mutableListOf(
            if (known != null) "${known.nameEn} (${graph.formulaPretty})" else graph.formulaPretty,
        )
        if (known != null) lines += known.nameRu
        if (known != null && known.structuralFormula.isNotEmpty()) lines += known.structuralFormula
        if (known != null && known.description.isNotEmpty()) lines += known.description
        lines += "Energy ${round(state().value.energy * 100) / 100}"
        dissociationEnergy?.let { energy ->
            lines += "Weakest bond ${round(energy * 100) / 100} eV"
        }
        return lines.joinToString("\n")
    }

    override fun step() {
        val neighbors = getNeighbors()
        val environment = getEnvironment()

        applyForce(calculateForce(neighbors))
        applyNewPosition()
        reduceVelocity()
        checkBorders(environment)

        neighbors
            .filter { entity -> state.value.kinematics.position.distanceSquareTo(entity.state().value.kinematics.position) < 10000f }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }

        // Спонтанный сброс внутренней энергии (MolecularSpontaneousEmission) — АВТО.
        // Усиление связи и замыкание кольца этим зовом НЕ запускаются: они живут в отдельном списке
        // forcedRules резолвера и ждут клика игрока (World.requestMoleculeAction → см. ForcedReactionRule).
        if (state.value.energy > 0f) {
            requestReaction(listOf(this))
        }

        // В звезде (TemperatureMode.Star) молекула термически распадается — зовёт себя, StarDissociation
        // рвёт слабейшую связь (зеркало StarThermalIonization у атома). Зов безусловный: даже насыщенная
        // молекула (у неё strengthenableBonds пусто) обязана распасться в звезде.
        if (environment.getEnvTemperature() == TemperatureMode.Star) { requestReaction(listOf(this)) }
    }
    override fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}




/**
 * Топология из формы: [MoleculeAtom] даёт узел (`localId` + изотоп), [MoleculeBond] — ребро (пара
 * атомов + кратность). Обратная операция к постановке в мир; координаты при этом теряются — центр
 * новой сущности задаётся отдельно, см. конструктор [Molecule] из формы.
 */
private fun MoleculeShape.toGraph(): MoleculeGraph = MoleculeGraph(
    nodes = atoms.map { AtomNode(it.structure.localId, it.structure.isotope) },
    bonds = bonds.map { Bond(it.atom1.structure.localId, it.atom2.structure.localId, it.order) },
)

/**
 * Граф молекулы, собранной из двух графов: между узлами [node1] и [node2] появляется ОДИНАРНАЯ связь
 */
private fun mergedGraph(graph1: MoleculeGraph, node1: Int, graph2: MoleculeGraph, node2: Int): MoleculeGraph {
    require(graph1.freeValence(node1) > 0) { "Узел $node1 в ${graph1.formula} насыщен: связь образовать нечем" }
    require(graph2.freeValence(node2) > 0) { "Узел $node2 в ${graph2.formula} насыщен: связь образовать нечем" }
    return MoleculeGraph.merge(graph1, node1, graph2, node2, bondOrder = 1)
}

/**
 * Кинематика молекулы, собранной из двух атомов: центр — середина отрезка между ними, движение — из
 * сохранения импульса (m₁v₁ + m₂v₂) / (m₁ + m₂).
 */
private fun mergedKinematics(entity1: Entity, entity2: Entity): Kinematics {
    val k1 = entity1.state().value.kinematics
    val k2 = entity2.state().value.kinematics

    val impulse = k1.direction * k1.velocity * entity1.mass + k2.direction * k2.velocity * entity2.mass
    val velocityVector = impulse.div(entity1.mass + entity2.mass)
    val velocity = velocityVector.length()

    return Kinematics(
        position = Position((k1.position.x + k2.position.x) / 2f, (k1.position.y + k2.position.y) / 2f),
        direction = if (velocity > 1e-6f) velocityVector.div(velocity) else Vec2D(1f, 0f),
        velocity = velocity.coerceAtMost(MAX_VELOCITY),
    )
}

// Затычка: у молекулы нет своего радиуса, её протяжённость — это атомы (см. Entity.radius).
// internal, а не private: то же число нужно CovalentBondFormation, где сущности ещё нет.
internal const val MOLECULE_RADIUS = 20f
