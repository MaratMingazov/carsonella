package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element

/**
 * Курируемая запись реестра: что знаем об известной молекуле сверх её структуры.
 * [structuralFormula] — сжатая СТРУКТУРНАЯ формула (связность + радикальный слот •): CH₃–CH₃, H–O–O•.
 * В отличие от брутто-формулы графа ([MoleculeGraph.formulaPretty] = C₂H₆) показывает, КАК атомы соединены.
 */
data class KnownMolecule(
    val nameEn: String,
    val nameRu: String,
    val structuralFormula: String = "",
    val description: String = "",
)

/**
 * Реестр известных молекул: канонический ключ → имя. ОПЦИОНАЛЕН (не ядро): без него молекулы анонимны и
 * опознаются структурно (это норма — большинство молекул безымянны). Ценность — имена курируемого
 * подмножества (позже сюда же ляжет «крючок вехи»: первое появление воды/аминокислоты — событие).
 *
 * Сидируется каноникализацией: строим ЭТАЛОННЫЙ граф известной молекулы и берём его [MoleculeGraph.canonical]
 * как ключ. Публичный [lookup] — по строке-канону, от [MoleculeGraph] не зависит (реестр = Map<canonical, _>).
 *
 * ВАЖНО про авторинг: эталонный граф обязан совпадать по СТРУКТУРЕ с тем, что рождает симуляция —
 *  - те же ИЗОТОПЫ (обычные: HYDROGEN/OXYGEN_16/CARBON_12/NITROGEN_14). D₂O не совпадёт с «Water» — это
 *    физически другая молекула (тяжёлая вода), и это правильно;
 *  - те же КРАТНОСТИ (эмёрджентные): движок доводит O₂ до O=O, N₂ до N≡N, CO₂ до O=C=O. Записать O–O
 *    (order 1) → реальный O₂ не опознается.
 */
object MoleculeRegistry {

    // Дикарбон C₂ (экзотический бирадикал: пламя/кометы). В модели встречается на всех трёх порядках связи
    // (C–C/C=C/C≡C) как промежуток сборки; порядок связи модельно неточен (в природе ≈ двойной). Одно имя
    // на все три канона (порядок связи всё равно виден в структурном рендере).
    // structuralFormula пуст: у дикарбона он зависит от порядка связи (C–C/C=C/C≡C), а запись одна на три.
    private val dicarbon = KnownMolecule("Dicarbon", "Дикарбон", description = "экзотический бирадикал (пламя/кометы); порядок связи модельно неточен")

    private val byCanonical: Map<String, KnownMolecule> = listOf(
        // --- двухатомные ---
        mol(listOf(h(0), h(1)), listOf(bond(0, 1)))                              to KnownMolecule("Dihydrogen", "Водород", "H–H"),
        mol(listOf(o(0), o(1)), listOf(bond(0, 1, 2)))                           to KnownMolecule("Dioxygen", "Кислород", "O=O"),      // O=O
        mol(listOf(n(0), n(1)), listOf(bond(0, 1, 3)))                           to KnownMolecule("Dinitrogen", "Азот", "N≡N"),        // N≡N

        // --- малые неорганические / простые ---
        mol(listOf(o(0), h(1), h(2)), listOf(bond(0, 1), bond(0, 2)))            to KnownMolecule("Water", "Вода", "H–O–H"),
        mol(listOf(o(0), o(1), h(2), h(3)), listOf(bond(0, 1), bond(0, 2), bond(1, 3))) to KnownMolecule("Hydrogen peroxide", "Перекись водорода", "H–O–O–H", // H–O–O–H
            "Вода с лишним атомом кислорода: два O держатся друг за друга слабой связью. " +
            "Аптечный антисептик (3%), отбеливатель для тканей и бумаги, в концентрате — ракетное топливо; " +
            "в живой клетке появляется при дыхании, и фермент каталаза срочно её разлагает. " +
            "Сама медленно распадается на воду и кислород — свет и тепло ускоряют, потому и хранят в тёмной бутылке."),

        // Полиоксиды: та же цепочка O–O, но длиннее. Показывают, где проходит граница существования —
        // слабая связь O–O (1.51 эВ) против сверхпрочной O=O (5.16), в которую цепочка «расстёгивается».
        mol(listOf(o(0), o(1), o(2), h(3), h(4)), listOf(bond(0, 1), bond(1, 2), bond(0, 3), bond(2, 4))) to KnownMolecule("Trioxidane", "Триоксидан", "H–O–O–O–H",
            "Три кислорода в цепочке — вещество на грани существования, но настоящее: его ловили приборами. " +
            "Рождается при встрече озона с перекисью, применений не имеет. " +
            "Живёт миллисекунды в воде (минуты — на сильном морозе в органическом растворителе) и разваливается на воду и кислород."),

        mol(listOf(o(0), o(1), o(2), o(3), h(4), h(5)), listOf(bond(0, 1), bond(1, 2), bond(2, 3), bond(0, 4), bond(3, 5))) to KnownMolecule("Tetraoxidane", "Тетраоксидан", "H–O–O–O–O–H",
            "Четыре кислорода подряд — предел, до которого такая цепочка вообще доживает. " +
            "Собирается из двух радикалов HO₂• и существует только в криогенной заморозке, ниже −100 °C; " +
            "интересен химикам атмосферы, применений нет. При нагреве мгновенно распадается на перекись и кислород. " +
            "Цепочек из пяти кислородов не наблюдали ни разу."),
        mol(listOf(n(0), h(1), h(2), h(3)), listOf(bond(0, 1), bond(0, 2), bond(0, 3))) to KnownMolecule("Ammonia", "Аммиак", "NH₃"),
        mol(listOf(c(0), o(1), o(2)), listOf(bond(0, 1, 2), bond(0, 2, 2)))      to KnownMolecule("Carbon dioxide", "Углекислый газ", "O=C=O"), // O=C=O
        mol(listOf(h(0), c(1), n(2)), listOf(bond(0, 1), bond(1, 2, 3)))         to KnownMolecule("Hydrogen cyanide", "Циановодород", "H–C≡N"),  // H–C≡N

        // --- углеводороды ---
        mol(listOf(c(0), h(1), h(2), h(3), h(4)), listOf(bond(0, 1), bond(0, 2), bond(0, 3), bond(0, 4))) to KnownMolecule("Methane", "Метан", "CH₄"),
        mol(listOf(c(0), c(1), h(2), h(3)), listOf(bond(0, 1, 3), bond(0, 2), bond(1, 3))) to KnownMolecule("Acetylene", "Ацетилен", "HC≡CH"),     // HC≡CH
        mol(listOf(c(0), c(1), h(2), h(3), h(4), h(5)),
            listOf(bond(0, 1, 2), bond(0, 2), bond(0, 3), bond(1, 4), bond(1, 5))) to KnownMolecule("Ethylene", "Этилен", "H₂C=CH₂"),        // H₂C=CH₂
        mol(listOf(c(0), c(1), h(2), h(3), h(4), h(5), h(6), h(7)), listOf(bond(0, 1), bond(0, 2), bond(0, 3), bond(0, 4), bond(1, 5), bond(1, 6), bond(1, 7))) to KnownMolecule("Ethane", "Этан", "CH₃–CH₃"), //  CH₃–CH₃

        // Бутаны C₄H₁₀ — изомеры по СКЕЛЕТУ: цепочка против ветвления, кратностей связи тут нет вообще.
        mol(listOf(c(0), c(1), c(2), c(3), h(4), h(5), h(6), h(7), h(8), h(9), h(10), h(11), h(12), h(13)),
            listOf(bond(0, 1), bond(1, 2), bond(2, 3),
                bond(0, 4), bond(0, 5), bond(0, 6), bond(1, 7), bond(1, 8), bond(2, 9), bond(2, 10), bond(3, 11), bond(3, 12), bond(3, 13))) to KnownMolecule("Butane", "Бутан", "CH₃–CH₂–CH₂–CH₃"),
        mol(listOf(c(0), c(1), c(2), c(3), h(4), h(5), h(6), h(7), h(8), h(9), h(10), h(11), h(12), h(13)),
            listOf(bond(0, 1), bond(0, 2), bond(0, 3),
                bond(0, 4), bond(1, 5), bond(1, 6), bond(1, 7), bond(2, 8), bond(2, 9), bond(2, 10), bond(3, 11), bond(3, 12), bond(3, 13))) to KnownMolecule("Isobutane", "Изобутан", "(CH₃)₃CH"),

        // Бутены C₄H₈
        mol(listOf(c(0), c(1), c(2), c(3), h(4), h(5), h(6), h(7), h(8), h(9), h(10), h(11)),
            listOf(bond(0, 1, 2), bond(1, 2), bond(2, 3),
                bond(0, 4), bond(0, 5), bond(1, 6), bond(2, 7), bond(2, 8), bond(3, 9), bond(3, 10), bond(3, 11))) to KnownMolecule("1-Butene", "Бутен-1", "H₂C=CH–CH₂–CH₃"),
        mol(listOf(c(0), c(1), c(2), c(3), h(4), h(5), h(6), h(7), h(8), h(9), h(10), h(11)),
            listOf(bond(0, 1), bond(1, 2, 2), bond(2, 3),
                bond(0, 4), bond(0, 5), bond(0, 6), bond(1, 7), bond(2, 8), bond(3, 9), bond(3, 10), bond(3, 11))) to KnownMolecule("2-Butene", "Бутен-2", "CH₃–CH=CH–CH₃"),
        mol(listOf(c(0), c(1), c(2), c(3), h(4), h(5), h(6), h(7), h(8), h(9), h(10), h(11)),
            listOf(bond(0, 1, 2), bond(1, 2), bond(1, 3),
                bond(0, 4), bond(0, 5), bond(2, 6), bond(2, 7), bond(2, 8), bond(3, 9), bond(3, 10), bond(3, 11))) to KnownMolecule("Isobutylene", "Изобутилен", "H₂C=C(CH₃)₂"),

        // --- кольца ---
        // Первые циклы, которые игрок замыкает сам (RingClosure). Оба трёхчленные и потому напряжённые —
        // модель напряжение пока не считает, так что фотон при замыкании выходит завышенным.
        mol(listOf(c(0), c(1), c(2), h(3), h(4), h(5), h(6), h(7), h(8)),
            listOf(bond(0, 1), bond(1, 2), bond(2, 0),
                bond(0, 3), bond(0, 4), bond(1, 5), bond(1, 6), bond(2, 7), bond(2, 8))) to KnownMolecule("Cyclopropane", "Циклопропан", "(CH₂)₃",
            "Три углерода в треугольнике — самое напряжённое кольцо органики: связи выгнуты почти на 50° " +
            "от угла, который углерод любит, и потому кольцо охотно раскрывается. Газ; до 1980-х им давали " +
            "быстрый наркоз, перестали из-за взрывоопасности. Показывает, что вещество задаёт не только состав, " +
            "но и скелет: у пропилена та же формула C₃H₆, а свойства другие."),
        mol(listOf(c(0), c(1), o(2), h(3), h(4), h(5), h(6)),
            listOf(bond(0, 1), bond(1, 2), bond(2, 0),
                bond(0, 3), bond(0, 4), bond(1, 5), bond(1, 6))) to KnownMolecule("Oxirane", "Оксиран", "(CH₂)₂O",
            "Он же этиленоксид: кислород, вставленный в связь C–C, — треугольник из двух углеродов и кислорода. " +
            "Напряжение делает его жадным, кольцо раскрывается почти обо что угодно, поэтому это один из самых " +
            "массовых промышленных реагентов: из него делают антифриз и полиэфирное волокно, им же стерилизуют " +
            "медицинский инструмент. Токсичен и канцерогенен. Найден и в межзвёздных облаках."),

        // Бензол по КЕКУЛЕ: чередование одинарных и двойных. В реальности связи делокализованы и все
        // одинаковые («полторы»), но кратности в модели целые — а игрок собирает ровно это чередование:
        // замкнул кольцо и усилил каждую вторую связь. Оба варианта чередования — один и тот же граф
        // с точностью до поворота, поэтому канон у них общий и записи хватает одной.
        mol(listOf(c(0), c(1), c(2), c(3), c(4), c(5), h(6), h(7), h(8), h(9), h(10), h(11)),
            listOf(bond(0, 1, 2), bond(1, 2), bond(2, 3, 2), bond(3, 4), bond(4, 5, 2), bond(5, 0),
                bond(0, 6), bond(1, 7), bond(2, 8), bond(3, 9), bond(4, 10), bond(5, 11))) to KnownMolecule("Benzene", "Бензол", "(CH)₆",
            "Шесть углеродов в правильном шестиугольнике — и электроны двойных связей размазаны по всему кольцу, " +
            "поэтому в реальности все шесть связей одинаковые, «полуторные», а кольцо необычно стойкое. " +
            "Выделил Фарадей в 1825-м, кольцевую структуру угадал Кекуле. На нём стоит огромная часть органики: " +
            "краски, лекарства, пластики; сам канцероген, поэтому его долю в бензине ограничивают. " +
            "Найден в межзвёздных облаках и в атмосфере Титана."),

        // --- кислородсодержащая органика ---
        mol(listOf(c(0), o(1), h(2), h(3)), listOf(bond(0, 1, 2), bond(0, 2), bond(0, 3))) to KnownMolecule("Formaldehyde", "Формальдегид", "H₂C=O"), // H₂C=O
        mol(listOf(c(0), o(1), h(2), h(3), h(4), h(5)), listOf(bond(0, 1), bond(0, 2), bond(0, 3), bond(0, 4), bond(1, 5))) to KnownMolecule("Methanol", "Метанол", "CH₃–OH"),        // CH₃–OH
        mol(listOf(c(0), o(1), o(2), h(3), h(4)), listOf(bond(0, 1, 2), bond(0, 2), bond(0, 3), bond(2, 4))) to KnownMolecule("Formic acid", "Муравьиная кислота", "H–C(=O)–OH"), // H–C(=O)–O–H
        mol(listOf(c(0), c(1), o(2), h(3), h(4), h(5), h(6), h(7), h(8)),
            listOf(bond(0, 1), bond(1, 2), bond(0, 3), bond(0, 4), bond(0, 5), bond(1, 6), bond(1, 7), bond(2, 8))) to KnownMolecule("Ethanol", "Этанол", "CH₃–CH₂–OH"),         // CH₃–CH₂–OH

        // --- радикалы (есть свободный валентный слот) ---
        mol(listOf(o(0), h(1)), listOf(bond(0, 1)))                              to KnownMolecule("Hydroxyl", "Гидроксил", "•OH"),      // •OH
        mol(listOf(c(0), h(1), h(2), h(3)), listOf(bond(0, 1), bond(0, 2), bond(0, 3))) to KnownMolecule("Methyl", "Метил", "•CH₃"),           // •CH₃
        mol(listOf(n(0), h(1), h(2)), listOf(bond(0, 1), bond(0, 2)))            to KnownMolecule("Amino radical", "Аминорадикал", "•NH₂"), // •NH₂
        mol(listOf(h(0), o(1), o(2)), listOf(bond(0, 1), bond(1, 2)))            to KnownMolecule("Hydroperoxyl", "Гидропероксил", "H–O–O•"), // H–O–O•
        mol(listOf(c(0), c(1), h(2), h(3), h(4), h(5), h(6)), listOf(bond(0, 1), bond(0, 2), bond(0, 3), bond(0, 4), bond(1, 5), bond(1, 6))) to KnownMolecule("Ethyl", "Этил", "CH₃–CH₂•"),             // CH₃–CH₂•
        mol(listOf(c(0), h(1)), listOf(bond(0, 1)))                              to KnownMolecule("Methylidyne", "Метилидин", "•CH"),   // •CH  (3 слота)
        mol(listOf(c(0), h(1), h(2)), listOf(bond(0, 1), bond(0, 2)))            to KnownMolecule("Methylene", "Метилен", ":CH₂"),       // :CH₂ (2 слота)
        mol(listOf(h(0), c(1), c(2)), listOf(bond(0, 1), bond(1, 2, 3)))         to KnownMolecule("Ethynyl", "Этинил", "HC≡C•"),          // H–C≡C•
        mol(listOf(c(0), c(1), h(2), h(3), h(4)), listOf(bond(0, 1, 2), bond(0, 2), bond(0, 3), bond(1, 4)))
                                                                                 to KnownMolecule("Vinyl", "Винил", "H₂C=CH•"),            // H₂C=CH•
        mol(listOf(c(0), o(1), h(2)), listOf(bond(0, 1, 2), bond(0, 2)))         to KnownMolecule("Formyl", "Формил", "H–C•=O"),           // H–C•=O
        mol(listOf(c(0), n(1)), listOf(bond(0, 1, 3)))                           to KnownMolecule("Cyano", "Циано", "•C≡N"),             // •C≡N

        // --- дикарбон C₂: все три порядка связи → одно имя «Дикарбон» ---
        mol(listOf(c(0), c(1)), listOf(bond(0, 1)))                              to dicarbon,   // •C–C•
        mol(listOf(c(0), c(1)), listOf(bond(0, 1, 2)))                           to dicarbon,   // C=C
        mol(listOf(c(0), c(1)), listOf(bond(0, 1, 3)))                           to dicarbon,   // •C≡C•
    ).let { entries ->
        // Две страховки авторинга, обе про ТИХИЕ ошибки. Пустой канон (молекула выше потолка перебора)
        // подписал бы своим именем ЛЮБУЮ крупную молекулу, потому что lookup("") нашёл бы эту запись.
        // Одинаковый канон у двух записей — associate молча оставил бы последнюю.
        val nameless = entries.filter { (graph, _) -> graph.canonical.isEmpty() }
        require(nameless.isEmpty()) {
            "Записи реестра без канона (тяжёлых атомов больше потолка): ${nameless.map { it.second.nameEn }}"
        }
        val byKey = entries.associate { (graph, known) -> graph.canonical to known }
        require(byKey.size == entries.size) {
            val collisions = entries.groupBy { it.first.canonical }.filterValues { it.size > 1 }
            "Записи реестра с одинаковым каноном: ${collisions.values.map { group -> group.map { it.second.nameEn } }}"
        }
        byKey
    }

    /**
     * Известная молекула по её каноническому ключу ([MoleculeGraph.canonical]), либо null — аноним.
     * Крупная молекула (canonical == "") даёт null сама собой: "" не ключ ни одной записи.
     */
    fun lookup(canonical: String): KnownMolecule? = byCanonical[canonical]
}

// Крошечные хелперы авторинга — чтобы список читался как «формулы», а не заборы из AtomNode/Bond.
private fun mol(nodes: List<AtomNode>, bonds: List<Bond>) = MoleculeGraph(nodes, bonds)
private fun h(id: Int) = AtomNode(id, Element.HYDROGEN)
private fun o(id: Int) = AtomNode(id, Element.OXYGEN_16)
private fun c(id: Int) = AtomNode(id, Element.CARBON_12)
private fun n(id: Int) = AtomNode(id, Element.NITROGEN_14)
private fun bond(a: Int, b: Int, order: Int = 1) = Bond(a, b, order)