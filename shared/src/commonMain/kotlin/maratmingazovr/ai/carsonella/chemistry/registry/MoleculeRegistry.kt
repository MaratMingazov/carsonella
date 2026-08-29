package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.chemistry.registry.MoleculeElement
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class KnownMoleculeDetails(
    val id: MoleculeElement,  // Ключ и он же имена на оба языка
    val graph: MoleculeGraph,
    val structuralFormula: String = "", // сжатая СТРУКТУРНАЯ формула (связность + радикальный слот •): CH₃–CH₃, H–O–O•.
    val offsets: Map<Int, Vec2D> = emptyMap(), // эталонное расположение атомов
    val ionizationEnergy: Float? = null, // Порог ионизации МОЛЕКУЛЫ (эВ) —  из NIST WebBook.
)

private val H = MoleculeGraph(listOf(AtomNode(0, AtomElement.HYDROGEN)), emptyList())
private val O = MoleculeGraph(listOf(AtomNode(0, AtomElement.OXYGEN_16)), emptyList())
private val C = MoleculeGraph(listOf(AtomNode(0, AtomElement.CARBON_12)), emptyList())
private val N = MoleculeGraph(listOf(AtomNode(0, AtomElement.NITROGEN_14)), emptyList())

// Реестр известных молекул
object MoleculeRegistry {

    private val entries = registry {

        // --- двухатомные ---
        val dihydrogen = H.attach(H); known(dihydrogen, MoleculeElement.DIHYDROGEN, "H–H", offsets = pair(), ionizationEnergy = 15.426f)
        val dioxygen = O.attach(O, order = 2); known(dioxygen, MoleculeElement.DIOXYGEN, "O=O", offsets = pair(), ionizationEnergy = 12.0697f)
        val dinitrogen = N.attach(N, order = 3); known(dinitrogen, MoleculeElement.DINITROGEN, "N≡N", offsets = pair(), ionizationEnergy = 15.581f)
        val hydroxyl = O.attach(H); val hydroxylShape: Map<Int, Vec2D> = at(0 to xy(0f, 0f), 1 to xy(0.4f, -0.9f)); known(hydroxyl, MoleculeElement.HYDROXYL, "•OH", offsets = pair(), ionizationEnergy = 13.017f)
        val dicarbonSingle = C.attach(C); known(dicarbonSingle, MoleculeElement.DICARBON, "•C–C•")
        val dicarbonDouble = C.attach(C, order = 2); known(dicarbonDouble, MoleculeElement.DICARBON, "C=C", ionizationEnergy = 11.4f) // 11.4 — у основного состояния C₂, у него и двойная связь; прочим кратностям порога нет
        val dicarbonTriple = C.attach(C, order = 3); known(dicarbonTriple, MoleculeElement.DICARBON, "•C≡C•")

        // --- малые неорганические / простые ---
        val water = hydroxyl.attach(H); known(water, MoleculeElement.WATER, "H–O–H", offsets = at(0 to xy(0f, -0.3f), 1 to polar(180f - 52.25f), 2 to polar(52.25f)), ionizationEnergy = 12.621f)
        val hydroperoxyl = hydroxyl.attach(O); known(hydroperoxyl, MoleculeElement.HYDROPEROXYL, "H–O–O•", ionizationEnergy = 11.35f)
        val hydrogenPeroxide = hydroperoxyl.attach(H); known(hydrogenPeroxide, MoleculeElement.HYDROGEN_PEROXIDE, "H–O–O–H", offsets = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f)), ionizationEnergy = 10.58f)
        val trioxidane = hydroperoxyl.attach(hydroxyl); known(trioxidane, MoleculeElement.TRIOXIDANE, "H–O–O–O–H", offsets = at(0 to xy(-1f, 0.2f), 1 to xy(-1.6f, -0.55f), 2 to xy(0f, -0.2f), 3 to xy(1f, 0.2f), 4 to xy(1.6f, 0.95f)))
        val tetraoxidane = hydroperoxyl.attach(hydroperoxyl); known(tetraoxidane, MoleculeElement.TETRAOXIDANE, "H–O–O–O–O–H", offsets = at(0 to xy(-1.5f, 0.2f), 1 to xy(-2.1f, 0.95f), 2 to xy(-0.5f, -0.2f), 3 to xy(1.5f, -0.2f), 4 to xy(2.1f, -0.95f), 5 to xy(0.5f, 0.2f)))
        val imidogen = N.attach(H); known(imidogen, MoleculeElement.IMIDOGEN, ":NH")
        val amino = imidogen.attach(H); known(amino, MoleculeElement.AMINO_RADICAL, "•NH₂")
        val ammonia = amino.attach(H); known(ammonia, MoleculeElement.AMMONIA, "NH₃", ionizationEnergy = 10.07f)
        val carbonyl = C.attach(O, order = 2)                 // >C=O — группа, а не вещество: своей записи нет
        val carbonDioxide = carbonyl.attach(O, order = 2); known(carbonDioxide, MoleculeElement.CARBON_DIOXIDE, "O=C=O", offsets = at(0 to xy(0f, 0f), 1 to xy(-1f, 0f), 2 to xy(1f, 0f)), ionizationEnergy = 13.777f)
        val cyano = C.attach(N, order = 3); known(cyano, MoleculeElement.CYANO, "•C≡N", ionizationEnergy = 13.598f)
        val hydrogenCyanide = cyano.attach(H); known(hydrogenCyanide, MoleculeElement.HYDROGEN_CYANIDE, "H–C≡N", ionizationEnergy = 13.60f)

        // --- углеводороды ---
        val methylidyne = C.attach(H); known(methylidyne, MoleculeElement.METHYLIDYNE, "•CH", ionizationEnergy = 10.64f)
        val methylene = methylidyne.attach(H); known(methylene, MoleculeElement.METHYLENE, ":CH₂", ionizationEnergy = 10.396f)
        val methyl = methylene.attach(H); val methylShape = at(0 to xy(0f, 0f), 1 to xy(-0.4f, -0.9f), 2 to xy(-1f, 0f), 3 to xy(-0.4f, 0.9f)); known(methyl, MoleculeElement.METHYL, "•CH₃", ionizationEnergy = 9.84f)
        val methane = methyl.attach(H); known(methane, MoleculeElement.METHANE, "CH₄", offsets = at(0 to xy(0f, 0f), 1 to polar(45f), 2 to polar(135f), 3 to polar(225f), 4 to polar(315f)), ionizationEnergy = 12.61f)
        val ethynyl = methylidyne.attach(C, order = 3); known(ethynyl, MoleculeElement.ETHYNYL, "HC≡C•", ionizationEnergy = 11.61f)
        val acetylene = ethynyl.attach(H); known(acetylene, MoleculeElement.ACETYLENE, "HC≡CH", offsets = at(0 to xy(-0.5f, 0f), 1 to xy(-1.5f, 0f), 2 to xy(0.5f, 0f), 3 to xy(1.5f, 0f)), ionizationEnergy = 11.400f)
        val vinyl = methylene.attach(methylidyne, order = 2); known(vinyl, MoleculeElement.VINYL, "H₂C=CH•")
        val ethylene = vinyl.attach(H); known(ethylene, MoleculeElement.ETHYLENE, "H₂C=CH₂", offsets = at(0 to xy(-0.5f, 0f), 1 to xy(-0.9f, -0.9f), 2 to xy(-0.9f, 0.9f), 3 to xy(0.5f, 0f), 4 to xy(0.9f, -0.9f), 5 to xy(0.9f, 0.9f)), ionizationEnergy = 10.5138f)
        val ethyl = methyl.attach(methylene); known(ethyl, MoleculeElement.ETHYL, "CH₃–CH₂•")
        val ethane = ethyl.attach(H); known(ethane, MoleculeElement.ETHANE, "CH₃–CH₃", offsets = methylShape.place(xy(-0.5f, 0f)) + methylShape.place(xy(0.5f, 0f), mirror = true, idOffset = 4), ionizationEnergy = 11.52f)

        // Бутаны C₄H₁₀
        val butane = ethyl.attach(ethyl); known(butane, MoleculeElement.BUTANE, "CH₃–CH₂–CH₂–CH₃", ionizationEnergy = 10.53f)
        val ethylidene = methyl.attach(methylidyne)           // CH₃–CH:
        val isopropyl = ethylidene.attach(methyl); known(isopropyl, MoleculeElement.ISOPROPYL, "(CH₃)₂CH•")
        val isobutane = isopropyl.attach(methyl); known(isobutane, MoleculeElement.ISOBUTANE, "(CH₃)₃CH", ionizationEnergy = 10.68f)

        // Бутены C₄H₈
        val butene1 = vinyl.attach(ethyl); known(butene1, MoleculeElement.BUTENE_1, "H₂C=CH–CH₂–CH₃", ionizationEnergy = 9.55f)
        val butene2 = ethylidene.attach(ethylidene, order = 2); known(butene2, MoleculeElement.BUTENE_2, "CH₃–CH=CH–CH₃", ionizationEnergy = 9.10f) // (E)-изомер: цис/транс граф не различает
        val vinylidene = methylene.attach(C, order = 2)       // H₂C=C:
        val isopropenyl = vinylidene.attach(methyl); known(isopropenyl, MoleculeElement.ISOPROPENYL, "H₂C=C(CH₃)•")
        val isobutylene = isopropenyl.attach(methyl); known(isobutylene, MoleculeElement.ISOBUTYLENE, "H₂C=C(CH₃)₂", ionizationEnergy = 9.22f)


        val ethanediyl = methylene.attach(methylene)          // •CH₂–CH₂•
        val trimethylene = ethanediyl.extend(methylene); known(trimethylene, MoleculeElement.TRIMETHYLENE, "•CH₂–CH₂–CH₂•")
        val cyclopropane = trimethylene.closeChain(); known(cyclopropane, MoleculeElement.CYCLOPROPANE, "(CH₂)₃", ionizationEnergy = 9.86f)
        val oxyethyl = ethanediyl.extend(O)                   // •CH₂–CH₂–O•
        val oxirane = oxyethyl.closeChain(); known(oxirane, MoleculeElement.OXIRANE, "(CH₂)₂O", ionizationEnergy = 10.56f)

        val ethenediyl = methylidyne.attach(methylidyne, order = 2)   // •CH=CH•
        val butadienediyl = ethenediyl.extend(ethenediyl)             // •CH=CH–CH=CH•
        val hexatrienediyl = butadienediyl.extend(ethenediyl)         // •CH=CH–CH=CH–CH=CH•
        val benzene = hexatrienediyl.closeChain(); known(benzene, MoleculeElement.BENZENE, "(CH)₆", ionizationEnergy = 9.2438f)

        // --- кислородсодержащая органика ---
        val formyl = carbonyl.attach(H); known(formyl, MoleculeElement.FORMYL, "H–C•=O", ionizationEnergy = 8.12f)
        val formaldehyde = formyl.attach(H); known(formaldehyde, MoleculeElement.FORMALDEHYDE, "H₂C=O", offsets = at(0 to xy(0f, 0f), 1 to xy(0f, -1f), 2 to xy(-0.87f, 0.5f), 3 to xy(0.87f, 0.5f)), ionizationEnergy = 10.88f)
        val methanol = methyl.attach(hydroxyl); known(methanol, MoleculeElement.METHANOL, "CH₃–OH", offsets = methylShape.place(xy(-0.5f, 0f)) + hydroxylShape.place(xy(0.5f, 0f), idOffset = 4), ionizationEnergy = 10.84f)
        val formicAcid = formyl.attach(hydroxyl); known(formicAcid, MoleculeElement.FORMIC_ACID, "H–C(=O)–OH", ionizationEnergy = 11.33f)
        val ethanol = ethyl.attach(hydroxyl); known(ethanol, MoleculeElement.ETHANOL, "CH₃–CH₂–OH", ionizationEnergy = 10.48f)
    }

    private val byCanonical: Map<String, KnownMoleculeDetails> = run {
        val nameless = entries.filter { it.graph.canonical.isEmpty() }
        require(nameless.isEmpty()) { "Записи реестра без канона (тяжёлых атомов больше потолка): ${nameless.map { it.id }}" }
        val byKey = entries.associateBy { it.graph.canonical }
        require(byKey.size == entries.size) {
            val collisions = entries.groupBy { it.graph.canonical }.filterValues { it.size > 1 }
            "Записи реестра с одинаковым каноном: ${collisions.values.map { group -> group.map { it.id } }}"
        }
        val brokenLayout = entries.filter { it.offsets.isNotEmpty() && it.offsets.keys != it.graph.nodes.mapTo(mutableSetOf()) { node -> node.localId } }
        require(brokenLayout.isEmpty()) { "Раскладка не совпадает с узлами графа: ${brokenLayout.map { it.id }}" }
        val uncovered = MoleculeElement.entries - byKey.values.map { it.id }.toSet()
        require(uncovered.isEmpty()) { "У ключей нет записи в реестре: $uncovered — byId() обещает не возвращать null, значит покрыть надо все" }
        byKey
    }

    private val knownById: Map<MoleculeElement, KnownMoleculeDetails> = entries.associateBy { it.id }

    fun lookup(canonical: String): KnownMoleculeDetails? = byCanonical[canonical] // Известная молекула по её каноническому ключу
    fun knownMoleculeById(id: MoleculeElement): KnownMoleculeDetails = knownById.getValue(id) // Запись реестра по ключу. Не null: покрытие всех ключей проверено при инициализации.
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

private class RegistryBuilder {
    val entries = mutableListOf<KnownMoleculeDetails>()
    fun known(
        graph: MoleculeGraph,
        id: MoleculeElement,
        structuralFormula: String = "",
        offsets: Map<Int, Vec2D> = emptyMap(),
        ionizationEnergy: Float? = null,
    ): MoleculeGraph {
        entries += KnownMoleculeDetails(id, graph, structuralFormula, offsets = offsets, ionizationEnergy = ionizationEnergy)
        return graph
    }
}
private fun registry(build: RegistryBuilder.() -> Unit): List<KnownMoleculeDetails> = RegistryBuilder().apply(build).entries.toList()

// Хелперы раскладки. Единицы — доли длины связи, ось y вниз (как на экране).
private fun at(vararg offsets: Pair<Int, Vec2D>): Map<Int, Vec2D> = mapOf(*offsets)
private fun xy(x: Float, y: Float) = Vec2D(x, y)
private fun polar(angleDeg: Float, distance: Float = 1f): Vec2D {
    val rad = angleDeg * PI.toFloat() / 180f
    return Vec2D(cos(rad) * distance, sin(rad) * distance)
}
/** Двухатомная молекула: узлы 0 и 1 по горизонтали. */
private fun pair(): Map<Int, Vec2D> = at(0 to xy(-0.5f, 0f), 1 to xy(0.5f, 0f))
private fun Map<Int, Vec2D>.place(origin: Vec2D, mirror: Boolean = false, idOffset: Int = 0): Map<Int, Vec2D> = entries.associate { (id, v) -> (id + idOffset) to Vec2D((if (mirror) -v.x else v.x) + origin.x, v.y + origin.y) }