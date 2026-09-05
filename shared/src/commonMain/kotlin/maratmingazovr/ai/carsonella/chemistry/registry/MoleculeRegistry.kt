package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.chemistry.registry.ElementOrMolecule
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
    val basedOn: List<ElementOrMolecule> = emptyList(),
)

private val H = MoleculeGraph(listOf(AtomNode(0, AtomElement.HYDROGEN)), emptyList())
private val O = MoleculeGraph(listOf(AtomNode(0, AtomElement.OXYGEN_16)), emptyList())
private val C = MoleculeGraph(listOf(AtomNode(0, AtomElement.CARBON_12)), emptyList())
private val N = MoleculeGraph(listOf(AtomNode(0, AtomElement.NITROGEN_14)), emptyList())

// Реестр известных молекул
object MoleculeRegistry {

    private val entries = registry {

        // --- двухатомные ---
        val dihydrogen = H.attach(H); known(dihydrogen, MoleculeElement.DIHYDROGEN, "H–H", offsets = pair(), ionizationEnergy = 15.426f, basedOn = listOf(AtomElement.HYDROGEN))
        val methylidyne = C.attach(H); val methylidyneShape = pair(); known(methylidyne, MoleculeElement.METHYLIDYNE, "•CH", offsets = methylidyneShape, ionizationEnergy = 10.64f, basedOn = listOf(AtomElement.CARBON_12))
        val dicarbonSingle = C.attach(C); known(dicarbonSingle, MoleculeElement.DICARBON_SINGLE, "•C–C•", offsets = pair(), basedOn = listOf(AtomElement.CARBON_12))
        val dicarbonDouble = C.attach(C, order = 2); known(dicarbonDouble, MoleculeElement.DICARBON_DOUBLE, "C=C", offsets = pair(), ionizationEnergy = 11.4f, basedOn = listOf(AtomElement.CARBON_12))
        val dicarbonTriple = C.attach(C, order = 3); known(dicarbonTriple, MoleculeElement.DICARBON_TRIPLE, "•C≡C•", offsets = pair(), basedOn = listOf(AtomElement.CARBON_12))
        val imidogen = N.attach(H); known(imidogen, MoleculeElement.IMIDOGEN, ":NH", offsets = pair(), basedOn = listOf(AtomElement.NITROGEN_14))
        val cyano = N.attach(C, order = 3); known(cyano, MoleculeElement.CYANO, "•C≡N", offsets = pair(), ionizationEnergy = 13.598f, basedOn = listOf(AtomElement.NITROGEN_14))
        val dinitrogen = N.attach(N, order = 3); known(dinitrogen, MoleculeElement.DINITROGEN, "N≡N", offsets = pair(), ionizationEnergy = 15.581f, basedOn = listOf(AtomElement.NITROGEN_14))
        val hydroxyl = O.attach(H); known(hydroxyl, MoleculeElement.HYDROXYL, "•OH", offsets = pair(), ionizationEnergy = 13.017f, basedOn = listOf(AtomElement.OXYGEN_16))
        val carbonyl = C.attach(O, order = 2); known(carbonyl, MoleculeElement.CARBONYL, ":C=O", offsets = pair(), basedOn = listOf(AtomElement.OXYGEN_16))  // на базе него будет углекислый газ
        // УГАРНЫЙ ГАЗ - ПОКА НЕ МОДЕЛИРУЕТСЯ (ТРОЙНАЯ СВЯЗЬ)
        val nitrogenMonoxide = N.attach(O, order = 2); known(nitrogenMonoxide, MoleculeElement.NITROGEN_MONOXIDE, "•N=O", offsets = pair(), ionizationEnergy = 9.2644f, basedOn = listOf(AtomElement.OXYGEN_16))
        val dioxygen = O.attach(O, order = 2); known(dioxygen, MoleculeElement.DIOXYGEN, "O=O", offsets = pair(), ionizationEnergy = 12.0697f, basedOn = listOf(AtomElement.OXYGEN_16))

        // --- трехатомные ---
        val methylene = methylidyne.attach(H); val methyleneShape = at(0 to xy(0f, 0f), 1 to polar(90f - 133.9f / 2f), 2 to polar(90f + 133.9f / 2f)); known(methylene, MoleculeElement.METHYLENE, ":CH₂", offsets = methyleneShape, ionizationEnergy = 10.396f, basedOn = listOf(MoleculeElement.METHYLIDYNE))
        val ethynyl = methylidyne.attach(C, order = 3); val ethynylShape = at(0 to xy(-0.5f, 0f), 1 to xy(-1.5f, 0f), 2 to xy(0.5f, 0f)); known(ethynyl, MoleculeElement.ETHYNYL, "HC≡C•", offsets = ethynylShape, ionizationEnergy = 11.61f, basedOn = listOf(MoleculeElement.DICARBON_TRIPLE))
        val tricarbon = dicarbonDouble.attach(C, order = 2, nodeId = 1); known(tricarbon, MoleculeElement.TRICARBON, ":C=C=C:", offsets = at(0 to xy(-1f, 0f), 1 to xy(0f, 0f), 2 to xy(1f, 0f)), basedOn = listOf(MoleculeElement.DICARBON_DOUBLE))
        val amino = imidogen.attach(H); val aminoShape = at(0 to xy(0f, 0f), 1 to polar(90f - 103.4f / 2f), 2 to polar(90f + 103.4f / 2f)); known(amino, MoleculeElement.AMINO_RADICAL, "•NH₂", offsets = aminoShape, basedOn = listOf(MoleculeElement.IMIDOGEN))
        val diazenyl = imidogen.attach(N, order = 2); val diazenylShape = at(0 to xy(0f, 0f), 1 to polar(-90f), 2 to polar(-90f + 112f)); known(diazenyl, MoleculeElement.DIAZENYL, "H–N=N•", offsets = diazenylShape, basedOn = listOf(MoleculeElement.IMIDOGEN))
        val hydrogenCyanide = cyano.attach(H); known(hydrogenCyanide, MoleculeElement.HYDROGEN_CYANIDE, "H–C≡N", offsets = at(0 to xy(-1f, 0f), 1 to xy(0f, 0f), 2 to xy(1f, 0f)), ionizationEnergy = 13.60f, basedOn = listOf(MoleculeElement.CYANO))  // линейная: N–C–H в ряд, узел 1 (С) в центре
        val water = hydroxyl.attach(H); known(water, MoleculeElement.WATER, "H–O–H", offsets = at(0 to xy(0f, -0.3f), 1 to polar(180f - 52.25f), 2 to polar(52.25f)), ionizationEnergy = 12.621f, basedOn = listOf(MoleculeElement.HYDROXYL))
        val hydroperoxyl = hydroxyl.attach(O); known(hydroperoxyl, MoleculeElement.HYDROPEROXYL, "H–O–O•", ionizationEnergy = 11.35f, basedOn = listOf(MoleculeElement.HYDROXYL))
        val formyl = carbonyl.attach(H); known(formyl, MoleculeElement.FORMYL, "H–C•=O", offsets = at(0 to xy(0f, 0f), 1 to polar(-90f), 2 to polar(-90f + 124.4f)), ionizationEnergy = 8.12f, basedOn = listOf(MoleculeElement.CARBONYL))  // радикал изогнут: угол H–C=O 124.4°
        val carbonDioxide = carbonyl.attach(O, order = 2); known(carbonDioxide, MoleculeElement.CARBON_DIOXIDE, "O=C=O", offsets = at(0 to xy(0f, 0f), 1 to xy(-1f, 0f), 2 to xy(1f, 0f)), ionizationEnergy = 13.777f, basedOn = listOf(MoleculeElement.CARBONYL))

        // --- четырехатомные ---
        val methyl = methylene.attach(H); val methylShape = methyleneShape + (3 to (methyleneShape.getValue(0) + polar(-90f))); known(methyl, MoleculeElement.METHYL, "•CH₃", offsets = methylShape, ionizationEnergy = 9.84f, basedOn = listOf(MoleculeElement.METHYLENE))
        val acetylene = ethynyl.attach(H); val acetyleneShape = ethynylShape + (3 to xy(1.5f, 0f)); known(acetylene, MoleculeElement.ACETYLENE, "HC≡CH", offsets = acetyleneShape, ionizationEnergy = 11.400f, basedOn = listOf(MoleculeElement.ETHYNYL))
        val ammonia = amino.attach(H); val ammoniaShape = aminoShape + (3 to (aminoShape.getValue(0) + polar(-90f))); known(ammonia, MoleculeElement.AMMONIA, "NH₃", offsets = ammoniaShape, ionizationEnergy = 10.07f, basedOn = listOf(MoleculeElement.AMINO_RADICAL))
        val hydrogenPeroxide = hydroperoxyl.attach(H); known(hydrogenPeroxide, MoleculeElement.HYDROGEN_PEROXIDE, "H–O–O–H", offsets = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f)), ionizationEnergy = 10.58f, basedOn = listOf(MoleculeElement.HYDROPEROXYL))
        val formaldehyde = formyl.attach(H); known(formaldehyde, MoleculeElement.FORMALDEHYDE, "H₂C=O", offsets = at(0 to xy(0f, 0f), 1 to xy(0f, -1f), 2 to xy(-0.87f, 0.5f), 3 to xy(0.87f, 0.5f)), ionizationEnergy = 10.88f, basedOn = listOf(MoleculeElement.FORMYL))
        val vinylidene = dicarbonDouble.attach(H, nodeId = 0).attach(H, nodeId = 0); val vinylideneShape = at(0 to xy(-0.5f, 0f), 1 to xy(0.5f, 0f), 2 to xy(-0.9f, -0.9f), 3 to xy(-0.9f, 0.9f)); known(vinylidene, MoleculeElement.VINYLIDENE, "H₂C=C:", offsets = vinylideneShape, basedOn = listOf(MoleculeElement.DICARBON_DOUBLE))
        val hydrazinediyl = imidogen.attach(imidogen); val hydrazinediylShape = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f)); known(hydrazinediyl, MoleculeElement.HYDRAZINEDIYL, "•NH–NH•", offsets = hydrazinediylShape, basedOn = listOf(MoleculeElement.IMIDOGEN))
        val diazene = diazenyl.attach(H); val diazeneShape = diazenylShape + (3 to (diazenylShape.getValue(2) + polar(90f))); known(diazene, MoleculeElement.DIAZENE, "HN=NH", offsets = diazeneShape, basedOn = listOf(MoleculeElement.DIAZENYL))
        val methyleneamidogen = methylene.attach(N, order = 2); val methyleneamidogenShape = methyleneShape + (3 to (methyleneShape.getValue(0) + polar(-90f))); known(methyleneamidogen, MoleculeElement.METHYLENEAMIDOGEN, "H₂C=N•", offsets = methyleneamidogenShape, basedOn = listOf(MoleculeElement.METHYLENE))
        // Нужно добавить формилоксил HCO2 -> потом на базе него получить муравьиную кислоту

        // --- пятиатомные ---
        val methane = methyl.attach(H); known(methane, MoleculeElement.METHANE, "CH₄", offsets = at(0 to xy(0f, 0f), 1 to polar(45f), 2 to polar(135f), 3 to polar(225f), 4 to polar(315f)), ionizationEnergy = 12.61f, basedOn = listOf(MoleculeElement.METHYL))
        val vinyl = vinylidene.attach(H); val vinylShape = vinylideneShape + (4 to (vinylideneShape.getValue(1) + polar(60f))); known(vinyl, MoleculeElement.VINYL, "H₂C=CH•", offsets = vinylShape, basedOn = listOf(MoleculeElement.VINYLIDENE))
        val trioxidane = hydroperoxyl.attach(hydroxyl); known(trioxidane, MoleculeElement.TRIOXIDANE, "H–O–O–O–H", offsets = at(0 to xy(-1f, 0.2f), 1 to xy(-1.6f, -0.55f), 2 to xy(0f, -0.2f), 3 to xy(1f, 0.2f), 4 to xy(1.6f, 0.95f)), basedOn = listOf(MoleculeElement.HYDROGEN_PEROXIDE))
        val ethylidyne = methyl.attach(C); val ethylidyneShape = at(0 to xy(-0.5f, 0f), 1 to xy(-0.9f, -0.9f), 2 to xy(-1.5f, 0f), 3 to xy(-0.9f, 0.9f), 4 to xy(0.5f, 0f)); known(ethylidyne, MoleculeElement.ETHYLIDYNE, "CH₃–C•", offsets = ethylidyneShape, basedOn = listOf(MoleculeElement.METHYL))
        /* нужнен на базе формилоксила */ val formicAcid = formyl.attach(hydroxyl); known(formicAcid, MoleculeElement.FORMIC_ACID, "H–C(=O)–OH", ionizationEnergy = 11.33f, basedOn = listOf(MoleculeElement.FORMYL))
        val methanimine = methyleneamidogen.attach(H); val methanimineShape = methyleneamidogenShape + (4 to (methyleneamidogenShape.getValue(3) + polar(-20f))); known(methanimine, MoleculeElement.METHANIMINE, "H₂C=NH", offsets = methanimineShape, basedOn = listOf(MoleculeElement.METHYLENEAMIDOGEN))
        val aminocarbene = amino.attach(methylidyne); val aminocarbeneShape = aminoShape + (3 to (aminoShape.getValue(0) + polar(-90f))) + (4 to (aminoShape.getValue(0) + polar(-90f) + polar(-20f))); known(aminocarbene, MoleculeElement.AMINOCARBENE, "H₂N–CH:", offsets = aminocarbeneShape, basedOn = listOf(MoleculeElement.AMINO_RADICAL))

        // --- шестиатомные ---
        val ethylene = vinyl.attach(H); val ethyleneShape = vinylShape + (5 to (vinylShape.getValue(1) + polar(-60f))); known(ethylene, MoleculeElement.ETHYLENE, "H₂C=CH₂", offsets = ethyleneShape, ionizationEnergy = 10.5138f, basedOn = listOf(MoleculeElement.VINYL))
        val ethylidene = ethylidyne.attach(H); val ethylideneShape = ethylidyneShape + (5 to (ethylidyneShape.getValue(4) + polar(60f))); known(ethylidene, MoleculeElement.ETHYLIDENE, "CH₃–CH:", offsets = ethylideneShape, basedOn = listOf(MoleculeElement.ETHYLIDYNE))
        val hydrazine = hydrazinediyl.attach(H, nodeId = 0).attach(H, nodeId = 2); val hydrazineShape = hydrazinediylShape + (4 to (hydrazinediylShape.getValue(0) + polar(135f))) + (5 to (hydrazinediylShape.getValue(2) + polar(-45f))); known(hydrazine, MoleculeElement.HYDRAZINE, "H₂N–NH₂", offsets = hydrazineShape, ionizationEnergy = 8.1f, basedOn = listOf(MoleculeElement.HYDRAZINEDIYL))
        val tetraoxidane = hydroperoxyl.attach(hydroperoxyl); known(tetraoxidane, MoleculeElement.TETRAOXIDANE, "H–O–O–O–O–H", offsets = at(0 to xy(-1.5f, 0.2f), 1 to xy(-2.1f, 0.95f), 2 to xy(-0.5f, -0.2f), 3 to xy(1.5f, -0.2f), 4 to xy(2.1f, -0.95f), 5 to xy(0.5f, 0.2f)), basedOn = listOf(MoleculeElement.TRIOXIDANE))
        val methylaminyl = methyl.attach(imidogen); val methylaminylShape = methylShape + (4 to (methylShape.getValue(0) + polar(90f))) + (5 to (methylShape.getValue(0) + polar(90f) + polar(22f))); known(methylaminyl, MoleculeElement.METHYLAMINYL, "CH₃–NH•", offsets = methylaminylShape, basedOn = listOf(MoleculeElement.METHYL))
        val methanol = methyl.attach(hydroxyl); known(methanol, MoleculeElement.METHANOL, "CH₃–OH", offsets = at(0 to xy(-0.5f, 0f), 1 to xy(-0.9f, -0.9f), 2 to xy(-1.5f, 0f), 3 to xy(-0.9f, 0.9f), 4 to xy(0.5f, 0f), 5 to xy(0.9f, -0.9f)), ionizationEnergy = 10.84f, basedOn = listOf(MoleculeElement.METHYL))

        // --- семиатомные ---
        val ethyl = ethylidene.attach(H); val ethylShape = ethylideneShape + (6 to (ethylideneShape.getValue(4) + polar(-60f))); known(ethyl, MoleculeElement.ETHYL, "CH₃–CH₂•", offsets = ethylShape, basedOn = listOf(MoleculeElement.ETHYLIDENE))
        val methylamine = methylaminyl.attach(H); val methylamineShape = methylaminylShape + (6 to (methylaminylShape.getValue(4) + polar(158f))); known(methylamine, MoleculeElement.METHYLAMINE, "CH₃–NH₂", offsets = methylamineShape, ionizationEnergy = 8.9f, basedOn = listOf(MoleculeElement.METHYLAMINYL))


        // --- восьмиатомные ---
        val ethane = ethyl.attach(H); known(ethane, MoleculeElement.ETHANE, "CH₃–CH₃", offsets = at(0 to xy(-0.5f, 0f), 1 to xy(-0.9f, -0.9f), 2 to xy(-1.5f, 0f), 3 to xy(-0.9f, 0.9f), 4 to xy(0.5f, 0f), 5 to xy(0.9f, -0.9f), 6 to xy(1.5f, 0f), 7 to xy(0.9f, 0.9f)), ionizationEnergy = 11.52f, basedOn = listOf(MoleculeElement.ETHYL))

        // --- девятиатомные ---
        val ethanol = ethyl.attach(hydroxyl); val ethanolShape = ethylShape + (7 to (ethylShape.getValue(4) + xy(1f, 0f))) + (8 to (ethylShape.getValue(4) + xy(1.4f, -0.9f))); known(ethanol, MoleculeElement.ETHANOL, "CH₃–CH₂–OH", offsets = ethanolShape, ionizationEnergy = 10.48f, basedOn = listOf(MoleculeElement.ETHYL))

        //////
        val ethanediyl = methylene.attach(methylene)          // •CH₂–CH₂• ЭТАНДИИЛ на базе DICARBON_SINGLE
        val ethenediyl = methylidyne.attach(methylidyne, order = 2)   // •CH=CH• ЭТЕНДИИЛ на базе DICARBON_DOUBLE


        // Бутаны C₄H₁₀
        //val butane = ethyl.attach(ethyl); known(butane, MoleculeElement.BUTANE, "CH₃–CH₂–CH₂–CH₃", ionizationEnergy = 10.53f)
        //val isopropyl = ethylidene.attach(methyl); known(isopropyl, MoleculeElement.ISOPROPYL, "(CH₃)₂CH•")
        //val isobutane = isopropyl.attach(methyl); known(isobutane, MoleculeElement.ISOBUTANE, "(CH₃)₃CH", ionizationEnergy = 10.68f)

        // Бутены C₄H₈
        //val butene1 = vinyl.attach(ethyl); known(butene1, MoleculeElement.BUTENE_1, "H₂C=CH–CH₂–CH₃", ionizationEnergy = 9.55f)
        //val butene2 = ethylidene.attach(ethylidene, order = 2); known(butene2, MoleculeElement.BUTENE_2, "CH₃–CH=CH–CH₃", ionizationEnergy = 9.10f) // (E)-изомер: цис/транс граф не различает
        //val isopropenyl = vinylidene.attach(methyl); known(isopropenyl, MoleculeElement.ISOPROPENYL, "H₂C=C(CH₃)•")
        //val isobutylene = isopropenyl.attach(methyl); known(isobutylene, MoleculeElement.ISOBUTYLENE, "H₂C=C(CH₃)₂", ionizationEnergy = 9.22f)



        //val trimethylene = ethanediyl.extend(methylene); known(trimethylene, MoleculeElement.TRIMETHYLENE, "•CH₂–CH₂–CH₂•")
        //val cyclopropane = trimethylene.closeChain(); known(cyclopropane, MoleculeElement.CYCLOPROPANE, "(CH₂)₃", ionizationEnergy = 9.86f)
        val oxyethyl = ethanediyl.extend(O)                   // •CH₂–CH₂–O•
        //val oxirane = oxyethyl.closeChain(); known(oxirane, MoleculeElement.OXIRANE, "(CH₂)₂O", ionizationEnergy = 10.56f)


        val butadienediyl = ethenediyl.extend(ethenediyl)             // •CH=CH–CH=CH•
        val hexatrienediyl = butadienediyl.extend(ethenediyl)         // •CH=CH–CH=CH–CH=CH•
        //val benzene = hexatrienediyl.closeChain(); known(benzene, MoleculeElement.BENZENE, "(CH)₆", ionizationEnergy = 9.2438f)

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
        basedOn: List<ElementOrMolecule> = emptyList(),
    ): MoleculeGraph {
        entries += KnownMoleculeDetails(id, graph, structuralFormula, offsets = offsets, ionizationEnergy = ionizationEnergy, basedOn = basedOn)
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