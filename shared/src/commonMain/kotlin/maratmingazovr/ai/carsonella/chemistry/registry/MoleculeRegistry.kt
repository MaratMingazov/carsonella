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
        val tricarbon = dicarbonDouble.attach(C, order = 2, nodeId = 1); val tricarbonShape = at(0 to xy(-1f, 0f), 1 to xy(0f, 0f), 2 to xy(1f, 0f)); known(tricarbon, MoleculeElement.TRICARBON, ":C=C=C:", offsets = tricarbonShape, basedOn = listOf(MoleculeElement.DICARBON_DOUBLE))
        val amino = imidogen.attach(H); val aminoShape = at(0 to xy(0f, 0f), 1 to polar(90f - 103.4f / 2f), 2 to polar(90f + 103.4f / 2f)); known(amino, MoleculeElement.AMINO_RADICAL, "•NH₂", offsets = aminoShape, basedOn = listOf(MoleculeElement.IMIDOGEN))
        val nitroxyl = nitrogenMonoxide.attach(H); val nitroxylShape = at(0 to xy(0f, 0f), 1 to polar(-90f), 2 to polar(-90f + 108f)); known(nitroxyl, MoleculeElement.NITROXYL, "H–N=O", offsets = nitroxylShape, basedOn = listOf(MoleculeElement.NITROGEN_MONOXIDE))
        val diazenyl = imidogen.attach(N, order = 2); val diazenylShape = at(0 to xy(0f, 0f), 1 to polar(-90f), 2 to polar(-90f + 112f)); known(diazenyl, MoleculeElement.DIAZENYL, "H–N=N•", offsets = diazenylShape, basedOn = listOf(MoleculeElement.IMIDOGEN))
        val hydrogenCyanide = cyano.attach(H); known(hydrogenCyanide, MoleculeElement.HYDROGEN_CYANIDE, "H–C≡N", offsets = at(0 to xy(-1f, 0f), 1 to xy(0f, 0f), 2 to xy(1f, 0f)), ionizationEnergy = 13.60f, basedOn = listOf(MoleculeElement.CYANO))
        val water = hydroxyl.attach(H); known(water, MoleculeElement.WATER, "H–O–H", offsets = at(0 to xy(0f, -0.3f), 1 to polar(180f - 52.25f), 2 to polar(52.25f)), ionizationEnergy = 12.621f, basedOn = listOf(MoleculeElement.HYDROXYL))
        val hydroperoxyl = hydroxyl.attach(O); val hydroperoxylShape = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f)); known(hydroperoxyl, MoleculeElement.HYDROPEROXYL, "H–O–O•", offsets = hydroperoxylShape, ionizationEnergy = 11.35f, basedOn = listOf(MoleculeElement.HYDROXYL))
        val formyl = carbonyl.attach(H); val formylShape = at(0 to xy(0f, 0f), 1 to polar(-90f), 2 to polar(-90f + 124.4f)); known(formyl, MoleculeElement.FORMYL, "H–C•=O", offsets = formylShape, ionizationEnergy = 8.12f, basedOn = listOf(MoleculeElement.CARBONYL))
        val isocyanato = carbonyl.attach(N, order = 2); val isocyanatoShape = at(0 to xy(0f, 0f), 1 to xy(1f, 0f), 2 to xy(-1f, 0f)); known(isocyanato, MoleculeElement.ISOCYANATO, "•N=C=O", offsets = isocyanatoShape, basedOn = listOf(MoleculeElement.CARBONYL))
        val carbonDioxide = carbonyl.attach(O, order = 2); known(carbonDioxide, MoleculeElement.CARBON_DIOXIDE, "O=C=O", offsets = at(0 to xy(0f, 0f), 1 to xy(-1f, 0f), 2 to xy(1f, 0f)), ionizationEnergy = 13.777f, basedOn = listOf(MoleculeElement.CARBONYL))

        // --- четырехатомные ---
        val methyl = methylene.attach(H); val methylShape = methyleneShape + (3 to (methyleneShape.getValue(0) + polar(-90f))); known(methyl, MoleculeElement.METHYL, "•CH₃", offsets = methylShape, ionizationEnergy = 9.84f, basedOn = listOf(MoleculeElement.METHYLENE))
        val acetylene = ethynyl.attach(H); val acetyleneShape = ethynylShape + (3 to xy(1.5f, 0f)); known(acetylene, MoleculeElement.ACETYLENE, "HC≡CH", offsets = acetyleneShape, ionizationEnergy = 11.400f, basedOn = listOf(MoleculeElement.ETHYNYL))
        val ammonia = amino.attach(H); val ammoniaShape = aminoShape + (3 to (aminoShape.getValue(0) + polar(-90f))); known(ammonia, MoleculeElement.AMMONIA, "NH₃", offsets = ammoniaShape, ionizationEnergy = 10.07f, basedOn = listOf(MoleculeElement.AMINO_RADICAL))
        val aminoxyl = amino.attach(O); val aminoxylShape = aminoShape + (3 to (aminoShape.getValue(0) + polar(-90f))); known(aminoxyl, MoleculeElement.AMINOXYL, "H₂N–O•", offsets = aminoxylShape, basedOn = listOf(MoleculeElement.AMINO_RADICAL))
        val hydrotrioxyl = hydroperoxyl.attach(O); val hydrotrioxylShape = hydroperoxylShape + (3 to (hydroperoxylShape.getValue(2) + xy(0.5f, 0.8f))); known(hydrotrioxyl, MoleculeElement.HYDROTRIOXYL, "H–O–O–O•", offsets = hydrotrioxylShape, basedOn = listOf(MoleculeElement.HYDROPEROXYL))
        val hydrogenPeroxide = hydroperoxyl.attach(H); known(hydrogenPeroxide, MoleculeElement.HYDROGEN_PEROXIDE, "H–O–O–H", offsets = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f)), ionizationEnergy = 10.58f, basedOn = listOf(MoleculeElement.HYDROPEROXYL))
        val isocyanicAcid = isocyanato.attach(H); val isocyanicAcidShape = isocyanatoShape + (3 to (isocyanatoShape.getValue(2) + polar(124f))); known(isocyanicAcid, MoleculeElement.ISOCYANIC_ACID, "H–N=C=O", offsets = isocyanicAcidShape, basedOn = listOf(MoleculeElement.ISOCYANATO))
        val formaldehyde = formyl.attach(H); known(formaldehyde, MoleculeElement.FORMALDEHYDE, "H₂C=O", offsets = at(0 to xy(0f, 0f), 1 to xy(0f, -1f), 2 to xy(-0.87f, 0.5f), 3 to xy(0.87f, 0.5f)), ionizationEnergy = 10.88f, basedOn = listOf(MoleculeElement.FORMYL))
        val vinylidene = dicarbonDouble.attach(H, nodeId = 0).attach(H, nodeId = 0); val vinylideneShape = at(0 to xy(-0.5f, 0f), 1 to xy(0.5f, 0f), 2 to xy(-0.9f, -0.9f), 3 to xy(-0.9f, 0.9f)); known(vinylidene, MoleculeElement.VINYLIDENE, "H₂C=C:", offsets = vinylideneShape, basedOn = listOf(MoleculeElement.DICARBON_DOUBLE))
        val ethenediyl = dicarbonDouble.attach(H, nodeId = 0).attach(H, nodeId = 1); val ethenediylShape = at(0 to xy(-0.5f, 0f), 1 to xy(0.5f, 0f), 2 to xy(-0.9f, -0.9f), 3 to xy(0.9f, 0.9f)); known(ethenediyl, MoleculeElement.ETHENEDIYL, "•CH=CH•", offsets = ethenediylShape, basedOn = listOf(MoleculeElement.DICARBON_DOUBLE))
        val hydrazinediyl = imidogen.attach(imidogen); val hydrazinediylShape = at(0 to xy(-0.5f, 0f), 1 to xy(-1f, -0.8f), 2 to xy(0.5f, 0f), 3 to xy(1f, 0.8f)); known(hydrazinediyl, MoleculeElement.HYDRAZINEDIYL, "•NH–NH•", offsets = hydrazinediylShape, basedOn = listOf(MoleculeElement.IMIDOGEN))
        val diazene = diazenyl.attach(H); val diazeneShape = diazenylShape + (3 to (diazenylShape.getValue(2) + polar(90f))); known(diazene, MoleculeElement.DIAZENE, "HN=NH", offsets = diazeneShape, basedOn = listOf(MoleculeElement.DIAZENYL))
        val methyleneamidogen = methylene.attach(N, order = 2); val methyleneamidogenShape = methyleneShape + (3 to (methyleneShape.getValue(0) + polar(-90f))); known(methyleneamidogen, MoleculeElement.METHYLENEAMIDOGEN, "H₂C=N•", offsets = methyleneamidogenShape, basedOn = listOf(MoleculeElement.METHYLENE))
        val formyloxyl = formyl.attach(O); val formyloxylShape = formylShape + (3 to (formylShape.getValue(0) + polar(150f))); known(formyloxyl, MoleculeElement.FORMYLOXYL, "H–C(=O)–O•", offsets = formyloxylShape, basedOn = listOf(MoleculeElement.FORMYL))

        // --- пятиатомные ---
        val methane = methyl.attach(H); known(methane, MoleculeElement.METHANE, "CH₄", offsets = at(0 to xy(0f, 0f), 1 to polar(45f), 2 to polar(135f), 3 to polar(225f), 4 to polar(315f)), ionizationEnergy = 12.61f, basedOn = listOf(MoleculeElement.METHYL))
        val vinyl = vinylidene.attach(H); val vinylShape = vinylideneShape + (4 to (vinylideneShape.getValue(1) + polar(60f))); known(vinyl, MoleculeElement.VINYL, "H₂C=CH•", offsets = vinylShape, basedOn = listOf(MoleculeElement.VINYLIDENE))
        val trioxidane = hydrotrioxyl.attach(H); known(trioxidane, MoleculeElement.TRIOXIDANE, "H–O–O–O–H", offsets = at(0 to xy(-1f, 0.2f), 1 to xy(-1.6f, -0.55f), 2 to xy(0f, -0.2f), 3 to xy(1f, 0.2f), 4 to xy(1.6f, 0.95f)), basedOn = listOf(MoleculeElement.HYDROTRIOXYL))
        val ethylidyne = methyl.attach(C); val ethylidyneShape = at(0 to xy(-0.5f, 0f), 1 to xy(-0.9f, -0.9f), 2 to xy(-1.5f, 0f), 3 to xy(-0.9f, 0.9f), 4 to xy(0.5f, 0f)); known(ethylidyne, MoleculeElement.ETHYLIDYNE, "CH₃–C•", offsets = ethylidyneShape, basedOn = listOf(MoleculeElement.METHYL))
        val formicAcid = formyloxyl.attach(H); val formicAcidShape = formyloxylShape + (4 to (formyloxylShape.getValue(3) + polar(-136f))); known(formicAcid, MoleculeElement.FORMIC_ACID, "H–C(=O)–OH", offsets = formicAcidShape, ionizationEnergy = 11.33f, basedOn = listOf(MoleculeElement.FORMYLOXYL))
        val methanimine = methyleneamidogen.attach(H); val methanimineShape = methyleneamidogenShape + (4 to (methyleneamidogenShape.getValue(3) + polar(-20f))); known(methanimine, MoleculeElement.METHANIMINE, "H₂C=NH", offsets = methanimineShape, basedOn = listOf(MoleculeElement.METHYLENEAMIDOGEN))
        val aminocarbene = amino.attach(methylidyne); val aminocarbeneShape = aminoShape + (3 to (aminoShape.getValue(0) + polar(-90f))) + (4 to (aminoShape.getValue(0) + polar(-90f) + polar(-20f))); known(aminocarbene, MoleculeElement.AMINOCARBENE, "H₂N–CH:", offsets = aminocarbeneShape, basedOn = listOf(MoleculeElement.AMINO_RADICAL))
        val methoxyl = methyl.attach(O); val methoxylShape = methylShape + (4 to (methylShape.getValue(0) + polar(90f))); known(methoxyl, MoleculeElement.METHOXYL, "CH₃–O•", offsets = methoxylShape, ionizationEnergy = 10.72f, basedOn = listOf(MoleculeElement.METHYL))
        val hydroxylamine = aminoxyl.attach(H); val hydroxylamineShape = aminoxylShape + (4 to (aminoxylShape.getValue(3) + polar(90f - 101.4f))); known(hydroxylamine, MoleculeElement.HYDROXYLAMINE, "H₂N–OH", offsets = hydroxylamineShape, ionizationEnergy = 10.0f, basedOn = listOf(MoleculeElement.AMINOXYL))
        val propadienylidene = tricarbon.attach(H, nodeId = 0).attach(H, nodeId = 0); val propadienylideneShape = tricarbonShape + (3 to (tricarbonShape.getValue(0) + xy(-0.4f, -0.9f))) + (4 to (tricarbonShape.getValue(0) + xy(-0.4f, 0.9f))); known(propadienylidene, MoleculeElement.PROPADIENYLIDENE, "H₂C=C=C:", offsets = propadienylideneShape, basedOn = listOf(MoleculeElement.TRICARBON))  // оба H на один крайний углерод, как у винилидена
        val carbamoyl = carbonyl.attach(amino); val carbamoylN = polar(30f); val carbamoylShape = at(0 to xy(0f, 0f), 1 to polar(-90f), 2 to carbamoylN, 3 to (carbamoylN + polar(-30f)), 4 to (carbamoylN + polar(90f))); known(carbamoyl, MoleculeElement.CARBAMOYL, "H₂N–C•=O", offsets = carbamoylShape, basedOn = listOf(MoleculeElement.CARBONYL))
        val hydrazinyl = hydrazinediyl.attach(H, nodeId = 0); val hydrazinylShape = hydrazinediylShape + (4 to (hydrazinediylShape.getValue(0) + polar(135f))); known(hydrazinyl, MoleculeElement.HYDRAZINYL, "H₂N–NH•", offsets = hydrazinylShape, basedOn = listOf(MoleculeElement.HYDRAZINEDIYL))

        // --- шестиатомные ---
        val ethylene = vinyl.attach(H); val ethyleneShape = vinylShape + (5 to (vinylShape.getValue(1) + polar(-60f))); known(ethylene, MoleculeElement.ETHYLENE, "H₂C=CH₂", offsets = ethyleneShape, ionizationEnergy = 10.5138f, basedOn = listOf(MoleculeElement.VINYL))
        val ethylidene = ethylidyne.attach(H); val ethylideneShape = ethylidyneShape + (5 to (ethylidyneShape.getValue(4) + polar(60f))); known(ethylidene, MoleculeElement.ETHYLIDENE, "CH₃–CH:", offsets = ethylideneShape, basedOn = listOf(MoleculeElement.ETHYLIDYNE))
        val hydrazine = hydrazinyl.attach(H, nodeId = 2); val hydrazineShape = hydrazinylShape + (5 to (hydrazinylShape.getValue(2) + polar(-45f))); known(hydrazine, MoleculeElement.HYDRAZINE, "H₂N–NH₂", offsets = hydrazineShape, ionizationEnergy = 8.1f, basedOn = listOf(MoleculeElement.HYDRAZINYL))
        val propargyl = propadienylidene.attach(H); val propargylShape = propadienylideneShape + (5 to (tricarbonShape.getValue(2) + xy(0.4f, -0.9f))); known(propargyl, MoleculeElement.PROPARGYL, "H₂C=C=CH•", offsets = propargylShape, ionizationEnergy = 8.67f, basedOn = listOf(MoleculeElement.PROPADIENYLIDENE))
        val ethanediyl = dicarbonSingle.attach(H, nodeId = 0).attach(H, nodeId = 0).attach(H, nodeId = 1).attach(H, nodeId = 1); val ethanediylShape = at(0 to xy(-0.5f, 0f), 1 to xy(0.5f, 0f), 2 to xy(-0.9f, -0.9f), 3 to xy(-0.9f, 0.9f), 4 to xy(0.9f, -0.9f), 5 to xy(0.9f, 0.9f)); known(ethanediyl, MoleculeElement.ETHANEDIYL, "•CH₂–CH₂•", offsets = ethanediylShape, basedOn = listOf(MoleculeElement.DICARBON_SINGLE))
        val tetraoxidane = hydrotrioxyl.attach(hydroxyl); known(tetraoxidane, MoleculeElement.TETRAOXIDANE, "H–O–O–O–O–H", offsets = at(0 to xy(-1.5f, 0.2f), 1 to xy(-2.1f, 0.95f), 2 to xy(-0.5f, -0.2f), 3 to xy(0.5f, 0.2f), 4 to xy(1.5f, -0.2f), 5 to xy(2.1f, -0.95f)), basedOn = listOf(MoleculeElement.HYDROTRIOXYL))  // цепочка по узлам 0–2–3–4, H на концах (1 и 5)
        val methylaminyl = methyl.attach(imidogen); val methylaminylShape = methylShape + (4 to (methylShape.getValue(0) + polar(90f))) + (5 to (methylShape.getValue(0) + polar(90f) + polar(22f))); known(methylaminyl, MoleculeElement.METHYLAMINYL, "CH₃–NH•", offsets = methylaminylShape, basedOn = listOf(MoleculeElement.METHYL))
        val methanol = methoxyl.attach(H); val methanolShape = methoxylShape + (5 to (methoxylShape.getValue(4) + polar(-90f + 108.6f))); known(methanol, MoleculeElement.METHANOL, "CH₃–OH", offsets = methanolShape, ionizationEnergy = 10.84f, basedOn = listOf(MoleculeElement.METHOXYL))  // от направления O→C на угол C–O–H 108.6°
        val acetyl = carbonyl.attach(methyl); val acetylShape = at(0 to xy(0.5f, 0f), 1 to xy(1.1f, -0.8f), 2 to xy(-0.5f, 0f), 3 to xy(-0.9f, -0.9f), 4 to xy(-1.5f, 0f), 5 to xy(-0.9f, 0.9f)); known(acetyl, MoleculeElement.ACETYL, "CH₃–C•=O", offsets = acetylShape, basedOn = listOf(MoleculeElement.CARBONYL))
        val carbamoyloxyl = carbamoyl.attach(O); val carbamoyloxylShape = carbamoylShape + (5 to (carbamoylShape.getValue(0) + polar(150f))); known(carbamoyloxyl, MoleculeElement.CARBAMOYLOXYL, "H₂N–C(=O)–O•", offsets = carbamoyloxylShape, basedOn = listOf(MoleculeElement.CARBAMOYL))
        val formamide = carbamoyl.attach(H); val formamideShape = carbamoylShape + (5 to (carbamoylShape.getValue(0) + polar(150f))); known(formamide, MoleculeElement.FORMAMIDE, "H–C(=O)–NH₂", offsets = formamideShape, ionizationEnergy = 10.16f, basedOn = listOf(MoleculeElement.CARBAMOYL))

        // --- семиатомные ---
        val ethyl = ethylidene.attach(H); val ethylShape = ethylideneShape + (6 to (ethylideneShape.getValue(4) + polar(-60f))); known(ethyl, MoleculeElement.ETHYL, "CH₃–CH₂•", offsets = ethylShape, basedOn = listOf(MoleculeElement.ETHYLIDENE))
        val acetaldehyde = acetyl.attach(H); val acetaldehydeShape = acetylShape + (6 to (acetylShape.getValue(0) + polar(60f))); known(acetaldehyde, MoleculeElement.ACETALDEHYDE, "CH₃–C(=O)–H", offsets = acetaldehydeShape, ionizationEnergy = 10.229f, basedOn = listOf(MoleculeElement.ACETYL))
        val methylamine = methylaminyl.attach(H); val methylamineShape = methylaminylShape + (6 to (methylaminylShape.getValue(4) + polar(158f))); known(methylamine, MoleculeElement.METHYLAMINE, "CH₃–NH₂", offsets = methylamineShape, ionizationEnergy = 8.9f, basedOn = listOf(MoleculeElement.METHYLAMINYL))
        val propyne = ethynyl.attach(methyl); val propyneShape = ethynylShape + (3 to xy(1.5f, 0f)) + (4 to xy(1.9f, -0.9f)) + (5 to xy(2.5f, 0f)) + (6 to xy(1.9f, 0.9f)); known(propyne, MoleculeElement.PROPYNE, "HC≡C–CH₃", offsets = propyneShape, ionizationEnergy = 10.36f, basedOn = listOf(MoleculeElement.ETHYNYL))
        val propadiene = propargyl.attach(H); val propadieneShape = propargylShape + (6 to (tricarbonShape.getValue(2) + xy(0.4f, 0.9f))); known(propadiene, MoleculeElement.PROPADIENE, "H₂C=C=CH₂", offsets = propadieneShape, ionizationEnergy = 9.69f, basedOn = listOf(MoleculeElement.PROPARGYL))
        val acetyloxyl = acetyl.attach(O); val acetyloxylShape = acetylShape + (6 to (acetylShape.getValue(0) + polar(60f))); known(acetyloxyl, MoleculeElement.ACETYLOXYL, "CH₃–C(=O)–O•", offsets = acetyloxylShape, basedOn = listOf(MoleculeElement.ACETYL))
        val carbamicAcid = carbamoyloxyl.attach(H); val carbamicAcidShape = carbamoyloxylShape + (6 to (carbamoyloxylShape.getValue(5) + polar(-136f))); known(carbamicAcid, MoleculeElement.CARBAMIC_ACID, "H₂N–C(=O)–OH", offsets = carbamicAcidShape, basedOn = listOf(MoleculeElement.CARBAMOYLOXYL))


        // --- восьмиатомные ---
        val urea = carbamoyl.attach(amino); val ureaN = polar(150f); val ureaShape = carbamoylShape + (5 to ureaN) + (6 to (ureaN + polar(-150f))) + (7 to (ureaN + polar(90f))); known(urea, MoleculeElement.UREA, "H₂N–C(=O)–NH₂", offsets = ureaShape, basedOn = listOf(MoleculeElement.CARBAMOYL))
        val aceticAcid = acetyloxyl.attach(H); val aceticAcidShape = acetyloxylShape + (7 to (acetyloxylShape.getValue(6) + polar(-14f))); known(aceticAcid, MoleculeElement.ACETIC_ACID, "CH₃–C(=O)–OH", offsets = aceticAcidShape, ionizationEnergy = 10.65f, basedOn = listOf(MoleculeElement.ACETYLOXYL))
        val ethoxyl = ethyl.attach(O); val ethoxylShape = ethylShape + (7 to (ethylShape.getValue(4) + xy(1f, 0f))); known(ethoxyl, MoleculeElement.ETHOXYL, "CH₃–CH₂–O•", offsets = ethoxylShape, basedOn = listOf(MoleculeElement.ETHYL))
        val ethane = ethyl.attach(H); known(ethane, MoleculeElement.ETHANE, "CH₃–CH₃", offsets = at(0 to xy(-0.5f, 0f), 1 to xy(-0.9f, -0.9f), 2 to xy(-1.5f, 0f), 3 to xy(-0.9f, 0.9f), 4 to xy(0.5f, 0f), 5 to xy(0.9f, -0.9f), 6 to xy(1.5f, 0f), 7 to xy(0.9f, 0.9f)), ionizationEnergy = 11.52f, basedOn = listOf(MoleculeElement.ETHYL))

        // --- девятиатомные ---
        val ethanol = ethoxyl.attach(H); val ethanolShape = ethoxylShape + (8 to (ethoxylShape.getValue(7) + xy(0.4f, -0.9f))); known(ethanol, MoleculeElement.ETHANOL, "CH₃–CH₂–OH", offsets = ethanolShape, ionizationEnergy = 10.48f, basedOn = listOf(MoleculeElement.ETHOXYL))

        //////


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