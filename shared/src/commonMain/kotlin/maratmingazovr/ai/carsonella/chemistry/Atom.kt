package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.DeathNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentAware
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.LogWritable
import maratmingazovr.ai.carsonella.chemistry.behavior.LoggingSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsAware
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.OnDeathSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequestSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequester

class Atom(
    id: Long,
    element: Element,
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
            species = Species.Elemental(element),
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

        if (state.value.energy > 0) { requestReaction(listOf(this)) }

        val element = (state.value.species as Species.Elemental).element

        // β⁺-нестабильные изотопы (¹³N, ¹⁵O и т.п.) всегда зовут себя в резолвер — там их подхватит BetaPlusDecay.
        if (element.details.betaPlusDecayResult != null) { requestReaction(listOf(this)) }

        // β⁻-нестабильные изотопы (нейтрон-избыточные продукты (n,γ), напр. ³¹Si) — аналогично, их подхватит BetaMinusDecay.
        if (element.details.betaMinusDecayResult != null) { requestReaction(listOf(this)) }

        // В недрах звезды (TemperatureMode.Star) атом тепловой ионизуется — зовёт себя, StarThermalIonization сорвёт электрон.
        if (state.value.electrons > 0 && getEnvironment().getEnvTemperature() == TemperatureMode.Star) { requestReaction(listOf(this)) }
    }


    override fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}


