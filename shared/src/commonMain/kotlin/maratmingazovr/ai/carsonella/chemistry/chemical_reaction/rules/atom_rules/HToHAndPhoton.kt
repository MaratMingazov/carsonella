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
 * Когда атом водорода находится в возбужденном состояние, электрон находится на втором уровне
 * Это не стабильное состояние.
 * Поэтому атом водорода может излучить фотон, чтобы уменьшить свою энергию
 */
class HToHAndPhoton(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "H->HandPhoton"

    private var hydrogen : Entity<*>? = null


    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        hydrogen = null

        if (reagents.size != 1) return false
        val first = reagents.first()

        if (first.state().value.element != Element.H) return false
        if (!first.state().value.alive) return false

        val H_ev = 10.2f // если у атома водорода больше этой энергии, то он находится в возбужденном состоянии
        if (first.state().value.energy < H_ev) return false
        if (!chance(0.02f)) return false // в этом случае он с определенной вероятностью избавится от этой энергии

        hydrogen = first
        return true
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        return ReactionOutcome(
            updateState = listOf { hydrogen!!.addEnergy(-1.8f) },
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.Photon,
                    hydrogen!!.state().value.position.plus(Position(Element.H.radius, 0f)),
                    randomDirection(),
                    40f,
                    energy = 1.8f
                )
            },
        )

    }


}