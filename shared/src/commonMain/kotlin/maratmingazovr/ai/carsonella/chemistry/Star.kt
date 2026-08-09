package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import kotlin.math.round


class Star private constructor(
    override val id: Long,
    val element: Element,
    // energy звезде не нужна: ей никто её не меняет, а пульс в drawStar от неё не зависит.
    electrons: Int,
    private val movement: PointMovement,
    private val children: MutableList<Entity> = mutableListOf(),
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
    private var radiusCounter = element.details.radius

    constructor(id: Long, element: Element, position: Position, direction: Vec2D, velocity: Float, electrons: Int) :
            this(id, element, electrons, PointMovement(position, direction, velocity, (element.details.p + element.details.n).toFloat()))

    init { movement.setOnChange(::markChanged) } // делегат сам до markChanged не дотянется: в клаузе делегирования this ещё нет

    override var alive: Boolean = true
        private set
    override val protons: Int = element.details.p
    // Символ и уровни звезды от заряда не зависят (не-атом), но соседи-атомы снаружи видят её в
    // calculateForce — там оболочка и работает.
    override val electrons: Int = electrons
    override val radius: Float = element.details.radius
    override fun distanceToSurface(point: Position): Float = kinematics.position.distanceTo(point) - radius // Кружок: расстояние до поверхности — это расстояние до центра минус радиус.
    override fun distanceSquareTo(point: Position): Float = kinematics.position.distanceSquareTo(point)
    override fun forcePoints(): List<ForcePoint> = listOf(ForcePoint(kinematics.position, radius, electrons, protons))
    override val displaySymbol: String get() = element.symbol(electrons)
    override val energyLevels: List<Float> get() = element.energyLevels(electrons)

    override val saveKey: String = element.name

    override fun describe(): String {
        return """
            |${element.label(electrons)}: ${id}
            |Position (${kinematics.position.x.toInt()}, ${kinematics.position.y.toInt()})
            |Velocity ${round(kinematics.velocity * 100) / 100}
        """.trimMargin()
    }

    override fun getEnvCenter() = kinematics.position
    override fun getEnvRadius() = radiusCounter
    override fun getEnvTemperature() = TemperatureMode.Star
    override fun getEnvChildren(): List<Entity> { return children }
    override fun addEnvChild(entity: Entity) { children.add(entity) }
    override fun removeEnvChild(entity: Entity) { children.remove(entity) }

    override fun step() {
        val neighbors = getNeighbors()
        val environment = getEnvironment()
        val radius = element.details.radius

        //applyForce(calculateForce(neighbors))
        applyNewPosition()
        reduceVelocity()
        checkBorders(environment)

        //radiusCounter = if (radiusCounter < 20) { state.value.element.details.radius } else { radiusCounter - 1 }

        // Поглощение: живые соседи снаружи звезды, коснувшиеся поверхности → StarEmission втянет их внутрь.
        neighbors
            .filter { it.alive }
            .filter { it.getEnvironment() !== this }
            .filter { it.distanceSquareTo(kinematics.position) < (radius + 10) * (radius + 10) }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }

        // Эмиссия/генерация: солнце создаёт протоны/электроны и выбрасывает накопленное наружу.
        requestReaction(listOf(this))
    }

    override fun destroy() {
        if (!alive) return
        alive = false
        markChanged()
        notifyDeath()
    }

}
