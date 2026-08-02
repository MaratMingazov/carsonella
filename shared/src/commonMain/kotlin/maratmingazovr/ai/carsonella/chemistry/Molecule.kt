package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry
import kotlin.math.round
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
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
)

class Molecule(
    override val id: Long,
    val graph: MoleculeGraph,
    position: Position,
    direction: Vec2D,
    velocity: Float,
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
    private var state = MutableStateFlow(
        EntityState(
            alive = true,
            kinematics = Kinematics(position, direction, velocity),
            energy = energy,
            electrons = electrons,
        )
    )

    override fun state() = state

    override val mass: Float = graph.mass
    override val protons: Int = graph.protons
    override val radius: Float = MOLECULE_RADIUS

    /** Атомы, поставленные в мир: структура из графа, координаты из состояния. */
    val atoms: List<MoleculeAtom> get() {
        val center = state().value.kinematics.position
        return graph.nodes.map { node ->
            MoleculeAtom(
                structure = MoleculeAtomStructure(localId = node.localId, isotope = node.isotope, freeValence = graph.freeValence(node.localId),),
                kinematics = Kinematics(position = center + graph.atomOffset(node.localId), direction = state.value.kinematics.direction, velocity = state.value.kinematics.velocity,),
            )
        }
    }

    /** Связи, поставленные в мир: у каждой оба конца — готовые MolecularAtom. */
    val bonds: List<MoleculeBond> get() = place(graph.bonds)

    /** Связи, которые можно усилить (кратность +1) — поставленные в мир. */
    val strengthenableBonds: List<MoleculeBond> get() = place(graph.strengthenableBonds)

    /** Есть ли пара атомов, между которыми можно замкнуть цикл. */
    val canCloseRing: Boolean get() = graph.ringClosureCandidates.isNotEmpty()

    private fun place(bonds: List<Bond>): List<MoleculeBond> {
        val byId = atoms.associateBy { it.structure.localId }
        return bonds.map { MoleculeBond(byId.getValue(it.atom1), byId.getValue(it.atom2), it.order) }
    }

    override fun distanceToSurface(point: Position): Float = atoms.minOf { it.kinematics.position.distanceTo(point) - it.structure.radius } // Молекула не кружок: берём ближайший АТОМ.
    override val displaySymbol: String get() = graph.formulaPretty + chargeSuffix(graph.protons - state().value.electrons)
    override val energyLevels: List<Float> = graph.energyLevels
    override val saveKey: String = graph.formula

    fun merge(other: MoleculeGraph, thisNode: Int, otherNode: Int, bondOrder: Int): MoleculeGraph = graph.merge(other, thisNode, otherNode, bondOrder)
    fun firstFreeValenceAtom(): MoleculeAtomStructure? {
        return graph.firstFreeValenceAtomNode?.let { atomNode ->
            MoleculeAtomStructure(localId = atomNode.localId, isotope = atomNode.isotope, freeValence = graph.freeValence(atomNode.localId),)
        }
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
        graph.weakestBondAndEnergy?.let { (_, energy) ->
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

// Затычка: у молекулы нет своего радиуса, её протяжённость — это атомы (см. Entity.radius).
// internal, а не private: то же число нужно CovalentBondFormation, где сущности ещё нет.
internal const val MOLECULE_RADIUS = 20f
