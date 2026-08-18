package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Prose

internal val MOLECULE_FACTS: Map<KnownMoleculeId, Prose> = mapOf(
    KnownMoleculeId.DIHYDROGEN to Prose(
        ru = "Водород - это самый распространённый элемент Вселенной, 92% всех атомов. Например, наше Солнце состоит на 73% из водорода. Мы с вами состоим из водорода, которому 13.8 миллиарда лет! Он кажется самым простым, но скрывает массу парадоксов!",
        en = "Hydrogen is the most common element in the Universe, 92% of all atoms. Our Sun, for one, is 73% hydrogen. The hydrogen you and I are made of is 13.8 billion years old! It looks like the simplest thing there is, and yet it hides a pile of paradoxes!",
    ),
    KnownMoleculeId.DIOXYGEN to Prose(
        ru = "Кислород - это то, чем дышим. В воздухе кислорода 21%, и без него человек живёт минуты. Но первые два миллиарда лет его в воздухе почти не было - кислород выдышали бактерии. Для древней жизни он оказался страшным ядом! Он жадный до связей: и огонь, и ржавчина - это он. А два атома здесь держит двойная связь - первая, которую ты усилил сам.",
        en = "Oxygen is what we breathe. Air is 21% oxygen, and without it a person lasts minutes. But for the first two billion years there was almost none of it in the air - bacteria breathed it out. To ancient life it turned out to be a terrible poison! It is greedy for bonds: fire is oxygen, and so is rust. And the two atoms here are held by a double bond - the first one you strengthened yourself.",
    ),
    KnownMoleculeId.HYDROXYL to Prose(
        ru = "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\".",
        en = "Hydroxyl is a tiny molecule of one hydrogen atom and one oxygen atom. On its own it lives less than a second, but it works like a building block.",
    ),
    KnownMoleculeId.WATER to Prose(
        ru = "Вода - самая известная молекула на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии.",
        en = "Water is the most famous molecule in the world and the main substance of life. We ourselves are about 60% water. Thanks to the unusual shape of its molecule it breaks almost every rule of physics and chemistry.",
    ),
    KnownMoleculeId.HYDROGEN_PEROXIDE to Prose(
        ru = "Это вода с лишним атомом кислорода - та самая перекись из аптечки. Она пенится на ранке потому, что тело мгновенно её разрушает: пузырьки, которые мы видим - это кислород. Кстати, врачи теперь не советуют лить перекись на раны: она убивает не только микробов, но и живые клетки.",
        en = "This is water with one extra oxygen atom - the very peroxide from the medicine cabinet. It foams on a cut because the body destroys it instantly: the bubbles we see are oxygen. By the way, doctors no longer advise pouring peroxide on wounds - it kills not only germs but living cells too.",
    )
)
