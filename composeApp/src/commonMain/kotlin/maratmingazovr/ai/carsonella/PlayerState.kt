package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.chemistry.registry.MoleculeElement

data class PlayerState(
    val settings: Settings = Settings(),
    val progress: Progress = Progress(),
)

data class Settings(
    val lang: Lang = Lang.RU,
    // Отладка: снимает замки на карте, чтобы можно было зайти в любой уровень и проверить его.
    // Прогресс при этом не подделывается — карта по-прежнему показывает, что пройдено на самом деле.
    val devMode: Boolean = false,
)

data class Progress(
    val discoveredElements: Set<AtomElement> = emptySet(),
    val discoveredMolecules: Set<MoleculeElement> = emptySet(), // какие молекулы игрок уже открыл
    val completedLevels: Set<LevelId> = emptySet(),    // пройденные уровни
) {
    fun discoverElement(element: AtomElement): Progress = if (element in discoveredElements) this else copy(discoveredElements = discoveredElements + element)
    fun discoverMolecule(id: MoleculeElement): Progress = if (id in discoveredMolecules) this else copy(discoveredMolecules = discoveredMolecules + id)
    fun completeLevel(id: LevelId): Progress = if (id in completedLevels) this else copy(completedLevels = completedLevels + id)
}