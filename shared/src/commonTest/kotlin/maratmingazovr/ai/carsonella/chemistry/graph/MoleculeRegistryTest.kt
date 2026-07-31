package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals("H–O–H", known?.structuralFormula)
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
    fun recognizesHydrogenPolyoxides() {
        // Цепочки H–O–…–O–H, которые собираются ростом молекулы: перекись (2 O), триоксидан (3), тетраоксидан (4).
        val peroxide = mol(listOf(o(0), o(1), h(2), h(3)), listOf(Bond(0, 1, 1), Bond(0, 2, 1), Bond(1, 3, 1)))
        val trioxidane = mol(listOf(o(0), o(1), o(2), h(3), h(4)), listOf(Bond(0, 1, 1), Bond(1, 2, 1), Bond(0, 3, 1), Bond(2, 4, 1)))
        val tetraoxidane = mol(
            listOf(o(0), o(1), o(2), o(3), h(4), h(5)),
            listOf(Bond(0, 1, 1), Bond(1, 2, 1), Bond(2, 3, 1), Bond(0, 4, 1), Bond(3, 5, 1)),
        )

        assertEquals("Перекись водорода", MoleculeRegistry.lookup(peroxide.canonical)?.nameRu)
        assertEquals("Триоксидан", MoleculeRegistry.lookup(trioxidane.canonical)?.nameRu)
        assertEquals("Тетраоксидан", MoleculeRegistry.lookup(tetraoxidane.canonical)?.nameRu)

        // Описание — то, ради чего реестр вообще нужен игроку: оно должно доезжать до карточки.
        listOf(peroxide, trioxidane, tetraoxidane).forEach {
            assertTrue(MoleculeRegistry.lookup(it.canonical)!!.description.isNotEmpty())
        }
    }

    @Test
    fun recognizesRadical() {
        // ·OH (у кислорода свободный слот) — есть в реестре как радикал.
        val hydroxyl = mol(listOf(o(0), h(1)), listOf(Bond(0, 1, 1)))
        assertEquals("Hydroxyl", MoleculeRegistry.lookup(hydroxyl.canonical)?.nameEn)
    }

    @Test
    fun recognizesDicarbonAtAnyBondOrder() {
        // C–C / C=C / C≡C (голый C₂) — один и тот же дикарбон, отличается лишь порядком связи.
        listOf(1, 2, 3).forEach { order ->
            val c2 = mol(listOf(c(0), c(1)), listOf(Bond(0, 1, order)))
            assertEquals("Dicarbon", MoleculeRegistry.lookup(c2.canonical)?.nameEn, "order=$order")
        }
    }

    @Test
    fun unknownMoleculeIsNull() {
        // Скелет озона O–O–O — осознанно НЕ в реестре (нужны формальные заряды) → аноним.
        val ozoneSkeleton = mol(listOf(o(0), o(1), o(2)), listOf(Bond(0, 1, 1), Bond(1, 2, 1)))
        assertNull(MoleculeRegistry.lookup(ozoneSkeleton.canonical))
    }

    @Test
    fun tooLargeMoleculeIsNull() {
        // Крупная молекула → canonical == "" → lookup("") → null (не бросает).
        // Цепочка из 10 углеродов: узлов больше CANONICAL_MAX_NODES, и граф связен (инвариант init).
        val tooBig = mol((0..9).map { c(it) }, (0..8).map { Bond(it, it + 1, order = 1) })
        assertEquals("", tooBig.canonical)
        assertNull(MoleculeRegistry.lookup(tooBig.canonical))
    }
}