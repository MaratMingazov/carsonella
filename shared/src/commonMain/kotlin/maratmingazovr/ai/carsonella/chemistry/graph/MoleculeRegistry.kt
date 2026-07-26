package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element

/** Курируемая запись реестра: что знаем об известной молекуле сверх её структуры. */
data class KnownMolecule(val nameEn: String, val nameRu: String, val description: String = "")

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
    private val dicarbon = KnownMolecule("Dicarbon", "Дикарбон", "экзотический бирадикал (пламя/кометы); порядок связи модельно неточен")

    private val byCanonical: Map<String, KnownMolecule> = listOf(
        // --- двухатомные ---
        mol(listOf(h(0), h(1)), listOf(bond(0, 1)))                              to KnownMolecule("Dihydrogen", "Водород"),
        mol(listOf(o(0), o(1)), listOf(bond(0, 1, 2)))                           to KnownMolecule("Dioxygen", "Кислород"),      // O=O
        mol(listOf(n(0), n(1)), listOf(bond(0, 1, 3)))                           to KnownMolecule("Dinitrogen", "Азот"),        // N≡N

        // --- малые неорганические / простые ---
        mol(listOf(o(0), h(1), h(2)), listOf(bond(0, 1), bond(0, 2)))            to KnownMolecule("Water", "Вода"),
        mol(listOf(o(0), o(1), h(2), h(3)), listOf(bond(0, 1), bond(0, 2), bond(1, 3))) to KnownMolecule("Hydrogen peroxide", "Перекись водорода"), // H–O–O–H
        mol(listOf(n(0), h(1), h(2), h(3)), listOf(bond(0, 1), bond(0, 2), bond(0, 3))) to KnownMolecule("Ammonia", "Аммиак"),
        mol(listOf(c(0), o(1), o(2)), listOf(bond(0, 1, 2), bond(0, 2, 2)))      to KnownMolecule("Carbon dioxide", "Углекислый газ"), // O=C=O
        mol(listOf(h(0), c(1), n(2)), listOf(bond(0, 1), bond(1, 2, 3)))         to KnownMolecule("Hydrogen cyanide", "Циановодород"),  // H–C≡N

        // --- углеводороды ---
        mol(listOf(c(0), h(1), h(2), h(3), h(4)), listOf(bond(0, 1), bond(0, 2), bond(0, 3), bond(0, 4))) to KnownMolecule("Methane", "Метан"),
        mol(listOf(c(0), c(1), h(2), h(3)), listOf(bond(0, 1, 3), bond(0, 2), bond(1, 3))) to KnownMolecule("Acetylene", "Ацетилен"),     // HC≡CH
        mol(listOf(c(0), c(1), h(2), h(3), h(4), h(5)),
            listOf(bond(0, 1, 2), bond(0, 2), bond(0, 3), bond(1, 4), bond(1, 5))) to KnownMolecule("Ethylene", "Этилен"),        // H₂C=CH₂
        mol(listOf(c(0), c(1), h(2), h(3), h(4), h(5), h(6), h(7)), listOf(bond(0, 1), bond(0, 2), bond(0, 3), bond(0, 4), bond(1, 5), bond(1, 6), bond(1, 7))) to KnownMolecule("Ethane", "Этан"),

        // --- кислородсодержащая органика ---
        mol(listOf(c(0), o(1), h(2), h(3)), listOf(bond(0, 1, 2), bond(0, 2), bond(0, 3))) to KnownMolecule("Formaldehyde", "Формальдегид"), // H₂C=O
        mol(listOf(c(0), o(1), h(2), h(3), h(4), h(5)), listOf(bond(0, 1), bond(0, 2), bond(0, 3), bond(0, 4), bond(1, 5))) to KnownMolecule("Methanol", "Метанол"),        // CH₃–OH
        mol(listOf(c(0), o(1), o(2), h(3), h(4)), listOf(bond(0, 1, 2), bond(0, 2), bond(0, 3), bond(2, 4))) to KnownMolecule("Formic acid", "Муравьиная кислота"), // H–C(=O)–O–H
        mol(listOf(c(0), c(1), o(2), h(3), h(4), h(5), h(6), h(7), h(8)),
            listOf(bond(0, 1), bond(1, 2), bond(0, 3), bond(0, 4), bond(0, 5), bond(1, 6), bond(1, 7), bond(2, 8))) to KnownMolecule("Ethanol", "Этанол"),         // CH₃–CH₂–OH

        // --- радикалы (есть свободный валентный слот) ---
        mol(listOf(o(0), h(1)), listOf(bond(0, 1)))                              to KnownMolecule("Hydroxyl", "Гидроксил"),      // •OH
        mol(listOf(c(0), h(1), h(2), h(3)), listOf(bond(0, 1), bond(0, 2), bond(0, 3))) to KnownMolecule("Methyl", "Метил"),           // •CH₃
        mol(listOf(n(0), h(1), h(2)), listOf(bond(0, 1), bond(0, 2)))            to KnownMolecule("Amino radical", "Аминорадикал"), // •NH₂
        mol(listOf(h(0), o(1), o(2)), listOf(bond(0, 1), bond(1, 2)))            to KnownMolecule("Hydroperoxyl", "Гидропероксил"), // H–O–O•
        mol(listOf(c(0), c(1), h(2), h(3), h(4), h(5), h(6)), listOf(bond(0, 1), bond(0, 2), bond(0, 3), bond(0, 4), bond(1, 5), bond(1, 6))) to KnownMolecule("Ethyl", "Этил"),             // •C₂H₅
        mol(listOf(c(0), h(1)), listOf(bond(0, 1)))                              to KnownMolecule("Methylidyne", "Метилидин"),   // •CH  (3 слота)
        mol(listOf(c(0), h(1), h(2)), listOf(bond(0, 1), bond(0, 2)))            to KnownMolecule("Methylene", "Метилен"),       // :CH₂ (2 слота)
        mol(listOf(h(0), c(1), c(2)), listOf(bond(0, 1), bond(1, 2, 3)))         to KnownMolecule("Ethynyl", "Этинил"),          // H–C≡C•
        mol(listOf(c(0), c(1), h(2), h(3), h(4)), listOf(bond(0, 1, 2), bond(0, 2), bond(0, 3), bond(1, 4)))
                                                                                 to KnownMolecule("Vinyl", "Винил"),            // H₂C=CH•
        mol(listOf(c(0), o(1), h(2)), listOf(bond(0, 1, 2), bond(0, 2)))         to KnownMolecule("Formyl", "Формил"),           // H–C•=O
        mol(listOf(c(0), n(1)), listOf(bond(0, 1, 3)))                           to KnownMolecule("Cyano", "Циано"),             // •C≡N

        // --- дикарбон C₂: все три порядка связи → одно имя «Дикарбон» ---
        mol(listOf(c(0), c(1)), listOf(bond(0, 1)))                              to dicarbon,   // •C–C•
        mol(listOf(c(0), c(1)), listOf(bond(0, 1, 2)))                           to dicarbon,   // C=C
        mol(listOf(c(0), c(1)), listOf(bond(0, 1, 3)))                           to dicarbon,   // •C≡C•
    ).associate { (graph, known) -> graph.canonical to known }

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