package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Element.H
import maratmingazovr.ai.carsonella.chemistry.Element.H_DEUTERIUM_ION
import maratmingazovr.ai.carsonella.chemistry.Element.H_DEUTERIUM
import maratmingazovr.ai.carsonella.chemistry.Element.HE_3_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.HE_4_ION_2
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
    fun createEntity(element: Element, position: Position, direction: Vec2D, velocity: Float, energy: Float, environment: IEnvironment? = null): Entity<*>
}



class ChemicalReactionResolver(entityGenerator: IEntityGenerator, ) {

    private val rules = listOf(

        PhotoIonization(entityGenerator), // отрыв электрона от элемента под действием света
        PhotoDissociation(entityGenerator), // деление молекулы на атомы под действием света
        SpontaneousEmission(entityGenerator), // элемент в возбужденном состоянии может излучить фотон

        StarEmission(entityGenerator),

        // STAR REACTIONS
        AtomPlusAtomToMolecule(entityGenerator, Proton, Proton, H_DEUTERIUM_ION, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f),
        AtomPlusAtomToMolecule(entityGenerator, H_DEUTERIUM_ION, Proton, Element.HE_3_ION_2, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f),
        AtomPlusAtomToMolecule(entityGenerator, HE_3_ION_2, HE_3_ION_2, HE_4_ION_2, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f, resultElement2 = Proton, resultElement3 = Proton),
        AtomPlusAtomToMolecule(entityGenerator, HE_4_ION_2, HE_4_ION_2, Element.BE_8_ION_4, temperatureMode = TemperatureMode.Star),
        AtomPlusAtomToMolecule(entityGenerator, HE_4_ION_2, Element.BE_8_ION_4, Element.C_12_ION_6, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f),
        AtomPlusAtomToMolecule(entityGenerator, Element.C_12_ION_6, HE_4_ION_2, Element.O_16_ION_8, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f),

        // Реакции атомов
        AtomPlusAtomToMolecule(entityGenerator, Proton, Electron, H, resultPhotonEnergy = 13.6f),
        AtomPlusAtomToMolecule(entityGenerator, H_DEUTERIUM_ION, Electron, H_DEUTERIUM, resultPhotonEnergy = 13.6f),
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
            val applicableRules = rules.filter { it.matches(reagents) }
            if (applicableRules.isEmpty()) return null
            // Сначала вычисляем веса для всех подходящих правил
            val weighted = applicableRules.map { it to it.weight() }
            // Находим максимальный вес
            val maxWeight = weighted.maxOf { it.second }
            // Отбираем все правила с максимальным весом
            val topRules = weighted.filter { it.second == maxWeight }.map { it.first }
            // Выбираем случайное из них
            val chosenRule = topRules.random()
            // val chosenRule = applicableRules.map { it to it.weight() }.maxBy { it.second }.first
            return chosenRule.produce()
        }
    }
}