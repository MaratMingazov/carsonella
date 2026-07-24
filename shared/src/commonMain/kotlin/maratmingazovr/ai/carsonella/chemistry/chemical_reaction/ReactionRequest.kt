package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.BondStrengthening
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.RingClosure
import kotlin.reflect.KClass

/**
 * Как [ChemicalReactionResolver.resolve] выбирает реакцию для запроса:
 *  - [WeightBased] (дефолт) — ЭМЁРДЖЕНТНО: среди всех применимых правил берётся лучшее по weight.
 *  - остальные — ФОРС игрока: рассматривается ТОЛЬКО правило [ruleClass] (клик в LeftPanel, механика «лего»).
 *    Форс имеет приоритет над weight-отбором.
 *
 * Привязка к КЛАССУ правила ([ruleClass]), а не к строковому id — типобезопасно: опечатка невозможна,
 * переименование класса подхватывает IDE-рефактор. `rule.id` остаётся строкой только для логов.
 */
enum class ReactionSelection(val ruleClass: KClass<out ReactionRule>?) {
    WeightBased(null),
    StrengthenBond(BondStrengthening::class),
    CloseRing(RingClosure::class),
}

/**
 * Запрос реакции от инициатора (`reagents.first()`). [selection] задаёт, как resolve её выберет.
 * Обычные запросы (сущность зовёт себя/соседей в step) идут с [ReactionSelection.WeightBased];
 * форс игрока создаёт `World.requestMoleculeAction` с конкретным [selection].
 */
data class ReactionRequest(
    val reagents: List<Entity>,
    val selection: ReactionSelection = ReactionSelection.WeightBased,
)