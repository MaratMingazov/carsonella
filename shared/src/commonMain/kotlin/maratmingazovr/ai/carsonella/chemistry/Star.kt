package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import kotlin.math.round


class Star(
    override val id: Long,
    val element: Element,
    position: Position,
    direction: Vec2D,
    velocity: Float,
    // energy звезде не нужна: ей никто её не меняет, а пульс в drawStar от неё не зависит.
    electrons: Int,
    private val children: MutableList<Entity> = mutableListOf(),
):
    Entity,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport()
{
    private var changes = MutableStateFlow(0L)
    private var radiusCounter = element.details.radius

    override fun changes() = changes

    override var kinematics: Kinematics = Kinematics(position, direction, velocity)
        set(value) { if (field != value) { field = value; markChanged() } }
    override var alive: Boolean = true
        private set
    override val mass: Float = (element.details.p + element.details.n).toFloat()
    override val protons: Int = element.details.p
    // Символ и уровни звезды от заряда не зависят (не-атом), но соседи-атомы снаружи видят её в
    // calculateForce — там оболочка и работает.
    override val electrons: Int = electrons
    override val radius: Float = element.details.radius
    override fun distanceToSurface(point: Position): Float = kinematics.position.distanceTo(point) - radius // Кружок: расстояние до поверхности — это расстояние до центра минус радиус.
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
            .filter { kinematics.position.distanceSquareTo(it.kinematics.position) < (radius + 10) * (radius + 10) }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }

        // Эмиссия/генерация: солнце создаёт протоны/электроны и выбрасывает накопленное наружу.
        requestReaction(listOf(this))
    }

    override fun destroy() {
        alive = false
        markChanged()
        notifyDeath()
    }

}
