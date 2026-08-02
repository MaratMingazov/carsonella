package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

/**
 * Базовый класс для правил, где субъект — НЕ молекула: атом, частица или звезда.
 *
 * Резолвер гоняет `matches()` по всем правилам на каждый запрос, а `Molecule.step()` запрашивает
 * реакцию первой собой — поэтому субъекта-молекулу надо отсечь здесь, иначе каждое правило
 * проверяло бы это само.
 *
 * Хвост НЕ фильтруется: соседей наследники разбирают сами, и каждый проверяет класс того, кого ищет
 * (`it is SubAtom && it.element == NEUTRON`). Сузить субъект до одного класса нельзя — `Annihilation`
 * ждёт позитрон, `StarEmission` звезду, `StarPPChain` и `RecombinationReaction` работают и с атомом,
 * и с голым протоном. Поэтому наследники кастуют субъекта к тому, что нужно именно им.
 *
 * Молекулярные правила живут в пакете `molecule_rules` и матчатся по графу.
 */
abstract class AtomReactionRule : ReactionRule {

    final override fun matches(reagents: List<Entity>): MatchedData? {
        val subject = reagents.firstOrNull() ?: return null
        if (subject is Molecule) return null
        return matchesAtoms(reagents)
    }

    /** Как `matches`, но субъект гарантированно не молекула. */
    abstract fun matchesAtoms(reagents: List<Entity>): MatchedData?
}