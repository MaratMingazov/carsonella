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
    val task: Prose,
    val goalMoleculeId: MoleculeId, // какой элемент должен получить игрок, чтобы пройти уровен
    val inventory: Map<PaletteItem, Int>, // Что выдаём в палитру и сколько: порядок сохраняется, он же порядок слотов на экране.
    val rewardText: Prose? = null, // Текст награды вместо описания из реестра: уровень бывает про действие, а не про саму молекулу.
) {
    val goal: KnownMolecule
        get() = MoleculeRegistry.byId(goalMoleculeId)
}

private fun atom(element: Element) = PaletteItem.Atom(element)
private fun known(id: MoleculeId) = PaletteItem.Known(id)


val LEVELS = listOf(
    Level(1, goalMoleculeId = MoleculeId.DIHYDROGEN, inventory = mapOf(atom(Element.HYDROGEN) to 2),
        task = Prose(
            ru = "Давай перетащим два атома водорода и соберем первую молекулу",
            en = "Let's drag two hydrogen atoms together and build our first molecule",
        )),
    Level(2, goalMoleculeId = MoleculeId.HYDROXYL, inventory = mapOf(atom(Element.HYDROGEN) to 1, atom(Element.OXYGEN_16) to 1),
        task = Prose(
            ru = "Теперь попробуем соединить атомы водорода и кислорода",
            en = "Now let's try joining a hydrogen atom and an oxygen atom",
        )),
    Level(3, goalMoleculeId = MoleculeId.WATER, inventory = mapOf(atom(Element.HYDROGEN) to 2, atom(Element.OXYGEN_16) to 1),
        task = Prose(
            ru = "Хочу пить! Нужна Вода!",
            en = "I'm thirsty! We need Water!",
        )),
    Level(4, goalMoleculeId = MoleculeId.DIOXYGEN, inventory = mapOf(atom(Element.OXYGEN_16) to 2),
        task = Prose(
            ru = "А ты знаешь что для дыхания всем нам нужен кислород! Давай соберем его. \n hint: у атомов должна быть двойная связь",
            en = "Did you know that we all need oxygen to breathe! Let's build some. \n hint: the atoms need a double bond",
        )),
    Level(5, goalMoleculeId = MoleculeId.HYDROGEN_PEROXIDE, inventory = mapOf(atom(Element.HYDROGEN) to 2, atom(Element.OXYGEN_16) to 2),
        task = Prose(
            ru = "Теперь давай попробуем построить перекись водорода",
            en = "Now let's try building hydrogen peroxide",
        )),
    Level(6, goalMoleculeId = MoleculeId.HYDROXYL, inventory = mapOf(known(MoleculeId.HYDROGEN_PEROXIDE) to 1, atom(Element.PHOTON) to 3),
        task = Prose(
            ru = "А теперь наоборот - разобьём перекись светом на два гидроксила \n hint: фотон нужно положить прямо на атом кислорода",
            en = "Now the other way round - let's break the peroxide apart with light into two hydroxyls \n hint: drop the photon right onto an oxygen atom",
        ),
        rewardText = Prose(
            ru = "Свет разорвал самую слабую связь - ту, что держала два кислорода вместе, и из одной молекулы получилось две! Так же светом разбивает молекулы и солнце: в атмосфере из таких осколков собирается всё остальное. Кстати, поэтому перекись и держат в тёмной бутылке.",
            en = "The light broke the weakest bond - the one holding the two oxygens together - and one molecule became two! The Sun breaks molecules the same way: up in the atmosphere everything else is built out of fragments like these. That, by the way, is why peroxide is kept in a dark bottle.",
        )),
)