package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.star_rules

import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

abstract class StarReactionRule : ReactionRule {

    final override fun matches(reagents: List<Entity>): MatchedData? {
        // Класс Star строится только для Element.Star, поэтому отдельная проверка элемента не нужна.
        val star = reagents.firstOrNull() as? Star ?: return null
        if (!star.state().value.alive) return null
        return matchesStar(star, reagents.subList(1, reagents.size))
    }

    abstract fun matchesStar(star: Star, neighbors: List<Entity>): MatchedData?
}