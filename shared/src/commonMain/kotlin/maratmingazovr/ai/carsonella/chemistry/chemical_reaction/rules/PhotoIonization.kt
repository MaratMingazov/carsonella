package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Element.Photon
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

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

    override suspend fun matches(reagents: List<Entity<*>>): Boolean {
        entity = null
        photon = null

        if (reagents.size < 2) return false

        val first = reagents.first()
        val firstElement = first.state().value.element
        if (firstElement.details.energyLevels.isEmpty()) return false
        if (firstElement.details.ion == null) return false
        if (!first.state().value.alive) return false
        val others = reagents.drop(1)
        val activationDistanceSquare = firstElement.details.radius * firstElement.details.radius

        val (nearestPhoton, distance) = others
            .asSequence()
            .filter { it.state().value.element == Photon }
            .filter { it.state().value.energy > 0 }
            .filter { it.state().value.alive }
            .map { it to first.state().value.position.distanceSquareTo(it.state().value.position) }
            .minByOrNull { it.second }
            ?: return false

        if (distance > activationDistanceSquare) return false
        val expectedEnergy = first.state().value.energy + nearestPhoton.state().value.energy
        if (firstElement.details.energyLevels.contains(expectedEnergy) || expectedEnergy > firstElement.details.energyLevels.last()) {
            entity = first
            photon = nearestPhoton
            return true
        }
        return false
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {
        /**
         *  Ионизация элемента
         *  Если в элемент прилетел фотон, то электрон заберет эту энергию.
         *  Если пройдем порог [ЭнергияИонизации], то электрон улетит из этого элемента
         */
        val energyIonization = entity!!.state().value.element.details.energyLevels.last()
        val entityEnergy = entity!!.state().value.energy
        val entityElement = entity!!.state().value.element
        val photonEnergy = photon!!.state().value.energy
        val photonElement = photon!!.state().value.element

        if (entityEnergy + photonEnergy < energyIonization) {
            // мы поглащаем энергию, так как порог еще не пройден
            return ReactionOutcome(
                consumed = listOf(photon!!),
                updateState = listOf { entity!!.addEnergy(photonEnergy) },
                description = "Фотоионизация: ${entityElement.details.label} (${entityEnergy}eV) + ${photonElement.details.label} (${photonEnergy}eV) -> ${entityElement.details.label} (${entityEnergy + photonEnergy}eV)"
            )
        } else {
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

            val electron = Electron
            val electronPosition = entityPosition.plus(Position(1f * entityElement.details.radius, 0f))
            val electronDirection = entityDirection
            val electronVelocity = 10 + 0.2f * freeEnergy
            val electronEnergy = 0f

            return ReactionOutcome(
                consumed = listOf(photon!!, entity!!),
                spawn = listOf {
                    entityGenerator.createEntity(ion, ionPosition, ionDirection, ionVelocity, ionEnergy)
                    entityGenerator.createEntity(electron, electronPosition, electronDirection, electronVelocity, electronEnergy)
                },
                description = "Фотоионизация: ${entityElement.details.label} (${entityEnergy}eV) + ${photonElement.details.label} (${photonEnergy}eV) -> ${ion.details.label} (${ionEnergy}eV) + ${electron.details.label} (${electronEnergy}eV)"
            )
        }
    }
}