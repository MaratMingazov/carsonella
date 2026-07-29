package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Раскладка графа молекулы в координаты атомов: детерминированная, центрированная по центроиду,
 * центральный атом ближе к центру.
 */
class MoleculeGeometryTest {

    private val center = Position(0f, 0f)

    @Test
    fun diatomicIsTwoCenteredPoints() {
        val h2 = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        val first = h2.atomOffset(0)
        val second = h2.atomOffset(1)
        assertTrue(first != second)                            // два разных места
        assertTrue(abs(first.x + second.x) < 0.01f)            // центроид ≈ 0
        assertTrue(abs(first.y + second.y) < 0.01f)
    }

    @Test
    fun unknownNodeIsAnError() {
        val h2 = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        assertFailsWith<IllegalStateException> { h2.atomOffset(99) }
    }

    @Test
    fun positionIsCentrePlusOffset() {
        val h2 = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        val moleculeAt = Position(100f, 50f)
        val expected = moleculeAt + h2.atomOffset(1)
        assertEquals(expected, h2.atomPosition(1, moleculeAt))
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
        // O ближе к центру, чем H (сравниваем квадраты расстояний — корень не нужен)
        assertTrue(water.atomOffset(0).distanceSquareTo(center) < water.atomOffset(1).distanceSquareTo(center))
    }
}