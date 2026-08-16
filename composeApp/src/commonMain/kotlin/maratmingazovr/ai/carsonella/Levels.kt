package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.graph.KnownMolecule
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry
import maratmingazovr.ai.carsonella.world.PaletteItem

/**
 * Уровень — чистые данные: что построить и как это назвать игроку. Цель адресуется ключом записи
 * реестра (канон вычисляется из графа, руками его не напишешь), имя и структурная формула для
 * карточки берутся оттуда же — дублировать их здесь нельзя, разъедутся.
 *
 * Цель всегда одна: «появилась такая молекула». Уровни на разрыв это тоже покрывает — если в палитре
 * лежит целая молекула и фотоны, а свободных атомов нет, осколок взять больше негде.
 */
data class Level(
    val number: Int,
    val task: String,
    val goalId: String,
    val inventory: Map<PaletteItem, Int>, // Что выдаём в палитру и сколько: порядок сохраняется, он же порядок слотов на экране.
    val rewardText: String? = null, // Текст награды вместо описания из реестра: уровень бывает про действие, а не про саму молекулу.
) {
    val goal: KnownMolecule
        get() = MoleculeRegistry.byId(goalId)
            ?: error("Уровень $number: в реестре нет записи '$goalId'")
}

private fun atom(element: Element) = PaletteItem.Atom(element)
private fun known(id: String) = PaletteItem.Known(id)

// Глава 1 «Связь» из лестницы ROADMAP, часть про сборку. Инвентари — оттуда же: нужное плюс люфт
// на одну ошибку, чтобы из лишнего можно было получить и побочное открытие.
val LEVELS = listOf(
    Level(1, "Давай перетащим два атома водорода и соберем первую молекулу", "Dihydrogen",
        inventory = mapOf(atom(Element.HYDROGEN) to 2)),
    Level(2, "Теперь попробуем соединить атомы водорода и кислорода", "Hydroxyl",
        inventory = mapOf(atom(Element.HYDROGEN) to 1, atom(Element.OXYGEN_16) to 1)),
    Level(3, "Хочу пить! Нужна Вода!", "Water",
        inventory = mapOf(atom(Element.HYDROGEN) to 2, atom(Element.OXYGEN_16) to 1)),
    Level(4, "А ты знаешь что для дыхания всем нам нужен кислород! Давай соберем его. \n hint: у атомов должна быть двойная связь", "Dioxygen",
        inventory = mapOf(atom(Element.OXYGEN_16) to 2)),
    Level(5, "Теперь давай попробуем построить перекись водорода", "Hydrogen peroxide",
        inventory = mapOf(atom(Element.HYDROGEN) to 2, atom(Element.OXYGEN_16) to 2)),
    Level(6, "А теперь наоборот - разобьём перекись светом на два гидроксила \n hint: фотон нужно положить прямо на атом кислорода", "Hydroxyl",
        inventory = mapOf(known("Hydrogen peroxide") to 1, atom(Element.PHOTON) to 3),
        rewardText = "Свет разорвал самую слабую связь - ту, что держала два кислорода вместе, " +
            "и из одной молекулы получилось две! " +
            "Так же светом разбивает молекулы и солнце: в атмосфере из таких осколков собирается всё остальное. " +
            "Кстати, поэтому перекись и держат в тёмной бутылке."),
)