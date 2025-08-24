package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.chemistry.Entity

/**
 * Описание одной реакции (набор реагентов → продукты).
 * */
interface ReactionRule {
    val id: String
    /**
     * Метод должен проверить возможна ли конкретная реакция.
     * true - реакция возможна и произойдет, если не найдется более выгодная реакция
     * false - реакция невозможна
     * */
    suspend fun matches(reagents: List<Entity<*>>): Boolean

    /** Этот метод вычисляет насколько вероятна реакция
     * Чем больше вес, тем реакция вероятнее
     * ЕСли у нас будет несколько допустимых реакций, мы выберем реакцию с наибольшим весом
     * */
    suspend fun weight(): Float

    suspend fun produce(): ReactionOutcome
}

// Что делать миру после реакции
data class ReactionOutcome(
    val consumed: List<Entity<*>>,       // атомы, которые участвовали в реакции
    val spawn: List<() -> Entity<*>>,    // новые атомы и молекулы, которые появились. Вот тут нужно каким то образом сказать что нужно создать
)