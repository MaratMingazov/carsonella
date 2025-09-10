package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Vec2D
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

    /**
     * Вычисляем направление движения и скорость новой частицы после столкновения двух частиц.
     * Учитываем скорости направления и массу этих частиц
     */
    fun calculateHydrogenDirectionAndVelocity(entity1: Entity<*>, entity2: Entity<*>,) : Pair<Vec2D, Float> {
        val electronMass = entity1.state().value.element.mass
        val protonMass = entity2.state().value.element.mass
        val sumMass = electronMass + protonMass

        val electronVelocityVector = entity1.state().value.direction.times(entity1.state().value.velocity)
        val protonVelocityVector = entity2.state().value.direction.times(entity2.state().value.velocity)
        val impulseVectorTotal = electronVelocityVector.times(electronMass) + protonVelocityVector.times(protonMass)

        val newEntityVelocityVector = impulseVectorTotal.div(sumMass)
        val newEntityVelocity = newEntityVelocityVector.length()
        val newEntityDirection = if (newEntityVelocity > 1e-6f) newEntityVelocityVector.div(newEntityVelocity) else Vec2D(1f, 0f)

        return Pair(newEntityDirection,newEntityVelocity)
    }
}

// Что делать миру после реакции
data class ReactionOutcome(
    val consumed: List<Entity<*>>,       // атомы, которые участвовали в реакции
    val spawn: List<() -> Entity<*>>,    // новые атомы и молекулы, которые появились. Вот тут нужно каким то образом сказать что нужно создать
)