package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Курируемая запись реестра: что знаем об известной молекуле сверх её структуры.
data class KnownMolecule(
    val nameEn: String,
    val nameRu: String,
    val structuralFormula: String = "", // сжатая СТРУКТУРНАЯ формула (связность + радикальный слот •): CH₃–CH₃, H–O–O•.
    val description: String = "",
    val layout: Map<Int, Vec2D> = emptyMap(),
)

// Эталонная картинка молекулы: чем рисовать (граф) и где стоят узлы (раскладка). Нужна там, где молекулы ещё нет в мире — карточка уровня, журнал открытий.
data class MoleculePicture(
    val graph: MoleculeGraph,
    val offsets: Map<Int, Vec2D>,
)


// Реестр известных молекул
object MoleculeRegistry {

    // Дикарбон C₂ (экзотический бирадикал: пламя/кометы). В модели встречается на всех трёх порядках связи
    // (C–C/C=C/C≡C) как промежуток сборки; порядок связи модельно неточен (в природе ≈ двойной). Одно имя
    // на все три канона (порядок связи всё равно виден в структурном рендере).
    // structuralFormula пуст: у дикарбона он зависит от порядка связи (C–C/C=C/C≡C), а запись одна на три.
    private val dicarbon = KnownMolecule("Dicarbon", "Дикарбон", description = "экзотический бирадикал (пламя/кометы); порядок связи модельно неточен")

    // Радикалы, из которых собираются записи ниже. У них есть и своя запись в реестре — имя одно на оба
    // применения, так что скопировать граф дважды (и разойтись в нём) больше нельзя. Объявлять ДО entries.
    private val hydroxyl = O.attach(H)                            // •OH     — o0 h1
    private val hydroperoxyl = hydroxyl.attach(O)                 // H–O–O•  — o0 h1 o2
    private val amino = N.attach(H).attach(H)                     // •NH₂
    private val methylidyne = C.attach(H)                         // •CH   (3 слота)
    private val methylene = methylidyne.attach(H)                 // :CH₂  (2 слота)
    private val methyl = methylene.attach(H)                      // •CH₃
    private val ethyl = methyl.attach(methylene)                  // CH₃–CH₂•
    private val vinyl = methylene.attach(methylidyne, order = 2)  // H₂C=CH•
    private val ethynyl = methylidyne.attach(C, order = 3)        // H–C≡C•
    private val formyl = C.attach(O, order = 2).attach(H)         // H–C•=O
    private val cyano = C.attach(N, order = 3)                    // •C≡N

    // Своей записи в реестре у этих двух нет: они нужны только как ступенька к бутену-2 и изобутилену,
    // которые из радикалов выше не собрать — там углерод с двумя слотами посередине.
    private val ethylidene = methyl.attach(methylidyne)           // CH₃–CH: (2 слота)
    private val vinylidene = methylene.attach(C, order = 2)       // H₂C=C:  (2 слота)

    private val entries: List<Pair<MoleculeGraph, KnownMolecule>> = listOf(
        // --- двухатомные ---
        H.attach(H)
        to KnownMolecule("Dihydrogen", "Водород", "H–H", "Мы получили самый распространённый элемент Вселенной - 92% всех атомов. Например, наше Солнце состоит на 73% из водорода. Мы с вами состоим из водорода, которому 13.8 миллиарда лет! Он кажется самым простым, но скрывает массу парадоксов!", layout = pair()),

        O.attach(O, order = 2)
        to KnownMolecule("Dioxygen", "Кислород", "O=O", "Мы получили то, чем дышим: в воздухе кислорода 21%, и без него человек живёт минуты. Но первые два миллиарда лет его в воздухе почти не было - кислород выдышали бактерии, и для древней жизни он оказался страшным ядом! Он жадный до связей: и огонь, и ржавчина - это он. А два атома здесь держит двойная связь - первая, которую ты усилил сам.", layout = pair()),

        N.attach(N, order = 3)
        to KnownMolecule("Dinitrogen", "Азот", "N≡N", layout = pair()),

        hydroxyl
        to KnownMolecule("Hydroxyl", "Гидроксил", "•OH", "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\". Мы будем прикреплять ее к другим молекулам и увидим как она полностью меняет их свойства. И у нас уже все готово, чтобы раздобыть ВОДУ! ", layout = pair()),


        // --- малые неорганические / простые ---
        hydroxyl.attach(H)
        to KnownMolecule("Water", "Вода", "H–O–H", "УРА! Мы получили самую известная молекулу на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии. Мы исследуем почему горячая вода замерзает быстрее холодной? Почему лед не тонет? И многое другое!", layout = at(0 to xy(0f, -0.3f), 1 to polar(180f - 52.25f), 2 to polar(52.25f))),

        hydroperoxyl.attach(H)
        to KnownMolecule("Hydrogen peroxide", "Перекись водорода", "H–O–O–H", "Это вода с лишним атомом кислорода - та самая перекись из аптечки. Она пенится на ранке потому, что тело мгновенно её разрушает: пузырьки, которые мы видим - это кислород. Кстати, врачи теперь не советуют лить перекись на раны: она убивает не только микробов, но и живые клетки.", layout = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f))),

        hydroperoxyl.attach(hydroxyl)
        to KnownMolecule("Trioxidane", "Триоксидан", "H–O–O–O–H", ""),

        hydroperoxyl.attach(hydroperoxyl)
        to KnownMolecule("Tetraoxidane", "Тетраоксидан", "H–O–O–O–O–H", "Четыре кислорода подряд — предел, до которого такая цепочка вообще доживает. Собирается из двух радикалов HO₂• и существует только в криогенной заморозке, ниже −100 °C; интересен химикам, применений нет. При нагреве мгновенно распадается на перекись и кислород. Цепочек из пяти кислородов не наблюдали ни разу."),

        amino.attach(H)
        to KnownMolecule("Ammonia", "Аммиак", "NH₃"),

        C.attach(O, order = 2).attach(O, order = 2)
        to KnownMolecule("Carbon dioxide", "Углекислый газ", "O=C=O"),

        cyano.attach(H)
        to KnownMolecule("Hydrogen cyanide", "Циановодород", "H–C≡N"),

        // --- углеводороды ---
        methyl.attach(H)
        to KnownMolecule("Methane", "Метан", "CH₄"),

        ethynyl.attach(H)
        to KnownMolecule("Acetylene", "Ацетилен", "HC≡CH"),

        vinyl.attach(H)
        to KnownMolecule("Ethylene", "Этилен", "H₂C=CH₂"),

        ethyl.attach(H)
        to KnownMolecule("Ethane", "Этан", "CH₃–CH₃"),

        // Бутаны C₄H₁₀ — изомеры по СКЕЛЕТУ: цепочка против ветвления, кратностей связи тут нет вообще.
        // Видно прямо в сборке: два этила встык против трёх метилов на одном углероде.
        ethyl.attach(ethyl)
        to KnownMolecule("Butane", "Бутан", "CH₃–CH₂–CH₂–CH₃"),

        methylidyne.attach(methyl).attach(methyl).attach(methyl)
        to KnownMolecule("Isobutane", "Изобутан", "(CH₃)₃CH"),

        // Бутены C₄H₈
        vinyl.attach(ethyl)
        to KnownMolecule("1-Butene", "Бутен-1", "H₂C=CH–CH₂–CH₃"),

        ethylidene.attach(ethylidene, order = 2)
        to KnownMolecule("2-Butene", "Бутен-2", "CH₃–CH=CH–CH₃"),

        vinylidene.attach(methyl).attach(methyl)
        to KnownMolecule("Isobutylene", "Изобутилен", "H₂C=C(CH₃)₂"),

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
        formyl.attach(H)              to KnownMolecule("Formaldehyde", "Формальдегид", "H₂C=O"),
        methyl.attach(hydroxyl)       to KnownMolecule("Methanol", "Метанол", "CH₃–OH"),
        formyl.attach(hydroxyl)       to KnownMolecule("Formic acid", "Муравьиная кислота", "H–C(=O)–OH"),
        ethyl.attach(hydroxyl)        to KnownMolecule("Ethanol", "Этанол", "CH₃–CH₂–OH"),

        // --- радикалы (есть свободный валентный слот) ---
        methyl                        to KnownMolecule("Methyl", "Метил", "•CH₃"),
        amino                         to KnownMolecule("Amino radical", "Аминорадикал", "•NH₂"),
        hydroperoxyl                  to KnownMolecule("Hydroperoxyl", "Гидропероксил", "H–O–O•"),
        ethyl                         to KnownMolecule("Ethyl", "Этил", "CH₃–CH₂•"),
        methylidyne                   to KnownMolecule("Methylidyne", "Метилидин", "•CH"),
        methylene                     to KnownMolecule("Methylene", "Метилен", ":CH₂"),
        ethynyl                       to KnownMolecule("Ethynyl", "Этинил", "HC≡C•"),
        vinyl                         to KnownMolecule("Vinyl", "Винил", "H₂C=CH•"),
        formyl                        to KnownMolecule("Formyl", "Формил", "H–C•=O"),
        cyano                         to KnownMolecule("Cyano", "Циано", "•C≡N"),

        // --- дикарбон C₂: все три порядка связи → одно имя «Дикарбон» ---
        C.attach(C)                   to dicarbon,   // •C–C•
        C.attach(C, order = 2)        to dicarbon,   // C=C
        C.attach(C, order = 3)        to dicarbon,   // •C≡C•
    )

    private val byCanonical: Map<String, KnownMolecule> = run {
        // Три страховки авторинга, все про ТИХИЕ ошибки. Пустой канон (молекула выше потолка перебора)
        // подписал бы своим именем ЛЮБУЮ крупную молекулу, потому что lookup("") нашёл бы эту запись.
        // Одинаковый канон у двух записей — associate молча оставил бы последнюю. Раскладка не по узлам
        // графа — картинка молча вышла бы кривой или без атома.
        val nameless = entries.filter { (graph, _) -> graph.canonical.isEmpty() }
        require(nameless.isEmpty()) {
            "Записи реестра без канона (тяжёлых атомов больше потолка): ${nameless.map { it.second.nameEn }}"
        }
        val byKey = entries.associate { (graph, known) -> graph.canonical to known }
        require(byKey.size == entries.size) {
            val collisions = entries.groupBy { it.first.canonical }.filterValues { it.size > 1 }
            "Записи реестра с одинаковым каноном: ${collisions.values.map { group -> group.map { it.second.nameEn } }}"
        }
        val brokenLayout = entries.filter { (graph, known) ->
            known.layout.isNotEmpty() && known.layout.keys != graph.nodes.mapTo(mutableSetOf()) { it.localId }
        }
        require(brokenLayout.isEmpty()) {
            "Раскладка не совпадает с узлами графа: ${brokenLayout.map { it.second.nameEn }}"
        }
        byKey
    }

    // Второй вход в тот же реестр — по nameEn. Нужен уровням: цель задаётся именем («Water»), а не
    // каноном (канон вычисляется из графа, руками его не напишешь). distinct() — из-за дикарбона:
    // одна запись на три канона.
    private val byNameEn: Map<String, KnownMolecule> = byCanonical.values.distinct().associateBy { it.nameEn }

    /**
     * Известная молекула по её каноническому ключу ([MoleculeGraph.canonical]), либо null — аноним.
     * Крупная молекула (canonical == "") даёт null сама собой: "" не ключ ни одной записи.
     */
    fun lookup(canonical: String): KnownMolecule? = byCanonical[canonical]

    /** Запись реестра по английскому имени — вход для авторинга уровней. */
    fun byName(nameEn: String): KnownMolecule? = byNameEn[nameEn]

    /** Все записи: узлы карты открытий — это реестр, рисовать её руками нечего. */
    val all: List<KnownMolecule> get() = byNameEn.values.toList()

    /**
     * Сколько действий игрока нужно от отдельных атомов. Каждое действие добавляет ровно одну единицу
     * кратности: новая связь +1, усиление +1, замыкание кольца +1 — поэтому сумма кратностей и есть
     * длина пути. Ею карта открытий раскладывает записи по слоям: •OH (1) раньше H₂O (2), а O₂ (2)
     * после первой связи, потому что двойную ещё надо усилить.
     */
    fun buildSteps(nameEn: String): Int =
        graphByNameEn[nameEn]?.bonds?.sumOf { it.order } ?: 0

    /** Из каких атомов собран реестр — нулевой слой карты, до всяких молекул. */
    val atomsInUse: List<Element> get() = graphByNameEn.values
        .flatMap { graph -> graph.nodes.map { it.isotope } }
        .distinct()
        .sortedBy { it.details.p }

    // Граф по имени. У дикарбона три графа на одно имя — остаётся последний (C≡C); для картинки сойдёт.
    private val graphByNameEn: Map<String, MoleculeGraph> = entries.associate { (graph, known) -> known.nameEn to graph }

    /**
     * Эталонная картинка по имени: граф + раскладка. null, если раскладка ещё не нарисована —
     * тогда рисующему нечего показывать, и он падает на текстовую структурную формулу.
     */
    fun picture(nameEn: String): MoleculePicture? {
        val known = byNameEn[nameEn] ?: return null
        if (known.layout.isEmpty()) return null
        val graph = graphByNameEn[nameEn] ?: return null
        return MoleculePicture(graph, known.layout)
    }
}

// Крошечные хелперы авторинга — чтобы список читался как «формулы», а не заборы из AtomNode/Bond.
private fun mol(nodes: List<AtomNode>, bonds: List<Bond>) = MoleculeGraph(nodes, bonds)

// Атом как фрагмент: одноузловой граф законен — узел один, связей нет, связность держится.
private val H = mol(listOf(h(0)), emptyList())
private val O = mol(listOf(o(0)), emptyList())
private val C = mol(listOf(c(0)), emptyList())
private val N = mol(listOf(n(0)), emptyList())

// Единственный узел со свободным слотом. Ноль или больше одного — точку присоединения надо указать явно.
private fun MoleculeGraph.slot(): Int {
    val free = nodes.map { it.localId }.filter { freeValence(it) > 0 }
    require(free.size == 1) { "У $formula свободных узлов ${free.size} ($free) — укажи at/otherAt явно" }
    return free.single()
}

// Присоединить фрагмент к уже описанной молекуле: •OH + H = H₂O. Номера узлов ЭТОГО графа сохраняются,
// у присоединяемого сдвигаются (mergeOffset) — поэтому раскладку писать по узлам РЕЗУЛЬТАТА.
private fun MoleculeGraph.attach(other: MoleculeGraph, order: Int = 1, at: Int = slot(), otherAt: Int = other.slot()) =
    merge(other, at, otherAt, order)

// Хелперы раскладки. Единицы — доли длины связи, ось y вниз (как на экране).
private fun at(vararg offsets: Pair<Int, Vec2D>): Map<Int, Vec2D> = mapOf(*offsets)
private fun xy(x: Float, y: Float) = Vec2D(x, y)
private fun polar(angleDeg: Float, distance: Float = 1f): Vec2D {
    val rad = angleDeg * PI.toFloat() / 180f
    return Vec2D(cos(rad) * distance, sin(rad) * distance)
}
/** Двухатомная молекула: узлы 0 и 1 по горизонтали. */
private fun pair(): Map<Int, Vec2D> = at(0 to xy(-0.5f, 0f), 1 to xy(0.5f, 0f))
private fun h(id: Int) = AtomNode(id, Element.HYDROGEN)
private fun o(id: Int) = AtomNode(id, Element.OXYGEN_16)
private fun c(id: Int) = AtomNode(id, Element.CARBON_12)
private fun n(id: Int) = AtomNode(id, Element.NITROGEN_14)
private fun bond(a: Int, b: Int, order: Int = 1) = Bond(a, b, order)