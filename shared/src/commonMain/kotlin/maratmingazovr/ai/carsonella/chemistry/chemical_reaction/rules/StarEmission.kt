package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.randomDirection
import kotlin.collections.List

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

        val starAtoms = first
            .getEnvChildren()
            .filter{reagent -> reagent.state().value.alive}


        entity = first
        entityReagents = starAtoms
        return true
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        /*
        Когда концентрация элементов в звезде повышается, она начинает излучить их в космос
         */
        if (entityReagents.size < 10) {
            val resultElement =  if (!chance(0.5f))  Element.Proton else Element.ELECTRON
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
            val updateList = mutableListOf<() -> Unit>()
            // звезда излучит первый элемент в космос
            val reagent =
                entityReagents.firstOrNull {
                    entity -> entity.state().value.element == Element.Proton
                        || entity.state().value.element == Element.ELECTRON
                        || entity.state().value.element == Element.O_16_ION_8
                }
            if (reagent != null) {
                updateList += {
                    reagent.updateMyEnvironment(entity!!.getEnvironment())
                    reagent.addVelocity(1f)
                }
            }
            return ReactionOutcome(updateState = updateList)
        }

    }
}