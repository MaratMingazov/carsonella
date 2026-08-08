package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.subatom_rules

import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

abstract class SubAtomReactionRule : ReactionRule {

    final override fun matches(reagents: List<Entity>): MatchedData? {
        val subAtom = reagents.firstOrNull() as? SubAtom ?: return null
        if (!subAtom.alive) return null
        return matchesSubAtom(subAtom, reagents.subList(1, reagents.size))
    }

    abstract fun matchesSubAtom(subAtom: SubAtom, neighbors: List<Entity>): MatchedData?
}