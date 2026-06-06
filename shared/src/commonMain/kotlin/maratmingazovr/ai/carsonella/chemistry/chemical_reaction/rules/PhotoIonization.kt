package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import kotlin.math.abs

// Допуск при сопоставлении энергии фотона с уровнем атома. Нужен потому, что фотон, рождённый
// в SpontaneousEmission как разность двух уровней (E_high - E_low), может из-за float-округления
// не совпасть бит-в-бит с уровнем атома-мишени, хотя физически это resonant scattering.
// 0.01 eV в ~150 раз меньше минимального промежутка между уровнями (~1.5 eV у He, Li) — коллизий нет.
private const val ENERGY_EPSILON = 0.01f

/**
 * Фотоионизация — это процесс, при котором атом или молекула теряет электрон под воздействием фотона, становясь ионом
 * Ионизация под действием света. Или фотоэффект.
 * Если элемент наберет достаточно энергии (energyIonization), то электрон может вылететь с орбиты
 */
class PhotoIonization (
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "PhotoIonization"

    private var entity : Entity<*>? = null
    private var photon : Entity<*>? = null
    // null означает «ионизация» (energy >= top level), Float — точный уровень, на который нужно «снапнуть» entity
    private var matchedLevel : Float? = null

    override fun matches(reagents: List<Entity<*>>): Boolean {
        entity = null
        photon = null
        matchedLevel = null

        if (reagents.size < 2) return false

        val first = reagents.first()
        val firstElement = first.state().value.element
        val levels = firstElement.energyLevels(first.state().value.electrons)
        if (levels.isEmpty()) return false
        if (!first.state().value.alive) return false
        val others = reagents.drop(1)
        val activationDistanceSquare = firstElement.details.radius * firstElement.details.radius

        val (nearestPhoton, distance) = others
            .asSequence()
            .filter { it.state().value.element == PHOTON }
            .filter { it.state().value.energy > 0 }
            .filter { it.state().value.alive }
            .map { it to first.state().value.position.distanceSquareTo(it.state().value.position) }
            .minByOrNull { it.second }
            ?: return false

        if (distance > activationDistanceSquare) return false
        val expectedEnergy = first.state().value.energy + nearestPhoton.state().value.energy

        // Ионизация: энергии хватает достать электрон (с допуском по верхнему уровню)
        if (expectedEnergy >= levels.last() - ENERGY_EPSILON) {
            entity = first
            photon = nearestPhoton
            matchedLevel = null
            return true
        }

        // Поглощение: энергия попадает в окрестность одного из уровней
        val matched = levels.firstOrNull { abs(it - expectedEnergy) < ENERGY_EPSILON }
        if (matched != null) {
            entity = first
            photon = nearestPhoton
            matchedLevel = matched
            return true
        }
        return false
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        /**
         *  Ионизация элемента
         *  Если в элемент прилетел фотон, то электрон заберет эту энергию.
         *  Если пройдем порог [ЭнергияИонизации], то электрон улетит из этого элемента
         */
        val entityEnergy = entity!!.state().value.energy
        val entityElement = entity!!.state().value.element
        val photonEnergy = photon!!.state().value.energy
        val photonElement = photon!!.state().value.element
        val level = matchedLevel

        if (level != null) {
            // Поглощение: «снапаем» энергию атома на точный уровень из таблицы через setEnergy.
            // addEnergy(level - entityEnergy) дал бы a + (b - a), что в float не гарантирует b бит-в-бит.
            return ReactionOutcome(
                consumed = listOf(photon!!),
                updateState = listOf { entity!!.setEnergy(level) },
                description = "$id: ${entityElement.details.label} (${entityEnergy}eV) + ${photonElement.details.label} (${photonEnergy}eV) -> ${entityElement.details.label} (${level}eV)"
            )
        } else {
            val energyIonization = entity!!.state().value.element.details.energyLevels.last()
            // пройден энергетический порог. Электрон накопил достаточно энергии, чтобы улететь
            val freeEnergy = entityEnergy + photonEnergy - energyIonization
            val entityPosition = entity!!.state().value.position
            val entityDirection = entity!!.state().value.direction
            val entityVelocity = entity!!.state().value.velocity

            val ion = entityElement.details.ion!!
            val ionPosition = entityPosition.plus(Position(-1f * entityElement.details.radius, 0f))
            val ionDirection = entityDirection
            val ionVelocity = entityVelocity
            val ionEnergy = 0f

            val electron = ELECTRON
            val electronPosition = entityPosition.plus(Position(1f * entityElement.details.radius, 0f))
            val electronDirection = entityDirection
            val electronVelocity = 10 + 0.2f * freeEnergy
            val electronEnergy = 0f

            val env = entity!!.getEnvironment()
            return ReactionOutcome(
                consumed = listOf(photon!!, entity!!),
                spawn = listOf {
                    entityGenerator.createEntity(ion, ionPosition, ionDirection, ionVelocity, ionEnergy, env)
                    entityGenerator.createEntity(electron, electronPosition, electronDirection, electronVelocity, electronEnergy, env)
                },
                description = "$id: ${entityElement.details.label} + ${photonElement.details.label} -> ${ion.details.label} + ${electron.details.label}"
            )
        }
    }
}