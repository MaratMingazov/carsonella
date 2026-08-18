package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Lang
import maratmingazovr.ai.carsonella.TranslatedText

enum class KnownMoleculeId(val title: TranslatedText, val description: TranslatedText? = null) {
    DIHYDROGEN(
        TranslatedText("Водород", "Dihydrogen"),
        TranslatedText(
            ru = "Водород - это самый распространённый элемент Вселенной, 92% всех атомов. Например, наше Солнце состоит на 73% из водорода. Мы с вами состоим из водорода, которому 13.8 миллиарда лет! Он кажется самым простым, но скрывает массу парадоксов!",
            en = "Hydrogen is the most common element in the Universe, 92% of all atoms. Our Sun, for one, is 73% hydrogen. The hydrogen you and I are made of is 13.8 billion years old! It looks like the simplest thing there is, and yet it hides a pile of paradoxes!",
        ),
    ),
    DIOXYGEN(
        TranslatedText("Кислород", "Dioxygen"),
        TranslatedText(
            ru = "Кислород - это то, чем дышим. В воздухе кислорода 21%, и без него человек живёт минуты. Но первые два миллиарда лет его в воздухе почти не было - кислород выдышали бактерии. Для древней жизни он оказался страшным ядом! Он жадный до связей: и огонь, и ржавчина - это он. А два атома здесь держит двойная связь - первая, которую ты усилил сам.",
            en = "Oxygen is what we breathe. Air is 21% oxygen, and without it a person lasts minutes. But for the first two billion years there was almost none of it in the air - bacteria breathed it out. To ancient life it turned out to be a terrible poison! It is greedy for bonds: fire is oxygen, and so is rust. And the two atoms here are held by a double bond - the first one you strengthened yourself.",
        ),
    ),
    DINITROGEN(TranslatedText("Азот", "Dinitrogen")),
    HYDROXYL(
        TranslatedText("Гидроксил", "Hydroxyl"),
        TranslatedText(
            ru = "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\".",
            en = "Hydroxyl is a tiny molecule of one hydrogen atom and one oxygen atom. On its own it lives less than a second, but it works like a building block.",
        ),
    ),
    DICARBON         (TranslatedText("Дикарбон", "Dicarbon")),
    WATER(
        TranslatedText("Вода", "Water"),
        TranslatedText(
            ru = "Вода - самая известная молекула на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии.",
            en = "Water is the most famous molecule in the world and the main substance of life. We ourselves are about 60% water. Thanks to the unusual shape of its molecule it breaks almost every rule of physics and chemistry.",
        ),
    ),
    HYDROPEROXYL     (TranslatedText("Гидропероксил", "Hydroperoxyl")),
    HYDROGEN_PEROXIDE(
        TranslatedText("Перекись водорода", "Hydrogen peroxide"),
        TranslatedText(
            ru = "Это вода с лишним атомом кислорода - та самая перекись из аптечки. Она пенится на ранке потому, что тело мгновенно её разрушает: пузырьки, которые мы видим - это кислород. Кстати, врачи теперь не советуют лить перекись на раны: она убивает не только микробов, но и живые клетки.",
            en = "This is water with one extra oxygen atom - the very peroxide from the medicine cabinet. It foams on a cut because the body destroys it instantly: the bubbles we see are oxygen. By the way, doctors no longer advise pouring peroxide on wounds - it kills not only germs but living cells too.",
        ),
    ),
    TRIOXIDANE       (TranslatedText("Триоксидан", "Trioxidane")),
    TETRAOXIDANE     (TranslatedText("Тетраоксидан", "Tetraoxidane")),
    IMIDOGEN         (TranslatedText("Имидоген", "Imidogen")),
    AMINO_RADICAL    (TranslatedText("Аминорадикал", "Amino radical")),
    AMMONIA          (TranslatedText("Аммиак", "Ammonia")),
    CARBON_DIOXIDE   (TranslatedText("Углекислый газ", "Carbon dioxide")),
    CYANO            (TranslatedText("Циано", "Cyano")),
    HYDROGEN_CYANIDE (TranslatedText("Циановодород", "Hydrogen cyanide")),
    METHYLIDYNE      (TranslatedText("Метилидин", "Methylidyne")),
    METHYLENE        (TranslatedText("Метилен", "Methylene")),
    METHYL           (TranslatedText("Метил", "Methyl")),
    METHANE          (TranslatedText("Метан", "Methane")),
    ETHYNYL          (TranslatedText("Этинил", "Ethynyl")),
    ACETYLENE        (TranslatedText("Ацетилен", "Acetylene")),
    VINYL            (TranslatedText("Винил", "Vinyl")),
    ETHYLENE         (TranslatedText("Этилен", "Ethylene")),
    ETHYL            (TranslatedText("Этил", "Ethyl")),
    ETHANE           (TranslatedText("Этан", "Ethane")),
    BUTANE           (TranslatedText("Бутан", "Butane")),
    ISOPROPYL        (TranslatedText("Изопропил", "Isopropyl")),
    ISOBUTANE        (TranslatedText("Изобутан", "Isobutane")),
    BUTENE_1         (TranslatedText("Бутен-1", "1-Butene")),
    BUTENE_2         (TranslatedText("Бутен-2", "2-Butene")),
    ISOPROPENYL      (TranslatedText("Изопропенил", "Isopropenyl")),
    ISOBUTYLENE      (TranslatedText("Изобутилен", "Isobutylene")),
    TRIMETHYLENE     (TranslatedText("Триметилен", "Trimethylene")),
    CYCLOPROPANE     (TranslatedText("Циклопропан", "Cyclopropane")),
    OXIRANE          (TranslatedText("Оксиран", "Oxirane")),
    BENZENE          (TranslatedText("Бензол", "Benzene")),
    FORMYL           (TranslatedText("Формил", "Formyl")),
    FORMALDEHYDE     (TranslatedText("Формальдегид", "Formaldehyde")),
    METHANOL         (TranslatedText("Метанол", "Methanol")),
    FORMIC_ACID      (TranslatedText("Муравьиная кислота", "Formic acid")),
    ETHANOL          (TranslatedText("Этанол", "Ethanol")),
    ;

    fun name(lang: Lang): String = title.of(lang)
    val details: KnownMoleculeDetails get() = MoleculeRegistry.knownMoleculeById(this)
}