package maratmingazovr.ai.carsonella.chemistry.registry

import maratmingazovr.ai.carsonella.TranslatedText
import maratmingazovr.ai.carsonella.chemistry.registry.ElementOrMolecule
import maratmingazovr.ai.carsonella.chemistry.KnownMoleculeDetails
import maratmingazovr.ai.carsonella.chemistry.MoleculeRegistry

enum class MoleculeElement(val title: TranslatedText, val description: TranslatedText) : ElementOrMolecule {
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
    NITROGEN_MONOXIDE(TranslatedText("Монооксид азота", "Nitric oxide"), TranslatedText(ru = "", en = "")),
    HYDROXYL(
        TranslatedText("Гидроксил", "Hydroxyl"),
        TranslatedText(
            ru = "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\".",
            en = "Hydroxyl is a tiny molecule of one hydrogen atom and one oxygen atom. On its own it lives less than a second, but it works like a building block.",
        ),
    ),
    DICARBON_SINGLE(
        TranslatedText("Дикарбон", "Dicarbon"),
        TranslatedText(ru = "", en = ""),
    ),
    DICARBON_DOUBLE(
        TranslatedText("Дикарбон", "Dicarbon"),
        TranslatedText(ru = "", en = ""),
    ),
    DICARBON_TRIPLE(
        TranslatedText("Дикарбон", "Dicarbon"),
        TranslatedText(ru = "", en = ""),
    ),
    TRICARBON(
        TranslatedText("Триуглерод", "Tricarbon"),
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
    HYDRAZINEDIYL(TranslatedText("Гидразиндиил", "Hydrazinediyl"), TranslatedText(ru = "", en = "")),
    HYDRAZINE(TranslatedText("Гидразин", "Hydrazine"), TranslatedText(ru = "", en = "")),
    METHYLENEAMIDOGEN(TranslatedText("Метиленамидоген", "Methyleneamidogen"), TranslatedText(ru = "", en = "")),
    METHANIMINE(TranslatedText("Метанимин", "Methanimine"), TranslatedText(ru = "", en = "")),
    CARBONYL(TranslatedText("Карбонил", "Carbonyl"), TranslatedText(ru = "", en = "")),
    CARBON_DIOXIDE(
        TranslatedText("Углекислый газ", "Carbon dioxide"),
        TranslatedText(
            ru = "У углерода получилось сразу две двойные связи, к одному атому. Этот газ мы выдыхаем, а растения им дышат и строят из него всё своё тело. Он же даёт пузырьки в газировке и держит планету в тепле - без него на Земле было бы холоднее.",
            en = "Carbon ended up with two double bonds at once, both on the same atom. This is the gas we breathe out, and the one plants breathe in to build their whole body out of it. It is also what makes soda fizzy, and it keeps the planet warm - without it, Earth would be colder.",
        ),
    ),
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
    ACETYLENE(
        TranslatedText("Ацетилен", "Acetylene"),
        TranslatedText(
            ru = "Тройная связь между двумя углеродами - самая крепкая связь, которую ты пока собирал. В ней столько энергии, что сваркой режут металл именно ацетиленовым пламенем - оно одно из самых горячих, какие вообще можно поджечь.",
            en = "The triple bond between the two carbons is the strongest bond you have built so far. It holds so much energy that welders cut metal with an acetylene flame - it is one of the hottest flames you can light.",
        ),
    ),
    VINYL            (TranslatedText("Винил", "Vinyl"), TranslatedText(ru = "", en = "")),
    VINYLIDENE       (TranslatedText("Винилиден", "Vinylidene"), TranslatedText(ru = "", en = "")),
    ETHYLENE(
        TranslatedText("Этилен", "Ethylene"),
        TranslatedText(
            ru = "Двойная связь между углеродами делает этилен вспыльчивым: ему хочется раскрыться и сцепиться с соседом - так собирают полиэтилен, пакеты и бутылки. А ещё это гормон растений: именно этилен заставляет бананы и яблоки дозревать.",
            en = "The double bond between the carbons makes ethylene eager to react: it wants to open up and latch onto its neighbour - that is how polyethylene, bags and bottles are made. It is also a plant hormone: ethylene is what makes bananas and apples ripen.",
        ),
    ),
    ETHYL            (TranslatedText("Этил", "Ethyl"), TranslatedText(ru = "", en = "")),
    ETHANE(
        TranslatedText("Этан", "Ethane"),
        TranslatedText(
            ru = "Два атома углерода, соединённые напрямую, - первая молекула, где углерод цепляется сам за себя. Так начинаются все цепочки жизни. Этан - младший брат метана в природном газе, и его тоже сжигают ради тепла.",
            en = "Two carbon atoms joined straight to each other - the first molecule where carbon latches onto itself. That is how every chain of life begins. Ethane is methane's little brother in natural gas, and it too gets burned for heat.",
        ),
    ),
    //BUTANE           (TranslatedText("Бутан", "Butane"), TranslatedText(ru = "", en = "")),
    ETHYLIDYNE       (TranslatedText("Этилидин", "Ethylidyne"), TranslatedText(ru = "", en = "")),
    ETHYLIDENE       (TranslatedText("Этилиден", "Ethylidene"), TranslatedText(ru = "", en = "")),
    //ISOPROPYL        (TranslatedText("Изопропил", "Isopropyl"), TranslatedText(ru = "", en = "")),
    //ISOBUTANE        (TranslatedText("Изобутан", "Isobutane"), TranslatedText(ru = "", en = "")),
    //BUTENE_1         (TranslatedText("Бутен-1", "1-Butene"), TranslatedText(ru = "", en = "")),
    //BUTENE_2         (TranslatedText("Бутен-2", "2-Butene"), TranslatedText(ru = "", en = "")),
    //ISOPROPENYL      (TranslatedText("Изопропенил", "Isopropenyl"), TranslatedText(ru = "", en = "")),
    //ISOBUTYLENE      (TranslatedText("Изобутилен", "Isobutylene"), TranslatedText(ru = "", en = "")),
    //TRIMETHYLENE     (TranslatedText("Триметилен", "Trimethylene"), TranslatedText(ru = "", en = "")),
    //CYCLOPROPANE     (TranslatedText("Циклопропан", "Cyclopropane"), TranslatedText(ru = "", en = "")),
    //OXIRANE          (TranslatedText("Оксиран", "Oxirane"), TranslatedText(ru = "", en = "")),
    //BENZENE          (TranslatedText("Бензол", "Benzene"), TranslatedText(ru = "", en = "")),
    FORMYL           (TranslatedText("Формил", "Formyl"), TranslatedText(ru = "", en = "")),
    FORMALDEHYDE(
        TranslatedText("Формальдегид", "Formaldehyde"),
        TranslatedText(
            ru = "Первая молекула, где кислород двойной связью сидит прямо на углероде. Её раствором - формалином - хранят анатомические препараты в музеях: формальдегид не даёт тканям разлагаться. В крошечных количествах его производит и твоё собственное тело.",
            en = "The first molecule where oxygen sits right on carbon by a double bond. Its solution - formalin - is what museums use to preserve anatomical specimens: formaldehyde keeps tissue from decaying. Your own body makes tiny amounts of it too.",
        ),
    ),
    METHANOL(
        TranslatedText("Метанол", "Methanol"),
        TranslatedText(
            ru = "Метанол называют древесным спиртом: раньше его получали, нагревая дерево без воздуха. Пить его нельзя - он ядовит, в отличие от родственного ему винного спирта.",
            en = "Methanol is called wood alcohol: it used to be made by heating wood without air. You cannot drink it - it is poisonous, unlike its cousin, drinking alcohol.",
        ),
    ),
    FORMIC_ACID      (TranslatedText("Муравьиная кислота", "Formic acid"), TranslatedText(ru = "", en = "")),
    ETHANOL          (TranslatedText("Этанол", "Ethanol"), TranslatedText(ru = "", en = "")),
    ;

    val details: KnownMoleculeDetails get() = MoleculeRegistry.knownMoleculeById(this)
}