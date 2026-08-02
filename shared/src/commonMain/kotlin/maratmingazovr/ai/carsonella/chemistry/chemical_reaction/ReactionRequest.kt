package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MoleculeBond

/**
 * Как [ChemicalReactionResolver.resolve] выбирает реакцию для запроса:
 *  - [WeightBased] (дефолт) — ЭМЁРДЖЕНТНО: среди всех применимых правил берётся лучшее по weight.
 *  - [Forced] — ФОРС игрока (клик, механика «лего»): выполняется ИМЕННО его выбор, минуя
 *    weight-конкуренцию. Такие выборы обслуживают отдельные правила
 *    ([maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ForcedReactionRule]).
 *
 * Форс несёт ПАРАМЕТР — что именно игрок выбрал. Раньше здесь был enum со ссылкой на класс правила, а
 * КАКУЮ связь усилить решало само правило (первую подходящую по номеру узла). Теперь связь выбирает
 * игрок мышью, поэтому выбор едет в запросе; правило само сужает тип выбора до своего.
 */
sealed interface ReactionSelection {

    data object WeightBased : ReactionSelection

    sealed interface Forced : ReactionSelection

    /**
     * Усилить кратность КОНКРЕТНОЙ связи (клик по связи выбранной молекулы). [bond] адресует связь
     * номерами своих узлов — координаты внутри лишь снимок на момент клика, реакция их не читает.
     */
    data class StrengthenBond(val bond: MoleculeBond) : Forced

    /**
     * Замкнуть кольцо. Пару атомов пока выбирает само правило (лучший кандидат по weight) —
     * параметризуем так же, как усиление, когда появится клик по атомам.
     */
    data object CloseRing : Forced
}

/**
 * Запрос реакции от инициатора (`reagents.first()`). [selection] задаёт, как resolve её выберет.
 * Обычные запросы (сущность зовёт себя/соседей в step) идут с [ReactionSelection.WeightBased];
 * форс игрока создаёт `World.requestMoleculeAction` с конкретным [ReactionSelection.Forced].
 */
data class ReactionRequest(
    val reagents: List<Entity>,
    val selection: ReactionSelection = ReactionSelection.WeightBased,
)