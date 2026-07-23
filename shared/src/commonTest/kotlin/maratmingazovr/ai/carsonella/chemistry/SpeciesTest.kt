package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Шаг 2a §3b: агрегаты по [Species] (Elemental → Element/Details, Molecular → граф) и
 * подстрочная формула. Чистые функции — тестируются без сущностей.
 */
class SpeciesTest {

    // H–O–H
    private val water = MoleculeGraph(
        nodes = listOf(
            AtomNode(0, Element.OXYGEN_16),
            AtomNode(1, Element.HYDROGEN),
            AtomNode(2, Element.HYDROGEN),
        ),
        bonds = listOf(Bond(0, 1, 1), Bond(0, 2, 1)),
    )

    @Test
    fun molecularAggregatesComeFromGraph() {
        val m = Species.Molecular(water)
        assertEquals(18f, m.mass)              // 16 + 1 + 1
        assertEquals(10, m.protons)            // 8 + 1 + 1
        assertEquals(20f, m.radius)            // константа для молекулы
        assertEquals("H₂O", m.displaySymbol(10)) // нейтральная (electrons = protons = 10): формула без заряда
        assertEquals("H₂O⁺", m.displaySymbol(9)) // катион +1 (electrons = 9): заряд из protons − electrons
    }

    @Test
    fun elementalAggregatesComeFromElement() {
        val h = Species.Elemental(Element.HYDROGEN)
        assertEquals(1f, h.mass)               // p+n = 1
        assertEquals(1, h.protons)
        assertEquals(20f, h.radius)            // атом — дефолтный радиус Details
        assertEquals("H", h.displaySymbol(1))    // нейтральный водород
    }

    @Test
    fun electronMassIsSpecialCased() {
        assertEquals(1f, Species.Elemental(Element.ELECTRON).mass)
    }

    @Test
    fun formulaPrettyUsesSubscripts() {
        assertEquals("H₂O", water.formulaPretty)
    }

    @Test
    fun moleculeEntityCarriesMolecularSpecies() {
        val m = Molecule(
            id = 1L,
            graph = water,
            position = Position(0f, 0f),
            direction = Vec2D(0f, 0f),
            velocity = 0f,
            energy = 0f,
            electrons = 10,
        )
        assertEquals(Species.Molecular(water), m.state().value.species)
        assertEquals(18f, m.mass())   // Entity.mass() идёт через species → graph
    }
}