package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule


abstract class AtomReactionRule : ReactionRule {

    final override fun matches(reagents: List<Entity>): MatchedData? {
        val subject = reagents.firstOrNull() ?: return null
        if (subject is Molecule) return null
        return matchesAtoms(reagents)
    }

    /** Как `matches`, но субъект гарантированно не молекула. */
    abstract fun matchesAtoms(reagents: List<Entity>): MatchedData?
}