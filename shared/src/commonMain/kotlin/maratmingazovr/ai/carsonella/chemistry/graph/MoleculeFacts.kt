package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Prose

internal val MOLECULE_FACTS: Map<MoleculeId, Prose> = mapOf(
    MoleculeId.DIHYDROGEN to Prose(
        ru = "Мы получили самый распространённый элемент Вселенной - 92% всех атомов. Например, наше Солнце состоит на 73% из водорода. Мы с вами состоим из водорода, которому 13.8 миллиарда лет! Он кажется самым простым, но скрывает массу парадоксов!",
        en = "We have made the most common element in the Universe - 92% of all atoms. Our Sun, for one, is 73% hydrogen. The hydrogen you and I are made of is 13.8 billion years old! It looks like the simplest thing there is, and yet it hides a pile of paradoxes!",
    ),
    MoleculeId.DIOXYGEN to Prose(
        ru = "Мы получили то, чем дышим: в воздухе кислорода 21%, и без него человек живёт минуты. Но первые два миллиарда лет его в воздухе почти не было - кислород выдышали бактерии, и для древней жизни он оказался страшным ядом! Он жадный до связей: и огонь, и ржавчина - это он. А два атома здесь держит двойная связь - первая, которую ты усилил сам.",
        en = "We have made the stuff we breathe: air is 21% oxygen, and without it a person lasts minutes. But for the first two billion years there was almost none of it in the air - bacteria breathed it out, and to ancient life it turned out to be a terrible poison! It is greedy for bonds: fire is oxygen, and so is rust. And the two atoms here are held by a double bond - the first one you strengthened yourself.",
    ),
    MoleculeId.HYDROXYL to Prose(
        ru = "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\". Мы будем прикреплять ее к другим молекулам и увидим как она полностью меняет их свойства. И у нас уже все готово, чтобы раздобыть ВОДУ! ",
        en = "Hydroxyl is a tiny molecule of one hydrogen atom and one oxygen atom. On its own it lives less than a second, but it works like a building block. We will be attaching it to other molecules and watching it change their properties completely. And we already have everything we need to get WATER! ",
    ),
    MoleculeId.WATER to Prose(
        ru = "УРА! Мы получили самую известная молекулу на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии. Мы исследуем почему горячая вода замерзает быстрее холодной? Почему лед не тонет? И многое другое!",
        en = "HOORAY! We have made the most famous molecule in the world and the main substance of life. We ourselves are about 60% water. Thanks to the unusual shape of its molecule it breaks almost every rule of physics and chemistry. We will look into why hot water freezes faster than cold water, why ice does not sink, and much more!",
    ),
    MoleculeId.HYDROGEN_PEROXIDE to Prose(
        ru = "Это вода с лишним атомом кислорода - та самая перекись из аптечки. Она пенится на ранке потому, что тело мгновенно её разрушает: пузырьки, которые мы видим - это кислород. Кстати, врачи теперь не советуют лить перекись на раны: она убивает не только микробов, но и живые клетки.",
        en = "This is water with one extra oxygen atom - the very peroxide from the medicine cabinet. It foams on a cut because the body destroys it instantly: the bubbles we see are oxygen. By the way, doctors no longer advise pouring peroxide on wounds - it kills not only germs but living cells too.",
    ),
    MoleculeId.TETRAOXIDANE to Prose(
        ru = "Четыре кислорода подряд — предел, до которого такая цепочка вообще доживает. Собирается из двух радикалов HO₂• и существует только в криогенной заморозке, ниже −100 °C; интересен химикам, применений нет. При нагреве мгновенно распадается на перекись и кислород. Цепочек из пяти кислородов не наблюдали ни разу.",
        en = "Four oxygens in a row is the limit such a chain survives at all. It comes together from two HO₂• radicals and exists only under cryogenic freezing, below −100 °C; chemists find it interesting, it has no uses. Heated, it falls apart instantly into peroxide and oxygen. A chain of five oxygens has never been observed even once.",
    ),
)
