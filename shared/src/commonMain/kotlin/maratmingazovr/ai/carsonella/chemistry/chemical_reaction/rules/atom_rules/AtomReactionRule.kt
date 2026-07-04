package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

/**
 * Базовый класс для атомных/ядерных/субатомных правил (§«крах» docs/molecule-graph.md).
 *
 * Эти правила читают [Entity.state]`.element` (шов [maratmingazovr.ai.carsonella.chemistry.EntityState.element]),
 * который БРОСАЕТ на [Species.Molecular]. Резолвер гоняет `matches()` по всем правилам на каждый запрос,
 * поэтому без фильтра молекула роняет всё — двумя путями:
 *  - молекула как субъект (`reagents.first()`) — `Molecule.step()` запрашивает реакцию первой собой;
 *  - молекула как сосед (хвост) — атом-субъект перебирает соседей, читая их `.element`.
 *
 * [matches] закрывает оба: субъект-не-[Species.Elemental] → правило не наше (return false);
 * соседи-молекулы выкидываются из хвоста. В [matchesAtoms] приходит список, где ВСЕ реагенты —
 * [Species.Elemental] и `first()` — исходный субъект, так что весь `.element`-код безопасен.
 *
 * Молекулярные правила (диссоциация графа и т.п.) живут в пакете `molecule_rules` и матчатся по графу.
 */
abstract class AtomReactionRule : ReactionRule {

    final override fun matches(reagents: List<Entity<*>>): Boolean {
        if (reagents.firstOrNull()?.state()?.value?.species !is Species.Elemental) return false
        return matchesAtoms(reagents)
    }

    /** Как прежний `matches`, но `reagents` гарантированно состоит только из [Species.Elemental]. */
    abstract fun matchesAtoms(reagents: List<Entity<*>>): Boolean
}