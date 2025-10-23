package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element.Photon
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

/**
 * Фотодиссоциация или Фотоэффект
 * Реакция фотодиссоциации, или фотолиза, — это химическое разложение молекул под действием фотонов,
 * где энергия фотона превышает энергию активации молекулы, вызывая её распад на атомы, радикалы или ионы.
 */
class PhotoDissociation(private val entityGenerator: IEntityGenerator, ) : ReactionRule {
    override val id = "Photodissociation"

    private var entity : Entity<*>? = null
    private var photon : Entity<*>? = null


    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        entity = null
        photon = null

        if (reagents.size < 2) return false

        val first = reagents.first()
        val firstElement = first.state().value.element
        if (firstElement.details.energyBondDissociation == null) return false
        if (firstElement.details.dissociationElements.isEmpty()) return false
        if (!first.state().value.alive) return false
        val others = reagents.drop(1)
        val activationDistanceSquare = firstElement.details.radius * firstElement.details.radius

        val (nearestPhoton, distance) = others
            .asSequence()
            .filter { it.state().value.element == Photon }
            .filter { it.state().value.alive }
            .map { it to first.state().value.position.distanceSquareTo(it.state().value.position) }
            .minByOrNull { it.second }
            ?: return false

        if (distance <= activationDistanceSquare) {
            entity = first
            photon = nearestPhoton
            return true
        }
        return false
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        /**
         *  Энергетический порог молекулы.
         *  Если в молекулу прилетел фотон, то молекула либо заберет эту энергию, либо сама распадется
         */
        val energyDissociation = entity!!.state().value.element.details.energyBondDissociation!!
        val entityEnergy = entity!!.state().value.energy
        val photonEnergy = photon!!.state().value.energy

        if (entityEnergy + photonEnergy < energyDissociation) {
            // мы поглащаем энергию, так как порог еще не пройден
            return ReactionOutcome(
                consumed = listOf(photon!!),
                updateState = listOf { entity!!.addEnergy(photonEnergy) },
            )
        } else {
            // пройден энергетический порог. Происходит диссоциация
            val entityPosition = entity!!.state().value.position
            val entityElement = entity!!.state().value.element
            val entityDirection = entity!!.state().value.direction
            val entityVelocity = entity!!.state().value.velocity
            val dissociationElements = entityElement.details.dissociationElements

            return ReactionOutcome(
                consumed = listOf(photon!!, entity!!),
                spawn = listOf {
                    entityGenerator.createEntity(
                        dissociationElements[0],
                        entityPosition.plus(Position(-1f * entityElement.details.radius, 0f)),
                        entityDirection,
                        entityVelocity,
                        energy = 0f,
                    )
                    entityGenerator.createEntity(
                        dissociationElements[1],
                        entityPosition.plus(Position(1f * entityElement.details.radius, 0f)),
                        entityDirection,
                        entityVelocity,
                        energy = 0f,
                    )
                },
            )
        }

    }


}