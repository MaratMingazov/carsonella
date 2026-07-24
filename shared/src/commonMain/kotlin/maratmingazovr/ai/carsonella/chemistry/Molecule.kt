package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph


class Molecule(
    id: Long,
    graph: MoleculeGraph,
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

        // Внутримолекулярные реакции (усиление 3c / замыкание кольца / спонтанный сброс энергии) —
        // запрашиваем реакцию сама с собой (listOf(this)), по аналогии с распадами в Atom.step. Рост идёт
        // на запросах с соседями (partner-first). Один self-request покрывает все size==1-правила
        // (BondStrengthening, RingClosure, MolecularSpontaneousEmission), resolve() выбирает по weight.
        // energy > 0 добавлено, чтобы «горячий» осколок без свободных слотов (напр. ·OH) тоже попал в
        // self-request и мог сбросить энергию через MolecularSpontaneousEmission (иначе застряла бы навсегда).
        val graph = (state.value.species as Species.Molecular).graph
        if (graph.strengthenableBonds.isNotEmpty() || graph.ringClosureCandidates.isNotEmpty() || state.value.energy > 0f
        ) {
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
