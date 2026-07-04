package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element

/**
 * Каталог энергий связи — эмпирический ПРИМИТИВ (как p/n/radius в [Element] details): средняя энергия
 * связи по ТИПУ (пара элементов + кратность), в эВ.
 *
 * Ключевая идея (почему таблица маленькая): энергия связи — свойство самой связи (два атома + кратность),
 * а НЕ молекулы. C–H в метане ≈ C–H в глюкозе. Поэтому таблица по типам связи, а не по молекулам:
 * ~20 записей на CHNO, переиспользуются всеми молекулами. Энергия молекулы = сумма энергий её связей
 * (аддитивность) — её не храним, а считаем.
 *
 * Детали:
 *  - Ключ по ХИМИЧЕСКОМУ элементу (Z = [Element] details.p), не по изотопу: D–H ≈ H–H.
 *  - Пара неупорядочена: of(a,b) == of(b,a).
 *  - Значения — средние bond enthalpies (кДж/моль ÷ 96.485 → эВ). Двухатомный частный случай совпадает
 *    со старым `H2.energyBondDissociation = 4.5`: это и есть энергия связи H–H.
 *  - null = тип связи не в каталоге. В частности тяжёлые элементы (Z>18, напр. железо) — они и
 *    ковалентно не связываются ([Element.valence] = 0), поэтому их в каталоге нет.
 *  - Аддитивность — приближение (окружение сдвигает энергию на несколько %); для симулятора достаточно.
 *
 * Один источник правды для энергетики: рост/усиление связи (правило «max выигрыш энергии») и
 * диссоциация (порог = энергия рвущейся связи) берут числа отсюда.
 */
object BondEnergy {

    /** Энергия связи [a]–[b] кратности [order] в эВ, либо null если тип связи не в каталоге. */
    fun of(a: Element, b: Element, order: Int): Float? = table[key(a.details.p, b.details.p, order)]

    private fun key(z1: Int, z2: Int, order: Int) = Triple(minOf(z1, z2), maxOf(z1, z2), order)

    private val table: Map<Triple<Int, Int, Int>, Float> = buildTable()

    private fun buildTable(): Map<Triple<Int, Int, Int>, Float> {
        val m = HashMap<Triple<Int, Int, Int>, Float>()
        val H = 1; val C = 6; val N = 7; val O = 8
        fun put(z1: Int, z2: Int, order: Int, eV: Float) { m[key(z1, z2, order)] = eV }

        // --- одинарные (order 1) ---
        put(H, H, 1, 4.52f)   // H–H  (= старый H2.energyBondDissociation)
        put(C, H, 1, 4.28f)   // C–H
        put(N, H, 1, 4.05f)   // N–H
        put(O, H, 1, 4.80f)   // O–H
        put(C, C, 1, 3.59f)   // C–C
        put(C, N, 1, 3.16f)   // C–N
        put(C, O, 1, 3.71f)   // C–O
        put(N, N, 1, 1.73f)   // N–N
        put(N, O, 1, 2.08f)   // N–O
        put(O, O, 1, 1.51f)   // O–O (перекисная)

        // --- двойные (order 2) — только тяжёлый–тяжёлый ---
        put(C, C, 2, 6.36f)   // C=C
        put(C, N, 2, 6.37f)   // C=N
        put(C, O, 2, 7.72f)   // C=O
        put(N, N, 2, 4.33f)   // N=N
        put(N, O, 2, 6.29f)   // N=O
        put(O, O, 2, 5.16f)   // O=O (кислород O₂)

        // --- тройные (order 3) ---
        put(C, C, 3, 8.70f)   // C≡C
        put(C, N, 3, 9.23f)   // C≡N
        put(N, N, 3, 9.79f)   // N≡N (глубокая яма → инертность N₂)
        put(C, O, 3, 11.11f)  // C≡O (угарный газ)

        return m
    }
}