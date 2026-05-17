package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Element.HYDROGEN
import maratmingazovr.ai.carsonella.chemistry.Element.DEUTERIUM_ION
import maratmingazovr.ai.carsonella.chemistry.Element.DEUTERIUM
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_16
import maratmingazovr.ai.carsonella.chemistry.Element.H2
import maratmingazovr.ai.carsonella.chemistry.Element.O2
import maratmingazovr.ai.carsonella.chemistry.Element.H2O
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.PhotoIonization
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarAlphaReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StarEmission
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StartPPChain
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.RecombinationReaction
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.AtomPlusAtomToMolecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.SpontaneousEmission
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.PhotoDissociation
import kotlin.random.Random


interface IEntityGenerator {
    val random: Random
    fun createEntity(element: Element, position: Position, direction: Vec2D, velocity: Float, energy: Float, environment: IEnvironment? = null): Entity<*>
}



class ChemicalReactionResolver(private val entityGenerator: IEntityGenerator) {

    private val rules = listOf(

        PhotoIonization(entityGenerator), // отрыв электрона от элемента под действием света
        PhotoDissociation(entityGenerator), // деление молекулы на атомы под действием света
        SpontaneousEmission(entityGenerator), // элемент в возбужденном состоянии может излучить фотон

        StarEmission(entityGenerator),
        RecombinationReaction(entityGenerator),
        StarAlphaReaction(entityGenerator), // в недрах звезд элементы могут захватывать альфа частицы (ядра гелия) для образования более тяжелых элементов
        StartPPChain(entityGenerator), // pp-цепочка: p+p→D⁺, D⁺+p→³He²⁺, ³He²⁺+³He²⁺→⁴He²⁺+2p

        // STAR REACTIONS
        AtomPlusAtomToMolecule(entityGenerator, Element.CARBON_12_ION_6, Element.CARBON_12_ION_6, Element.NEON_20_ION_10, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f, resultElement2 = HELIUM_4_ION_2),
        AtomPlusAtomToMolecule(entityGenerator, Element.CARBON_12_ION_6, Element.CARBON_12_ION_6, Element.NA_23_ION_11, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f, resultElement2 = Proton),
        AtomPlusAtomToMolecule(entityGenerator, Element.CARBON_12_ION_6, Element.CARBON_12_ION_6, Element.MG_24_ION_12, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f),
        AtomPlusAtomToMolecule(entityGenerator, Element.OXYGEN_16_ION_8, Element.OXYGEN_16_ION_8, Element.SILICON_28_ION_14, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f, resultElement2 = HELIUM_4_ION_2),
        AtomPlusAtomToMolecule(entityGenerator, Element.OXYGEN_16_ION_8, Element.OXYGEN_16_ION_8, Element.PHOSPHORUS_31_ION_15, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f, resultElement2 = Proton),
        AtomPlusAtomToMolecule(entityGenerator, Element.OXYGEN_16_ION_8, Element.OXYGEN_16_ION_8, Element.SULFUR_31_ION_16, temperatureMode = TemperatureMode.Star, resultPhotonEnergy = 1000f, resultElement2 = Element.Neutron),

        // Реакции атомов
        //AtomPlusAtomToMolecule(entityGenerator, Proton, Electron, HYDROGEN, resultPhotonEnergy = 13.6f),
        AtomPlusAtomToMolecule(entityGenerator, DEUTERIUM_ION, ELECTRON, DEUTERIUM, resultPhotonEnergy = 13.6f),
        AtomPlusAtomToMolecule(entityGenerator, HYDROGEN, HYDROGEN, H2, 4.5f),
        AtomPlusAtomToMolecule(entityGenerator, OXYGEN_16, OXYGEN_16, O2),
        AtomPlusAtomToMolecule(entityGenerator, OXYGEN_16, H2, H2O),

    )

    /**
     * 1 - Прогоняем наши реагенты по всему списку правил химических реакций.
     *     Определяем какие реакции в принципе возможны
     * 2 - Если ни одна реакция невозможна, возвращаем null
     * 3 - Если нашли несколько возможных реакций, то определяем какая из них наиболее вероятна
     * 4 - Выполняем химическую реакцию и возвращаем результат
     */
    fun resolve(reagents: List<Entity<*>>): ReactionOutcome? {
        val applicableRules = rules.filter { it.matches(reagents) }
        if (applicableRules.isEmpty()) return null
        // Сначала вычисляем веса для всех подходящих правил
        val weighted = applicableRules.map { it to it.weight() }
        // Находим максимальный вес
        val maxWeight = weighted.maxOf { it.second }
        // Отбираем все правила с максимальным весом
        val topRules = weighted.filter { it.second == maxWeight }.map { it.first }
        // Выбираем случайное из них
        val chosenRule = topRules.random(entityGenerator.random)
        // val chosenRule = applicableRules.map { it to it.weight() }.maxBy { it.second }.first
        return chosenRule.produce()
    }
}