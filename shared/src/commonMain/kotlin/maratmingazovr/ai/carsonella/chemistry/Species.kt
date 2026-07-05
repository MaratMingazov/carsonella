package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph

/**
 * Идентичность сущности (§3b, docs/molecule-graph.md): чем «является» частица.
 *
 * - [Elemental] — задаётся изотопом [Element] (атом, субатомная частица, звезда, модуль) — как сейчас.
 * - [Molecular] — задаётся графом [MoleculeGraph] (молекула). Своего [Element] у неё нет: идентичность
 *   и агрегаты (масса/формула/символ) вычисляются из графа.
 *
 * Граф у молекулы non-null by construction: молекула — это [Molecular], атом — [Elemental].
 * На время миграции у [EntityState] остаётся шов `element`, работающий для [Elemental]
 * (весь не-молекулярный код продолжает читать `.element` как раньше).
 */
sealed interface Species {
    data class Elemental(val element: Element) : Species
    data class Molecular(val graph: MoleculeGraph) : Species
}

// --- агрегаты по Species (мост §3b): Elemental читает Element/Details, Molecular — граф ---

// Пока константа (как старый дефолт Details.radius); при желании позже выведем из размера графа.
private const val MOLECULE_RADIUS = 20f

/** Масса: для атома/частицы — p+n (электрон — особый случай 1f); для молекулы — сумма по графу. */
fun Species.mass(): Float = when (this) {
    is Species.Elemental -> if (element == Element.ELECTRON) 1f else (element.details.p + element.details.n).toFloat()
    is Species.Molecular -> graph.mass
}

/** Сумма протонов: из Details (Elemental) или из графа (Molecular). */
fun Species.protons(): Int = when (this) {
    is Species.Elemental -> element.details.p
    is Species.Molecular -> graph.protons
}

/** Радиус для физики/рендера: из Details (Elemental) или константа (Molecular). */
fun Species.radius(): Float = when (this) {
    is Species.Elemental -> element.details.radius
    is Species.Molecular -> MOLECULE_RADIUS
}

/** Символ для показа: атом/частица — символ элемента с зарядом; молекула — формула с подстрочными. */
fun Species.displaySymbol(electrons: Int): String = when (this) {
    is Species.Elemental -> element.symbol(electrons)
    is Species.Molecular -> graph.formulaPretty()
}