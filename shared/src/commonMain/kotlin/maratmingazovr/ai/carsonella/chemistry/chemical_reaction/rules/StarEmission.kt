package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.randomDirection

class StarEmission (
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "PhotoIonization"

    private var entity : Entity<*>? = null

    override suspend fun matches(reagents: List<Entity<*>>): Boolean {
        entity = null

        if (reagents.isEmpty()) return false

        val first = reagents.first()
        val firstElement = first.state().value.element
        if (firstElement != Element.Star) return false
        if (!first.state().value.alive) return false

        if (!chance(0.002f)) return false

        entity = first
        return true
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        val resultElement =  if (!chance(0.5f))  Element.Proton else  Element.Electron

        return ReactionOutcome(
            spawn = listOf {
                entityGenerator.createEntity(
                    resultElement,
                    entity!!.state().value.position,
                    randomDirection(),
                    2f,
                    energy = 0f,
                    environment = entity!!
                )
            },
        )
    }
}