package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element

/**
 * Ядро графовой модели молекулы..
 *
 * Молекула = граф: атомы — узлы [AtomNode], связи — рёбра [Bond].
 * Сущность держит СТРУКТУРУ, а не идентичность.
 * Так этанол (C–C–O) и диметиловый эфир (C–O–C) станут двумя разными графами с одной формулой
 *
 * Граф моделирует только ЯДЕРНЫЙ скелет + связи. Электроны (ионизация) — динамическое состояние
 * сущности (`state.electrons`), как у атома сегодня; в графе их нет. Поэтому узел — это лишь изотоп.
 *
 */

/** Узел графа — атом конкретного изотопа. [isotope] несёт протоны/нейтроны/символ через [Element.details]. */
data class AtomNode(
    val localId: Int,        // номер узла, локальный для этой молекулы; на него ссылаются связи
    val isotope: Element,    // HYDROGEN, OXYGEN_16 — ядро узла (p/n/символ)
)

/**
 * Ребро графа — химическая связь между двумя узлами (по их [AtomNode.localId]). Ненаправленное.
 *
 * [order] — кратность связи = число общих электронных пар между атомами:
 *  - 1 — одинарная (H–H, C–C, O–H);
 *  - 2 — двойная: O=O в молекуле кислорода O₂;
 *  - 3 — тройная: N≡N в молекуле азота N₂, C≡C в ацетилене.
 *
 * Кратность — ЧАСТЬ ИДЕНТИЧНОСТИ молекулы. Пример: двойная связь O=O (order = 2) и одинарная
 * перекисная связь O–O (order = 1, как в H₂O₂) состоят из тех же атомов, но это РАЗНЫЕ вещества.
 * Поэтому каноникализация обязана учитывать order, иначе их не различить.
 *
 * Потолок — тройная (3): для химии CHNO (наш случай — малые молекулы, жизнь) выше не встречается.
 * Четверные–шестерные связи существуют лишь в экзотике переходных металлов (Re₂Cl₈²⁻, Cr–Cr, Mo₂)
 * и нам не нужны; промышленный формат MOL/SDF тоже кодирует реальные кратности как 1/2/3.
 */
data class Bond(
    val atom1: Int,          // localId одного узла
    val atom2: Int,          // localId другого узла
    val order: Int,          // кратность 1..3;
)


data class MoleculeGraph(
    val nodes: List<AtomNode>,
    val bonds: List<Bond>,
) {
    init {
        val ids = nodes.map { it.localId }
        require(ids.size == ids.toSet().size) { "Дубли localId среди узлов: $ids" }
        val idSet = ids.toHashSet()
        for (bond in bonds) {
            require(bond.atom1 != bond.atom2) { "Петля запрещена: связь $bond соединяет узел сам с собой" }
            require(bond.atom1 in idSet && bond.atom2 in idSet) { "Связь $bond ссылается на несуществующий узел" }
            require(bond.order in 1..3) { "Кратность связи $bond вне диапазона 1..3 (см. комментарий к Bond)" }
        }
    }

    /**
     * Масса молекулы — сумма нуклонов (p + n) всех узлов.
     * Кэшируется один раз при построении: граф иммутабелен, значение не меняется за его жизнь
     */
    val mass: Float = nodes.sumOf { it.isotope.details.p + it.isotope.details.n }.toFloat()

    /** Сумма протонов всех узлов. Кэшируется один раз */
    val protons: Int = nodes.sumOf { it.isotope.details.p }

    private val isotopeById: Map<Int, Element> = nodes.associate { it.localId to it.isotope }

    /**
     * Слабейшая связь молекулы и её энергия — ПОРОГ ДИССОЦИАЦИИ.
     * Слабейшая связь требует меньше всего энергии → рвётся первой.
     *
     * `null`, если связей нет ИЛИ тип связи не в каталоге (для CHNO не случается, но `Float?` честно это выражает).
     *
     * Нам это нужно, чтобы понять какая связь разорветс во время диссоциации.
     * Но если молекула кольцо, тогда после разрыва молекула остается
     */
     val weakestBondAndEnergy: Pair<Bond, Float>? = bonds
        .mapNotNull { bond -> BondEnergy.of(isotopeById.getValue(bond.atom1), isotopeById.getValue(bond.atom2), bond.order)?.let { energy -> bond to energy } }
        .minByOrNull { it.second }

    /**
     * Свободные валентные слоты каждого узла: localId → сколько ещё связей узел может образовать/усилить.
     * Кэшируется один раз при построении (граф иммутабелен): один проход по связям копит «занятые»
     * слоты (сумма order у инцидентных рёбер), затем `валентность − занятые` на узел. Так [freeSlots],
     * [hasFreeSlot], [firstFreeSlotNode], [strengthenableBonds] читают слоты за O(1) вместо пересчёта.
     */
    private val freeSlotsById: Map<Int, Int> = run {
        val used = HashMap<Int, Int>()
        for (bond in bonds) {
            used[bond.atom1] = (used[bond.atom1] ?: 0) + bond.order
            used[bond.atom2] = (used[bond.atom2] ?: 0) + bond.order
        }
        nodes.associate { it.localId to (it.isotope.valence() - (used[it.localId] ?: 0)) }
    }

    /**
     * Узнаем есть ли еще валентные слоты у конкретного атома в молекуле (localId - номер узла)
     * Если > 0 значит этот атом в молекуле еще может образовать новую валентную связь, либо усилить сузествующую связь
     * Например, когда Углерод + Углерод -> C-C то теперь либо связь усилится С=С
     * либо образуется еще связь H-C-C
     */
    fun freeSlots(localId: Int): Int =
        freeSlotsById[localId] ?: error("Узла с localId=$localId нет в графе")

    /** Есть ли в молекуле хоть один незакрытый валентный слот (есть куда расти / что усиливать). */
    val hasFreeSlot: Boolean = nodes.any { freeSlots(it.localId) > 0 }

    /**
     * localId узла со свободным слотом для новой связи — наименьший среди кандидатов (детерминированно),
     * либо null, если свободных слотов нет. Межатомную геометрию внутри молекулы физика пока не моделирует,
     * поэтому узел выбирается детерминированно, а не «ближайший к партнёру» (см. 3b в docs/molecule-graph.md).
     */
    fun firstFreeSlotNode(): Int? = nodes.map { it.localId }.filter { freeSlots(it) > 0 }.minOrNull()

    /**
     * Слияние двух молекул в одну (рост, 3b): этот граф остаётся якорем (его нумерация не меняется), к нему
     * дописывается [other], а между узлом [thisNode] (в нумерации этого графа) и узлом [otherNode]
     * (в нумерации [other]) добавляется связь кратности [bondOrder].
     *
     * Узлы [other] переиндексируются: `localId += offset`, где `offset` больше любого localId этого графа —
     * чтобы локальные нумерации не столкнулись (у обоих может быть узел 0). Топология при этом НЕ меняется,
     * это лишь переименование меток (см. [canonical]). Зеркало диссоциации: рост склеивает графы, распад их
     * разрезает (одна графовая хирургия). СШИВАЕТ графы именно новое ребро — без него вышли бы две несвязные
     * компоненты (формально две молекулы) в одном списке узлов.
     *
     * Пример ·OH + H· → H₂O:
     * ```
     * this  = ·OH:  nodes [O@0, H@1], bonds [(0–1)]      other = H·: nodes [H@0], bonds []
     * thisNode = 0 (O), otherNode = 0 (H), bondOrder = 1
     *   offset       = max(0,1) + 1 = 2
     *   shiftedNodes = [H@0] → [H@2]                      (узлы other в свободный диапазон)
     *   shiftedBonds = []                                 (внутренних связей у атома нет)
     *   newBond      = Bond(0, 0+2=2, 1)                  ← сшивает O(0) и H(2)
     *   → nodes [O@0, H@1, H@2], bonds [(0–1), (0–2)]     = H₂O
     * ```
     *
     * merge — чистая операция и НЕ проверяет валентность: что у [thisNode]/[otherNode] есть свободный слот,
     * гарантирует вызывающий (правило роста через [freeSlots]). Атом-партнёр оборачивается в тривиальный
     * одноузловой граф и сливается тем же merge (атом = вырожденная молекула, §8).
     */
    fun merge(other: MoleculeGraph, thisNode: Int, otherNode: Int, bondOrder: Int): MoleculeGraph {
        require(nodes.any { it.localId == thisNode }) { "Узла thisNode=$thisNode нет в этом графе" }
        require(other.nodes.any { it.localId == otherNode }) { "Узла otherNode=$otherNode нет в other" }
        val offset = nodes.maxOf { it.localId } + 1
        val shiftedNodes = other.nodes.map { AtomNode(it.localId + offset, it.isotope) }
        val shiftedBonds = other.bonds.map { Bond(it.atom1 + offset, it.atom2 + offset, it.order) }
        val newBond = Bond(thisNode, otherNode + offset, bondOrder)
        return MoleculeGraph(nodes = nodes + shiftedNodes, bonds = bonds + shiftedBonds + newBond)
    }

    /**
     * Связи, которые можно усилить (3c): `order < 3` И у ОБОИХ концов есть свободный слот (усиление
     * order→order+1 занимает по одному слоту у каждого атома). Так, O–O усиливаема (по слоту на каждом O),
     * а звено цепи O–O–O — нет (средний атом насыщен). Пусто → усиливать нечего.
     */
    fun strengthenableBonds(): List<Bond> =
        bonds.filter { it.order < 3 && freeSlots(it.atom1) > 0 && freeSlots(it.atom2) > 0 }

    /**
     * Усиление связи (3c): вернуть копию графа, где кратность связи между узлами [atom1] и [atom2]
     * увеличена на 1 (O–O → O=O, N=N → N≡N). Так эмёрджентно рождаются кратные связи, когда рост новым
     * партнёром недоступен/невыгоден (см. правило BondStrengthening).
     *
     * Предусловие (гарантирует вызывающий, обычно через [strengthenableBonds]): связь существует, её
     * order < 3, у обоих концов есть свободный слот. Потолок 3 страхует инвариант конструктора.
     */
    fun strengthenBond(atom1: Int, atom2: Int): MoleculeGraph {
        require(bonds.any { sameBond(it, atom1, atom2) }) { "Связи $atom1–$atom2 нет в графе" }
        val newBonds = bonds.map { if (sameBond(it, atom1, atom2)) Bond(it.atom1, it.atom2, it.order + 1) else it }
        return MoleculeGraph(nodes = nodes, bonds = newBonds)
    }

    /**
     * Разрыв связи — ЗЕРКАЛО [merge] (одна графовая хирургия: рост склеивает графы, распад их разрезает).
     * Убираем ребро [atom1]–[atom2] и возвращаем связные компоненты — каждую самостоятельным подграфом
     * с переиндексацией localId в 0-based (merge сдвигает номера в свободный диапазон — split компактит
     * обратно). Топология компонент НЕ меняется, это лишь перенумерация меток (см. [canonical]).
     *
     * Обычно (граф — дерево/цепь, любая связь — мост) даёт РОВНО две компоненты: H₂O рвём O–H → [·OH, H·].
     * Краевой случай — КОЛЬЦО: если удаляемое ребро в цикле, граф остаётся связным → ОДНА компонента
     * (это не распад, а раскрытие кольца); проверка связности ловит это естественно. Колец пока нет.
     *
     * split — чистая операция, энергетику НЕ проверяет: КАКУЮ связь рвать и хватает ли
     * энергии, решает вызывающий (правило PhotoDissociation/StarDissociation). Осколок из одного узла
     * выйдет одноузловым графом (атом = вырожденная молекула, §8) — вызывающий обернёт его в Elemental.
     */
    fun split(atom1: Int, atom2: Int): List<MoleculeGraph> {
        require(bonds.any { sameBond(it, atom1, atom2) }) { "Связи $atom1–$atom2 нет в графе" }
        val remaining = bonds.filterNot { sameBond(it, atom1, atom2) }

        // Связные компоненты по оставшимся рёбрам (BFS). Порядок обхода — по списку nodes → детерминизм.
        val adjacency = nodes.associate { it.localId to mutableListOf<Int>() }
        for (bond in remaining) {
            adjacency.getValue(bond.atom1).add(bond.atom2)
            adjacency.getValue(bond.atom2).add(bond.atom1)
        }
        val visited = HashSet<Int>()
        val components = mutableListOf<List<Int>>()
        for (node in nodes) {
            if (!visited.add(node.localId)) continue
            val component = mutableListOf(node.localId)
            val queue = ArrayDeque(listOf(node.localId))
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                for (neighbor in adjacency.getValue(id)) {
                    if (visited.add(neighbor)) { component.add(neighbor); queue.add(neighbor) }
                }
            }
            components.add(component)
        }

        // Каждую компоненту — в самостоятельный подграф с переиндексацией 0-based (порядок узлов — из nodes).
        return components.map { componentIds ->
            val idSet = componentIds.toHashSet()
            val subNodes = nodes.filter { it.localId in idSet }
            val remap = HashMap<Int, Int>()
            subNodes.forEachIndexed { i, n -> remap[n.localId] = i }
            MoleculeGraph(
                nodes = subNodes.mapIndexed { i, n -> AtomNode(i, n.isotope) },
                bonds = remaining
                    .filter { it.atom1 in idSet && it.atom2 in idSet }
                    .map { Bond(remap.getValue(it.atom1), remap.getValue(it.atom2), it.order) },
            )
        }
    }

    private fun sameBond(bond: Bond, a: Int, b: Int): Boolean =
        (bond.atom1 == a && bond.atom2 == b) || (bond.atom1 == b && bond.atom2 == a)

    /**
     * Брутто-формула в системе Хилла: сначала C, затем H, затем остальные элементы по алфавиту;
     * если углерода нет — все элементы по алфавиту. Счётчик 1 опускается. Примеры: H2O, CH4, C2H6O.
     * Изотопы одного элемента схлопываются (²H считается как H).
     */
    fun formula(): String {
        val counts = HashMap<String, Int>()
        for (node in nodes) {
            val symbol = bareSymbol(node.isotope)
            counts[symbol] = (counts[symbol] ?: 0) + 1
        }
        val ordered = if ("C" in counts) {
            listOf("C") +
                (if ("H" in counts) listOf("H") else emptyList()) +
                counts.keys.filter { it != "C" && it != "H" }.sorted()
        } else {
            counts.keys.sorted()
        }
        return ordered.joinToString("") { symbol ->
            val count = counts.getValue(symbol)
            if (count == 1) symbol else "$symbol$count"
        }
    }

    /**
     * Формула с подстрочными индексами для показа: H2O → H₂O, C2H6O → C₂H₆O.
     * ASCII-форма ([formula]) остаётся для идентичности/ключей; эта — только для UI.
     */
    fun formulaPretty(): String {
        val subscripts = "₀₁₂₃₄₅₆₇₈₉"
        return formula().map { c -> if (c in '0'..'9') subscripts[c - '0'] else c }.joinToString("")
    }

    /**
     * Канонический ключ молекулы — детерминированная строка, ОДИНАКОВАЯ у одной и той же молекулы
     * при любой перенумерации узлов и РАЗНАЯ у разных молекул.
     *
     * Чем отличается от [formula]:
     *  - [formula] — это СОСТАВ: сколько каких атомов («C2H6O»). Грубый отпечаток; связность теряется,
     *    поэтому формула НЕ различает изомеры — у этанола и диметилового эфира она одна (C2H6O).
     *  - [canonical] — это СТРУКТУРА: кто с кем соединён и какой кратностью. Различает изомеры:
     *    этанол (C–C–O) и эфир (C–O–C) дают РАЗНЫЕ ключи. Аналогия: формула — «8 красных кубиков
     *    Lego, 4 синих» (детали), канон — хеш точного чертежа сборки (та же горсть деталей, разная
     *    форма → разный хеш).
     *
     * Зачем нужен: сравнить «это та же молекула?», ключ в Map/реестре, дедупликация, различение изомеров.
     *
     * Реализация — НАИВНАЯ (перебор, §5.1 дока): перебрать все перенумерации узлов, для каждой собрать
     * сериализацию (изотопы в новом порядке + рёбра, перемапленные/нормализованные/отсортированные,
     * с кратностью), взять лексикографически минимальную. Точно и просто, но O(n!) — годится только для
     * малых молекул; для крупных позже заменим на Морган-подобный алгоритм (Стадия 2), отсюда гард.
     *
     * Токен узла — полный изотоп ([Element.name]), поэтому канон РАЗЛИЧАЕТ изотопы (²H ≠ H, ¹³C ≠ ¹²C),
     * в отличие от формулы, которая их схлопывает. Заряд молекулы в ключ не входит — это динамическое
     * состояние сущности; канон описывает структуру (как изотоп атома не меняется от ионизации).
     */
    fun canonical(): String {
        require(nodes.size <= CANONICAL_MAX_NODES) {
            "Наивная каноникализация перебором рассчитана на малые молекулы (<= $CANONICAL_MAX_NODES узлов); " +
                "для крупных нужен Морган-подобный алгоритм (Стадия 2). Узлов: ${nodes.size}"
        }
        val n = nodes.size
        if (n == 0) return ""

        val tokens = nodes.map { it.isotope.name }                 // токен узла = полный изотоп
        val localIdToIndex = HashMap<Int, Int>()                   // localId -> позиция 0..n-1
        nodes.forEachIndexed { i, node -> localIdToIndex[node.localId] = i }
        // рёбра в терминах исходных индексов (0..n-1) — для быстрого перемаппинга на каждой перестановке
        val edges = bonds.map { Triple(localIdToIndex.getValue(it.atom1), localIdToIndex.getValue(it.atom2), it.order) }

        val perm = IntArray(n)        // perm[newIndex] = исходный индекс узла
        val newPos = IntArray(n)      // newPos[origIndex] = newIndex (обратное к perm)
        val used = BooleanArray(n)
        var best: String? = null

        fun serialize(): String {
            val sb = StringBuilder()
            for (newIdx in 0 until n) sb.append(tokens[perm[newIdx]]).append(',')
            sb.append('|')
            val remapped = edges.map { (a, b, order) ->
                val lo = minOf(newPos[a], newPos[b])
                val hi = maxOf(newPos[a], newPos[b])
                Triple(lo, hi, order)
            }.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
            for ((lo, hi, order) in remapped) sb.append(lo).append('-').append(hi).append(':').append(order).append(';')
            return sb.toString()
        }

        fun recurse(newIdx: Int) {
            if (newIdx == n) {
                val s = serialize()
                if (best == null || s < best!!) best = s
                return
            }
            for (orig in 0 until n) {
                if (!used[orig]) {
                    used[orig] = true
                    perm[newIdx] = orig
                    newPos[orig] = newIdx
                    recurse(newIdx + 1)
                    used[orig] = false
                }
            }
        }
        recurse(0)
        return best!!
    }
}

/** Потолок наивного перебора O(n!) в [MoleculeGraph.canonical]; выше — Морган (Стадия 2). */
private const val CANONICAL_MAX_NODES = 9

/**
 * «Голый» символ элемента без масс-индекса и заряда: ²H→H, ¹²C→C, ³He→He.
 * Каталог elementDetails() кодирует букву элемента ASCII-символами, а масс-индекс/заряд —
 * надстрочными; оставив только ASCII-буквы, получаем химический символ. Переиспользуем каталог
 * как единственный источник правды — без отдельной таблицы Менделеева.
 */
private fun bareSymbol(element: Element): String =
    element.details.symbol.filter { it in 'A'..'Z' || it in 'a'..'z' }