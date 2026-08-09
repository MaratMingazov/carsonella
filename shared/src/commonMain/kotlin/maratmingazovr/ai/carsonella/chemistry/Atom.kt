package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.ChangeNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.ChangeSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.DeathNotifiable
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentAware
import maratmingazovr.ai.carsonella.chemistry.behavior.EnvironmentSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.LogWritable
import maratmingazovr.ai.carsonella.chemistry.behavior.Movable
import maratmingazovr.ai.carsonella.chemistry.behavior.PointMovement
import maratmingazovr.ai.carsonella.chemistry.behavior.LoggingSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsAware
import maratmingazovr.ai.carsonella.chemistry.behavior.NeighborsSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.OnDeathSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequestSupport
import maratmingazovr.ai.carsonella.chemistry.behavior.ReactionRequester
import kotlin.math.round

class Atom private constructor(
    override val id: Long,
    val element: Element,
    energy: Float,
    electrons: Int,
    private val movement: PointMovement,
):
    Entity,
    Movable by movement,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport(),
    ChangeNotifiable by ChangeSupport()
{
    constructor(id: Long, element: Element, position: Position, direction: Vec2D, velocity: Float, energy: Float, electrons: Int) :
            this(id, element, energy, electrons, PointMovement(position, direction, velocity, (element.details.p + element.details.n).toFloat()))

    init {
        movement.setOnChange(::markChanged) // делегат сам до markChanged не дотянется: в клаузе делегирования this ещё нет
        val levels = element.energyLevels(electrons)
        require(energy == 0f || energy in levels) {
            "Atom ${element.name}: недопустимая energy=$energy эВ (electrons=$electrons) — не 0 и не уровень из $levels"
        }
    }

    override var alive: Boolean = true
        private set
    override val protons: Int = element.details.p
    override var electrons: Int = electrons
        set(value) { field = value; markChanged() }
    var energy: Float = energy
        set(value) { field = value.coerceAtLeast(0f); markChanged() }
    val radius: Float = element.details.radius
    override fun distanceToSurface(point: Position): Float = kinematics.position.distanceTo(point) - radius // Кружок: расстояние до поверхности — это расстояние до центра минус радиус.
    override fun distanceSquareTo(point: Position): Float = kinematics.position.distanceSquareTo(point)
    override fun forcePoints(): List<ForcePoint> = listOf(ForcePoint(kinematics.position, radius, electrons, protons))
    override val displaySymbol: String get() = element.symbol(electrons)
    override val energyLevels: List<Float> get() = element.energyLevels(electrons)
    override val saveKey: String = element.name

    override fun describe(): String {
        return """
            |${element.label(electrons)}
            |Protons: ${element.details.p}
            |Neutrons: ${element.details.n}
            |Electrons: $electrons
            |Energy ${round(energy * 100) / 100} eV
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
            .filter { entity -> entity.distanceSquareTo(kinematics.position) < 10000f }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }

        if (energy > 0) { requestReaction(listOf(this)) }


        // β⁺-нестабильные изотопы (¹³N, ¹⁵O и т.п.) всегда зовут себя в резолвер — там их подхватит BetaPlusDecay.
        if (element.details.betaPlusDecayResult != null) { requestReaction(listOf(this)) }

        // β⁻-нестабильные изотопы (нейтрон-избыточные продукты (n,γ), напр. ³¹Si) — аналогично, их подхватит BetaMinusDecay.
        if (element.details.betaMinusDecayResult != null) { requestReaction(listOf(this)) }

        // В недрах звезды (TemperatureMode.Star) атом тепловой ионизуется — зовёт себя, StarThermalIonization сорвёт электрон.
        if (electrons > 0 && getEnvironment().getEnvTemperature() == TemperatureMode.Star) { requestReaction(listOf(this)) }
    }


    override fun destroy() {
        if (!alive) return
        alive = false
        markChanged()
        notifyDeath()
    }

}


