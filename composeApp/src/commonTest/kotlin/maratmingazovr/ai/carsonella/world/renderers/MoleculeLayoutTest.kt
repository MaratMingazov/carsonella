package maratmingazovr.ai.carsonella.world.renderers

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Раскладка графа молекулы в координаты атомов: детерминированная, центрированная по центроиду,
 * центральный атом ближе к центру.
 */
class MoleculeLayoutTest {

    @Test
    fun diatomicIsTwoCenteredPoints() {
        val h2 = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        val pos = MoleculeLayout.layout(h2)
        assertEquals(2, pos.size)
        assertTrue(pos.getValue(0) != pos.getValue(1))                    // два разных места
        assertTrue(abs(pos.getValue(0).x + pos.getValue(1).x) < 0.01f)   // центроид ≈ 0
        assertTrue(abs(pos.getValue(0).y + pos.getValue(1).y) < 0.01f)
    }

    @Test
    fun centralAtomIsNearerCenter() {
        // вода: O(0) связан с H(1), H(2) → O центральный, должен быть ближе к центру, чем H.
        val water = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.OXYGEN_16),
                AtomNode(1, Element.HYDROGEN),
                AtomNode(2, Element.HYDROGEN),
            ),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1)),
        )
        val pos = MoleculeLayout.layout(water)
        assertEquals(3, pos.size)
        assertTrue(pos.getValue(0).getDistance() < pos.getValue(1).getDistance())   // O ближе к центру, чем H
    }
}