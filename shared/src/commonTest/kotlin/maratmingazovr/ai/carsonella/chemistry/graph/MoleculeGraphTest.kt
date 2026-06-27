package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}