package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Lang
import maratmingazovr.ai.carsonella.Prose
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Курируемая запись реестра: что знаем об известной молекуле сверх её структуры.
data class KnownMolecule(
    val id: MoleculeId,  // Ключ и он же имена на оба языка — см. MoleculeId.
    val structuralFormula: String = "", // сжатая СТРУКТУРНАЯ формула (связность + радикальный слот •): CH₃–CH₃, H–O–O•.
    val description: Prose? = null,
    val layout: Map<Int, Vec2D> = emptyMap(),
) {
    fun name(lang: Lang): String = id.name(lang)
}

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
        val dihydrogen = H.attach(H); known(dihydrogen, MoleculeId.DIHYDROGEN, "H–H", layout = pair(),
            description = Prose(
                ru = "Мы получили самый распространённый элемент Вселенной - 92% всех атомов. Например, наше Солнце состоит на 73% из водорода. Мы с вами состоим из водорода, которому 13.8 миллиарда лет! Он кажется самым простым, но скрывает массу парадоксов!",
                en = "We have made the most common element in the Universe - 92% of all atoms. Our Sun, for one, is 73% hydrogen. The hydrogen you and I are made of is 13.8 billion years old! It looks like the simplest thing there is, and yet it hides a pile of paradoxes!",
            ))
        val dioxygen = O.attach(O, order = 2); known(dioxygen, MoleculeId.DIOXYGEN, "O=O", layout = pair(),
            description = Prose(
                ru = "Мы получили то, чем дышим: в воздухе кислорода 21%, и без него человек живёт минуты. Но первые два миллиарда лет его в воздухе почти не было - кислород выдышали бактерии, и для древней жизни он оказался страшным ядом! Он жадный до связей: и огонь, и ржавчина - это он. А два атома здесь держит двойная связь - первая, которую ты усилил сам.",
                en = "We have made the stuff we breathe: air is 21% oxygen, and without it a person lasts minutes. But for the first two billion years there was almost none of it in the air - bacteria breathed it out, and to ancient life it turned out to be a terrible poison! It is greedy for bonds: fire is oxygen, and so is rust. And the two atoms here are held by a double bond - the first one you strengthened yourself.",
            ))
        val dinitrogen = N.attach(N, order = 3); known(dinitrogen, MoleculeId.DINITROGEN, "N≡N", layout = pair())
        val hydroxyl = O.attach(H); known(hydroxyl, MoleculeId.HYDROXYL, "•OH", layout = pair(),
            description = Prose(
                ru = "Гидроксил - маленькая молекула из одного атома водорода и кислорода. Сама по себе живет меньше секунды, зато работает как \"конструктор\". Мы будем прикреплять ее к другим молекулам и увидим как она полностью меняет их свойства. И у нас уже все готово, чтобы раздобыть ВОДУ! ",
                en = "Hydroxyl is a tiny molecule of one hydrogen atom and one oxygen atom. On its own it lives less than a second, but it works like a building block. We will be attaching it to other molecules and watching it change their properties completely. And we already have everything we need to get WATER! ",
            ))
        val dicarbonSingle = C.attach(C); known(dicarbonSingle, MoleculeId.DICARBON, "•C–C•")
        val dicarbonDouble = C.attach(C, order = 2); known(dicarbonDouble, MoleculeId.DICARBON, "C=C")
        val dicarbonTriple = C.attach(C, order = 3); known(dicarbonTriple, MoleculeId.DICARBON, "•C≡C•")

        // --- малые неорганические / простые ---
        val water = hydroxyl.attach(H); known(water, MoleculeId.WATER, "H–O–H", layout = at(0 to xy(0f, -0.3f), 1 to polar(180f - 52.25f), 2 to polar(52.25f)),
            description = Prose(
                ru = "УРА! Мы получили самую известная молекулу на свете и главное вещество жизни. Мы сами примерно на 60% состоим из воды. Благодаря необычному строению своей молекулы, она нарушает почти все правила физики и химии. Мы исследуем почему горячая вода замерзает быстрее холодной? Почему лед не тонет? И многое другое!",
                en = "HOORAY! We have made the most famous molecule in the world and the main substance of life. We ourselves are about 60% water. Thanks to the unusual shape of its molecule it breaks almost every rule of physics and chemistry. We will look into why hot water freezes faster than cold water, why ice does not sink, and much more!",
            ))
        val hydroperoxyl = hydroxyl.attach(O); known(hydroperoxyl, MoleculeId.HYDROPEROXYL, "H–O–O•")
        val hydrogenPeroxide = hydroperoxyl.attach(H); known(hydrogenPeroxide, MoleculeId.HYDROGEN_PEROXIDE, "H–O–O–H", layout = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f)),
            description = Prose(
                ru = "Это вода с лишним атомом кислорода - та самая перекись из аптечки. Она пенится на ранке потому, что тело мгновенно её разрушает: пузырьки, которые мы видим - это кислород. Кстати, врачи теперь не советуют лить перекись на раны: она убивает не только микробов, но и живые клетки.",
                en = "This is water with one extra oxygen atom - the very peroxide from the medicine cabinet. It foams on a cut because the body destroys it instantly: the bubbles we see are oxygen. By the way, doctors no longer advise pouring peroxide on wounds - it kills not only germs but living cells too.",
            ))
        val trioxidane = hydroperoxyl.attach(hydroxyl); known(trioxidane, MoleculeId.TRIOXIDANE, "H–O–O–O–H")
        val tetraoxidane = hydroperoxyl.attach(hydroperoxyl); known(tetraoxidane, MoleculeId.TETRAOXIDANE, "H–O–O–O–O–H",
            description = Prose(
                ru = "Четыре кислорода подряд — предел, до которого такая цепочка вообще доживает. Собирается из двух радикалов HO₂• и существует только в криогенной заморозке, ниже −100 °C; интересен химикам, применений нет. При нагреве мгновенно распадается на перекись и кислород. Цепочек из пяти кислородов не наблюдали ни разу.",
                en = "Four oxygens in a row is the limit such a chain survives at all. It comes together from two HO₂• radicals and exists only under cryogenic freezing, below −100 °C; chemists find it interesting, it has no uses. Heated, it falls apart instantly into peroxide and oxygen. A chain of five oxygens has never been observed even once.",
            ))
        val imidogen = N.attach(H); known(imidogen, MoleculeId.IMIDOGEN, ":NH")
        val amino = imidogen.attach(H); known(amino, MoleculeId.AMINO_RADICAL, "•NH₂")
        val ammonia = amino.attach(H); known(ammonia, MoleculeId.AMMONIA, "NH₃")
        val carbonyl = C.attach(O, order = 2)                 // >C=O — группа, а не вещество: своей записи нет
        val carbonDioxide = carbonyl.attach(O, order = 2); known(carbonDioxide, MoleculeId.CARBON_DIOXIDE, "O=C=O")
        val cyano = C.attach(N, order = 3); known(cyano, MoleculeId.CYANO, "•C≡N")
        val hydrogenCyanide = cyano.attach(H); known(hydrogenCyanide, MoleculeId.HYDROGEN_CYANIDE, "H–C≡N")

        // --- углеводороды ---
        val methylidyne = C.attach(H); known(methylidyne, MoleculeId.METHYLIDYNE, "•CH")
        val methylene = methylidyne.attach(H); known(methylene, MoleculeId.METHYLENE, ":CH₂")
        val methyl = methylene.attach(H); known(methyl, MoleculeId.METHYL, "•CH₃")
        val methane = methyl.attach(H); known(methane, MoleculeId.METHANE, "CH₄")
        val ethynyl = methylidyne.attach(C, order = 3); known(ethynyl, MoleculeId.ETHYNYL, "HC≡C•")
        val acetylene = ethynyl.attach(H); known(acetylene, MoleculeId.ACETYLENE, "HC≡CH")
        val vinyl = methylene.attach(methylidyne, order = 2); known(vinyl, MoleculeId.VINYL, "H₂C=CH•")
        val ethylene = vinyl.attach(H); known(ethylene, MoleculeId.ETHYLENE, "H₂C=CH₂")
        val ethyl = methyl.attach(methylene); known(ethyl, MoleculeId.ETHYL, "CH₃–CH₂•")
        val ethane = ethyl.attach(H); known(ethane, MoleculeId.ETHANE, "CH₃–CH₃")

        // Бутаны C₄H₁₀
        val butane = ethyl.attach(ethyl); known(butane, MoleculeId.BUTANE, "CH₃–CH₂–CH₂–CH₃")
        val ethylidene = methyl.attach(methylidyne)           // CH₃–CH:
        val isopropyl = ethylidene.attach(methyl); known(isopropyl, MoleculeId.ISOPROPYL, "(CH₃)₂CH•")
        val isobutane = isopropyl.attach(methyl); known(isobutane, MoleculeId.ISOBUTANE, "(CH₃)₃CH")

        // Бутены C₄H₈
        val butene1 = vinyl.attach(ethyl); known(butene1, MoleculeId.BUTENE_1, "H₂C=CH–CH₂–CH₃")
        val butene2 = ethylidene.attach(ethylidene, order = 2); known(butene2, MoleculeId.BUTENE_2, "CH₃–CH=CH–CH₃")
        val vinylidene = methylene.attach(C, order = 2)       // H₂C=C:
        val isopropenyl = vinylidene.attach(methyl); known(isopropenyl, MoleculeId.ISOPROPENYL, "H₂C=C(CH₃)•")
        val isobutylene = isopropenyl.attach(methyl); known(isobutylene, MoleculeId.ISOBUTYLENE, "H₂C=C(CH₃)₂")


        val ethanediyl = methylene.attach(methylene)          // •CH₂–CH₂•
        val trimethylene = ethanediyl.extend(methylene); known(trimethylene, MoleculeId.TRIMETHYLENE, "•CH₂–CH₂–CH₂•")
        val cyclopropane = trimethylene.closeChain(); known(cyclopropane, MoleculeId.CYCLOPROPANE, "(CH₂)₃")
        val oxyethyl = ethanediyl.extend(O)                   // •CH₂–CH₂–O•
        val oxirane = oxyethyl.closeChain(); known(oxirane, MoleculeId.OXIRANE, "(CH₂)₂O")

        val ethenediyl = methylidyne.attach(methylidyne, order = 2)   // •CH=CH•
        val butadienediyl = ethenediyl.extend(ethenediyl)             // •CH=CH–CH=CH•
        val hexatrienediyl = butadienediyl.extend(ethenediyl)         // •CH=CH–CH=CH–CH=CH•
        val benzene = hexatrienediyl.closeChain(); known(benzene, MoleculeId.BENZENE, "(CH)₆")

        // --- кислородсодержащая органика ---
        val formyl = carbonyl.attach(H); known(formyl, MoleculeId.FORMYL, "H–C•=O")
        val formaldehyde = formyl.attach(H); known(formaldehyde, MoleculeId.FORMALDEHYDE, "H₂C=O")
        val methanol = methyl.attach(hydroxyl); known(methanol, MoleculeId.METHANOL, "CH₃–OH")
        val formicAcid = formyl.attach(hydroxyl); known(formicAcid, MoleculeId.FORMIC_ACID, "H–C(=O)–OH")
        val ethanol = ethyl.attach(hydroxyl); known(ethanol, MoleculeId.ETHANOL, "CH₃–CH₂–OH")
    }

    private val byCanonical: Map<String, KnownMolecule> = run {
        val nameless = entries.filter { (graph, _) -> graph.canonical.isEmpty() }
        require(nameless.isEmpty()) {
            "Записи реестра без канона (тяжёлых атомов больше потолка): ${nameless.map { it.second.id }}"
        }
        val byKey = entries.associate { (graph, known) -> graph.canonical to known }
        require(byKey.size == entries.size) {
            val collisions = entries.groupBy { it.first.canonical }.filterValues { it.size > 1 }
            "Записи реестра с одинаковым каноном: ${collisions.values.map { group -> group.map { it.second.id } }}"
        }
        val brokenLayout = entries.filter { (graph, known) ->
            known.layout.isNotEmpty() && known.layout.keys != graph.nodes.mapTo(mutableSetOf()) { it.localId }
        }
        require(brokenLayout.isEmpty()) {
            "Раскладка не совпадает с узлами графа: ${brokenLayout.map { it.second.id }}"
        }
        val uncovered = MoleculeId.entries - byKey.values.map { it.id }.toSet()
        require(uncovered.isEmpty()) {
            "У ключей нет записи в реестре: $uncovered — byId() обещает не возвращать null, значит покрыть надо все"
        }
        byKey
    }
    private val knownById: Map<MoleculeId, KnownMolecule> = byCanonical.values.distinct().associateBy { it.id }

    val all: List<KnownMolecule> get() = knownById.values.toList() // Все записи: узлы карты открытий — это реестр, рисовать её руками нечего.

    fun lookup(canonical: String): KnownMolecule? = byCanonical[canonical] // Известная молекула по её каноническому ключу
    fun byId(id: MoleculeId): KnownMolecule = knownById.getValue(id) // Запись реестра по ключу. Не null: покрытие всех ключей проверено при инициализации.

    fun buildSteps(id: MoleculeId): Int = graphById.getValue(id).bonds.sumOf { it.order }

    /** Из каких атомов собран реестр — нулевой слой карты, до всяких молекул. */
    val atomsInUse: List<Element> get() = graphById.values
        .flatMap { graph -> graph.nodes.map { it.isotope } }
        .distinct()
        .sortedBy { it.details.p }

    // Граф по ключу. У дикарбона три графа на один ключ — остаётся последний (C≡C); для картинки сойдёт.
    private val graphById: Map<MoleculeId, MoleculeGraph> = entries.associate { (graph, known) -> known.id to graph }

    /**
     * Эталонная картинка по ключу: граф + раскладка. null, если раскладка ещё не нарисована —
     * тогда рисующему нечего показывать, и он падает на текстовую структурную формулу.
     */
    fun picture(id: MoleculeId): MoleculePicture? {
        val known = knownById.getValue(id)
        if (known.layout.isEmpty()) return null
        return MoleculePicture(graphById.getValue(id), known.layout)
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
        id: MoleculeId,
        structuralFormula: String = "",
        description: Prose? = null,
        layout: Map<Int, Vec2D> = emptyMap(),
    ): MoleculeGraph {
        entries += graph to KnownMolecule(id, structuralFormula, description, layout)
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