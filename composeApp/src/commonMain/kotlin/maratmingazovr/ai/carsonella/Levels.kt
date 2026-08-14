package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.graph.KnownMolecule
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry

/**
 * Уровень — чистые данные: что построить и как это назвать игроку. Цель адресуется английским
 * именем записи реестра (канон вычисляется из графа, руками его не напишешь), имя и структурная
 * формула для карточки берутся оттуда же — дублировать их здесь нельзя, разъедутся.
 *
 * Пока только «собери молекулу». Уровни 6–7 лестницы («разбери перекись», «разбей воду фотоном»)
 * ждут цели другого типа — на исчезновение молекулы, а не на появление.
 */
data class Level(
    val number: Int,
    val task: String,
    val goalNameEn: String,
) {
    val goal: KnownMolecule
        get() = MoleculeRegistry.byName(goalNameEn)
            ?: error("Уровень $number: в реестре нет записи '$goalNameEn'")
}

// Глава 1 «Связь» из лестницы ROADMAP, часть про сборку.
val LEVELS = listOf(
    Level(1, "Давай перетащим два атома водорода и соберем первую молекулу", "Dihydrogen"),
    Level(2, "Теперь попробуем соединить атомы водорода и кислорода", "Hydroxyl"),
    Level(3, "Хочу пить! Нужна Вода!", "Water"),
    Level(4, "А ты знаешь что для дыхания всем нам нужен кислород! Давай соберем его. \n hint: у атомов должна быть двойная связь", "Dioxygen"),
    Level(5, "Теперь давай попробуем построить перекись водорода", "Hydrogen peroxide"),
)