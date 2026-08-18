package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Lang
import maratmingazovr.ai.carsonella.TranslatedText
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class KnownMoleculeDetails(
    val id: KnownMoleculeId,  // Ключ и он же имена на оба языка
    val graph: MoleculeGraph,
    val structuralFormula: String = "", // сжатая СТРУКТУРНАЯ формула (связность + радикальный слот •): CH₃–CH₃, H–O–O•.
    val offsets: Map<Int, Vec2D> = emptyMap(), // эталонное расположение атомов
) {
    fun name(lang: Lang): String = id.name(lang)
    val description: TranslatedText? get() = id.description
}

private val H = MoleculeGraph(listOf(AtomNode(0, Element.HYDROGEN)), emptyList())
private val O = MoleculeGraph(listOf(AtomNode(0, Element.OXYGEN_16)), emptyList())
private val C = MoleculeGraph(listOf(AtomNode(0, Element.CARBON_12)), emptyList())
private val N = MoleculeGraph(listOf(AtomNode(0, Element.NITROGEN_14)), emptyList())

// Реестр известных молекул
object MoleculeRegistry {

    private val entries = registry {

        // --- двухатомные ---
        val dihydrogen = H.attach(H); known(dihydrogen, KnownMoleculeId.DIHYDROGEN, "H–H", offsets = pair())
        val dioxygen = O.attach(O, order = 2); known(dioxygen, KnownMoleculeId.DIOXYGEN, "O=O", offsets = pair())
        val dinitrogen = N.attach(N, order = 3); known(dinitrogen, KnownMoleculeId.DINITROGEN, "N≡N", offsets = pair())
        val hydroxyl = O.attach(H); known(hydroxyl, KnownMoleculeId.HYDROXYL, "•OH", offsets = pair())
        val dicarbonSingle = C.attach(C); known(dicarbonSingle, KnownMoleculeId.DICARBON, "•C–C•")
        val dicarbonDouble = C.attach(C, order = 2); known(dicarbonDouble, KnownMoleculeId.DICARBON, "C=C")
        val dicarbonTriple = C.attach(C, order = 3); known(dicarbonTriple, KnownMoleculeId.DICARBON, "•C≡C•")

        // --- малые неорганические / простые ---
        val water = hydroxyl.attach(H); known(water, KnownMoleculeId.WATER, "H–O–H", offsets = at(0 to xy(0f, -0.3f), 1 to polar(180f - 52.25f), 2 to polar(52.25f)))
        val hydroperoxyl = hydroxyl.attach(O); known(hydroperoxyl, KnownMoleculeId.HYDROPEROXYL, "H–O–O•")
        val hydrogenPeroxide = hydroperoxyl.attach(H); known(hydrogenPeroxide, KnownMoleculeId.HYDROGEN_PEROXIDE, "H–O–O–H", offsets = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f)))
        val trioxidane = hydroperoxyl.attach(hydroxyl); known(trioxidane, KnownMoleculeId.TRIOXIDANE, "H–O–O–O–H")
        val tetraoxidane = hydroperoxyl.attach(hydroperoxyl); known(tetraoxidane, KnownMoleculeId.TETRAOXIDANE, "H–O–O–O–O–H")
        val imidogen = N.attach(H); known(imidogen, KnownMoleculeId.IMIDOGEN, ":NH")
        val amino = imidogen.attach(H); known(amino, KnownMoleculeId.AMINO_RADICAL, "•NH₂")
        val ammonia = amino.attach(H); known(ammonia, KnownMoleculeId.AMMONIA, "NH₃")
        val carbonyl = C.attach(O, order = 2)                 // >C=O — группа, а не вещество: своей записи нет
        val carbonDioxide = carbonyl.attach(O, order = 2); known(carbonDioxide, KnownMoleculeId.CARBON_DIOXIDE, "O=C=O")
        val cyano = C.attach(N, order = 3); known(cyano, KnownMoleculeId.CYANO, "•C≡N")
        val hydrogenCyanide = cyano.attach(H); known(hydrogenCyanide, KnownMoleculeId.HYDROGEN_CYANIDE, "H–C≡N")

        // --- углеводороды ---
        val methylidyne = C.attach(H); known(methylidyne, KnownMoleculeId.METHYLIDYNE, "•CH")
        val methylene = methylidyne.attach(H); known(methylene, KnownMoleculeId.METHYLENE, ":CH₂")
        val methyl = methylene.attach(H); known(methyl, KnownMoleculeId.METHYL, "•CH₃")
        val methane = methyl.attach(H); known(methane, KnownMoleculeId.METHANE, "CH₄")
        val ethynyl = methylidyne.attach(C, order = 3); known(ethynyl, KnownMoleculeId.ETHYNYL, "HC≡C•")
        val acetylene = ethynyl.attach(H); known(acetylene, KnownMoleculeId.ACETYLENE, "HC≡CH")
        val vinyl = methylene.attach(methylidyne, order = 2); known(vinyl, KnownMoleculeId.VINYL, "H₂C=CH•")
        val ethylene = vinyl.attach(H); known(ethylene, KnownMoleculeId.ETHYLENE, "H₂C=CH₂")
        val ethyl = methyl.attach(methylene); known(ethyl, KnownMoleculeId.ETHYL, "CH₃–CH₂•")
        val ethane = ethyl.attach(H); known(ethane, KnownMoleculeId.ETHANE, "CH₃–CH₃")

        // Бутаны C₄H₁₀
        val butane = ethyl.attach(ethyl); known(butane, KnownMoleculeId.BUTANE, "CH₃–CH₂–CH₂–CH₃")
        val ethylidene = methyl.attach(methylidyne)           // CH₃–CH:
        val isopropyl = ethylidene.attach(methyl); known(isopropyl, KnownMoleculeId.ISOPROPYL, "(CH₃)₂CH•")
        val isobutane = isopropyl.attach(methyl); known(isobutane, KnownMoleculeId.ISOBUTANE, "(CH₃)₃CH")

        // Бутены C₄H₈
        val butene1 = vinyl.attach(ethyl); known(butene1, KnownMoleculeId.BUTENE_1, "H₂C=CH–CH₂–CH₃")
        val butene2 = ethylidene.attach(ethylidene, order = 2); known(butene2, KnownMoleculeId.BUTENE_2, "CH₃–CH=CH–CH₃")
        val vinylidene = methylene.attach(C, order = 2)       // H₂C=C:
        val isopropenyl = vinylidene.attach(methyl); known(isopropenyl, KnownMoleculeId.ISOPROPENYL, "H₂C=C(CH₃)•")
        val isobutylene = isopropenyl.attach(methyl); known(isobutylene, KnownMoleculeId.ISOBUTYLENE, "H₂C=C(CH₃)₂")


        val ethanediyl = methylene.attach(methylene)          // •CH₂–CH₂•
        val trimethylene = ethanediyl.extend(methylene); known(trimethylene, KnownMoleculeId.TRIMETHYLENE, "•CH₂–CH₂–CH₂•")
        val cyclopropane = trimethylene.closeChain(); known(cyclopropane, KnownMoleculeId.CYCLOPROPANE, "(CH₂)₃")
        val oxyethyl = ethanediyl.extend(O)                   // •CH₂–CH₂–O•
        val oxirane = oxyethyl.closeChain(); known(oxirane, KnownMoleculeId.OXIRANE, "(CH₂)₂O")

        val ethenediyl = methylidyne.attach(methylidyne, order = 2)   // •CH=CH•
        val butadienediyl = ethenediyl.extend(ethenediyl)             // •CH=CH–CH=CH•
        val hexatrienediyl = butadienediyl.extend(ethenediyl)         // •CH=CH–CH=CH–CH=CH•
        val benzene = hexatrienediyl.closeChain(); known(benzene, KnownMoleculeId.BENZENE, "(CH)₆")

        // --- кислородсодержащая органика ---
        val formyl = carbonyl.attach(H); known(formyl, KnownMoleculeId.FORMYL, "H–C•=O")
        val formaldehyde = formyl.attach(H); known(formaldehyde, KnownMoleculeId.FORMALDEHYDE, "H₂C=O")
        val methanol = methyl.attach(hydroxyl); known(methanol, KnownMoleculeId.METHANOL, "CH₃–OH")
        val formicAcid = formyl.attach(hydroxyl); known(formicAcid, KnownMoleculeId.FORMIC_ACID, "H–C(=O)–OH")
        val ethanol = ethyl.attach(hydroxyl); known(ethanol, KnownMoleculeId.ETHANOL, "CH₃–CH₂–OH")
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
        val uncovered = KnownMoleculeId.entries - byKey.values.map { it.id }.toSet()
        require(uncovered.isEmpty()) { "У ключей нет записи в реестре: $uncovered — byId() обещает не возвращать null, значит покрыть надо все" }
        byKey
    }

    private val knownById: Map<KnownMoleculeId, KnownMoleculeDetails> = entries.associateBy { it.id }

    fun lookup(canonical: String): KnownMoleculeDetails? = byCanonical[canonical] // Известная молекула по её каноническому ключу
    fun knownMoleculeById(id: KnownMoleculeId): KnownMoleculeDetails = knownById.getValue(id) // Запись реестра по ключу. Не null: покрытие всех ключей проверено при инициализации.
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
        id: KnownMoleculeId,
        structuralFormula: String = "",
        offsets: Map<Int, Vec2D> = emptyMap(),
    ): MoleculeGraph {
        entries += KnownMoleculeDetails(id, graph, structuralFormula, offsets = offsets)
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