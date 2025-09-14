package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.Electron
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Element.H
import maratmingazovr.ai.carsonella.chemistry.Element.O
import maratmingazovr.ai.carsonella.chemistry.Element.H2
import maratmingazovr.ai.carsonella.chemistry.Element.O2
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.AtomPlusAtomToMolecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.AtomToAtomAndPhoton
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.HplusPhotonToProtonAndElectron
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules.H2plusPhotonToHandH


interface IEntityGenerator {
    fun createEntity(element: Element, position: Position, direction: Vec2D, velocity: Float, energy: Float): Entity<*>
}



class ChemicalReactionResolver(
    private val entityGenerator: IEntityGenerator,
) {

    private val rules = listOf(
        // subAtoms
        AtomPlusAtomToMolecule(entityGenerator, Proton, Electron, H),


        // Фотодиссоциация Фотоэффект PhotodissociationThreshold
        // PhotodissociationThreshold - энергетический порог, после которого может разорваться связь и элемент может распасться на составные элементы
        HplusPhotonToProtonAndElectron(entityGenerator), // Фотоэффект
        H2plusPhotonToHandH(entityGenerator), // Фотодиссоциация молекулы водорода (светом)

        // Излучение фотона
        // excitationEnergy - энергия возбуждения. Если атом накопит такую энергию, то он перейдет в возбужденное состояние, и может выстрелить фотоном, чтобы отдать лишнюю энергию
        AtomToAtomAndPhoton(entityGenerator),

        // Molecules
        AtomPlusAtomToMolecule(entityGenerator, H, H, H2),
        AtomPlusAtomToMolecule(entityGenerator, O, O, O2),

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