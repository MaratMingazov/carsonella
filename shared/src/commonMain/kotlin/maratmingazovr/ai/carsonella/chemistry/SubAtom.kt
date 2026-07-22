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
    override val alive: Boolean,
    override val position: Position,
    override val direction: Vec2D,
    override val velocity: Float,
    override val energy: Float,
    override val electrons: Int,
) : EntityState<SubAtomState> {
    // species сужен до Elemental (субатомная частица — всегда Elemental) → element читается напрямую, без каста/броска шва EntityState.
    val element: Element get() = species.element
    override fun copyWith(alive: Boolean, position: Position, direction: Vec2D, velocity: Float, energy: Float, electrons: Int) =  this.copy(alive = alive, position = position, direction = direction, velocity = velocity, energy = energy, electrons = electrons)
    override fun toString(): String {
        val base = """
            |${element.label(electrons)}: $id
            |Position (${position.x.toInt()}, ${position.y.toInt()})
            |Velocity ${round(velocity * 100) / 100}
            |Energy ${round(energy * 100) / 100}
        """.trimMargin()
        // LightBand и длина волны осмысленны только у фотона (у него energy — это E=hν). У протона,
        // электрона и т.п. energy к свету не относится, поэтому строку не добавляем и функцию не зовём.
        if (element != PHOTON) return base
        return "$base\nСпектр: ${lightBandFromEnergyEv(energy).label}"
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

// --- Физика фотона: что можно узнать из одной лишь энергии ---
// energy — единственный источник правды; длина волны и «тип света» из неё выводятся, не хранятся.
// Живёт пока рядом с SubAtom (фотон — субатомная частица); вынесем в отдельный файл, если разрастётся.

/**
 * Связь энергии фотона и длины волны: E = hc / λ. Энергия в модели в эВ, длину волны считаем в нм.
 * В этих единицах hc свёрнуто в одну константу: E[эВ] · λ[нм] = 1239.841984 эВ·нм (CODATA).
 */
const val PHOTON_HC_EV_NM = 1239.841984f

// Энергия фотона по умолчанию при создании из палитры: красный H-α (1.89 эВ ≈ 656 нм) — видимый
// свет ниже порога ионизации водорода. Инвариант: фотон не должен рождаться с нулевой энергией.
const val DEFAULT_PHOTON_ENERGY_EV = 1.89f

/**
 * λ[нм] по энергии E[эВ]. У реального фотона энергия всегда > 0, поэтому λ всегда конечна.
 * E ≤ 0 — это не крайний случай, а баг (фотона с нулевой энергией не существует) → падаем с require,
 * чтобы источник проблемы всплыл, а не маскировался «нет света».
 */
fun wavelengthNmFromEnergyEv(energyEv: Float): Float {
    require(energyEv > 0f) { "Энергия фотона должна быть > 0, получено: $energyEv" }
    return PHOTON_HC_EV_NM / energyEv
}

/**
 * Диапазон электромагнитного спектра (и цвет внутри видимого). Порядок — по возрастанию энергии.
 * label — человекочитаемое имя для UI. Позже сюда можно добавить Color для отрисовки фотона.
 */
enum class LightBand(val label: String) {
    RADIO("радио"),
    MICROWAVE("микроволны"),
    INFRARED("инфракрасный"),
    RED("красный"),
    ORANGE("оранжевый"),
    YELLOW("жёлтый"),
    GREEN("зелёный"),
    BLUE("синий"),
    VIOLET("фиолетовый"),
    ULTRAVIOLET("ультрафиолет"),
    XRAY("рентген"),
    GAMMA("гамма"),
}

/**
 * «Что это за свет» по энергии фотона. Классифицируем через длину волны (границы диапазонов
 * естественно заданы в нм). У любого реального фотона (E > 0) диапазон определён; E ≤ 0 → require
 * в wavelengthNmFromEnergyEv упадёт (это баг). Границы видимых цветов — общепринятое приближение.
 */
fun lightBandFromEnergyEv(energyEv: Float): LightBand {
    val nm = wavelengthNmFromEnergyEv(energyEv)
    return when {
        nm > 1e9f  -> LightBand.RADIO        // λ > 1 м
        nm > 1e6f  -> LightBand.MICROWAVE    // 1 мм … 1 м
        nm > 750f  -> LightBand.INFRARED     // 750 нм … 1 мм
        nm > 620f  -> LightBand.RED          // видимый: 380 … 750 нм
        nm > 590f  -> LightBand.ORANGE
        nm > 570f  -> LightBand.YELLOW
        nm > 495f  -> LightBand.GREEN
        nm > 450f  -> LightBand.BLUE
        nm > 380f  -> LightBand.VIOLET
        nm > 10f   -> LightBand.ULTRAVIOLET  // 10 … 380 нм
        nm > 0.01f -> LightBand.XRAY         // 0.01 … 10 нм
        else       -> LightBand.GAMMA        // λ < 0.01 нм
    }
}