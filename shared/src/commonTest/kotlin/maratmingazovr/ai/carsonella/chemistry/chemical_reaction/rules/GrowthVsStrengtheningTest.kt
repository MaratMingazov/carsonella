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
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Регрессия на ① (docs/molecule-graph.md, §6 «рост vs усиление»): рост ([MoleculeGrowth]) и усиление
 * связи ([BondStrengthening]) приходят разными запросами инициатора, но теперь конкурируют в одном
 * [ChemicalReactionResolver.resolve] и выбираются по энергетическому [ReactionRule.weight].
 *
 * Проверяем эмёрджентное расхождение из одной формулы «вес = энергия реакции»:
 *  - кислород (валентность 2): усиление O=O (выигрыш 3.65 эВ) бьёт рост O–O (1.51) → O₂, а не цепь;
 *  - углерод (валентность 4): рост C–H (4.28) бьёт усиление C=C (2.77) → цепи/углеводороды.
 */
class GrowthVsStrengtheningTest {

    private class StubGenerator : IEntityGenerator {
        override val random = Random(0)
        override fun createEntity(
            species: Species, position: Position, direction: Vec2D,
            velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
        ): Entity<*> = Atom(0L, Element.HYDROGEN, position, direction, velocity, energy, electrons = 1)
    }

    private val env = Environment()   // TemperatureMode.Space
    private var nextId = 1L

    private fun atom(element: Element, x: Float, electrons: Int): Atom =
        Atom(nextId++, element, Position(x, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)
            .also { it.setEnvironment(env) }

    // Двухатомная граф-молекула X–X (order 1), как её рождает CovalentBondFormation.
    private fun diatomic(element: Element, x: Float, electrons: Int): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, element), AtomNode(1, element)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        return Molecule(nextId++, graph, Position(x, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)
            .also { it.setEnvironment(env) }
    }

    // Два запроса инициатора-молекулы за тик: рост (listOf(this)+сосед) и усиление (listOf(this)).
    private fun requestsOf(molecule: Molecule, neighbor: Atom): List<List<Entity<*>>> =
        listOf(listOf(molecule, neighbor), listOf(molecule))

    @Test
    fun oxygenStrengthensToDoubleBondInsteadOfGrowingAChain() {
        val resolver = ChemicalReactionResolver(StubGenerator())
        val o2 = diatomic(Element.OXYGEN_16, 0f, electrons = 16)   // O–O, оба атома с 1 свободным слотом
        val o = atom(Element.OXYGEN_16, 1f, electrons = 8)         // нейтральный сосед-кислород

        val result = resolver.resolve(requestsOf(o2, o))
        assertNotNull(result, "хоть одна реакция должна сработать (рост или усиление)")
        assertTrue(
            result.description.startsWith("BondStrengthening"),
            "кислород должен усилиться до O=O, а не расти в цепь. Выбрано: ${result.description}",
        )
    }

    @Test
    fun carbonGrowsAChainInsteadOfStrengthening() {
        val resolver = ChemicalReactionResolver(StubGenerator())
        val cc = diatomic(Element.CARBON_12, 0f, electrons = 12)   // C–C, по 3 свободных слота
        val h = atom(Element.HYDROGEN, 1f, electrons = 1)          // нейтральный сосед-водород

        val result = resolver.resolve(requestsOf(cc, h))
        assertNotNull(result, "хоть одна реакция должна сработать")
        assertTrue(
            result.description.startsWith("MoleculeGrowth"),
            "углерод должен расти (C–H выгоднее усиления C=C). Выбрано: ${result.description}",
        )
    }
}