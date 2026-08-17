package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeId

data class PlayerState(
    val settings: Settings = Settings(),
    val progress: Progress = Progress(),
)

data class Settings(
    val lang: Lang = Lang.RU,
)

data class Progress(
    val discovered: Set<MoleculeId> = emptySet(),   // журнал открытий: что игрок получал хоть раз
) {
    fun discover(id: MoleculeId): Progress =
        if (id in discovered) this else copy(discovered = discovered + id)
}