package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.behavior.Movable
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionSelection

/**
 * Данные успешного матча — ИММУТАБЕЛЬНЫЙ снимок всего, что понадобится [ReactionRule.weight] и
 * [ReactionRule.produce]: сами реагенты и всё, что правило успело про них выяснить (уровень энергии,
 * связь для усиления, канал реакции…). Каждое правило объявляет свой дата-класс `Match`.
 *
 * ЗАЧЕМ отдельный объект, а не поля правила. Экземпляр правила ОДИН на весь резолвер, а `matches()`
 * гоняется по всем правилам на каждый запрос — и обнуляет поля в начале, даже когда не матчится.
 * Отложенные лямбды исхода (`spawn`/`updateState`) исполняются ПОЗЖЕ, уже после всех этих вызовов:
 * читай они поля, получили бы null (NPE) или, что хуже, чужую частицу — и молча её изменили.
 * Матч передаётся аргументом и живёт в замыкании, поэтому протухнуть ему негде.
 */
interface MatchedData

/**
 * Описание одной реакции (набор реагентов → продукты).
 * */
interface ReactionRule {
    val id: String
    /**
     * Метод должен проверить возможна ли конкретная реакция.
     * [MatchedData] - реакция возможна и произойдет, если не найдется более выгодная реакция
     * null - реакция невозможна
     * */
    fun matches(reagents: List<Entity>): MatchedData?

    /** Этот метод вычисляет насколько вероятна реакция
     * Чем больше вес, тем реакция вероятнее
     * ЕСли у нас будет несколько допустимых реакций, мы выберем реакцию с наибольшим весом
     * Дефолт 0f — большинству правил вес не нужен, они конкурируют только между собой.
     * */
    fun weight(match: MatchedData): Float = 0f

    /** [match] — то, что вернул [matches] ЭТОГО же правила; внутри кастуется к своему типу. */
    fun produce(match: MatchedData): ReactionOutcome

    /**
     * Вычисляем направление движения и скорость новой частицы после столкновения двух частиц.
     * Учитываем скорости направления и массу этих частиц
     */
    // Movable, а не Entity: нужны направление, скорость и масса — то есть ровно движение. Молекулы сюда
    // не приходят, но и запрещать их незачем: центр масс она посчитает по атомам сама.
    fun calculateNewEntityDirectionAndVelocity(entity1: Movable, entity2: Movable,) : Pair<Vec2D, Float> {
        val electronMass = entity1.mass
        val protonMass = entity2.mass
        val sumMass = electronMass + protonMass

        val electronVelocityVector = entity1.kinematics.direction.times(entity1.kinematics.velocity)
        val protonVelocityVector = entity2.kinematics.direction.times(entity2.kinematics.velocity)
        val impulseVectorTotal = electronVelocityVector.times(electronMass) + protonVelocityVector.times(protonMass)

        val newEntityVelocityVector = impulseVectorTotal.div(sumMass)
        val newEntityVelocity = newEntityVelocityVector.length()
        val newEntityDirection = if (newEntityVelocity > 1e-6f) newEntityVelocityVector.div(newEntityVelocity) else Vec2D(1f, 0f)

        return Pair(newEntityDirection,newEntityVelocity.coerceAtMost(MAX_VELOCITY))
    }
}

/**
 * Правило, которое запускает ТОЛЬКО игрок (механика «лего»): вместе с реагентами приходит его ВЫБОР —
 * какую именно связь усилить, где замкнуть кольцо.
 *
 * ЗАЧЕМ отдельный интерфейс, а не флаг на [ReactionRule]: у форса другой контракт. На вход идёт лишний
 * аргумент (выбор игрока), а [ReactionRule.weight] не нужен вовсе — форс ни с кем не конкурирует,
 * resolve выполняет его без отбора. Из-за разных контрактов и списки в резолвере разные (`rules` /
 * `forcedRules`): так эмёрджентный отбор не может случайно задеть правило, которое обязано ждать клика.
 *
 * Правило может реализовать ОБА интерфейса и попасть в оба списка — тогда оно и срабатывает само (выбрав
 * лучший вариант по weight), и подчиняется выбору игрока (RingClosure сегодня так и живёт).
 */
interface ForcedReactionRule {
    val id: String

    /**
     * Применимо ли правило к выбору игрока. [selection] сужается реализацией до своего типа
     * (`selection as? ReactionSelection.StrengthenBond ?: return null`) — чужой выбор не наш.
     */
    fun matches(reagents: List<Entity>, selection: ReactionSelection.Forced): MatchedData?

    fun produce(match: MatchedData): ReactionOutcome
}

data class StateUpdate(
    val entity: Entity,
    val mutate: () -> Unit,
)

// Что делать миру после реакции
data class ReactionOutcome(
    val consumed: List<Entity> = listOf(),       // атомы, которые участвовали в реакции, как правило они умирают
    val spawn: List<() -> Entity> = listOf(),    // новые атомы и молекулы, которые появились. Вот тут нужно каким то образом сказать что нужно создать
    val updateState: List<StateUpdate> = listOf(),  // реагенты, которые реакцию пережили, но изменились
    val ruleId: String = "",                        // кто сработал; проставляет резолвер, правилу думать не нужно
)