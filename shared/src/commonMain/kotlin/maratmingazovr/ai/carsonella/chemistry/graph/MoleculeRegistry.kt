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

        // --- двухатомные ---
        val dihydrogen = H.attach(H); known(dihydrogen, "Dihydrogen", "Водород", "H–H", "Мы получили самый распространённый элемент Вселенной - 92% всех атомов. Например, наше Солнце состоит на 73% из водорода. Мы с вами состоим из водорода, которому 13.8 миллиарда лет! Он кажется самым простым, но скрывает массу парадоксов!", layout = pair())
        val dioxygen = O.attach(O, order = 2); known(dioxygen, "Dioxygen", "Кислород", "O=O", "Мы получили то, чем дышим: в воздухе кислорода 21%, и без него человек живёт минуты. Но первые два миллиарда лет его в воздухе почти не было - кислород выдышали бактерии, и для древней жизни он оказался страшным ядом! Он жадный до связей: и огонь, и ржавчина - это он. А два атома здесь держит двойная связь - первая, которую ты усилил сам.", layout = pair())
        val dinitrogen = N.attach(N, order = 3); known(dinitrogen, "Dinitrogen", "Азот", "N≡N", layout = pair())
        val hydroxyl = O.attach(H); known(hydroxyl, "Hydroxyl", "Гидроксил", "•OH", "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\". Мы будем прикреплять ее к другим молекулам и увидим как она полностью меняет их свойства. И у нас уже все готово, чтобы раздобыть ВОДУ! ", layout = pair())
        val dicarbonSingle = C.attach(C); known(dicarbonSingle, "Dicarbon", "Дикарбон", "•C–C•", description = "")
        val dicarbonDouble = C.attach(C, order = 2); known(dicarbonDouble, "Dicarbon", "Дикарбон", "C=C", description = "")
        val dicarbonTriple = C.attach(C, order = 3); known(dicarbonTriple, "Dicarbon", "Дикарбон", "•C≡C•", description = "")

        // --- малые неорганические / простые ---
        val water = hydroxyl.attach(H); known(water, "Water", "Вода", "H–O–H", "УРА! Мы получили самую известная молекулу на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии. Мы исследуем почему горячая вода замерзает быстрее холодной? Почему лед не тонет? И многое другое!", layout = at(0 to xy(0f, -0.3f), 1 to polar(180f - 52.25f), 2 to polar(52.25f)))
        val hydroperoxyl = hydroxyl.attach(O); known(hydroperoxyl, "Hydroperoxyl", "Гидропероксил", "H–O–O•")
        val hydrogenPeroxide = hydroperoxyl.attach(H); known(hydrogenPeroxide, "Hydrogen peroxide", "Перекись водорода", "H–O–O–H", "Это вода с лишним атомом кислорода - та самая перекись из аптечки. Она пенится на ранке потому, что тело мгновенно её разрушает: пузырьки, которые мы видим - это кислород. Кстати, врачи теперь не советуют лить перекись на раны: она убивает не только микробов, но и живые клетки.", layout = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f)))
        val trioxidane = hydroperoxyl.attach(hydroxyl); known(trioxidane, "Trioxidane", "Триоксидан", "H–O–O–O–H", "")
        val tetraoxidane = hydroperoxyl.attach(hydroperoxyl); known(tetraoxidane, "Tetraoxidane", "Тетраоксидан", "H–O–O–O–O–H", "Четыре кислорода подряд — предел, до которого такая цепочка вообще доживает. Собирается из двух радикалов HO₂• и существует только в криогенной заморозке, ниже −100 °C; интересен химикам, применений нет. При нагреве мгновенно распадается на перекись и кислород. Цепочек из пяти кислородов не наблюдали ни разу.")
        val imidogen = N.attach(H); known(imidogen, "Imidogen", "Имидоген", ":NH")
        val amino = imidogen.attach(H); known(amino, "Amino radical", "Аминорадикал", "•NH₂")
        val ammonia = amino.attach(H); known(ammonia, "Ammonia", "Аммиак", "NH₃")
        val carbonyl = C.attach(O, order = 2)                 // >C=O — группа, а не вещество: своей записи нет
        val carbonDioxide = carbonyl.attach(O, order = 2); known(carbonDioxide, "Carbon dioxide", "Углекислый газ", "O=C=O")
        val cyano = C.attach(N, order = 3); known(cyano, "Cyano", "Циано", "•C≡N")
        val hydrogenCyanide = cyano.attach(H); known(hydrogenCyanide, "Hydrogen cyanide", "Циановодород", "H–C≡N")

        // --- углеводороды ---
        val methylidyne = C.attach(H); known(methylidyne, "Methylidyne", "Метилидин", "•CH")
        val methylene = methylidyne.attach(H); known(methylene, "Methylene", "Метилен", ":CH₂")
        val methyl = methylene.attach(H); known(methyl, "Methyl", "Метил", "•CH₃")
        val methane = methyl.attach(H); known(methane, "Methane", "Метан", "CH₄")
        val ethynyl = methylidyne.attach(C, order = 3); known(ethynyl, "Ethynyl", "Этинил", "HC≡C•")
        val acetylene = ethynyl.attach(H); known(acetylene, "Acetylene", "Ацетилен", "HC≡CH")
        val vinyl = methylene.attach(methylidyne, order = 2); known(vinyl, "Vinyl", "Винил", "H₂C=CH•")
        val ethylene = vinyl.attach(H); known(ethylene, "Ethylene", "Этилен", "H₂C=CH₂")
        val ethyl = methyl.attach(methylene); known(ethyl, "Ethyl", "Этил", "CH₃–CH₂•")
        val ethane = ethyl.attach(H); known(ethane, "Ethane", "Этан", "CH₃–CH₃")

        // Бутаны C₄H₁₀
        val butane = ethyl.attach(ethyl); known(butane, "Butane", "Бутан", "CH₃–CH₂–CH₂–CH₃")
        val ethylidene = methyl.attach(methylidyne)           // CH₃–CH:
        val isopropyl = ethylidene.attach(methyl); known(isopropyl, "Isopropyl", "Изопропил", "(CH₃)₂CH•")
        val isobutane = isopropyl.attach(methyl); known(isobutane, "Isobutane", "Изобутан", "(CH₃)₃CH")

        // Бутены C₄H₈
        val butene1 = vinyl.attach(ethyl); known(butene1, "1-Butene", "Бутен-1", "H₂C=CH–CH₂–CH₃")
        val butene2 = ethylidene.attach(ethylidene, order = 2); known(butene2, "2-Butene", "Бутен-2", "CH₃–CH=CH–CH₃")
        val vinylidene = methylene.attach(C, order = 2)       // H₂C=C:
        val isopropenyl = vinylidene.attach(methyl); known(isopropenyl, "Isopropenyl", "Изопропенил", "H₂C=C(CH₃)•")
        val isobutylene = isopropenyl.attach(methyl); known(isobutylene, "Isobutylene", "Изобутилен", "H₂C=C(CH₃)₂")


        val ethanediyl = methylene.attach(methylene)          // •CH₂–CH₂•
        val trimethylene = ethanediyl.extend(methylene); known(trimethylene, "Trimethylene", "Триметилен", "•CH₂–CH₂–CH₂•")
        val cyclopropane = trimethylene.closeChain(); known(cyclopropane, "Cyclopropane", "Циклопропан", "(CH₂)₃", "")
        val oxyethyl = ethanediyl.extend(O)                   // •CH₂–CH₂–O•
        val oxirane = oxyethyl.closeChain(); known(oxirane, "Oxirane", "Оксиран", "(CH₂)₂O", "")

        val ethenediyl = methylidyne.attach(methylidyne, order = 2)   // •CH=CH•
        val butadienediyl = ethenediyl.extend(ethenediyl)             // •CH=CH–CH=CH•
        val hexatrienediyl = butadienediyl.extend(ethenediyl)         // •CH=CH–CH=CH–CH=CH•
        val benzene = hexatrienediyl.closeChain(); known(benzene, "Benzene", "Бензол", "(CH)₆", "")

        // --- кислородсодержащая органика ---
        val formyl = carbonyl.attach(H); known(formyl, "Formyl", "Формил", "H–C•=O")
        val formaldehyde = formyl.attach(H); known(formaldehyde, "Formaldehyde", "Формальдегид", "H₂C=O")
        val methanol = methyl.attach(hydroxyl); known(methanol, "Methanol", "Метанол", "CH₃–OH")
        val formicAcid = formyl.attach(hydroxyl); known(formicAcid, "Formic acid", "Муравьиная кислота", "H–C(=O)–OH")
        val ethanol = ethyl.attach(hydroxyl); known(ethanol, "Ethanol", "Этанол", "CH₃–CH₂–OH")
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



private fun MoleculeGraph.slot(): Int {
    val free = freeNodes
    require(free.size == 1) { "У $formula свободных узлов ${free.size} ($free) — укажи nodeId/otherNodeId явно" }
    return free.single()
} // Единственный узел со свободным слотом. Ноль или больше одного — точку присоединения надо указать явно.
private fun MoleculeGraph.attach(other: MoleculeGraph, order: Int = 1, nodeId: Int = slot(), otherNodeId: Int = other.slot()) = merge(other, nodeId, otherNodeId, order)


private fun MoleculeGraph.extend(link: MoleculeGraph, order: Int = 1) = attach(link, order, nodeId = freeNodes.max(), otherNodeId = link.freeNodes.min())


private fun MoleculeGraph.closeChain(): MoleculeGraph {
    val ends = freeNodes
    require(ends.size == 2) { "У $formula свободных концов ${ends.size} ($ends) — цепочка так не замкнётся" }
    return closeRing(ends.first(), ends.last())
} // Замкнуть цепочку саму на себя. Свободных концов ровно два, поэтому указывать нечего.

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