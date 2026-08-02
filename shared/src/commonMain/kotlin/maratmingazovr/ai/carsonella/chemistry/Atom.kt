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
import kotlin.math.round

class Atom(
    override val id: Long,
    val element: Element,
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
    init {
        // Инвариант энергии атома: energy — это ТОЛЬКО 0 (основное состояние) или ТОЧНО один из дискретных
        // уровней возбуждения energyLevels(electrons). Проверяем при создании (fail-fast): «не-уровень»
        // должен ронять здесь и сразу, а не позже в SpontaneousEmission во время какой-то реакции.
        // Пусто (голый ион/Z>18) → допустим только 0. Заряд меняет лестницу, поэтому берём по electrons.
        val levels = element.energyLevels(electrons)
        require(energy == 0f || energy in levels) {
            "Atom ${element.name}: недопустимая energy=$energy эВ (electrons=$electrons) — не 0 и не уровень из $levels"
        }
    }

    private var state = MutableStateFlow(
        EntityState(
            alive = true,
            kinematics = Kinematics(position, direction, velocity),
            energy = energy,
            electrons = electrons,
        )
    )

    override fun state() = state

    override val mass: Float = (element.details.p + element.details.n).toFloat()
    override val protons: Int = element.details.p
    override val radius: Float = element.details.radius
    override fun distanceToSurface(point: Position): Float = state().value.centerPosition.distanceTo(point) - radius // Кружок: расстояние до поверхности — это расстояние до центра минус радиус.
    override val displaySymbol: String get() = element.symbol(state().value.electrons)
    override val energyLevels: List<Float> get() = element.energyLevels(state().value.electrons)

    override val saveKey: String = element.name

    override fun describe(): String {
        val s = state().value
        return """
            |${element.label(s.electrons)}
            |Protons: ${element.details.p}
            |Neutrons: ${element.details.n}
            |Electrons: ${s.electrons}
            |Energy ${round(s.energy * 100) / 100} eV
        """.trimMargin()
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

        if (state.value.energy > 0) { requestReaction(listOf(this)) }


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


