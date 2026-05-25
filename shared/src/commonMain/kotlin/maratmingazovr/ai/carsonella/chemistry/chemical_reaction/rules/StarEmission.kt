package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.randomDirection
import kotlin.collections.List

// Звезда либо генерирует внутри себя протон с электроном
// Либо при большой концентрации  излучает элементы наружку в космос
class StarEmission (
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "StarEmission"

    private var entity : Entity<*>? = null
    private var entityReagents: List<Entity<*>> = listOf()

    override fun matches(reagents: List<Entity<*>>): Boolean {
        entity = null
        entityReagents = listOf()

        if (reagents.isEmpty()) return false

        val first = reagents.first()
        val firstElement = first.state().value.element
        if (firstElement != Element.Star) return false
        if (!first.state().value.alive) return false

        if (!chance(0.012f, entityGenerator.random)) return false

        val starAtoms = first
            .getEnvChildren()
            .filter{reagent -> reagent.state().value.alive}


        entity = first
        entityReagents = starAtoms
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {

        /*
        Когда концентрация элементов в звезде повышается, она начинает излучить их в космос
         */
        if (entityReagents.size < 20) {
            val resultElement =  if (!chance(0.5f, entityGenerator.random))  Element.Proton else Element.ELECTRON
            // TEMP: временно логируем fuel-ветку чтобы понять, какая ветка срабатывает у игрока.
            // По умолчанию она молчит (chance 0.012/тик → ~0.75 раз/сек на звезду, шум в логе).
            return ReactionOutcome(
                spawn = listOf {
                    entityGenerator.createEntity(
                        resultElement,
                        entity!!.state().value.position,
                        randomDirection(entityGenerator.random),
                        2f,
                        energy = 0f,
                        environment = entity!!
                    )
                },
                //description = "$id (fuel, n=${entityReagents.size}): ${Element.Star.details.symbol} ⊕ ${resultElement.details.symbol}",
            )
        } else {
            // Звезда выбрасывает случайного живого ребёнка наружу. Раньше был хардкод p⁺/e⁻/O⁸⁺,
            // из-за которого продукты нуклеосинтеза (Li, N, Ne, Mg, Si, … вплоть до ⁵⁶Ni) застревали
            // внутри звезды и игроку не показывались.
            val reagent = entityReagents.randomOrNull(entityGenerator.random)
            val updateList = mutableListOf<() -> Unit>()
            var description = ""
            if (reagent != null) {
                updateList += {
                    reagent.updateMyEnvironment(entity!!.getEnvironment())
                    reagent.addVelocity(1f)
                }
                description = "$id: ${Element.Star.details.symbol} → ${reagent.state().value.element.details.symbol}"
            }
            return ReactionOutcome(updateState = updateList, description = description)
        }

    }
}