package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Species
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
    fun placedAtomIsCentrePlusOffset() {
        // Мировые координаты собирает молекула (Species.Molecular), граф даёт только смещение.
        val h2 = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        val moleculeAt = Position(100f, 50f)
        val atom = Species.Molecular(h2).atom(localId = 1, center = moleculeAt)

        assertEquals(1, atom.localId)
        assertEquals(Element.HYDROGEN, atom.isotope)
        assertEquals(moleculeAt + h2.atomOffset(1), atom.position)
    }

    @Test
    fun bondsCarryBothEndsPlaced() {
        // Связь отдаётся с готовыми концами — искать их по localId вызывающему не нужно.
        val water = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.OXYGEN_16),
                AtomNode(1, Element.HYDROGEN),
                AtomNode(2, Element.HYDROGEN),
            ),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1)),
        )
        val moleculeAt = Position(100f, 50f)
        val molecule = Species.Molecular(water)
        val bonds = molecule.bonds(moleculeAt)

        assertEquals(2, bonds.size)
        bonds.forEach { bond ->
            assertEquals(0, bond.atom1.localId)                                   // оба раза от кислорода
            assertEquals(Element.HYDROGEN, bond.atom2.isotope)
            assertEquals(1, bond.order)
            // Концы — те же атомы, что отдаёт atom(): один источник координат.
            assertEquals(molecule.atom(bond.atom1.localId, moleculeAt), bond.atom1)
            assertEquals(molecule.atom(bond.atom2.localId, moleculeAt), bond.atom2)
        }
        // Длина связи = расстояние между поставленными концами, а не какая-то константа.
        val length = bonds.first().atom1.position.distanceTo(bonds.first().atom2.position)
        assertTrue(length > 0f, "у связи должна быть ненулевая длина: $length")
    }

    @Test
    fun multipleBondKeepsItsOrder() {
        val oxygen = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.OXYGEN_16)),
            bonds = listOf(Bond(0, 1, order = 2)),   // O=O
        )
        assertEquals(2, Species.Molecular(oxygen).bonds(Position(0f, 0f)).single().order)
    }

    @Test
    fun allAtomsArePlacedAtOnce() {
        val water = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.OXYGEN_16),
                AtomNode(1, Element.HYDROGEN),
                AtomNode(2, Element.HYDROGEN),
            ),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1)),
        )
        val moleculeAt = Position(100f, 50f)
        val atoms = Species.Molecular(water).atoms(moleculeAt)

        assertEquals(listOf(0, 1, 2), atoms.map { it.localId })
        // Тот же ответ, что и поштучно.
        atoms.forEach { assertEquals(moleculeAt + water.atomOffset(it.localId), it.position) }
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