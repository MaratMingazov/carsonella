package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element

/**
 * Геометрия молекулы: на сколько разведены концы связи.
 *
 * Раньше здесь жила ещё и раскладка — детерминированная расстановка узлов графа в координаты, по
 * которой атомы садились при рождении молекулы. Она больше не нужна: атом хранит свою кинематику сам,
 * позиции новорождённая молекула берёт у источников, а до правильной длины связи её доводят пружины
 * (`Molecule.applyInternalForces`). Длина покоя для них берётся отсюда — так у геометрии и у физики
 * остаётся один источник истины.
 *
 * Единицы — мировые, они же пиксельные: перевод `Position.toOffset()` в рендере тождественный.
 */
object MoleculeGeometry {
    private const val BOND_PX = 25f // расстояние между двумя атомами

    /** Длина покоя связи. Наружу — потому что по ней же тянут пружины связей в Molecule. */
    fun bondLengthPx(a: Element, b: Element, order: Int): Float {
        // по идее чем больше кратность связи order, тем короче должно быть, позже сделаем
        return a.details.radius + b.details.radius + BOND_PX
    }

}
