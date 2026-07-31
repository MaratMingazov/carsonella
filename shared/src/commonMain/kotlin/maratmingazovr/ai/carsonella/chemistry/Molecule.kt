package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph


class Molecule(
    id: Long,
    species: Species.Molecular,
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
            species = species,
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
