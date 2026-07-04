package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Контракт [MoleculeReactionRule]: правило срабатывает только когда субъект (`reagents.first()`) —
 * молекула; субъект-атом отсекается ДО делегирования в matchesMolecule.
 */
class MoleculeReactionRuleTest {

    private val env = Environment()
    private var nextId = 1L

    private fun hAtom() = Atom(nextId++, Element.HYDROGEN, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 1)
        .also { it.setEnvironment(env) }

    private fun h2Molecule(): Molecule {
        val graph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        return Molecule(nextId++, graph, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 2)
            .also { it.setEnvironment(env) }
    }

    private class Dummy : MoleculeReactionRule() {
        override val id = "Dummy"
        var delegated = false
        override fun matchesMolecule(reagents: List<Entity<*>>): Boolean { delegated = true; return true }
        override fun weight() = 0f
        override fun produce() = ReactionOutcome()
    }

    @Test
    fun atomSubjectIsRejectedWithoutDelegating() {
        val rule = Dummy()
        assertFalse(rule.matches(listOf(hAtom())))
        assertFalse(rule.delegated)   // matchesMolecule даже не звался
    }

    @Test
    fun moleculeSubjectDelegatesToMatchesMolecule() {
        val rule = Dummy()
        assertTrue(rule.matches(listOf(h2Molecule(), hAtom())))
        assertTrue(rule.delegated)
    }
}