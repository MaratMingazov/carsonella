package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.world.InitialEntity
import maratmingazovr.ai.carsonella.world.PaletteItem
import maratmingazovr.ai.carsonella.world.WorldArea
import maratmingazovr.ai.carsonella.world.neutralElectrons
import maratmingazovr.ai.carsonella.world.renderers.BARE_PROTON_DESCRIPTION
import maratmingazovr.ai.carsonella.world.renderers.BARE_PROTON_TITLE

import maratmingazovr.ai.carsonella.chemistry.registry.MoleculeElement

enum class LevelId {

    // SUB ATOMS
    PROTON, ELECTRON, PHOTON, NEUTRON,

    // 1-ATOMS
    HYDROGEN_ATOM, RECOMBINATION, CARBON_ATOM, NITROGEN_ATOM, OXYGEN_ATOM,

    // 2-ATOMS
    DIHYDROGEN, HYDROXYL, DIOXYGEN,

    // 3-ATOMS
    WATER, CARBON_DIOXIDE,

    // 4-ATOMS
    HYDROGEN_PEROXIDE, HYDROGEN_PEROXIDE_SPLIT, ACETYLENE, FORMALDEHYDE,

    // 5-ATOMS
    METHANE, TRIOXIDANE,

    // 6-ATOMS
    TETRAOXIDANE, ETHYLENE, METHANOL,

    // 7-ATOMS

    // 8-ATOMS
    ETHANE,

}

// Что должно появиться на холсте, чтобы задание считалось успешно пройденным
sealed interface LevelGoal {
    // count — сколько таких атомов должно быть живо ОДНОВРЕМЕННО (тип задания «урожай»).
    data class CreateAtom(val element: AtomElement, val electrons: Int = neutralElectrons(element), val count: Int = 1) : LevelGoal
    data class CreateMolecule(val knownMoleculeId: MoleculeElement) : LevelGoal
}

data class LevelReward(
    val text: TranslatedText, // Когда игрок успещно проходит уровень, то у него появляется модальное окно  поздравлением. Там будет отображаться этот текст
)

data class Level(
    val id: LevelId,
    val title: TranslatedText, // название уровня
    val requiredLevels: Set<LevelId> = emptySet(), // задание доступно, когда эти пройдены; пока цепочка линейна
    val granted: Boolean = false, // дано с самого начала: узел на карте есть и закрыт, играть его не нужно
    val description: TranslatedText, // описание задачи, которое нужно выполнить
    val levelGoal: LevelGoal, // что должно появиться на холсте, чтобы задание закрылось
    val image: List<PaletteItem>, // картинка уровня
    val inventory: Map<PaletteItem, Int> = emptyMap(), // Что выдаём в палитру и сколько: порядок сохраняется, он же порядок слотов на экране.
    val worldArea: WorldArea = WorldArea.FitCanvas, // размер мира: по умолчанию весь холст, для опытов — круг заданного радиуса
    val initialEntities: List<InitialEntity> = emptyList(), // что уже стоит на сцене к началу уровня; смещение от центра мира в пикселях
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

// Сетка одинаковых частиц: columns × rows с шагом step, центр — на centerX по горизонтали и на оси мира по вертикали.
// Шаг меньше радиуса действия сил, иначе облако не расталкивается и стоит решёткой.
private fun cluster(item: PaletteItem, centerX: Float, columns: Int, rows: Int, step: Float): List<InitialEntity> =
    (0 until columns).flatMap { column ->
        (0 until rows).map { row ->
            InitialEntity(
                item,
                Vec2D(
                    x = centerX + (column - (columns - 1) / 2f) * step,
                    y = (row - (rows - 1) / 2f) * step,
                ),
            )
        }
    }




val LEVELS = listOf(
    // Элементарные частицы игрок не собирает — они у него уже есть, поэтому узлы сразу закрыты.

    Level(
        LevelId.PROTON,
        title = BARE_PROTON_TITLE,
        granted = true,
        levelGoal = LevelGoal.CreateAtom(AtomElement.HYDROGEN, electrons = 0),
        image = listOf(PaletteItem.Atom(AtomElement.HYDROGEN, electrons = 0)),
        description = TranslatedText(ru = "", en = ""),
        reward = LevelReward(text = BARE_PROTON_DESCRIPTION),
    ),

    Level(
        LevelId.ELECTRON,
        title = AtomElement.ELECTRON.title,
        granted = true,
        levelGoal = LevelGoal.CreateAtom(AtomElement.ELECTRON),
        image = listOf(PaletteItem.Atom(AtomElement.ELECTRON)),
        description = TranslatedText(ru = "", en = ""),
        reward = LevelReward(text = AtomElement.ELECTRON.description),
    ),

    Level(
        LevelId.PHOTON,
        title =  AtomElement.PHOTON.title,
        granted = true,
        levelGoal = LevelGoal.CreateAtom(AtomElement.PHOTON),
        image = listOf(PaletteItem.Atom(AtomElement.PHOTON)),
        description = TranslatedText(ru = "", en = ""),
        reward = LevelReward(text = AtomElement.PHOTON.description),
    ),

    Level(
        LevelId.NEUTRON,
        title =  AtomElement.NEUTRON.title,
        granted = true,
        levelGoal = LevelGoal.CreateAtom(AtomElement.NEUTRON),
        image = listOf(PaletteItem.Atom(AtomElement.NEUTRON)),
        description = TranslatedText(ru = "", en = ""),
        reward = LevelReward(text = AtomElement.NEUTRON.description),
    ),

    Level(
        LevelId.HYDROGEN_ATOM,
        title = AtomElement.HYDROGEN.title,
        requiredLevels = setOf(LevelId.PROTON, LevelId.ELECTRON),
        levelGoal = LevelGoal.CreateAtom(AtomElement.HYDROGEN),
        image = listOf(PaletteItem.Atom(AtomElement.HYDROGEN)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.HYDROGEN, electrons = 0) to 1, PaletteItem.Atom(AtomElement.ELECTRON) to 1),
        description = TranslatedText(
            ru = "Начнём с самого начала: пусть протон поймает электрон и станет атомом водорода \n hint: подведи электрон к протону",
            en = "Let's start at the very beginning: let a proton catch an electron and become a hydrogen atom \n hint: bring the electron up to the proton",
        ),
        worldArea = WorldArea.Circle(radiusPx = 300f),
        reward = LevelReward(
            text =
                TranslatedText(
                    ru = "Видишь фотон, который улетел в сторону? Электрон притянулся к протону, разогнался — и лишнее выбросил светом. Почти весь свет вокруг рождается так же: и в лампе, и на Солнце светят электроны, которые нашли своё место и отдали лишнюю энергию фотоном",
                    en = "See the photon that flew off? The electron was pulled to the proton, picked up speed - and threw the spare out as light. Almost all the light around you is made the same way: in a lamp and in the Sun it is electrons finding their place and giving up the spare energy as a photon",
                )
            ),
    ),
    // Экспериментальный раунд: та же рекомбинация, что и в HYDROGEN_ATOM, но масштабом сцены.
    // Протоны и электроны стоят двумя облаками с шагом меньше радиуса сил — облака сами расталкиваются.
    Level(
        LevelId.RECOMBINATION,
        title = TranslatedText(
            ru = "Эпоха рекомбинации",
            en = "The recombination era",
        ),
        requiredLevels = setOf(LevelId.HYDROGEN_ATOM),
        levelGoal = LevelGoal.CreateAtom(AtomElement.HYDROGEN, count = 10),
        image = listOf(PaletteItem.Atom(AtomElement.HYDROGEN, electrons = 0), PaletteItem.Atom(AtomElement.ELECTRON)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.HYDROGEN, electrons = 0) to 10, PaletteItem.Atom(AtomElement.ELECTRON) to 10),
        worldArea = WorldArea.Circle(radiusPx = 300f),
        initialEntities =
            cluster(PaletteItem.Atom(AtomElement.HYDROGEN, electrons = 0), centerX = -170f, columns = 3, rows = 3, step = 55f) +
            cluster(PaletteItem.Atom(AtomElement.ELECTRON), centerX = 170f, columns = 3, rows = 3, step = 50f),
        description = TranslatedText(
            ru = "Вот так выглядела Вселенная: состояла из протонов и электронов, и больше ничего. Собери из неё 10 атомов водорода \n hint: одинаковые заряды расталкиваются — толпу придётся перемешать",
            en = "This is what the Universe looked like: protons and electrons, and nothing else. Make 10 hydrogen atoms out of it \n hint: like charges push each other apart - you will have to stir the crowd",
        ),
        reward = LevelReward(
            text =
                TranslatedText(
                    ru = "Вселенной на это понадобилось 380 тысяч лет: было слишком горячо — только протон поймает электрон, как налетевший фотон выбивает его обратно. А пока электроны летали свободно, свет не мог пробиться: Вселенная была как густой туман — светло, а разглядеть ничего нельзя. Потом она остыла, атомы собрались, и туман пропал разом. Свет полетел во все стороны и летит до сих пор: телескопы ловят его со всего неба и зовут реликтовым излучением.",
                    en = "It took the Universe 380 thousand years: it was simply too hot - the moment a proton caught an electron, a passing photon knocked it straight back out. And while the electrons flew loose, light could not get through: the Universe was like thick fog - bright, but you could not see a thing. Then it cooled, the atoms came together, and the fog cleared all at once. The light flew off in every direction and is still flying: telescopes pick it up from all over the sky and call it the cosmic microwave background.",
                )
        ),
    ),

    Level(
        LevelId.DIHYDROGEN,
        title = MoleculeElement.DIHYDROGEN.title,
        requiredLevels = setOf(LevelId.HYDROGEN_ATOM),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.DIHYDROGEN),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.DIHYDROGEN)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.HYDROGEN) to 2),
        description = TranslatedText(
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

    Level(
        LevelId.OXYGEN_ATOM,
        title =  AtomElement.OXYGEN_16.title,
        granted = true,
        requiredLevels = setOf(LevelId.DIHYDROGEN), // чтобы на карте нарисовать после молекулы водорода
        levelGoal = LevelGoal.CreateAtom(AtomElement.OXYGEN_16),
        image = listOf(PaletteItem.Atom(AtomElement.OXYGEN_16)),
        description = TranslatedText(ru = "", en = ""),
        reward = LevelReward(text = AtomElement.OXYGEN_16.description),
    ),

    Level(
        LevelId.NITROGEN_ATOM,
        title =  AtomElement.NITROGEN_14.title,
        granted = true,
        requiredLevels = setOf(LevelId.OXYGEN_ATOM), // чтобы на карте нарисовать после молекулы водорода
        levelGoal = LevelGoal.CreateAtom(AtomElement.NITROGEN_14),
        image = listOf(PaletteItem.Atom(AtomElement.NITROGEN_14)),
        description = TranslatedText(ru = "", en = ""),
        reward = LevelReward(text = AtomElement.NITROGEN_14.description),
    ),

    Level(
        LevelId.HYDROXYL,
        title = MoleculeElement.HYDROXYL.title,
        requiredLevels = setOf(LevelId.HYDROGEN_ATOM, LevelId.OXYGEN_ATOM),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.HYDROXYL),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.HYDROXYL)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.HYDROGEN) to 1, PaletteItem.Atom(AtomElement.OXYGEN_16) to 1),
        description = TranslatedText(
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
    Level(
        LevelId.WATER,
        title = MoleculeElement.WATER.title,
        requiredLevels = setOf(LevelId.HYDROXYL),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.WATER),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.WATER)), inventory = mapOf(PaletteItem.Atom(AtomElement.HYDROGEN) to 2, PaletteItem.Atom(AtomElement.OXYGEN_16) to 1),
        description = TranslatedText(
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
    Level(
        LevelId.DIOXYGEN,
        title = MoleculeElement.DIOXYGEN.title,
        requiredLevels = setOf(LevelId.HYDROGEN_ATOM, LevelId.OXYGEN_ATOM),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.DIOXYGEN), image = listOf(PaletteItem.KnownMolecule(MoleculeElement.DIOXYGEN)), inventory = mapOf(PaletteItem.Atom(AtomElement.OXYGEN_16) to 2),
        description = TranslatedText(
            ru = "А ты знаешь что для дыхания всем нам нужен кислород! Давай соберем его. \n hint: у атомов должна быть двойная связь",
            en = "Did you know that we all need oxygen to breathe! Let's build some. \n hint: the atoms need a double bond",
        ),
        reward = LevelReward(text=MoleculeElement.DIOXYGEN.description),
    ),
    Level(
        LevelId.HYDROGEN_PEROXIDE,
        title = MoleculeElement.HYDROGEN_PEROXIDE.title,
        requiredLevels = setOf(LevelId.DIOXYGEN),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.HYDROGEN_PEROXIDE),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.HYDROGEN_PEROXIDE)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.HYDROGEN) to 2, PaletteItem.Atom(AtomElement.OXYGEN_16) to 2),
        description = TranslatedText(
            ru = "Теперь давай попробуем построить перекись водорода",
            en = "Now let's try building hydrogen peroxide",
        ),
        reward = LevelReward(text=MoleculeElement.HYDROGEN_PEROXIDE.description),
    ),
    Level(
        LevelId.HYDROGEN_PEROXIDE_SPLIT,
        title = TranslatedText(
            ru = "Разрыв перекиси светом",
            en = "Peroxide split by light",
        ),
        requiredLevels = setOf(LevelId.HYDROGEN_PEROXIDE),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.HYDROXYL), image = listOf(PaletteItem.KnownMolecule(MoleculeElement.HYDROGEN_PEROXIDE), PaletteItem.Atom(AtomElement.PHOTON)), inventory = mapOf(PaletteItem.KnownMolecule(MoleculeElement.HYDROGEN_PEROXIDE) to 1, PaletteItem.Atom(AtomElement.PHOTON) to 3),
        description = TranslatedText(
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
    Level(
        LevelId.TRIOXIDANE,
        title = MoleculeElement.TRIOXIDANE.title,
        requiredLevels = setOf(LevelId.HYDROGEN_PEROXIDE),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.TRIOXIDANE),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.TRIOXIDANE)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.HYDROGEN) to 2, PaletteItem.Atom(AtomElement.OXYGEN_16) to 3),
        description = TranslatedText(
            ru = "В перекиси два кислорода подряд. А если поставить третий? \n hint: нужна цепочка H–O–O–O–H",
            en = "Peroxide has two oxygens in a row. What if we put in a third? \n hint: you need the chain H–O–O–O–H",
        ),
        reward = LevelReward(text = MoleculeElement.TRIOXIDANE.description),
    ),
    Level(
        LevelId.TETRAOXIDANE,
        title = MoleculeElement.TETRAOXIDANE.title,
        requiredLevels = setOf(LevelId.TRIOXIDANE),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.TETRAOXIDANE),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.TETRAOXIDANE)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.HYDROGEN) to 2, PaletteItem.Atom(AtomElement.OXYGEN_16) to 4),
        description = TranslatedText(
            ru = "А ещё один кислород влезет? \n hint: нужна цепочка H–O–O–O–O–H",
            en = "Will one more oxygen fit? \n hint: you need the chain H–O–O–O–O–H",
        ),
        reward = LevelReward(text = MoleculeElement.TETRAOXIDANE.description),
    ),
    Level(
        LevelId.CARBON_ATOM,
        title =  AtomElement.CARBON_12.title,
        granted = true,
        requiredLevels = setOf(LevelId.TETRAOXIDANE),
        levelGoal = LevelGoal.CreateAtom(AtomElement.CARBON_12),
        image = listOf(PaletteItem.Atom(AtomElement.CARBON_12)),
        description = TranslatedText(ru = "", en = ""),
        reward = LevelReward(text = AtomElement.CARBON_12.description),
    ),
    Level(
        LevelId.METHANE,
        title = MoleculeElement.METHANE.title,
        requiredLevels = setOf(LevelId.HYDROGEN_ATOM, LevelId.CARBON_ATOM),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.METHANE),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.METHANE)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.CARBON_12) to 1, PaletteItem.Atom(AtomElement.HYDROGEN) to 4),
        description = TranslatedText(
            ru = "У углерода целых четыре свободные связи. Давай посадим на них четыре атома водорода",
            en = "Carbon has four free bonds all at once. Let's put four hydrogen atoms on them",
        ),
        reward = LevelReward(text = MoleculeElement.METHANE.description),
    ),
    Level(
        LevelId.ETHANE,
        title = MoleculeElement.ETHANE.title,
        requiredLevels = setOf(LevelId.METHANE),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.ETHANE),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.ETHANE)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.CARBON_12) to 2, PaletteItem.Atom(AtomElement.HYDROGEN) to 6),
        description = TranslatedText(
            ru = "Возьми два атома углерода. Обвесь оба водородами, оставив у каждого по одной свободной связи, а потом соедини эти два конца",
            en = "Take two carbon atoms. Hang hydrogens on both, leaving one free bond on each, then join those two ends",
        ),
        reward = LevelReward(text = MoleculeElement.ETHANE.description),
    ),
    Level(
        LevelId.ETHYLENE,
        title = MoleculeElement.ETHYLENE.title,
        requiredLevels = setOf(LevelId.ETHANE),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.ETHYLENE),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.ETHYLENE)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.CARBON_12) to 2, PaletteItem.Atom(AtomElement.HYDROGEN) to 4),
        description = TranslatedText(
            ru = "На этот раз оставь на каждом углероде по две свободные связи. Соедини их между собой, а потом усиль эту связь \n hint: клик по связи повышает её кратность",
            en = "This time leave two free bonds on each carbon. Join them together, then strengthen that bond \n hint: click a bond to raise its order",
        ),
        reward = LevelReward(text = MoleculeElement.ETHYLENE.description),
    ),
    Level(
        LevelId.ACETYLENE,
        title = MoleculeElement.ACETYLENE.title,
        requiredLevels = setOf(LevelId.ETHYLENE),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.ACETYLENE),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.ACETYLENE)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.CARBON_12) to 2, PaletteItem.Atom(AtomElement.HYDROGEN) to 2),
        description = TranslatedText(
            ru = "Оставь на каждом углероде уже по три свободные связи. Соединив их, ты сможешь усилить связь дважды \n hint: усиливай связь, пока получается",
            en = "Leave three free bonds on each carbon this time. Once you join them, you can strengthen the bond twice \n hint: keep strengthening the bond while it lets you",
        ),
        reward = LevelReward(text = MoleculeElement.ACETYLENE.description),
    ),
    Level(
        LevelId.METHANOL,
        title = MoleculeElement.METHANOL.title,
        requiredLevels = setOf(LevelId.ACETYLENE),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.METHANOL),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.METHANOL)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.CARBON_12) to 1, PaletteItem.Atom(AtomElement.OXYGEN_16) to 1, PaletteItem.Atom(AtomElement.HYDROGEN) to 4),
        description = TranslatedText(
            ru = "Собери отдельно метил (три водорода на углероде) и гидроксил (один водород на кислороде), а потом соедини их свободные концы",
            en = "Build a methyl (three hydrogens on carbon) and a hydroxyl (one hydrogen on oxygen) separately, then join their free ends",
        ),
        reward = LevelReward(text = MoleculeElement.METHANOL.description),
    ),
    Level(
        LevelId.FORMALDEHYDE,
        title = MoleculeElement.FORMALDEHYDE.title,
        requiredLevels = setOf(LevelId.METHANOL),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.FORMALDEHYDE),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.FORMALDEHYDE)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.CARBON_12) to 1, PaletteItem.Atom(AtomElement.OXYGEN_16) to 1, PaletteItem.Atom(AtomElement.HYDROGEN) to 2),
        description = TranslatedText(
            ru = "Оставь на углероде две свободные связи и присоедини к нему кислород, а потом усиль эту связь",
            en = "Leave two free bonds on carbon and attach oxygen to it, then strengthen that bond",
        ),
        reward = LevelReward(text = MoleculeElement.FORMALDEHYDE.description),
    ),
    Level(
        LevelId.CARBON_DIOXIDE,
        title = MoleculeElement.CARBON_DIOXIDE.title,
        requiredLevels = setOf(LevelId.FORMALDEHYDE),
        levelGoal = LevelGoal.CreateMolecule(MoleculeElement.CARBON_DIOXIDE),
        image = listOf(PaletteItem.KnownMolecule(MoleculeElement.CARBON_DIOXIDE)),
        inventory = mapOf(PaletteItem.Atom(AtomElement.CARBON_12) to 1, PaletteItem.Atom(AtomElement.OXYGEN_16) to 2),
        description = TranslatedText(
            ru = "Присоедини к углероду один кислород и усиль связь до двойной. У углерода ещё останется два свободных места — сделай то же самое со вторым кислородом",
            en = "Attach one oxygen to carbon and strengthen the bond to a double one. Carbon still has two free bonds left - do the same with a second oxygen",
        ),
        reward = LevelReward(text = MoleculeElement.CARBON_DIOXIDE.description),
    ),
)