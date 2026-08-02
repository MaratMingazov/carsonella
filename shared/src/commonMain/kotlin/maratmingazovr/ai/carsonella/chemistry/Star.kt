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
    energy: Float,
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
    private var state = MutableStateFlow(
        EntityState(
            alive = true,
            kinematics = Kinematics(position, direction, velocity),
            energy = energy,
            electrons = electrons,
        )
    )
    private var radiusCounter = element.details.radius

    override fun state() = state

    override val mass: Float = (element.details.p + element.details.n).toFloat()
    override val protons: Int = element.details.p
    override val radius: Float = element.details.radius
    override fun distanceToSurface(point: Position): Float = state().value.kinematics.position.distanceTo(point) - radius // Кружок: расстояние до поверхности — это расстояние до центра минус радиус.
    override val displaySymbol: String get() = element.symbol(state().value.electrons)
    override val energyLevels: List<Float> get() = element.energyLevels(state().value.electrons)

    override val saveKey: String = element.name

    override fun describe(): String {
        val state = state().value
        return """
            |${element.label(state.electrons)}: ${id}
            |Position (${state.kinematics.position.x.toInt()}, ${state.kinematics.position.y.toInt()})
            |Velocity ${round(state.kinematics.velocity * 100) / 100}
            |Energy ${round(state.energy * 100) / 100}
        """.trimMargin()
    }

    override fun getEnvCenter() = state.value.kinematics.position
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
            .filter { it.state().value.alive }
            .filter { it.getEnvironment() !== this }
            .filter { state.value.kinematics.position.distanceSquareTo(it.state().value.kinematics.position) < (radius + 10) * (radius + 10) }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }

        // Эмиссия/генерация: солнце создаёт протоны/электроны и выбрасывает накопленное наружу.
        requestReaction(listOf(this))
    }

    override fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}
