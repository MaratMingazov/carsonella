package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Каталог энергий связи: ключ по типу (пара элементов + кратность), а не по молекуле; примитив-константа.
 * Проверяем симметрию, схлопывание изотопов, границы каталога и — главное — что числа дают ПРАВИЛЬНЫЙ
 * выбор «расти vs усилить» для C и O одним механизмом (max выигрыш энергии).
 */
class BondEnergyTest {

    @Test
    fun symmetricInElementOrder() {
        assertEquals(
            BondEnergy.of(Element.HYDROGEN, Element.OXYGEN_16, 1),
            BondEnergy.of(Element.OXYGEN_16, Element.HYDROGEN, 1),
        )
    }

    @Test
    fun hydrogenBondMatchesLegacyH2Value() {
        // Старое H2.energyBondDissociation = 4.5 — это энергия связи H–H.
        assertEquals(4.52f, BondEnergy.of(Element.HYDROGEN, Element.HYDROGEN, 1))
    }

    @Test
    fun isotopesShareBondEnergy() {
        // Ключ по элементу (Z), не по изотопу: D–H ≈ H–H.
        assertEquals(
            BondEnergy.of(Element.HYDROGEN, Element.HYDROGEN, 1),
            BondEnergy.of(Element.DEUTERIUM, Element.HYDROGEN, 1),
        )
    }

    @Test
    fun oxygenPrefersDoubleBondOverChaining() {
        // O=O выгоднее двух одинарных: усиление (E(O=O) − E(O–O)) > рост (E(O–O)) → O₂, а не цепь O–O–O.
        val single = BondEnergy.of(Element.OXYGEN_16, Element.OXYGEN_16, 1)!!
        val double = BondEnergy.of(Element.OXYGEN_16, Element.OXYGEN_16, 2)!!
        assertTrue(double - single > single, "усиление O=O должно бить рост цепи")
    }

    @Test
    fun carbonPrefersNewSingleBondOverStrengthening() {
        // Углерод: рост C–H выгоднее усиления C–C→C=C → цепи, а не авто-двойные.
        val cc1 = BondEnergy.of(Element.CARBON_12, Element.CARBON_12, 1)!!
        val cc2 = BondEnergy.of(Element.CARBON_12, Element.CARBON_12, 2)!!
        val ch = BondEnergy.of(Element.CARBON_12, Element.HYDROGEN, 1)!!
        assertTrue(ch > cc2 - cc1, "рост C–H должен бить усиление C=C")
    }

    @Test
    fun nitrogenTripleIsDeepWell() {
        // N≡N — огромная энергия (инертность N₂).
        assertTrue(BondEnergy.of(Element.NITROGEN_14, Element.NITROGEN_14, 3)!! > 9f)
    }

    @Test
    fun heavyElementHasNoBondEnergy() {
        // Железо вне ковалентной модели (Z>18, valence 0) → в каталоге его нет.
        assertNull(BondEnergy.of(Element.IRON_56, Element.IRON_56, 1))
    }

    @Test
    fun uncataloguedOrderIsNull() {
        assertNull(BondEnergy.of(Element.OXYGEN_16, Element.OXYGEN_16, 3))   // тройной O–O не бывает
        assertNotNull(BondEnergy.of(Element.OXYGEN_16, Element.OXYGEN_16, 2)) // а двойной — есть
    }
}