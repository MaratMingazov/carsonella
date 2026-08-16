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

private val H = MoleculeGraph(listOf(AtomNode(0, Element.HYDROGEN)), emptyList())
private val O = MoleculeGraph(listOf(AtomNode(0, Element.OXYGEN_16)), emptyList())
private val C = MoleculeGraph(listOf(AtomNode(0, Element.CARBON_12)), emptyList())
private val N = MoleculeGraph(listOf(AtomNode(0, Element.NITROGEN_14)), emptyList())

// Реестр известных молекул
object MoleculeRegistry {

    private val entries = registry {
        // Фрагменты сборки. Имя нужно только тем, из кого собирают что-то ниже; у большинства есть и
        // своя запись в реестре — тогда known() ниже ссылается на это же имя.
        val hydroxyl = O.attach(H)                            // •OH
        val hydroperoxyl = hydroxyl.attach(O)                 // H–O–O•
        val amino = N.attach(H).attach(H)                     // •NH₂
        val methylidyne = C.attach(H)                         // •CH
        val methylene = methylidyne.attach(H)                 // :CH₂
        val methyl = methylene.attach(H)                      // •CH₃
        val ethyl = methyl.attach(methylene)                  // CH₃–CH₂•
        val vinyl = methylene.attach(methylidyne, order = 2)  // H₂C=CH•
        val ethynyl = methylidyne.attach(C, order = 3)        // H–C≡C•
        val formyl = C.attach(O, order = 2).attach(H)         // H–C•=O
        val cyano = C.attach(N, order = 3)                    // •C≡N
        val ethylidene = methyl.attach(methylidyne)           // CH₃–CH: (2 слота), своей записи нет
        val vinylidene = methylene.attach(C, order = 2)       // H₂C=C:  (2 слота), своей записи нет

        // --- двухатомные ---
        known(H.attach(H), "Dihydrogen", "Водород", "H–H", "Мы получили самый распространённый элемент Вселенной - 92% всех атомов. Например, наше Солнце состоит на 73% из водорода. Мы с вами состоим из водорода, которому 13.8 миллиарда лет! Он кажется самым простым, но скрывает массу парадоксов!", layout = pair())
        known(O.attach(O, order = 2), "Dioxygen", "Кислород", "O=O", "Мы получили то, чем дышим: в воздухе кислорода 21%, и без него человек живёт минуты. Но первые два миллиарда лет его в воздухе почти не было - кислород выдышали бактерии, и для древней жизни он оказался страшным ядом! Он жадный до связей: и огонь, и ржавчина - это он. А два атома здесь держит двойная связь - первая, которую ты усилил сам.", layout = pair())
        known(N.attach(N, order = 3), "Dinitrogen", "Азот", "N≡N", layout = pair())
        known(hydroxyl, "Hydroxyl", "Гидроксил", "•OH", "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\". Мы будем прикреплять ее к другим молекулам и увидим как она полностью меняет их свойства. И у нас уже все готово, чтобы раздобыть ВОДУ! ", layout = pair())


        // --- малые неорганические / простые ---
        known(hydroxyl.attach(H), "Water", "Вода", "H–O–H", "УРА! Мы получили самую известная молекулу на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии. Мы исследуем почему горячая вода замерзает быстрее холодной? Почему лед не тонет? И многое другое!", layout = at(0 to xy(0f, -0.3f), 1 to polar(180f - 52.25f), 2 to polar(52.25f)))
        known(hydroperoxyl.attach(H), "Hydrogen peroxide", "Перекись водорода", "H–O–O–H", "Это вода с лишним атомом кислорода - та самая перекись из аптечки. Она пенится на ранке потому, что тело мгновенно её разрушает: пузырьки, которые мы видим - это кислород. Кстати, врачи теперь не советуют лить перекись на раны: она убивает не только микробов, но и живые клетки.", layout = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f)))
        known(hydroperoxyl.attach(hydroxyl), "Trioxidane", "Триоксидан", "H–O–O–O–H", "")
        known(hydroperoxyl.attach(hydroperoxyl), "Tetraoxidane", "Тетраоксидан", "H–O–O–O–O–H", "Четыре кислорода подряд — предел, до которого такая цепочка вообще доживает. Собирается из двух радикалов HO₂• и существует только в криогенной заморозке, ниже −100 °C; интересен химикам, применений нет. При нагреве мгновенно распадается на перекись и кислород. Цепочек из пяти кислородов не наблюдали ни разу.")
        known(amino.attach(H), "Ammonia", "Аммиак", "NH₃")
        known(C.attach(O, order = 2).attach(O, order = 2), "Carbon dioxide", "Углекислый газ", "O=C=O")
        known(cyano.attach(H), "Hydrogen cyanide", "Циановодород", "H–C≡N")

        // --- углеводороды ---
        known(methyl.attach(H), "Methane", "Метан", "CH₄")
        known(ethynyl.attach(H), "Acetylene", "Ацетилен", "HC≡CH")
        known(vinyl.attach(H), "Ethylene", "Этилен", "H₂C=CH₂")
        known(ethyl.attach(H), "Ethane", "Этан", "CH₃–CH₃")

        // Бутаны C₄H₁₀ — изомеры по СКЕЛЕТУ: цепочка против ветвления, кратностей связи тут нет вообще.
        // Видно прямо в сборке: два этила встык против трёх метилов на одном углероде.
        known(ethyl.attach(ethyl), "Butane", "Бутан", "CH₃–CH₂–CH₂–CH₃")

        known(methylidyne.attach(methyl).attach(methyl).attach(methyl), "Isobutane", "Изобутан", "(CH₃)₃CH")

        // Бутены C₄H₈
        known(vinyl.attach(ethyl), "1-Butene", "Бутен-1", "H₂C=CH–CH₂–CH₃")

        known(ethylidene.attach(ethylidene, order = 2), "2-Butene", "Бутен-2", "CH₃–CH=CH–CH₃")

        known(vinylidene.attach(methyl).attach(methyl), "Isobutylene", "Изобутилен", "H₂C=C(CH₃)₂")

        // --- кольца ---
        // Первые циклы, которые игрок замыкает сам (RingClosure). Оба трёхчленные и потому напряжённые —
        // модель напряжение пока не считает, так что фотон при замыкании выходит завышенным.
        known(ring(methylene, methylene, methylene), "Cyclopropane", "Циклопропан", "(CH₂)₃",
            "Три углерода в треугольнике — самое напряжённое кольцо органики: связи выгнуты почти на 50° " +
            "от угла, который углерод любит, и потому кольцо охотно раскрывается. Газ; до 1980-х им давали " +
            "быстрый наркоз, перестали из-за взрывоопасности. Показывает, что вещество задаёт не только состав, " +
            "но и скелет: у пропилена та же формула C₃H₆, а свойства другие.")
        known(ring(methylene, methylene, O), "Oxirane", "Оксиран", "(CH₂)₂O",
            "Он же этиленоксид: кислород, вставленный в связь C–C, — треугольник из двух углеродов и кислорода. " +
            "Напряжение делает его жадным, кольцо раскрывается почти обо что угодно, поэтому это один из самых " +
            "массовых промышленных реагентов: из него делают антифриз и полиэфирное волокно, им же стерилизуют " +
            "медицинский инструмент. Токсичен и канцерогенен. Найден и в межзвёздных облаках.")

        // Бензол по КЕКУЛЕ: чередование одинарных и двойных. В реальности связи делокализованы и все
        // одинаковые («полторы»), но кратности в модели целые — а игрок собирает ровно это чередование:
        // замкнул кольцо и усилил каждую вторую связь. Оба варианта чередования — один и тот же граф
        // с точностью до поворота, поэтому канон у них общий и записи хватает одной.
        // Углероды кольца идут через один (0, 2, 4, …): между ними в нумерации стоят их водороды.
        known(ring(methylidyne, methylidyne, methylidyne, methylidyne, methylidyne, methylidyne)
            .strengthenBond(0, 2).strengthenBond(4, 6).strengthenBond(8, 10), "Benzene", "Бензол", "(CH)₆",
            "Шесть углеродов в правильном шестиугольнике — и электроны двойных связей размазаны по всему кольцу, " +
            "поэтому в реальности все шесть связей одинаковые, «полуторные», а кольцо необычно стойкое. " +
            "Выделил Фарадей в 1825-м, кольцевую структуру угадал Кекуле. На нём стоит огромная часть органики: " +
            "краски, лекарства, пластики; сам канцероген, поэтому его долю в бензине ограничивают. " +
            "Найден в межзвёздных облаках и в атмосфере Титана.")

        // --- кислородсодержащая органика ---
        known(formyl.attach(H), "Formaldehyde", "Формальдегид", "H₂C=O")
        known(methyl.attach(hydroxyl), "Methanol", "Метанол", "CH₃–OH")
        known(formyl.attach(hydroxyl), "Formic acid", "Муравьиная кислота", "H–C(=O)–OH")
        known(ethyl.attach(hydroxyl), "Ethanol", "Этанол", "CH₃–CH₂–OH")

        // --- радикалы (есть свободный валентный слот) ---
        known(methyl, "Methyl", "Метил", "•CH₃")
        known(amino, "Amino radical", "Аминорадикал", "•NH₂")
        known(hydroperoxyl, "Hydroperoxyl", "Гидропероксил", "H–O–O•")
        known(ethyl, "Ethyl", "Этил", "CH₃–CH₂•")
        known(methylidyne, "Methylidyne", "Метилидин", "•CH")
        known(methylene, "Methylene", "Метилен", ":CH₂")
        known(ethynyl, "Ethynyl", "Этинил", "HC≡C•")
        known(vinyl, "Vinyl", "Винил", "H₂C=CH•")
        known(formyl, "Formyl", "Формил", "H–C•=O")
        known(cyano, "Cyano", "Циано", "•C≡N")

        // --- дикарбон C₂: все три порядка связи → одно имя «Дикарбон» ---
        known(C.attach(C), "Dicarbon", "Дикарбон", description = "")   // •C–C•
        known(C.attach(C, order = 2), "Dicarbon", "Дикарбон", description = "")   // C=C
        known(C.attach(C, order = 3), "Dicarbon", "Дикарбон", description = "")   // •C≡C•
    }
    private val byCanonical: Map<String, KnownMolecule> = run {
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
    private val byNameEn: Map<String, KnownMolecule> = byCanonical.values.distinct().associateBy { it.nameEn }

    val all: List<KnownMolecule> get() = byNameEn.values.toList() // Все записи: узлы карты открытий — это реестр, рисовать её руками нечего.

    fun lookup(canonical: String): KnownMolecule? = byCanonical[canonical] // Известная молекула по её каноническому ключу
    fun byName(nameEn: String): KnownMolecule? = byNameEn[nameEn] //Запись реестра по английскому имени — вход для авторинга уровней.


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

// Кольцо из фрагментов: цепочка, замкнутая последним звеном на первое. Каждое звено цепляется к
// ПРЕДЫДУЩЕМУ (не к первому), так что номера узлов ведёт хелпер, а не автор записи.
private fun ring(first: MoleculeGraph, vararg rest: MoleculeGraph): MoleculeGraph {
    var graph = first
    var tail = first.slot()                  // свободный слот последнего присоединённого звена
    for (link in rest) {
        val offset = graph.mergeOffset()     // на столько attach сдвинет узлы звена
        graph = graph.attach(link, at = tail)
        tail = link.slot() + offset
    }
    return graph.closeRing(first.slot(), tail)
}

// Сборщик реестра. known() кладёт пару «граф → запись» и ВОЗВРАЩАЕТ граф, чтобы из него собиралась
// следующая молекула; порядок вызовов = порядок записей.
private class RegistryBuilder {
    val entries = mutableListOf<Pair<MoleculeGraph, KnownMolecule>>()
    fun known(
        graph: MoleculeGraph,
        nameEn: String,
        nameRu: String,
        structuralFormula: String = "",
        description: String = "",
        layout: Map<Int, Vec2D> = emptyMap(),
    ): MoleculeGraph {
        entries += graph to KnownMolecule(nameEn, nameRu, structuralFormula, description, layout)
        return graph
    }
}

private fun registry(build: RegistryBuilder.() -> Unit): List<Pair<MoleculeGraph, KnownMolecule>> =
    RegistryBuilder().apply(build).entries.toList()

// Хелперы раскладки. Единицы — доли длины связи, ось y вниз (как на экране).
private fun at(vararg offsets: Pair<Int, Vec2D>): Map<Int, Vec2D> = mapOf(*offsets)
private fun xy(x: Float, y: Float) = Vec2D(x, y)
private fun polar(angleDeg: Float, distance: Float = 1f): Vec2D {
    val rad = angleDeg * PI.toFloat() / 180f
    return Vec2D(cos(rad) * distance, sin(rad) * distance)
}
/** Двухатомная молекула: узлы 0 и 1 по горизонтали. */
private fun pair(): Map<Int, Vec2D> = at(0 to xy(-0.5f, 0f), 1 to xy(0.5f, 0f))