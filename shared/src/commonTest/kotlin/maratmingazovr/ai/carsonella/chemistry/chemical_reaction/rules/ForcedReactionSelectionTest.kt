package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MolecularAtom
import maratmingazovr.ai.carsonella.chemistry.MolecularBond
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ChemicalReactionResolver
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionRequest
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionSelection
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Форс игрока (механика «лего»): [ReactionSelection.Forced] заставляет
 * [ChemicalReactionResolver.resolve] выполнить ИМЕННО его выбор, минуя weight-конкуренцию. Обратная
 * сторона: форс-правила живут в отдельном списке и САМИ не срабатывают никогда.
 */
class ForcedReactionSelectionTest {

    private class StubGenerator : IEntityGenerator {
        override val random = Random(0)
        override fun createEntity(
            species: Species, position: Position, direction: Vec2D,
            velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
        ): Entity = Atom(0L, Element.HYDROGEN, position, direction, velocity, energy = 0f, electrons = 1)
    }

    private val env = Environment()
    private var nextId = 1L

    private fun atom(element: Element, x: Float, electrons: Int): Atom =
        Atom(nextId++, element, Position(x, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)
            .also { it.setEnvironment(env) }

    // Выбор игрока: связь берём из кандидатов молекулы — ровно как это делает UI.
    private fun clickOnFirstStrengthenableBond(mol: Molecule): ReactionSelection.StrengthenBond {
        val state = mol.state().value
        val bonds = (state.species as Species.Molecular).strengthenableBonds(state.centerPosition)
        return ReactionSelection.StrengthenBond(bonds.first())
    }

    private fun diatomic(element: Element, electrons: Int): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, element), AtomNode(1, element)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)
            .also { it.setEnvironment(env) }
    }

    @Test
    fun forcedStrengthenBeatsEmergentGrowth() {
        // Рядом с молекулой есть партнёр для роста, и эмёрджентно сработал бы именно рост.
        // Форс игрока StrengthenBond обязан победить его, не участвуя в weight-конкуренции.
        val resolver = ChemicalReactionResolver(StubGenerator())
        val cc = diatomic(Element.CARBON_12, electrons = 12)   // C–C, по 3 свободных слота
        val h = atom(Element.HYDROGEN, x = 1f, electrons = 1)  // сосед для роста

        val result = resolver.resolve(listOf(
            ReactionRequest(listOf(cc, h)),                                          // WeightBased: рост
            ReactionRequest(listOf(cc), clickOnFirstStrengthenableBond(cc)),         // форс: усиление
        ))

        assertNotNull(result)
        assertTrue(
            result.description.startsWith("BondStrengthening"),
            "форс усиления должен победить эмёрджентный рост. Выбрано: ${result.description}",
        )
    }

    @Test
    fun forcedSelectionThatCannotApplyDoesNothing() {
        // Субъект — атом, а не молекула: усиливать нечего. Форс не находит применимого правила и НЕ
        // подменяется другой реакцией — resolve возвращает null.
        val resolver = ChemicalReactionResolver(StubGenerator())
        val h = atom(Element.HYDROGEN, x = 0f, electrons = 1)
        val end = MolecularAtom(0, Element.HYDROGEN, Position(0f, 0f), freeValence = 1)

        val result = resolver.resolve(listOf(
            ReactionRequest(
                listOf(h),
                ReactionSelection.StrengthenBond(MolecularBond(end, end.copy(localId = 1), order = 1)),
            ),
        ))

        assertNull(result)
    }

    @Test
    fun strengtheningNeverHappensEmergently() {
        // Усиление живёт ТОЛЬКО в forcedRules: без клика O–O рядом с кислородом не сворачивается в O=O,
        // а растёт в цепь. Раньше усиление выигрывало эту конкуренцию по weight (3.65 против 1.51 у роста)
        // — см. удалённый GrowthVsStrengtheningTest. Цена решения: O₂ и N₂ сами собой не собираются.
        val resolver = ChemicalReactionResolver(StubGenerator())
        val oo = diatomic(Element.OXYGEN_16, electrons = 16)
        val o = atom(Element.OXYGEN_16, x = 1f, electrons = 8)

        val result = resolver.resolve(listOf(
            ReactionRequest(listOf(oo, o)),   // рост
            ReactionRequest(listOf(oo)),      // «сам с собой» — усилить было бы выгоднее, но правила тут нет
        ))

        assertNotNull(result)
        assertTrue(
            result.description.startsWith("MoleculeGrowth"),
            "без клика усиления быть не должно, ожидался рост. Выбрано: ${result.description}",
        )
    }
}