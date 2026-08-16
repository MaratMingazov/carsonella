package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.graph.KnownMolecule
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry
import maratmingazovr.ai.carsonella.world.PaletteItem

import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeId
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
    val goalMoleculeId: MoleculeId, // какой элемент должен получить игрок, чтобы пройти уровен
    val inventory: Map<PaletteItem, Int>, // Что выдаём в палитру и сколько: порядок сохраняется, он же порядок слотов на экране.
    val rewardText: String? = null, // Текст награды вместо описания из реестра: уровень бывает про действие, а не про саму молекулу.
) {
    val goal: KnownMolecule
        get() = MoleculeRegistry.byId(goalMoleculeId)
}

private fun atom(element: Element) = PaletteItem.Atom(element)
private fun known(id: MoleculeId) = PaletteItem.Known(id)


val LEVELS = listOf(
    Level(1, task = "Давай перетащим два атома водорода и соберем первую молекулу", MoleculeId.DIHYDROGEN, inventory = mapOf(atom(Element.HYDROGEN) to 2)),
    Level(2, task = "Теперь попробуем соединить атомы водорода и кислорода", MoleculeId.HYDROXYL, inventory = mapOf(atom(Element.HYDROGEN) to 1, atom(Element.OXYGEN_16) to 1)),
    Level(3, task = "Хочу пить! Нужна Вода!", MoleculeId.WATER, inventory = mapOf(atom(Element.HYDROGEN) to 2, atom(Element.OXYGEN_16) to 1)),
    Level(4, task = "А ты знаешь что для дыхания всем нам нужен кислород! Давай соберем его. \n hint: у атомов должна быть двойная связь", MoleculeId.DIOXYGEN, inventory = mapOf(atom(Element.OXYGEN_16) to 2)),
    Level(5, task = "Теперь давай попробуем построить перекись водорода", MoleculeId.HYDROGEN_PEROXIDE, inventory = mapOf(atom(Element.HYDROGEN) to 2, atom(Element.OXYGEN_16) to 2)),
    Level(6, task = "А теперь наоборот - разобьём перекись светом на два гидроксила \n hint: фотон нужно положить прямо на атом кислорода", MoleculeId.HYDROXYL, inventory = mapOf(known(MoleculeId.HYDROGEN_PEROXIDE) to 1, atom(Element.PHOTON) to 3), rewardText = "Свет разорвал самую слабую связь - ту, что держала два кислорода вместе, и из одной молекулы получилось две! Так же светом разбивает молекулы и солнце: в атмосфере из таких осколков собирается всё остальное. Кстати, поэтому перекись и держат в тёмной бутылке."),
)