package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.graph.KnownMoleculeDetails
import maratmingazovr.ai.carsonella.world.PaletteItem
import maratmingazovr.ai.carsonella.world.neutralElectrons

import maratmingazovr.ai.carsonella.chemistry.graph.KnownMoleculeId
/**
 * Ключ уровня. Не порядковый номер: с ветвлением номер перестаёт быть адресом, а вставка уровня в
 * середину сдвинула бы все следующие. Ключ свой, а не цель уровня — цель бывает общая (гидроксил у
 * [HYDROXYL] и [PEROXIDE_SPLIT]).
 */
enum class LevelId {
    HYDROGEN_ATOM, DIHYDROGEN, HYDROXYL, WATER, DIOXYGEN, PEROXIDE, PEROXIDE_SPLIT,
}

/**
 * Что должно появиться на холсте. Молекулой цель не исчерпывается: первое задание — собрать АТОМ из
 * протона и электрона, а атом в реестре молекул не живёт. Заряд в цели значим: протон это тот же
 * `HYDROGEN`, только без электрона, и «получить атом водорода» обязано отличать одно от другого.
 */
sealed interface Goal {
    data class Molecule(val id: KnownMoleculeId) : Goal
    data class Atom(val element: Element, val electrons: Int = neutralElectrons(element)) : Goal
}

/** Запись реестра для молекулярной цели: у неё есть имя, картинка и факт. У атомарной — нет. */
val Goal.known: KnownMoleculeDetails? get() = (this as? Goal.Molecule)?.id?.details

data class Level(
    val id: LevelId,
    val requires: Set<LevelId>, // задание доступно, когда эти пройдены; пока цепочка линейна
    val task: TranslatedText,
    val goal: Goal, // что должно появиться на холсте, чтобы задание закрылось
    val inventory: Map<PaletteItem, Int>, // Что выдаём в палитру и сколько: порядок сохраняется, он же порядок слотов на экране.
    val rewardText: TranslatedText? = null, // Текст награды вместо описания из реестра: уровень бывает про действие, а не про саму молекулу.
)

private fun atom(element: Element) = PaletteItem.Atom(element)
private fun known(id: KnownMoleculeId) = PaletteItem.Known(id)
private val proton = PaletteItem.Atom(Element.HYDROGEN, electrons = 0)
private val electron = PaletteItem.Atom(Element.ELECTRON)

/**
 * Задания, за которые игрок может взяться сейчас: сами не пройдены, а зависимости выполнены. Пока
 * цепочка линейна, доступное всегда одно; с ветвлением их станет два-три, и это тот же список.
 */
fun availableLevels(completed: Set<LevelId>): List<Level> =
    LEVELS.filter { it.id !in completed && completed.containsAll(it.requires) }


val LEVELS = listOf(
    Level(LevelId.HYDROGEN_ATOM, requires = emptySet(), goal = Goal.Atom(Element.HYDROGEN), inventory = mapOf(proton to 3, electron to 3),
        task = TranslatedText(
            ru = "Начнём с самого начала: пусть протон поймает электрон и станет атомом водорода \n hint: подведи электрон к протону",
            en = "Let's start at the very beginning: let a proton catch an electron and become a hydrogen atom \n hint: bring the electron up to the proton",
        ),
        rewardText = TranslatedText(
            ru = "Электрон сел на протон, а лишнюю энергию отдал светом - тем самым фотоном, который улетел в сторону. Так было и во Вселенной: через 380 тысяч лет после Большого взрыва протоны наконец разобрали себе электроны, туман из заряженных частиц пропал, и свет полетел свободно. Мы этот свет видим до сих пор - это реликтовое излучение.",
            en = "The electron settled onto the proton and gave up the spare energy as light - the very photon that just flew off. That is how it went in the Universe too: 380 thousand years after the Big Bang the protons finally took up their electrons, the fog of charged particles cleared, and light flew free. We still see that light today - it is the cosmic microwave background.",
        )),
    Level(LevelId.DIHYDROGEN, requires = setOf(LevelId.HYDROGEN_ATOM), goal = Goal.Molecule(KnownMoleculeId.DIHYDROGEN), inventory = mapOf(atom(Element.HYDROGEN) to 2),
        task = TranslatedText(
            ru = "Давай перетащим два атома водорода и соберем первую молекулу",
            en = "Let's drag two hydrogen atoms together and build our first molecule",
        ),
        rewardText = TranslatedText(
            ru = "Мы получили Водород - самый распространённый элемент Вселенной, 92% всех атомов. Например, наше Солнце состоит на 73% из водорода. Мы с вами состоим из водорода, которому 13.8 миллиарда лет! Он кажется самым простым, но скрывает массу парадоксов!",
            en = "We have made Hydrogen - the most common element in the Universe, 92% of all atoms. Our Sun, for one, is 73% hydrogen. The hydrogen you and I are made of is 13.8 billion years old! It looks like the simplest thing there is, and yet it hides a pile of paradoxes!",
        )),
    Level(LevelId.HYDROXYL, requires = setOf(LevelId.DIHYDROGEN), goal = Goal.Molecule(KnownMoleculeId.HYDROXYL), inventory = mapOf(atom(Element.HYDROGEN) to 1, atom(Element.OXYGEN_16) to 1),
        task = TranslatedText(
            ru = "Теперь попробуем соединить атомы водорода и кислорода",
            en = "Now let's try joining a hydrogen atom and an oxygen atom",
        ),
        rewardText = TranslatedText(
            ru = "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\". Мы будем прикреплять ее к другим молекулам и увидим как она полностью меняет их свойства. И у нас уже все готово, чтобы раздобыть ВОДУ! ",
            en = "Hydroxyl is a tiny molecule of one hydrogen atom and one oxygen atom. On its own it lives less than a second, but it works like a building block. We will be attaching it to other molecules and watching it change their properties completely. And we already have everything we need to get WATER! ",
        )),
    Level(LevelId.WATER, requires = setOf(LevelId.HYDROXYL), goal = Goal.Molecule(KnownMoleculeId.WATER), inventory = mapOf(atom(Element.HYDROGEN) to 2, atom(Element.OXYGEN_16) to 1),
        task = TranslatedText(
            ru = "Хочу пить! Нужна Вода!",
            en = "I'm thirsty! We need Water!",
        ),
        rewardText = TranslatedText(
            ru = "УРА! Мы получили самую известную молекулу на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии. Мы исследуем почему горячая вода замерзает быстрее холодной? Почему лед не тонет? И многое другое!",
            en = "HOORAY! We have made the most famous molecule in the world and the main substance of life. We ourselves are about 60% water. Thanks to the unusual shape of its molecule it breaks almost every rule of physics and chemistry. Why does hot water freeze faster than cold? Why does ice not sink? We will find that out, and much more!",
    )),
    Level(LevelId.DIOXYGEN, requires = setOf(LevelId.WATER), goal = Goal.Molecule(KnownMoleculeId.DIOXYGEN), inventory = mapOf(atom(Element.OXYGEN_16) to 2),
        task = TranslatedText(
            ru = "А ты знаешь что для дыхания всем нам нужен кислород! Давай соберем его. \n hint: у атомов должна быть двойная связь",
            en = "Did you know that we all need oxygen to breathe! Let's build some. \n hint: the atoms need a double bond",
        )),
    Level(LevelId.PEROXIDE, requires = setOf(LevelId.DIOXYGEN), goal = Goal.Molecule(KnownMoleculeId.HYDROGEN_PEROXIDE), inventory = mapOf(atom(Element.HYDROGEN) to 2, atom(Element.OXYGEN_16) to 2),
        task = TranslatedText(
            ru = "Теперь давай попробуем построить перекись водорода",
            en = "Now let's try building hydrogen peroxide",
        )),
    Level(LevelId.PEROXIDE_SPLIT, requires = setOf(LevelId.PEROXIDE), goal = Goal.Molecule(KnownMoleculeId.HYDROXYL), inventory = mapOf(known(KnownMoleculeId.HYDROGEN_PEROXIDE) to 1, atom(Element.PHOTON) to 3),
        task = TranslatedText(
            ru = "А теперь наоборот - разобьём перекись светом на два гидроксила \n hint: фотон нужно положить прямо на атом кислорода",
            en = "Now the other way round - let's break the peroxide apart with light into two hydroxyls \n hint: drop the photon right onto an oxygen atom",
        ),
        rewardText = TranslatedText(
            ru = "Свет разорвал самую слабую связь - ту, что держала два кислорода вместе, и из одной молекулы получилось две! Так же светом разбивает молекулы и солнце: в атмосфере из таких осколков собирается всё остальное. Кстати, поэтому перекись и держат в тёмной бутылке.",
            en = "The light broke the weakest bond - the one holding the two oxygens together - and one molecule became two! The Sun breaks molecules the same way: up in the atmosphere everything else is built out of fragments like these. That, by the way, is why peroxide is kept in a dark bottle.",
        )),
)