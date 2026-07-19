package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.math.round


data class MoleculeState(
    override val id: Long,
    override val species: Species,
    override val alive: Boolean,
    override val position: Position,
    override val direction: Vec2D,
    override val velocity: Float,
    override val energy: Float,
    override val electrons: Int,
) : EntityState<MoleculeState> {
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float, energy: Float, electrons: Int) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
    override fun toString(): String {
        val title = when (val s = species) {
            is Species.Molecular -> s.graph.formulaPretty
            is Species.Elemental -> s.element.label(electrons)
        }
        return """
            |$title: $id
            |Position (${position.x.toInt()}, ${position.y.toInt()})
            |Velocity ${round(velocity * 100) / 100}
            |Energy ${round(energy * 100) / 100}
        """.trimMargin()
    }
}

class Molecule(
    id: Long,
    graph: MoleculeGraph,
    position: Position,
    direction: Vec2D,
    velocity: Float,
    energy: Float,
    electrons: Int,
):
    Entity<MoleculeState>,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport()
{
    private var state = MutableStateFlow(
        MoleculeState(
            id = id,
            species = Species.Molecular(graph),
            alive = true,
            position = position,
            direction = direction,
            velocity = velocity,
            energy = energy,
            electrons = electrons,
        )
    )

    override fun state() = state

    override fun step() {
        val neighbors = getNeighbors()
        val environment = getEnvironment()

        applyForce(calculateForce(neighbors))
        applyNewPosition()
        reduceVelocity()
        checkBorders(environment)

        neighbors
            .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 10000f }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }

        // Усиление связи (3c): если у молекулы есть ненасыщенная связь — запрашиваем реакцию сама с собой
        // (listOf(this)), по аналогии с распадами в Atom.step. Рост идёт на запросах с соседями (partner-first).
        val graph = (state.value.species as? Species.Molecular)?.graph
        if (graph?.strengthenableBonds?.isNotEmpty() == true) { requestReaction(listOf(this)) }

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
