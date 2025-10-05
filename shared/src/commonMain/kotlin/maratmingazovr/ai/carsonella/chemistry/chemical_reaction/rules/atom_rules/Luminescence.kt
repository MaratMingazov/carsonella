package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Люминесценция — это холодное свечение вещества, возникающее после поглощения им энергии возбуждения,
 * то есть излучение света нетеплового происхождения, в отличие от накала.
 * В результате перехода молекул из возбуждённого состояния в основное состояние происходит излучение света.
 * Этот процесс отличается от теплового излучения и может быть вызван различными видами энергии,
 * например, химическими реакциями, электрическим полем или облучением.
 *
 * Атом или молекула находится в возбужденном состоянии, он может испускать фотон, чтобы отдать лишнюю энергию
 */
class Luminescence(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "Luminescence"

    private var entity : Entity<*>? = null


    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        entity = null

        if (reagents.size != 1) return false
        val first = reagents.first()
        if (!first.state().value.alive) return false

        val firstElement = first.state().value.element
        val firstElementLuminescenceEnergy = if (firstElement.energyIonization != null) {
              0.8 * firstElement.energyIonization
        } else if (firstElement.energyBondDissociation != null) {
            0.8 * firstElement.energyBondDissociation
        } else {
            return false
        }

        if (first.state().value.energy < firstElementLuminescenceEnergy) return false
        if (!chance(0.02f)) return false // в этом случае он с определенной вероятностью избавится от этой энергии

        entity = first
        return true
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        return ReactionOutcome(
            updateState = listOf { entity!!.addEnergy(-1.8f) },
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.Photon,
                    entity!!.state().value.position.plus(Position(Element.H.radius, 0f)),
                    randomDirection(),
                    40f,
                    energy = 1.8f
                )
            },
        )
    }

}