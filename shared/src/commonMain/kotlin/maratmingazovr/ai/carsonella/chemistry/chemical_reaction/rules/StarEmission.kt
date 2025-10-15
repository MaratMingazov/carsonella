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
    private var entityReagents: List<Entity<*>> = listOf()

    override suspend fun matches(reagents: List<Entity<*>>): Boolean {
        entity = null
        entityReagents = listOf()

        if (reagents.isEmpty()) return false

        val first = reagents.first()
        val firstElement = first.state().value.element
        if (firstElement != Element.Star) return false
        if (!first.state().value.alive) return false

        if (!chance(0.012f)) return false

        val firstPosition = first.state().value.position
        val firstRadius = firstElement.radius
        val starAtoms = reagents
            .drop(1)
            .filter{reagent -> reagent.state().value.alive}
            .filter { reagent -> reagent.state().value.position.distanceSquareTo(firstPosition) < firstRadius * firstRadius }


        entity = first
        entityReagents = starAtoms
        return true
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        /*
        Когда концентрация элементов в звезде повышается, она начинает излучить их в космом
         */
        if (entityReagents.size < 10) {
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
        } else {
            // звезда излучит первый элемент в космос
            val reagent = entityReagents.first()
            return ReactionOutcome(
                updateState = listOf {
                    reagent.setEnvironment(entity!!.getEnvironment())
                    reagent.addVelocity(1f)
                                     },
            )
        }

    }
}