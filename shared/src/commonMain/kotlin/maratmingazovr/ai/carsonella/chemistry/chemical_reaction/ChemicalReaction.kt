package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Element.H
import maratmingazovr.ai.carsonella.chemistry.Element.O
import maratmingazovr.ai.carsonella.chemistry.Element.H2
import maratmingazovr.ai.carsonella.chemistry.Element.O2
import maratmingazovr.ai.carsonella.chemistry.Element.H2O
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.PhotoIonization
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarEmission
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.AtomPlusAtomToMolecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.SpontaneousEmission
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.PhotoDissociation


interface IEntityGenerator {
    fun createEntity(element: Element, position: Position, direction: Vec2D, velocity: Float, energy: Float, environment: IEnvironment): Entity<*>
}



class ChemicalReactionResolver(entityGenerator: IEntityGenerator, ) {

    private val rules = listOf(

        PhotoIonization(entityGenerator), // отрыв электрона от элемента под действием света
        PhotoDissociation(entityGenerator), // деление молекулы на атомы под действием света
        SpontaneousEmission(entityGenerator), // элемент в возбужденном состоянии может излучить фотон

        StarEmission(entityGenerator),

        // Реакции атомов
        AtomPlusAtomToMolecule(entityGenerator, Proton, Electron, H, resultPhotonEnergy = 13.6f),
        AtomPlusAtomToMolecule(entityGenerator, H, H, H2, 4.5f),
        AtomPlusAtomToMolecule(entityGenerator, O, O, O2),
        AtomPlusAtomToMolecule(entityGenerator, O, H2, H2O),

    )

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