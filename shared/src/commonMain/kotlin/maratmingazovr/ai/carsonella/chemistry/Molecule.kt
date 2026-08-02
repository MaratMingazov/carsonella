package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph


class Molecule(
    id: Long,
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
            id = id,
            species = Species.Molecular(graph),
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
    override val displaySymbol: String get() = graph.formulaPretty + chargeSuffix(graph.protons - state().value.electrons)
    override val energyLevels: List<Float> = graph.energyLevels

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
