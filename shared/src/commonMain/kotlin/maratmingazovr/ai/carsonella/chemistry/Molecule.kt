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
    val atoms: List<MolecularAtom> get() {
        val center = state().value.centerPosition
        return graph.nodes.map { node ->
            MolecularAtom(
                localId = node.localId,
                isotope = node.isotope,
                position = center + graph.atomOffset(node.localId),
                freeValence = graph.freeValence(node.localId),
            )
        }
    }

    /** Связи, поставленные в мир: у каждой оба конца — готовые MolecularAtom. */
    val bonds: List<MolecularBond> get() = place(graph.bonds)

    /** Связи, которые можно усилить (кратность +1) — поставленные в мир. */
    val strengthenableBonds: List<MolecularBond> get() = place(graph.strengthenableBonds)

    /** Есть ли пара атомов, между которыми можно замкнуть цикл. */
    val canCloseRing: Boolean get() = graph.ringClosureCandidates.isNotEmpty()

    private fun place(bonds: List<Bond>): List<MolecularBond> {
        val byId = atoms.associateBy { it.localId }
        return bonds.map { MolecularBond(byId.getValue(it.atom1), byId.getValue(it.atom2), it.order) }
    }

    override fun distanceToSurface(point: Position): Float = atoms.minOf { it.position.distanceTo(point) - it.radius } // Молекула не кружок: берём ближайший АТОМ.
    override val displaySymbol: String get() = graph.formulaPretty + chargeSuffix(graph.protons - state().value.electrons)
    override val energyLevels: List<Float> = graph.energyLevels

    override val saveKey: String = graph.formula

    override fun describe(): String {
        // Известная молекула из реестра: англ. имя + брутто-формула первой строкой, затем русское имя и
        // структурная формула (связность) — отдельными строками. Аноним → просто брутто-формула.
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
            .filter { entity -> state.value.centerPosition.distanceSquareTo(entity.state().value.centerPosition) < 10000f }
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
