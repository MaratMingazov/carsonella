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
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.SpontaneousEmission
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull


class MoleculeReagentCrashTest {

    private class StubGenerator : IEntityGenerator {
        override val random = Random(0)
        override fun createEntity(
            species: Species, position: Position, direction: Vec2D,
            velocity: Float, energy: Float, environment: IEnvironment, electrons: Int,
        ): Entity = Atom(0L, Element.HYDROGEN, position, direction, velocity, energy, electrons = 1)
    }

    private val env = Environment()   // TemperatureMode.Space
    private var nextId = 1L

    private fun atom(element: Element, x: Float, electrons: Int): Atom =
        Atom(nextId++, element, Position(x, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)
            .also { it.setEnvironment(env) }

    // Граф-молекула H₂ (как её рождает CovalentBondFormation).
    private fun h2Molecule(x: Float): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        return Molecule(nextId++, graph, Position(x, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 2)
            .also { it.setEnvironment(env) }
    }

    @Test
    fun resolverDoesNotThrowWhenMoleculeIsSubject() {
        val resolver = ChemicalReactionResolver(StubGenerator())
        // субъект — молекула, сосед — атом: до фикса .element на reagents.first() бросал
        // resolve теперь принимает списки запросов одного инициатора → оборачиваем один запрос в listOf
        assertNull(resolver.resolve(listOf(listOf(h2Molecule(0f), atom(Element.HYDROGEN, 1f, electrons = 1)))))
    }

    @Test
    fun resolverDoesNotThrowWhenMoleculeIsNeighbor() {
        val resolver = ChemicalReactionResolver(StubGenerator())
        // субъект — атом, сосед — молекула: до фикса перебор соседей по .element бросал
        assertNull(resolver.resolve(listOf(listOf(atom(Element.HYDROGEN, 0f, electrons = 1), h2Molecule(1f)))))
    }

    @Test
    fun atomRuleSkipsMoleculeSubjectInsteadOfThrowing() {
        // SpontaneousEmission — AtomReactionRule: молекула-субъект отсекается фильтром (а не бросает шов).
        val rule = SpontaneousEmission(StubGenerator())
        assertFalse(rule.matches(listOf(h2Molecule(0f))))
    }
}