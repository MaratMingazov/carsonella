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