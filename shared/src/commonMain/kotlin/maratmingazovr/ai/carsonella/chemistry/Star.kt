package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*


class Star(
    id: Long,
    element: Element,
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
    private var radiusCounter = element.details.radius

    override fun state() = state

    override fun getEnvCenter() = state.value.position
    override fun getEnvRadius() = radiusCounter
    override fun getEnvTemperature() = TemperatureMode.Star
    override fun getEnvChildren(): List<Entity> { return children }
    override fun addEnvChild(entity: Entity) { children.add(entity) }
    override fun removeEnvChild(entity: Entity) { children.remove(entity) }

    override fun step() {
        val neighbors = getNeighbors()
        val environment = getEnvironment()
        val element = (state.value.species as Species.Elemental).element
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
            .filter { state.value.position.distanceSquareTo(it.state().value.position) < (radius + 10) * (radius + 10) }
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
