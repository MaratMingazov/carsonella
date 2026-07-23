package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

/**
 * Базовый класс для молекулярных правил (рост 3b, граф-диссоциация, …): субъект реакции
 * (`reagents.first()`) ОБЯЗАН быть молекулой [Species.Molecular].
 *
 * Симметрично AtomReactionRule, но с двумя отличиями:
 *  - гейтит субъект на [Species.Molecular] (а не на Elemental);
 *  - хвост НЕ фильтрует — партнёром молекулы законно бывает и атом (рост атом+молекула, фотон при
 *    диссоциации), и другая молекула (рост молекула+молекула). Правила-наследники читают граф/`species`,
 *    а НЕ шов [Entity.state]`.element`, поэтому соседи-молекулы их не роняют (в отличие от атомных правил,
 *    которым фильтр нужен именно ради безопасности `.element`).
 *
 * Так рост молекулы привязан к субъекту-молекуле: атом+атом собирает атомное правило
 * (`CovalentBondFormation`), а атом+молекула / молекула+молекула — правило отсюда.
 */
abstract class MoleculeReactionRule : ReactionRule {

    final override fun matches(reagents: List<Entity>): Boolean {
        if (reagents.firstOrNull()?.state()?.value?.species !is Species.Molecular) return false
        return matchesMolecule(reagents)
    }

    /** Как `matches`, но гарантированно `reagents.first()` — молекула ([Species.Molecular]). */
    abstract fun matchesMolecule(reagents: List<Entity>): Boolean
}