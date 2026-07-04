package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Тесты ядра графа (Стадия 0): агрегаты (mass/protons/formula) и инварианты конструктора.
 * Каноникализация — отдельным набором, когда добавим canonical().
 */
class MoleculeGraphTest {

    // --- маленькие фабрики молекул для читаемости тестов ---

    // H₂O: O(0) — H(1), O(0) — H(2)
    private fun water() = MoleculeGraph(
        nodes = listOf(
            AtomNode(0, Element.OXYGEN_16),
            AtomNode(1, Element.HYDROGEN),
            AtomNode(2, Element.HYDROGEN),
        ),
        bonds = listOf(
            Bond(0, 1, order = 1),
            Bond(0, 2, order = 1),
        ),
    )

    // CH₄: C(0) связан с четырьмя H(1..4)
    private fun methane() = MoleculeGraph(
        nodes = listOf(
            AtomNode(0, Element.CARBON_12),
            AtomNode(1, Element.HYDROGEN),
            AtomNode(2, Element.HYDROGEN),
            AtomNode(3, Element.HYDROGEN),
            AtomNode(4, Element.HYDROGEN),
        ),
        bonds = listOf(
            Bond(0, 1, order = 1),
            Bond(0, 2, order = 1),
            Bond(0, 3, order = 1),
            Bond(0, 4, order = 1),
        ),
    )

    // Этанол C₂H₆O (CH₃–CH₂–OH): C0–C1, C1–O2, водороды по местам.
    private fun ethanol() = MoleculeGraph(
        nodes = listOf(
            AtomNode(0, Element.CARBON_12),
            AtomNode(1, Element.CARBON_12),
            AtomNode(2, Element.OXYGEN_16),
            AtomNode(3, Element.HYDROGEN),
            AtomNode(4, Element.HYDROGEN),
            AtomNode(5, Element.HYDROGEN),
            AtomNode(6, Element.HYDROGEN),
            AtomNode(7, Element.HYDROGEN),
            AtomNode(8, Element.HYDROGEN),
        ),
        bonds = listOf(
            Bond(0, 1, order = 1),
            Bond(1, 2, order = 1),
            Bond(0, 3, order = 1),
            Bond(0, 4, order = 1),
            Bond(0, 5, order = 1),
            Bond(1, 6, order = 1),
            Bond(1, 7, order = 1),
            Bond(2, 8, order = 1),
        ),
    )

    // --- масса / протоны ---

    @Test
    fun waterMassIs18() = assertEquals(18f, water().mass())   // O16(16) + H(1) + H(1)

    @Test
    fun waterProtonsAre10() = assertEquals(10, water().protons())   // 8 + 1 + 1

    @Test
    fun methaneMassIs16() = assertEquals(16f, methane().mass())   // C12(12) + 4*H(1)

    @Test
    fun methaneProtonsAre10() = assertEquals(10, methane().protons())   // 6 + 4*1

    // --- формула (система Хилла) ---

    @Test
    fun waterFormulaIsH2O() = assertEquals("H2O", water().formula())   // нет углерода → по алфавиту

    @Test
    fun methaneFormulaIsCH4() = assertEquals("CH4", methane().formula())   // C, затем H

    @Test
    fun ethanolFormulaIsC2H6O() = assertEquals("C2H6O", ethanol().formula())   // C, H, затем O

    @Test
    fun deuteriumCountsAsHydrogenInFormula() {
        // «тяжёлая вода» D₂O: дейтерий (²H) — изотоп водорода, в формуле это H.
        val heavyWater = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.OXYGEN_16),
                AtomNode(1, Element.DEUTERIUM),
                AtomNode(2, Element.DEUTERIUM),
            ),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1)),
        )
        assertEquals("H2O", heavyWater.formula())
        assertEquals(20f, heavyWater.mass())   // O16(16) + D(2) + D(2) — масса другая, формула та же
    }

    // --- свободные слоты (3b) ---

    @Test
    fun saturatedMoleculesHaveNoFreeSlots() {
        // Вода/метан/этанол — закрытые оболочки: все слоты закрыты, расти некуда.
        assertEquals(0, water().freeSlots(0))   // O: valence 2 − 2 связи
        assertEquals(0, water().freeSlots(1))   // H: valence 1 − 1
        assertFalse(water().hasFreeSlot())
        assertFalse(methane().hasFreeSlot())
        assertFalse(ethanol().hasFreeSlot())
    }

    @Test
    fun hydroxylRadicalHasOneFreeSlotOnOxygen() {
        // ·OH: O(0)–H(1). У кислорода один свободный слот, у водорода — ноль.
        val hydroxyl = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        assertEquals(1, hydroxyl.freeSlots(0))   // O: 2 − 1
        assertEquals(0, hydroxyl.freeSlots(1))   // H: 1 − 1
        assertTrue(hydroxyl.hasFreeSlot())
    }

    @Test
    fun bondOrderConsumesSlots() {
        // O=O (двойная) насыщает оба кислорода: 2 − 2 = 0. А O–O (одинарная) оставляет по слоту: 2 − 1 = 1.
        val o2 = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.OXYGEN_16)),
            bonds = listOf(Bond(0, 1, order = 2)),
        )
        assertEquals(0, o2.freeSlots(0))
        assertFalse(o2.hasFreeSlot())

        val singleOO = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.OXYGEN_16)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        assertEquals(1, singleOO.freeSlots(0))
        assertEquals(1, singleOO.freeSlots(1))
        assertTrue(singleOO.hasFreeSlot())
    }

    @Test
    fun methylRadicalHasOneFreeSlotOnCarbon() {
        // ·CH₃: C(0) связан с тремя H → 4 − 3 = 1 свободный слот на углероде.
        val methyl = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.CARBON_12),
                AtomNode(1, Element.HYDROGEN),
                AtomNode(2, Element.HYDROGEN),
                AtomNode(3, Element.HYDROGEN),
            ),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1), Bond(0, 3, order = 1)),
        )
        assertEquals(1, methyl.freeSlots(0))
        assertTrue(methyl.hasFreeSlot())
    }

    @Test
    fun freeSlotsOfUnknownNodeFails() {
        assertFailsWith<IllegalStateException> { water().freeSlots(99) }
    }

    @Test
    fun firstFreeSlotNodePicksSmallestLocalIdWithSlot() {
        assertEquals(null, water().firstFreeSlotNode())   // закрытая оболочка — слотов нет
        // H(0)–O(1): водород насыщен, слот на кислороде → берём узел 1, а не 0.
        val oh = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.HYDROGEN), AtomNode(1, Element.OXYGEN_16)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        assertEquals(1, oh.firstFreeSlotNode())
    }

    // --- слияние графов (3b, merge) ---

    // ·OH: O(0) — H(1), свободный слот на O.
    private fun hydroxyl() = MoleculeGraph(
        nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.HYDROGEN)),
        bonds = listOf(Bond(0, 1, order = 1)),
    )

    // Атом как одноузловой граф (так оборачивается партнёр-атом перед merge).
    private fun atomGraph(element: Element) = MoleculeGraph(
        nodes = listOf(AtomNode(0, element)),
        bonds = emptyList(),
    )

    @Test
    fun mergeHydroxylAndHydrogenMakesWater() {
        // ·OH + H· → H₂O: связываем слот O (узел 0) с атомом H.
        val result = hydroxyl().merge(atomGraph(Element.HYDROGEN), thisNode = 0, otherNode = 0, bondOrder = 1)
        assertEquals("H2O", result.formula())
        assertEquals(18f, result.mass())
        assertEquals(water().canonical(), result.canonical())   // та же молекула, что собранная вручную
        assertFalse(result.hasFreeSlot())                        // закрытая оболочка
    }

    @Test
    fun mergeReindexesToAvoidLocalIdCollision() {
        // ·OH + ·OH → H₂O₂ (H–O–O–H): у обоих графов есть узлы 0 и 1 — переиндексация обязана развести их.
        val result = hydroxyl().merge(hydroxyl(), thisNode = 0, otherNode = 0, bondOrder = 1)
        assertEquals(4, result.nodes.size)
        assertEquals(4, result.nodes.map { it.localId }.toSet().size)   // все localId уникальны
        assertEquals("H2O2", result.formula())
        assertEquals(34f, result.mass())                                // 2*O16 + 2*H = 32+2
        assertFalse(result.hasFreeSlot())                               // оба O насыщены (по 2 связи)
    }

    @Test
    fun mergeIsNumberingIndependentForMethanol() {
        // ·CH₃ + ·OH → метанол CH₃OH: слот C ↔ слот O.
        val methyl = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.CARBON_12),
                AtomNode(1, Element.HYDROGEN),
                AtomNode(2, Element.HYDROGEN),
                AtomNode(3, Element.HYDROGEN),
            ),
            bonds = listOf(Bond(0, 1, order = 1), Bond(0, 2, order = 1), Bond(0, 3, order = 1)),
        )
        val result = methyl.merge(hydroxyl(), thisNode = 0, otherNode = 0, bondOrder = 1)

        // Метанол, собранный вручную с ДРУГОЙ нумерацией узлов — канон должен совпасть.
        val methanolRenumbered = MoleculeGraph(
            nodes = listOf(
                AtomNode(0, Element.OXYGEN_16),
                AtomNode(1, Element.CARBON_12),
                AtomNode(2, Element.HYDROGEN),   // на O
                AtomNode(3, Element.HYDROGEN),   // на C
                AtomNode(4, Element.HYDROGEN),
                AtomNode(5, Element.HYDROGEN),
            ),
            bonds = listOf(
                Bond(0, 1, order = 1),   // O–C
                Bond(0, 2, order = 1),   // O–H
                Bond(1, 3, order = 1),   // C–H
                Bond(1, 4, order = 1),
                Bond(1, 5, order = 1),
            ),
        )
        assertEquals("CH4O", result.formula())
        assertEquals(methanolRenumbered.canonical(), result.canonical())
        assertFalse(result.hasFreeSlot())
    }

    @Test
    fun mergeRejectsUnknownNode() {
        assertFailsWith<IllegalArgumentException> {
            hydroxyl().merge(atomGraph(Element.HYDROGEN), thisNode = 99, otherNode = 0, bondOrder = 1)
        }
    }

    // --- инварианты конструктора ---

    @Test
    fun bondToMissingNodeFails() {
        assertFailsWith<IllegalArgumentException> {
            MoleculeGraph(
                nodes = listOf(AtomNode(0, Element.HYDROGEN)),
                bonds = listOf(Bond(0, 5, order = 1)),   // узла 5 нет
            )
        }
    }

    @Test
    fun selfLoopBondFails() {
        assertFailsWith<IllegalArgumentException> {
            MoleculeGraph(
                nodes = listOf(AtomNode(0, Element.HYDROGEN)),
                bonds = listOf(Bond(0, 0, order = 1)),   // петля
            )
        }
    }

    @Test
    fun duplicateLocalIdFails() {
        assertFailsWith<IllegalArgumentException> {
            MoleculeGraph(
                nodes = listOf(AtomNode(0, Element.HYDROGEN), AtomNode(0, Element.HYDROGEN)),
                bonds = emptyList(),
            )
        }
    }

    @Test
    fun bondOrderOutOfRangeFails() {
        assertFailsWith<IllegalArgumentException> {
            MoleculeGraph(
                nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.OXYGEN_16)),
                bonds = listOf(Bond(0, 1, order = 4)),   // нет четверных связей в нашей химии
            )
        }
    }

    // --- каноникализация ---

    // Диметиловый эфир CH₃–O–CH₃: тот же состав C₂H₆O, что у этанола, но связность C–O–C.
    private fun dimethylEther() = MoleculeGraph(
        nodes = listOf(
            AtomNode(0, Element.CARBON_12),
            AtomNode(1, Element.CARBON_12),
            AtomNode(2, Element.OXYGEN_16),
            AtomNode(3, Element.HYDROGEN),
            AtomNode(4, Element.HYDROGEN),
            AtomNode(5, Element.HYDROGEN),
            AtomNode(6, Element.HYDROGEN),
            AtomNode(7, Element.HYDROGEN),
            AtomNode(8, Element.HYDROGEN),
        ),
        bonds = listOf(
            Bond(0, 2, order = 1),   // C–O
            Bond(1, 2, order = 1),   // C–O (кислород центральный)
            Bond(0, 3, order = 1),
            Bond(0, 4, order = 1),
            Bond(0, 5, order = 1),
            Bond(1, 6, order = 1),
            Bond(1, 7, order = 1),
            Bond(1, 8, order = 1),
        ),
    )

    @Test
    fun canonicalIsInvariantToNumbering() {
        // Та же вода, но узлы пронумерованы иначе и перечислены в другом порядке.
        val waterRenumbered = MoleculeGraph(
            nodes = listOf(
                AtomNode(5, Element.HYDROGEN),
                AtomNode(7, Element.OXYGEN_16),
                AtomNode(9, Element.HYDROGEN),
            ),
            bonds = listOf(
                Bond(9, 7, order = 1),
                Bond(5, 7, order = 1),
            ),
        )
        assertEquals(water().canonical(), waterRenumbered.canonical())
    }

    @Test
    fun ethanolAndDimethylEtherShareFormulaButDifferInCanonical() {
        // Одинаковый состав...
        assertEquals(ethanol().formula(), dimethylEther().formula())   // оба C2H6O
        // ...но разная структура → разные канонические ключи (изомеры различаются).
        assertNotEquals(ethanol().canonical(), dimethylEther().canonical())
    }

    @Test
    fun bondOrderAffectsCanonical() {
        // O=O (двойная, молекула кислорода) и O–O (одинарная, перекисная связь) — те же атомы,
        // разные вещества. Канон обязан их различать.
        val o2 = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.OXYGEN_16)),
            bonds = listOf(Bond(0, 1, order = 2)),
        )
        val peroxideBond = MoleculeGraph(
            nodes = listOf(AtomNode(0, Element.OXYGEN_16), AtomNode(1, Element.OXYGEN_16)),
            bonds = listOf(Bond(0, 1, order = 1)),
        )
        assertNotEquals(o2.canonical(), peroxideBond.canonical())
    }

    @Test
    fun canonicalIsStable() {
        // Идемпотентность: повторный вызов даёт ту же строку.
        assertEquals(ethanol().canonical(), ethanol().canonical())
    }

    @Test
    fun canonicalRejectsTooLargeMolecules() {
        // Наивный перебор O(n!) ограничен гардом; 10 изолированных узлов → отказ.
        val tooBig = MoleculeGraph(
            nodes = (0..9).map { AtomNode(it, Element.HYDROGEN) },
            bonds = emptyList(),
        )
        assertFailsWith<IllegalArgumentException> { tooBig.canonical() }
    }
}