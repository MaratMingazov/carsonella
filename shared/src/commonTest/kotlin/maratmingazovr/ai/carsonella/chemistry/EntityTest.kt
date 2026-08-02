package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Агрегаты сущности (символ с зарядом, масса) и работа молекулы со своими связями. */
class EntityTest {

    // H–O–H
    private val water = MoleculeGraph(
        nodes = listOf(
            AtomNode(0, Element.OXYGEN_16),
            AtomNode(1, Element.HYDROGEN),
            AtomNode(2, Element.HYDROGEN),
        ),
        bonds = listOf(Bond(0, 1, 1), Bond(0, 2, 1)),
    )

    private fun molecule(graph: MoleculeGraph, at: Position = Position(100f, 50f)) =
        Molecule(1L, graph, at, Vec2D(0f, 0f), 0f, 0f, electrons = graph.protons)

    private fun waterEntity(electrons: Int) =
        Molecule(1L, water, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons)

    @Test
    fun moleculeSymbolCarriesCharge() {
        assertEquals("H₂O", waterEntity(electrons = 10).displaySymbol) // нейтральная (electrons = protons = 10)
        assertEquals("H₂O⁺", waterEntity(electrons = 9).displaySymbol) // катион +1: заряд из protons − electrons
    }

    @Test
    fun atomSymbolComesFromElement() {
        val h = Atom(1L, Element.HYDROGEN, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 1)
        assertEquals("H", h.displaySymbol)    // нейтральный водород
    }

    @Test
    fun electronMassIsSpecialCased() {
        val e = SubAtom(1L, Element.ELECTRON, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 1)
        assertEquals(1f, e.mass)
        val positron = SubAtom(2L, Element.POSITRON, Position(0f, 0f), Vec2D(0f, 0f), 0f, 0f, electrons = 0)
        assertEquals(1f, positron.mass)   // позитрону спецкейс не нужен — у него p = 1
    }

    @Test
    fun formulaPrettyUsesSubscripts() {
        assertEquals("H₂O", water.formulaPretty)
    }

    @Test
    fun strengthenBondGivesNewMoleculeAndLeavesOldIntact() {
        // O–O: у обоих кислородов свободный слот → связь усиливаема. Молекула отвечает про усиление
        // сама, графа вызывающему не видно: кандидат берётся из strengthenableBonds и им же адресуется.
        val ooGraph = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.OXYGEN_16)),
            bonds = listOf(Bond(0, 1, 1)),
        )
        val oo = molecule(ooGraph)

        val candidate = oo.strengthenableBonds.single()
        assertEquals(1, candidate.order)

        val o2 = molecule(ooGraph.strengthenBond(candidate.atom1.localId, candidate.atom2.localId))
        assertEquals(2, o2.bonds.single().order)   // O–O → O=O
        assertEquals(1, oo.bonds.single().order)   // исходная молекула не изменилась
        assertTrue(o2.strengthenableBonds.isEmpty())   // O=O насыщен — усиливать больше нечего
    }

    @Test
    fun saturatedMoleculeHasNothingToStrengthen() {
        // В H–O–H у каждого водорода валентность занята → ни одна связь не усиливаема.
        assertTrue(molecule(water).strengthenableBonds.isEmpty())
    }

    @Test
    fun moleculeCarriesItsGraph() {
        val m = Molecule(
            id = 1L,
            graph = water,
            position = Position(0f, 0f),
            direction = Vec2D(0f, 0f),
            velocity = 0f,
            energy = 0f,
            electrons = 10,
        )
        assertEquals(water, m.graph)
        assertEquals(18f, m.mass)   // Entity.mass — прямо из графа
    }
}