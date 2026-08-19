package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.world.PaletteItem
import maratmingazovr.ai.carsonella.world.neutralElectrons
import maratmingazovr.ai.carsonella.world.renderers.BARE_PROTON_TITLE
import maratmingazovr.ai.carsonella.world.renderers.isBareProton

import maratmingazovr.ai.carsonella.chemistry.graph.KnownMoleculeId

enum class LevelId {
    PROTON, ELECTRON, PHOTON,
    HYDROGEN_ATOM, DIHYDROGEN, OXYGEN_ATOM, HYDROXYL, WATER, DIOXYGEN, HYDROGEN_PEROXIDE, PEROXIDE_SPLIT,
}

// Что должно появиться на холсте, чтобы задание считалось успешно пройденным
sealed interface LevelGoal {
    val goalElementTitle: TranslatedText // название элемента, которое нужно получить
    data class CreateAtom(val element: Element, val electrons: Int = neutralElectrons(element)) : LevelGoal {
        override val goalElementTitle get() = if (isBareProton(element, electrons)) BARE_PROTON_TITLE else element.title
    }
    data class CreateMolecule(val knownMoleculeId: KnownMoleculeId) : LevelGoal {
        override val goalElementTitle get() = knownMoleculeId.title
    }
}

data class LevelReward(
    val text: TranslatedText, // Когда игрок успещно проходит уровень, то у него появляется модальное окно  поздравлением. Там будет отображаться этот текст
)

data class Level(
    val id: LevelId,
    val requiredLevels: Set<LevelId> = emptySet(), // задание доступно, когда эти пройдены; пока цепочка линейна
    val granted: Boolean = false, // дано с самого начала: узел на карте есть и закрыт, играть его не нужно
    val taskDescription: TranslatedText, // описание задачи, которое нужно выполнить
    val levelGoal: LevelGoal, // что должно появиться на холсте, чтобы задание закрылось
    val image: List<PaletteItem>, // картинка уровня
    val inventory: Map<PaletteItem, Int>, // Что выдаём в палитру и сколько: порядок сохраняется, он же порядок слотов на экране.
    val reward: LevelReward, // Награда за успешное прохождение уровня
)

// Пройдено — это и то, что игрок закрыл сам, и то, что выдано ([Level.granted]) и до чего дошла очередь.
// Выданное открывается по своим же зависимостям, поэтому досчитываем до неподвижной точки: granted
// стоит и в середине цепочки, и может зависеть от другого granted.
fun effectiveCompleted(completed: Set<LevelId>): Set<LevelId> {
    var done = completed
    while (true) {
        val opened = LEVELS.filter { it.granted && it.id !in done && done.containsAll(it.requiredLevels) }
        if (opened.isEmpty()) return done
        done = done + opened.map { it.id }
    }
}

fun availableLevels(completed: Set<LevelId>): List<Level> {
    val done = effectiveCompleted(completed)
    return LEVELS.filter { it.id !in done && done.containsAll(it.requiredLevels) }
}

// Узел «дано»: играть нечего, поэтому ни задания, ни награды, ни инвентаря у него нет.
private fun granted(id: LevelId, levelGoal: LevelGoal, levelImage: List<PaletteItem>, requiredLevels: Set<LevelId> = emptySet()) = Level(
    id = id,
    requiredLevels = requiredLevels,
    levelGoal = levelGoal,
    image = levelImage,
    granted = true,
    taskDescription = TranslatedText("", ""),
    inventory = emptyMap(),
    reward = LevelReward(text = TranslatedText("", "")),
)


val LEVELS = listOf(
    // Элементарные частицы игрок не собирает — они у него уже есть, поэтому узлы сразу закрыты.
    granted(LevelId.PROTON, LevelGoal.CreateAtom(Element.HYDROGEN, electrons = 0), levelImage = listOf(PaletteItem.Atom(Element.HYDROGEN, electrons = 0))),
    granted(LevelId.ELECTRON, LevelGoal.CreateAtom(Element.ELECTRON), levelImage = listOf(PaletteItem.Atom(Element.ELECTRON))),
    granted(LevelId.PHOTON, LevelGoal.CreateAtom(Element.PHOTON), levelImage = listOf(PaletteItem.Atom(Element.PHOTON))),

    Level(
        LevelId.HYDROGEN_ATOM,
        requiredLevels = setOf(LevelId.PROTON, LevelId.ELECTRON),
        levelGoal = LevelGoal.CreateAtom(Element.HYDROGEN),
        image = listOf(PaletteItem.Atom(Element.HYDROGEN)),
        inventory = mapOf(PaletteItem.Atom(Element.HYDROGEN, electrons = 0) to 1, PaletteItem.Atom(Element.ELECTRON) to 1),
        taskDescription = TranslatedText(
            ru = "Начнём с самого начала: пусть протон поймает электрон и станет атомом водорода \n hint: подведи электрон к протону",
            en = "Let's start at the very beginning: let a proton catch an electron and become a hydrogen atom \n hint: bring the electron up to the proton",
        ),
        reward = LevelReward(
            text =
                TranslatedText(
                    ru = "Электрон сел на протон, а лишнюю энергию отдал светом - тем самым фотоном, который улетел в сторону. Так было и во Вселенной: через 380 тысяч лет после Большого взрыва протоны наконец разобрали себе электроны, туман из заряженных частиц пропал, и свет полетел свободно. Мы этот свет видим до сих пор - это реликтовое излучение.",
                    en = "The electron settled onto the proton and gave up the spare energy as light - the very photon that just flew off. That is how it went in the Universe too: 380 thousand years after the Big Bang the protons finally took up their electrons, the fog of charged particles cleared, and light flew free. We still see that light today - it is the cosmic microwave background.",
                )
            ),
    ),
    Level(LevelId.DIHYDROGEN, requiredLevels = setOf(LevelId.HYDROGEN_ATOM), levelGoal = LevelGoal.CreateMolecule(KnownMoleculeId.DIHYDROGEN), image = listOf(PaletteItem.KnownMolecule(KnownMoleculeId.DIHYDROGEN)), inventory = mapOf(PaletteItem.Atom(Element.HYDROGEN) to 2),
        taskDescription = TranslatedText(
            ru = "Давай перетащим два атома водорода и соберем первую молекулу",
            en = "Let's drag two hydrogen atoms together and build our first molecule",
        ),
        reward = LevelReward(
            text =
                TranslatedText(
                    ru = "Мы получили Водород - самый распространённый элемент Вселенной, 92% всех атомов. Например, наше Солнце состоит на 73% из водорода. Мы с вами состоим из водорода, которому 13.8 миллиарда лет! Он кажется самым простым, но скрывает массу парадоксов!",
                    en = "We have made Hydrogen - the most common element in the Universe, 92% of all atoms. Our Sun, for one, is 73% hydrogen. The hydrogen you and I are made of is 13.8 billion years old! It looks like the simplest thing there is, and yet it hides a pile of paradoxes!",
                )
        ),
    ),
    granted(LevelId.OXYGEN_ATOM, LevelGoal.CreateAtom(Element.OXYGEN_16), levelImage = listOf(PaletteItem.Atom(Element.OXYGEN_16)), requiredLevels = setOf(LevelId.PROTON, LevelId.ELECTRON)),

    Level(LevelId.HYDROXYL, requiredLevels = setOf(LevelId.HYDROGEN_ATOM, LevelId.OXYGEN_ATOM), levelGoal = LevelGoal.CreateMolecule(KnownMoleculeId.HYDROXYL), image = listOf(PaletteItem.KnownMolecule(KnownMoleculeId.HYDROXYL)), inventory = mapOf(PaletteItem.Atom(Element.HYDROGEN) to 1, PaletteItem.Atom(Element.OXYGEN_16) to 1),
        taskDescription = TranslatedText(
            ru = "Теперь попробуем соединить атомы водорода и кислорода",
            en = "Now let's try joining a hydrogen atom and an oxygen atom",
        ),
        reward = LevelReward(
            text =
                TranslatedText(
                    ru = "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\". Мы будем прикреплять ее к другим молекулам и увидим как она полностью меняет их свойства. И у нас уже все готово, чтобы раздобыть ВОДУ! ",
                    en = "Hydroxyl is a tiny molecule of one hydrogen atom and one oxygen atom. On its own it lives less than a second, but it works like a building block. We will be attaching it to other molecules and watching it change their properties completely. And we already have everything we need to get WATER! ",
                )
        ),
    ),
    Level(LevelId.WATER, requiredLevels = setOf(LevelId.HYDROXYL), levelGoal = LevelGoal.CreateMolecule(KnownMoleculeId.WATER), image = listOf(PaletteItem.KnownMolecule(KnownMoleculeId.WATER)), inventory = mapOf(PaletteItem.Atom(Element.HYDROGEN) to 2, PaletteItem.Atom(Element.OXYGEN_16) to 1),
        taskDescription = TranslatedText(
            ru = "Хочу пить! Нужна Вода!",
            en = "I'm thirsty! We need Water!",
        ),
        reward = LevelReward(
            text =
                TranslatedText(
                    ru = "УРА! Мы получили самую известную молекулу на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии. Мы исследуем почему горячая вода замерзает быстрее холодной? Почему лед не тонет? И многое другое!",
                    en = "HOORAY! We have made the most famous molecule in the world and the main substance of life. We ourselves are about 60% water. Thanks to the unusual shape of its molecule it breaks almost every rule of physics and chemistry. Why does hot water freeze faster than cold? Why does ice not sink? We will find that out, and much more!",
                )
        ),
    ),
    Level(LevelId.DIOXYGEN, requiredLevels = setOf(LevelId.HYDROGEN_ATOM, LevelId.OXYGEN_ATOM), levelGoal = LevelGoal.CreateMolecule(KnownMoleculeId.DIOXYGEN), image = listOf(PaletteItem.KnownMolecule(KnownMoleculeId.DIOXYGEN)), inventory = mapOf(PaletteItem.Atom(Element.OXYGEN_16) to 2),
        taskDescription = TranslatedText(
            ru = "А ты знаешь что для дыхания всем нам нужен кислород! Давай соберем его. \n hint: у атомов должна быть двойная связь",
            en = "Did you know that we all need oxygen to breathe! Let's build some. \n hint: the atoms need a double bond",
        ),
        reward = LevelReward(text=KnownMoleculeId.DIOXYGEN.description),
    ),
    Level(
        LevelId.HYDROGEN_PEROXIDE,
        requiredLevels = setOf(LevelId.DIOXYGEN),
        levelGoal = LevelGoal.CreateMolecule(KnownMoleculeId.HYDROGEN_PEROXIDE),
        image = listOf(PaletteItem.KnownMolecule(KnownMoleculeId.HYDROGEN_PEROXIDE)),
        inventory = mapOf(PaletteItem.Atom(Element.HYDROGEN) to 2, PaletteItem.Atom(Element.OXYGEN_16) to 2),
        taskDescription = TranslatedText(
            ru = "Теперь давай попробуем построить перекись водорода",
            en = "Now let's try building hydrogen peroxide",
        ),
        reward = LevelReward(text=KnownMoleculeId.HYDROGEN_PEROXIDE.description),
    ),
    Level(LevelId.PEROXIDE_SPLIT, requiredLevels = setOf(LevelId.HYDROGEN_PEROXIDE), levelGoal = LevelGoal.CreateMolecule(KnownMoleculeId.HYDROXYL), image = listOf(PaletteItem.KnownMolecule(KnownMoleculeId.HYDROGEN_PEROXIDE), PaletteItem.Atom(Element.PHOTON)), inventory = mapOf(PaletteItem.KnownMolecule(KnownMoleculeId.HYDROGEN_PEROXIDE) to 1, PaletteItem.Atom(Element.PHOTON) to 3),
        taskDescription = TranslatedText(
            ru = "А теперь наоборот - разобьём перекись светом на два гидроксила \n hint: фотон нужно положить прямо на атом кислорода",
            en = "Now the other way round - let's break the peroxide apart with light into two hydroxyls \n hint: drop the photon right onto an oxygen atom",
        ),
        reward = LevelReward(
            text =
                TranslatedText(
                    ru = "Свет разорвал самую слабую связь - ту, что держала два кислорода вместе, и из одной молекулы получилось две! Так же светом разбивает молекулы и солнце: в атмосфере из таких осколков собирается всё остальное. Кстати, поэтому перекись и держат в тёмной бутылке.",
                    en = "The light broke the weakest bond - the one holding the two oxygens together - and one molecule became two! The Sun breaks molecules the same way: up in the atmosphere everything else is built out of fragments like these. That, by the way, is why peroxide is kept in a dark bottle.",
                )
        ),
    ),
)