package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import kotlin.math.round


data class SpaceModuleState(
    override val id: Long,
    override val element: Element,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
    override var energy: Float,
) : EntityState<SpaceModuleState> {
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float, energy: Float) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity, energy = energy)
    override fun toString(): String {
        return """
            |${element.details.label}: $id
            |Position (${position.x.toInt()}, ${position.y.toInt()})
            |Velocity ${round(velocity * 100) / 100}
            |Energy ${round(energy * 100) / 100}
        """.trimMargin()
    }
}

open class SpaceModule(
    id: Long,
    element: Element,
    position: Position,
    direction: Vec2D,
    velocity: Float,
    energy: Float,
    protected val children: MutableList<Entity<*>> = mutableListOf(),
):
    Entity<SpaceModuleState>,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport()
{
    protected var state = MutableStateFlow(
        SpaceModuleState(
            id = id,
            element = element,
            alive = true,
            position = position,
            direction = direction,
            velocity = velocity,
            energy = energy,
        )
    )
    protected val stepMutex = Mutex()

    protected var radiusCounter = element.details.radius
    private var reagent1Element: Element = Element.ELECTRON
    private var reagent2Element: Element = Element.Proton
    protected var reagent1: Entity<*>? = null
    protected var reagent2: Entity<*>? = null

    override fun state() = state

    override suspend fun init() {

        while (state.value.alive) {
            stepMutex.withLock {

                //val neighbors = getNeighbors()

                reagent1 = findReagent(predicate = { it.state().value.element == reagent1Element }, reagent1)
                reagent2 = findReagent(predicate = { it.state().value.element == reagent2Element }, reagent2)

                updateChildrenAndRadius(needToExcludePredicate = { it.state().value.element != reagent1Element  &&  it.state().value.element != reagent2Element })
//                val environment = getEnvironment()
//                val radius = state.value.element.radius
//
//                //applyForce(calculateForce(neighbors))
//                applyNewPosition()
//                reduceVelocity()
//                checkBorders(environment)
//
//                neighbors
//                    .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < (radius + 10) * (radius + 10) }
//                    .takeIf { it.isNotEmpty() }
//                    ?.let { requestReaction(listOf(this) + it) }
//
//                //if (state.value.energy > 0) { requestReaction(listOf(this)) }
//                requestReaction(listOf(this))

            }
            delay(10)
        }
    }

    fun setReagent1Element(element: Element) {
        reagent1Element = element
    }
    fun setReagent2Element(element: Element) {
        reagent2Element = element
    }

    protected fun updateChildrenAndRadius(
        needToExcludePredicate: (Entity<*>) -> Boolean,
    ) {
        children
            .find { needToExcludePredicate(it) }
            ?.updateMyEnvironment(getEnvironment())

        radiusCounter = if (radiusCounter < 2) { state.value.element.details.radius } else { radiusCounter - 1 }
    }

    protected fun findReagent(
        predicate: (Entity<*>) -> Boolean,
        childReagent: Entity<*>?
    ) : Entity<*>? {
        val reagent = children.find { it == childReagent }
        if (reagent == null) {
            val newReagent = getNeighbors().find { predicate(it) &&  it.state().value.alive}
            newReagent?.updateMyEnvironment(this)
            return newReagent
        } else {
            return reagent
        }
    }

    override fun getEnvCenter() = state.value.position
    override fun getEnvRadius() = radiusCounter
    override fun getEnvTemperature() = TemperatureMode.Space
    override fun getEnvChildren(): List<Entity<*>> { return children }
    override fun addEnvChild(entity: Entity<*>) { children.add(entity) }
    override fun removeEnvChild(entity: Entity<*>) { children.remove(entity) }

    override suspend fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}

class RecombinationModule(
    id: Long,
    element: Element,
    position: Position,
    direction: Vec2D,
    velocity: Float,
    energy: Float,
): SpaceModule(id, element, position, direction, velocity, energy) {
    override suspend fun init() {

        while (state.value.alive) {
            stepMutex.withLock {
                reagent1 = findReagent(predicate = { it.state().value.element.details.recombinationElement != null}, reagent1)
                reagent2 = findReagent(predicate = { it.state().value.element == Element.ELECTRON }, reagent2)

                updateChildrenAndRadius(needToExcludePredicate = {!(it.state().value.element == Element.ELECTRON || it.state().value.element.details.recombinationElement != null)})
            }
            delay(10)
        }
    }
}
