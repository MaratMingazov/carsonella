package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY

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
    fun calculateNewEntityDirectionAndVelocity(entity1: Entity, entity2: Entity,) : Pair<Vec2D, Float> {
        val electronMass = entity1.mass()
        val protonMass = entity2.mass()
        val sumMass = electronMass + protonMass

        val electronVelocityVector = entity1.state().value.direction.times(entity1.state().value.velocity)
        val protonVelocityVector = entity2.state().value.direction.times(entity2.state().value.velocity)
        val impulseVectorTotal = electronVelocityVector.times(electronMass) + protonVelocityVector.times(protonMass)

        val newEntityVelocityVector = impulseVectorTotal.div(sumMass)
        val newEntityVelocity = newEntityVelocityVector.length()
        val newEntityDirection = if (newEntityVelocity > 1e-6f) newEntityVelocityVector.div(newEntityVelocity) else Vec2D(1f, 0f)

        return Pair(newEntityDirection,newEntityVelocity.coerceAtMost(MAX_VELOCITY))
    }
}

// Что делать миру после реакции
data class ReactionOutcome(
    val consumed: List<Entity> = listOf(),       // атомы, которые участвовали в реакции, как правило они умирают
    val spawn: List<() -> Entity> = listOf(),    // новые атомы и молекулы, которые появились. Вот тут нужно каким то образом сказать что нужно создать
    val updateState: List<() -> Unit> = listOf(),   // когда нужно обновить состояние элемента
    val description: String = "",                   // строка для лога
)