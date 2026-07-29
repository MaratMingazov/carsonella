package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
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
 * Форс игрока (механика «лего»): [ReactionRequest.selection] ≠ WeightBased заставляет
 * [ChemicalReactionResolver.resolve] выполнить ИМЕННО указанное правило, минуя weight-конкуренцию.
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

    private fun diatomic(element: Element, electrons: Int): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, element), AtomNode(1, element)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        return Molecule(nextId++, Species.Molecular(graph), Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)
            .also { it.setEnvironment(env) }
    }

    @Test
    fun forcedStrengthenBeatsEmergentGrowth() {
        // Углерод: эмёрджентно рост C–H бьёт усиление C=C по weight (см. GrowthVsStrengtheningTest).
        // Но форс игрока StrengthenBond должен победить рост, даже когда рядом есть партнёр для роста.
        val resolver = ChemicalReactionResolver(StubGenerator())
        val cc = diatomic(Element.CARBON_12, electrons = 12)   // C–C, по 3 свободных слота
        val h = atom(Element.HYDROGEN, x = 1f, electrons = 1)  // сосед для роста

        val result = resolver.resolve(listOf(
            ReactionRequest(listOf(cc, h)),                                 // WeightBased: рост победил бы
            ReactionRequest(listOf(cc), ReactionSelection.StrengthenBond),  // форс: усиление
        ))

        assertNotNull(result)
        assertTrue(
            result.description.startsWith("BondStrengthening"),
            "форс усиления должен победить эмёрджентный рост. Выбрано: ${result.description}",
        )
    }

    @Test
    fun forcedSelectionThatCannotApplyDoesNothing() {
        // H₂ насыщена (у H нет свободных слотов) → усиливать нечего. Форс StrengthenBond не находит
        // применимого правила и НЕ подменяется другой реакцией — resolve возвращает null.
        val resolver = ChemicalReactionResolver(StubGenerator())
        val h2 = diatomic(Element.HYDROGEN, electrons = 2)

        val result = resolver.resolve(listOf(
            ReactionRequest(listOf(h2), ReactionSelection.StrengthenBond),
        ))

        assertNull(result)
    }
}