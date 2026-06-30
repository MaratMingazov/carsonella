package maratmingazovr.ai.carsonella.chemistry

import kotlinx.coroutines.flow.MutableStateFlow
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.POSITRON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.behavior.*
import kotlin.math.round


data class SubAtomState(
    override val id: Long,
    override val species: Species.Elemental,
    override var alive: Boolean,
    override var position: Position,
    override var direction: Vec2D,
    override var velocity: Float,
    override var energy: Float,
    override var electrons: Int,
) : EntityState<SubAtomState> {
    // species сужен до Elemental (субатомная частица — всегда Elemental) → element читается напрямую, без каста/броска шва EntityState.
    override val element: Element get() = species.element
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float, energy: Float, electrons: Int) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
    override fun toString(): String {
        return """
            |${element.label(electrons)}: $id
            |Position (${position.x.toInt()}, ${position.y.toInt()})
            |Velocity ${round(velocity * 100) / 100}
            |Energy ${round(energy * 100) / 100}
        """.trimMargin()
    }
}

class SubAtom(
    id: Long,
    element: Element,
    position: Position,
    direction: Vec2D,
    velocity: Float,
    energy: Float,
    electrons: Int,
):
    Entity<SubAtomState>,
    DeathNotifiable by OnDeathSupport(),
    NeighborsAware by NeighborsSupport(),
    ReactionRequester by ReactionRequestSupport(),
    EnvironmentAware by EnvironmentSupport(),
    LogWritable  by LoggingSupport()
{
    private var state = MutableStateFlow(
        SubAtomState(
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

        when (state.value.element) {
            PHOTON -> initPhoton(environment)
            ELECTRON -> initElectron(environment, neighbors)
            Proton -> initProton(environment, neighbors)
            POSITRON -> initPositron(environment, neighbors)
            NEUTRON -> initNeutron(environment)
            else -> throw NotImplementedError()
        }
    }


    private fun initPhoton(environment: IEnvironment) {
        applyNewPosition()
        // Фотон достиг границы своей среды?
        val distanceSquare = state.value.position.distanceSquareTo(environment.getEnvCenter())
        if (distanceSquare > environment.getEnvRadius() * environment.getEnvRadius()) {
            // Если среда — частица-контейнер (звезда/модуль), она выпускает фотон в свою внешнюю
            // среду: свет уходит из звезды в космос (тот же приём updateMyEnvironment, что и в StarEmission).
            // Если это корневая среда (не Entity) — фотон покидает мир и гаснет.
            val container = environment as? Entity<*>
            if (container != null) {
                updateMyEnvironment(container.getEnvironment())
            } else {
                destroy()
            }
        }
    }

    private fun initElectron(environment: IEnvironment, neighbors: List<Entity<*>>) {
        reduceVelocity()
        applyForce(calculateForce(neighbors)) // электроны должны отталкиваться друг от друга
        applyNewPosition()
        checkBorders(environment)
    }

    private fun initProton(environment: IEnvironment, neighbors: List<Entity<*>>) {
        reduceVelocity()
        applyForce(calculateForce(neighbors))
        applyNewPosition()
        checkBorders(environment)

        neighbors
            .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 5000f }
            .takeIf { it.isNotEmpty() }
            ?.let {requestReaction(listOf(this) + it) }
    }

    // Нейтрон электрически нейтрален → не реагирует на кулоновские силы (нет applyForce).
    // Реакции с участием нейтрона (n,γ-захват, (α,n) и т.п.) запрашиваются другими реагентами —
    // сам нейтрон requestReaction не вызывает, чтобы не дублировать запросы.
    private fun initNeutron(environment: IEnvironment) {
        reduceVelocity()
        applyNewPosition()
        checkBorders(environment)
    }

    // Поведение идентично протону: движение под действием сил + запрос реакции с близкими соседями.
    // requestReaction нужен для будущей аннигиляции с электроном (e⁻ + e⁺ → 2γ).
    private fun initPositron(environment: IEnvironment, neighbors: List<Entity<*>>) {
        reduceVelocity()
        applyForce(calculateForce(neighbors))
        applyNewPosition()
        checkBorders(environment)

        neighbors
            .filter { entity -> state.value.position.distanceSquareTo(entity.state().value.position) < 5000f }
            .takeIf { it.isNotEmpty() }
            ?.let { requestReaction(listOf(this) + it) }
    }

    override fun destroy() {
        state.value = state.value.copy(alive = false)
        notifyDeath()
    }

}