package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Реестр известных молекул: lookup по каноническому ключу. Опознаёт курируемое подмножество,
 * инвариантен к перенумерации (канон), различает структуру (кратности), крупные/неизвестные → null.
 */
class MoleculeRegistryTest {

    private fun mol(nodes: List<AtomNode>, bonds: List<Bond>) = MoleculeGraph(nodes, bonds)
    private fun h(id: Int) = AtomNode(id, Element.HYDROGEN)
    private fun o(id: Int) = AtomNode(id, Element.OXYGEN_16)
    private fun c(id: Int) = AtomNode(id, Element.CARBON_12)

    @Test
    fun recognizesWater() {
        val water = mol(listOf(o(0), h(1), h(2)), listOf(Bond(0, 1, 1), Bond(0, 2, 1)))
        val known = MoleculeRegistry.lookup(water.canonical)
        assertEquals("Water", known?.nameEn)
        assertEquals("Вода", known?.nameRu)
    }

    @Test
    fun recognitionIsInvariantToNodeRenumbering() {
        // Та же вода, но узлы перенумерованы (H, H, O вместо O, H, H) — канон тот же → всё ещё Water.
        val renumbered = mol(listOf(h(0), h(1), o(2)), listOf(Bond(2, 0, 1), Bond(2, 1, 1)))
        assertEquals("Water", MoleculeRegistry.lookup(renumbered.canonical)?.nameEn)
    }

    @Test
    fun distinguishesBondOrder() {
        // O=O (двойная) — это Dioxygen; O–O (одинарная, как в перекиси) — другая структура → аноним.
        val o2 = mol(listOf(o(0), o(1)), listOf(Bond(0, 1, 2)))
        val ooSingle = mol(listOf(o(0), o(1)), listOf(Bond(0, 1, 1)))
        assertEquals("Dioxygen", MoleculeRegistry.lookup(o2.canonical)?.nameEn)
        assertNull(MoleculeRegistry.lookup(ooSingle.canonical))
    }

    @Test
    fun recognizesRadical() {
        // ·OH (у кислорода свободный слот) — есть в реестре как радикал.
        val hydroxyl = mol(listOf(o(0), h(1)), listOf(Bond(0, 1, 1)))
        assertEquals("Hydroxyl", MoleculeRegistry.lookup(hydroxyl.canonical)?.nameEn)
    }

    @Test
    fun unknownMoleculeIsNull() {
        // :CH₂ (метилен) — не в реестре → аноним.
        val methylene = mol(listOf(c(0), h(1), h(2)), listOf(Bond(0, 1, 1), Bond(0, 2, 1)))
        assertNull(MoleculeRegistry.lookup(methylene.canonical))
    }

    @Test
    fun tooLargeMoleculeIsNull() {
        // Крупная молекула → canonical == "" → lookup("") → null (не бросает).
        val tooBig = mol((0..9).map { h(it) }, emptyList())
        assertEquals("", tooBig.canonical)
        assertNull(MoleculeRegistry.lookup(tooBig.canonical))
    }
}