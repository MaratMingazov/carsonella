package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.randomDirection


class H2ToH2AndPhoton(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {
    override val id = "H₂->H₂ + γ"

    private var diHydrogen : Entity<*>? = null


    override suspend fun matches(reagents: List<Entity<*>>) : Boolean {
        diHydrogen = null

        if (reagents.size != 1) return false
        val first = reagents.first()

        if (first.state().value.element != Element.H2) return false
        if (!first.state().value.alive) return false

        val H_ev = 3f
        if (first.state().value.energy < H_ev) return false
        if (!chance(0.02f)) return false // в этом случае он с определенной вероятностью избавится от этой энергии

        diHydrogen = first
        return true
    }

    override suspend fun weight() = 0f

    override suspend fun produce(): ReactionOutcome {

        return ReactionOutcome(
            updateState = listOf { diHydrogen!!.addEnergy(-1.8f) },
            spawn = listOf {
                entityGenerator.createEntity(
                    Element.Photon,
                    diHydrogen!!.state().value.position.plus(Position(Element.H.radius, 0f)),
                    randomDirection(),
                    40f,
                    energy = 1.8f
                )
            },
        )

    }


}