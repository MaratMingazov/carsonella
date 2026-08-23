package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.TranslatedText

enum class KnownMoleculeId(val title: TranslatedText, val description: TranslatedText) {
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
    DINITROGEN(
        TranslatedText("Азот", "Dinitrogen"),
        TranslatedText(ru = "", en = ""),
    ),
    HYDROXYL(
        TranslatedText("Гидроксил", "Hydroxyl"),
        TranslatedText(
            ru = "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\".",
            en = "Hydroxyl is a tiny molecule of one hydrogen atom and one oxygen atom. On its own it lives less than a second, but it works like a building block.",
        ),
    ),
    DICARBON(
        TranslatedText("Дикарбон", "Dicarbon"),
        TranslatedText(ru = "", en = ""),
    ),
    WATER(
        TranslatedText("Вода", "Water"),
        TranslatedText(
            ru = "Вода - самая известная молекула на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии.",
            en = "Water is the most famous molecule in the world and the main substance of life. We ourselves are about 60% water. Thanks to the unusual shape of its molecule it breaks almost every rule of physics and chemistry.",
        ),
    ),
    HYDROPEROXYL(TranslatedText("Гидропероксил", "Hydroperoxyl"), TranslatedText(ru = "", en = "")),
    HYDROGEN_PEROXIDE(
        TranslatedText("Перекись водорода", "Hydrogen peroxide"),
        TranslatedText(
            ru = "Это вода с лишним атомом кислорода - та самая перекись из аптечки. Она пенится на ранке потому, что тело мгновенно её разрушает: пузырьки, которые мы видим - это кислород. Кстати, врачи теперь не советуют лить перекись на раны: она убивает не только микробов, но и живые клетки.",
            en = "This is water with one extra oxygen atom - the very peroxide from the medicine cabinet. It foams on a cut because the body destroys it instantly: the bubbles we see are oxygen. By the way, doctors no longer advise pouring peroxide on wounds - it kills not only germs but living cells too.",
        ),
    ),
    TRIOXIDANE(
        TranslatedText("Триоксидан", "Trioxidane"), 
        TranslatedText(
            ru = "Это перекись, которой добавили ещё один кислород. Такая цепочка держится еле-еле: в тепле она разваливается за минуты, поэтому в аптеке её не купишь. Зато в воздухе над нами она рождается постоянно - и заметили её там совсем недавно, в 2022 году.",
            en = "This is peroxide with one more oxygen added. A chain like this barely holds: in the warm it falls apart in minutes, so you will not find it in a pharmacy. But up in the air above us it is being made all the time - and it was only spotted there recently, in 2022.",
        ),
    ),
    TETRAOXIDANE(
        TranslatedText("Тетраоксидан", "Tetraoxidane"), 
        TranslatedText(
            ru = "Четыре кислорода подряд - предел, до которого такую цепочку вообще смогли дотянуть. Живёт она только на сильном морозе: чуть теплее - и цепочка рвётся сама, выбрасывая кислород. Чем длиннее цепочка, тем слабее она держится.",
            en = "Four oxygens in a row is as far as a chain like this has ever been stretched. It survives only in deep cold: any warmer and the chain snaps by itself, throwing off oxygen. The longer the chain, the worse it holds together.",
        ),
    ),
    IMIDOGEN(TranslatedText("Имидоген", "Imidogen"), TranslatedText(ru = "", en = "")),
    AMINO_RADICAL(TranslatedText("Аминорадикал", "Amino radical"), TranslatedText(ru = "", en = "")),
    AMMONIA(TranslatedText("Аммиак", "Ammonia"), TranslatedText(ru = "", en = "")),
    CARBON_DIOXIDE   (TranslatedText("Углекислый газ", "Carbon dioxide"), TranslatedText(ru = "", en = "")),
    CYANO            (TranslatedText("Циано", "Cyano"), TranslatedText(ru = "", en = "")),
    HYDROGEN_CYANIDE (TranslatedText("Циановодород", "Hydrogen cyanide"), TranslatedText(ru = "", en = "")),
    METHYLIDYNE      (TranslatedText("Метилидин", "Methylidyne"), TranslatedText(ru = "", en = "")),
    METHYLENE        (TranslatedText("Метилен", "Methylene"), TranslatedText(ru = "", en = "")),
    METHYL           (TranslatedText("Метил", "Methyl"), TranslatedText(ru = "", en = "")),
    METHANE(
        TranslatedText("Метан", "Methane"),
        TranslatedText(
            ru = "Метан - простейшая молекула с углеродом: один атом держит сразу четыре водорода. Это тот самый природный газ, на котором готовят еду, и главный газ, который пускают коровы и болота. А ещё на Титане, спутнике Сатурна, из метана целые реки и озёра - там так холодно, что он течёт, как у нас вода.",
            en = "Methane is the simplest carbon molecule: one atom holding four hydrogens at once. It is the natural gas used for cooking, and the main gas that cows and swamps let out. On Titan, a moon of Saturn, whole rivers and lakes are made of methane - it is so cold there that it flows the way water does here.",
        ),
    ),
    ETHYNYL          (TranslatedText("Этинил", "Ethynyl"), TranslatedText(ru = "", en = "")),
    ACETYLENE        (TranslatedText("Ацетилен", "Acetylene"), TranslatedText(ru = "", en = "")),
    VINYL            (TranslatedText("Винил", "Vinyl"), TranslatedText(ru = "", en = "")),
    ETHYLENE         (TranslatedText("Этилен", "Ethylene"), TranslatedText(ru = "", en = "")),
    ETHYL            (TranslatedText("Этил", "Ethyl"), TranslatedText(ru = "", en = "")),
    ETHANE           (TranslatedText("Этан", "Ethane"), TranslatedText(ru = "", en = "")),
    BUTANE           (TranslatedText("Бутан", "Butane"), TranslatedText(ru = "", en = "")),
    ISOPROPYL        (TranslatedText("Изопропил", "Isopropyl"), TranslatedText(ru = "", en = "")),
    ISOBUTANE        (TranslatedText("Изобутан", "Isobutane"), TranslatedText(ru = "", en = "")),
    BUTENE_1         (TranslatedText("Бутен-1", "1-Butene"), TranslatedText(ru = "", en = "")),
    BUTENE_2         (TranslatedText("Бутен-2", "2-Butene"), TranslatedText(ru = "", en = "")),
    ISOPROPENYL      (TranslatedText("Изопропенил", "Isopropenyl"), TranslatedText(ru = "", en = "")),
    ISOBUTYLENE      (TranslatedText("Изобутилен", "Isobutylene"), TranslatedText(ru = "", en = "")),
    TRIMETHYLENE     (TranslatedText("Триметилен", "Trimethylene"), TranslatedText(ru = "", en = "")),
    CYCLOPROPANE     (TranslatedText("Циклопропан", "Cyclopropane"), TranslatedText(ru = "", en = "")),
    OXIRANE          (TranslatedText("Оксиран", "Oxirane"), TranslatedText(ru = "", en = "")),
    BENZENE          (TranslatedText("Бензол", "Benzene"), TranslatedText(ru = "", en = "")),
    FORMYL           (TranslatedText("Формил", "Formyl"), TranslatedText(ru = "", en = "")),
    FORMALDEHYDE     (TranslatedText("Формальдегид", "Formaldehyde"), TranslatedText(ru = "", en = "")),
    METHANOL         (TranslatedText("Метанол", "Methanol"), TranslatedText(ru = "", en = "")),
    FORMIC_ACID      (TranslatedText("Муравьиная кислота", "Formic acid"), TranslatedText(ru = "", en = "")),
    ETHANOL          (TranslatedText("Этанол", "Ethanol"), TranslatedText(ru = "", en = "")),
    ;

    val details: KnownMoleculeDetails get() = MoleculeRegistry.knownMoleculeById(this)
}