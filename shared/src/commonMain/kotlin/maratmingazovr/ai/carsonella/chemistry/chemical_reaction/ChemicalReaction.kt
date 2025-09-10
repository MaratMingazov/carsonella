package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionRule

class ChemicalReaction {
}




interface ISubAtomGenerator {
    fun createSubAtom(element: Element, position: Position, direction: Vec2D, velocity: Float): Entity<*>
}

interface IAtomGenerator {
    fun createHydrogen(position: Position, direction: Vec2D, velocity: Float): Entity<*>
//    fun createOxygen(position: Position): Entity<*>
}

interface IMoleculeGenerator {
    fun createDiHydrogen(position: Position, direction: Vec2D, velocity: Float,): Entity<*>
}



class ChemicalReactionResolver(
    private val rules: List<ReactionRule>,
) {

    private val _stepMutex = Mutex()

    /**
     * 1 - Прогоняем наши реагенты по всему списку правил химических реакций.
     *     Определяем какие реакции в принципе возможны
     * 2 - Если ни одна реакция невозможна, возвращаем null
     * 3 - Если нашли несколько возможных реакций, то определяем какая из них наиболее вероятна
     * 4 - Выполняем химическую реакцию и возвращаем результат
     */
    suspend fun resolve(reagents: List<Entity<*>>): ReactionOutcome? {

        _stepMutex.withLock {
            val applicable = rules.filter { it.matches(reagents) }
            if (applicable.isEmpty()) return null
            val chosenRule = applicable.map { it to it.weight() }.maxBy { it.second }.first
            return chosenRule.produce()
        }
    }
}