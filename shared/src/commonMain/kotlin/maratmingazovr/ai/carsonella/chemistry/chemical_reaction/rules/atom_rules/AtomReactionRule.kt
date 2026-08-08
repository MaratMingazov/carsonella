package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule


abstract class AtomReactionRule : ReactionRule {

    final override fun matches(reagents: List<Entity>): MatchedData? {
        val atom = reagents.firstOrNull() as? Atom ?: return null
        if (!atom.state().value.alive) return null
        return matchesAtom(atom, reagents.subList(1, reagents.size))
    }

    abstract fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData?
}