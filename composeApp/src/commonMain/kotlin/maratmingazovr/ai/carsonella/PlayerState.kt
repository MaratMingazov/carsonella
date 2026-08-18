package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.graph.KnownMoleculeId

data class PlayerState(
    val settings: Settings = Settings(),
    val progress: Progress = Progress(),
)

data class Settings(
    val lang: Lang = Lang.RU,
)

data class Progress(
    val discoveredElements: Set<Element> = emptySet(),
    val discoveredMolecules: Set<KnownMoleculeId> = emptySet(), // какие молекулы игрок уже открыл
    val completedLevels: Set<LevelId> = emptySet(),    // пройденные уровни
) {
    fun discoverElement(element: Element): Progress = if (element in discoveredElements) this else copy(discoveredElements = discoveredElements + element)
    fun discoverMolecule(id: KnownMoleculeId): Progress = if (id in discoveredMolecules) this else copy(discoveredMolecules = discoveredMolecules + id)
    fun completeLevel(id: LevelId): Progress = if (id in completedLevels) this else copy(completedLevels = completedLevels + id)
}