package maratmingazovr.ai.carsonella.chemistry

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Шаг 3: валентность вычисляется из ЧИСЛА ЭЛЕКТРОНОВ по октету/дублету (не таблицей).
 * Для нейтрального атома electrons == Z, поэтому период 1–3 / благородные газы / тяжёлые проверяем
 * через [neutralValence]. Отдельно — ионы: у них внешний слой пересчитывается от реального числа электронов.
 */
class ValenceTest {

    // Валентность нейтрального атома: число электронов == Z (details.p). Дефолта у valence() нет — задаём здесь.
    private fun Element.neutralValence(): Int = valence(details.p)

    @Test
    fun period1() {
        assertEquals(1, Element.HYDROGEN.neutralValence())   // 1 e⁻, хочет дублет → 1
        assertEquals(0, Element.HELIUM_4.neutralValence())   // полный дублет → благородный
    }

    @Test
    fun period2() {
        assertEquals(1, Element.LITHIUM_7.neutralValence())
        assertEquals(2, Element.BERYLLIUM_8.neutralValence())
        assertEquals(3, Element.BORON_8.neutralValence())
        assertEquals(4, Element.CARBON_12.neutralValence())
        assertEquals(3, Element.NITROGEN_14.neutralValence())
        assertEquals(2, Element.OXYGEN_16.neutralValence())
        assertEquals(1, Element.FLUORINE_19.neutralValence())
        assertEquals(0, Element.NEON_20.neutralValence())    // октет полон → благородный
    }

    @Test
    fun period3() {
        assertEquals(1, Element.SODIUM_23.neutralValence())
        assertEquals(2, Element.MAGNESIUM_24.neutralValence())
        assertEquals(3, Element.ALUMINUM_27.neutralValence())
        assertEquals(4, Element.SILICON_28.neutralValence())
        assertEquals(3, Element.PHOSPHORUS_31.neutralValence())
        assertEquals(2, Element.SULFUR_32.neutralValence())
        assertEquals(1, Element.CHLORINE_35.neutralValence())
        assertEquals(0, Element.ARGON_36.neutralValence())   // октет полон → благородный
    }

    @Test
    fun heavyElementsAreNotCovalent() {
        // Z > 18: октет не применим → 0 (а не мусор вроде отрицательного значения).
        assertEquals(0, Element.IRON_56.neutralValence())    // Z = 26
        assertEquals(0, Element.COPPER_63.neutralValence())  // Z = 29 (раньше дал бы 8−11 = −3)
    }

    @Test
    fun cationValenceFromElectrons() {
        // Ион: внешний слой пересчитывается от РЕАЛЬНОГО числа электронов (не от Z).
        assertEquals(3, Element.CARBON_12.valence(5))   // C⁺ (6→5 e⁻): 3 связи, как CH₃⁺
        assertEquals(4, Element.NITROGEN_14.valence(6)) // N⁺ (7→6 e⁻): 4 связи, как NH₄⁺
        assertEquals(3, Element.OXYGEN_16.valence(7))   // O⁺ (8→7 e⁻): 3
        assertEquals(0, Element.CARBON_12.valence(0))   // голое ядро — связей нет
        assertEquals(0, Element.SODIUM_23.valence(10))  // Na⁺ (11→10 e⁻): закрытая оболочка → 0
    }

    @Test
    fun highlyChargedCationsDoNotBond() {
        // Потолок по заряду (+1): сильно ободранный катион электроны сдирает, а не делит.
        // Одного числа электронов тут мало: C⁵⁺ и H оба 1s¹, и правило дублета дало бы обоим 1.
        assertEquals(1, Element.HYDROGEN.valence(1))     // нейтральный H — связь есть
        assertEquals(0, Element.CARBON_12.valence(1))    // C⁵⁺ — та же 1s¹, но заряд +5 → связей нет
        assertEquals(0, Element.CARBON_12.valence(2))    // C⁴⁺ (+4)
        assertEquals(0, Element.CARBON_12.valence(3))    // C³⁺ (+3)
        assertEquals(0, Element.CARBON_12.valence(4))    // C²⁺ (+2) — дикатионы не связываем
        assertEquals(0, Element.OXYGEN_16.valence(1))    // O⁷⁺ (+7)
        assertEquals(0, Element.NITROGEN_14.valence(1))  // N⁶⁺ (+6)
    }

    @Test
    fun valenceIsMonotonicAsElectronsAreStripped() {
        // Выбиваем электроны по одному — способность связываться только падает, без всплеска на C⁵⁺.
        val series = (6 downTo 0).map { Element.CARBON_12.valence(it) }
        assertEquals(listOf(4, 3, 0, 0, 0, 0, 0), series)
    }
}