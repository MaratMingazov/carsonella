package maratmingazovr.ai.carsonella.chemistry

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Шаг 3: валентность вычисляется из Z по октету/дублету (не таблицей).
 * Проверяем весь лёгкий диапазон (период 1–3), благородные газы (0) и что тяжёлые → 0.
 */
class ValenceTest {

    @Test
    fun period1() {
        assertEquals(1, Element.HYDROGEN.valence())   // 1 e⁻, хочет дублет → 1
        assertEquals(0, Element.HELIUM_4.valence())   // полный дублет → благородный
    }

    @Test
    fun period2() {
        assertEquals(1, Element.LITHIUM_7.valence())
        assertEquals(2, Element.BERYLLIUM_8.valence())
        assertEquals(3, Element.BORON_8.valence())
        assertEquals(4, Element.CARBON_12.valence())
        assertEquals(3, Element.NITROGEN_14.valence())
        assertEquals(2, Element.OXYGEN_16.valence())
        assertEquals(1, Element.FLUORINE_19.valence())
        assertEquals(0, Element.NEON_20.valence())    // октет полон → благородный
    }

    @Test
    fun period3() {
        assertEquals(1, Element.SODIUM_23.valence())
        assertEquals(2, Element.MAGNESIUM_24.valence())
        assertEquals(3, Element.ALUMINUM_27.valence())
        assertEquals(4, Element.SILICON_28.valence())
        assertEquals(3, Element.PHOSPHORUS_31.valence())
        assertEquals(2, Element.SULFUR_32.valence())
        assertEquals(1, Element.CHLORINE_35.valence())
        assertEquals(0, Element.ARGON_36.valence())   // октет полон → благородный
    }

    @Test
    fun heavyElementsAreNotCovalent() {
        // Z > 18: октет не применим → 0 (а не мусор вроде отрицательного значения).
        assertEquals(0, Element.IRON_56.valence())    // Z = 26
        assertEquals(0, Element.COPPER_63.valence())  // Z = 29 (раньше дал бы 8−11 = −3)
    }
}