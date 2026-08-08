package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule


abstract class AtomReactionRule : ReactionRule {

    final override fun matches(reagents: List<Entity>): MatchedData? {
        if (reagents.firstOrNull() !is Atom) return null
        return matchesAtoms(reagents)
    }

    /** Как `matches`, но субъект гарантированно атом. */
    abstract fun matchesAtoms(reagents: List<Entity>): MatchedData?
}