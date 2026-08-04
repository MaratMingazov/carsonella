package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Position
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

/** Узел графа — атом конкретного изотопа. [isotope] несёт протоны/нейтроны/символ через [Element.details].
 * electrons -  количество электронов здесь хранить нельзя. Даже если мы у атома кислорода выбили 1 электрон
 *              и теперь соединили его с атомом углерода, то теперь нельзя сказать у какого кокретного атома в молекуле
 *              не хватает электрона, его не хватает у молекулы в целом
 * */
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

/**
 * Кандидат на замыкание кольца: между узлами [atom1] и [atom2] (оба со свободным слотом, не соседи)
 * можно добавить связь → цикл размера [ringSize] (= длина кратчайшего пути между ними + 1). См.
 * [MoleculeGraph.ringClosureCandidates] и [MoleculeGraph.closeRing].
 */
data class RingClosureCandidate(val atom1: Int, val atom2: Int, val ringSize: Int)


data class MoleculeGraph(
    val nodes: List<AtomNode>,
    val bonds: List<Bond>,
) {
    init {
        require(nodes.isNotEmpty()) { "Граф без узлов: молекулы без атомов не бывает" }
        val ids = nodes.map { it.localId }
        require(ids.size == ids.toSet().size) { "Дубли localId среди узлов: $ids" }
        val idSet = ids.toHashSet()
        for (bond in bonds) {
            require(bond.atom1 != bond.atom2) { "Петля запрещена: связь $bond соединяет узел сам с собой" }
            require(bond.atom1 in idSet && bond.atom2 in idSet) { "Связь $bond ссылается на несуществующий узел" }
            require(bond.order in 1..3) { "Кратность связи $bond вне диапазона 1..3 (см. комментарий к Bond)" }
        }
        val unreachable = ids.toHashSet() - reachableFrom(nodes.first().localId) // Проверяем что все атомы связаны между собой
        require(unreachable.isEmpty()) {
            "Граф не связен: узлы $unreachable не соединены с ${nodes.first().localId} — это не одна молекула, а несколько"
        }
    }

    val protons: Int = nodes.sumOf { it.isotope.details.p }
    val mass: Float = nodes.sumOf { it.isotope.details.p + it.isotope.details.n }.toFloat() // Масса молекулы — сумма нуклонов (p + n) всех узлов.
    private val isotopeById: Map<Int, Element> = nodes.associate { it.localId to it.isotope }

    /** localId всех узлов, достижимых из [start] по связям (BFS). Опора инварианта связности. */
    private fun reachableFrom(start: Int): Set<Int> {
        val neighbours = HashMap<Int, MutableList<Int>>()
        for (bond in bonds) {
            neighbours.getOrPut(bond.atom1) { mutableListOf() }.add(bond.atom2)
            neighbours.getOrPut(bond.atom2) { mutableListOf() }.add(bond.atom1)
        }
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            for (neighbour in neighbours[queue.removeFirst()].orEmpty()) {
                if (seen.add(neighbour)) queue.add(neighbour)
            }
        }
        return seen
    }







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
     * Энергетическая лестница молекулы список уровней, где ПОСЛЕДНИЙ = порог (первый потенциал ионизации, IP). Кэш на графе (иммутабелен).
     *
     * Пусто, если ни один атом не ионизируем (пустая атомная лестница — напр. Z>18) — как у атома
     * с пустой лестницей. Для CHNO не случается.
     */
    val energyLevels: List<Float> = listOfNotNull(
        nodes.mapNotNull { it.isotope.energyLevels(it.isotope.details.p).lastOrNull() }.minOrNull()
    )

    /**
     * Свободные валентные слоты каждого узла: localId → сколько ещё связей узел может образовать/усилить.
     * Кэшируется один раз при построении (граф иммутабелен): один проход по связям копит «занятые»
     * слоты (сумма order у инцидентных рёбер), затем `валентность − занятые` на узел.
     */
    private val freeValenceById: Map<Int, Int> = run {
        val used = HashMap<Int, Int>()
        for (bond in bonds) {
            used[bond.atom1] = (used[bond.atom1] ?: 0) + bond.order
            used[bond.atom2] = (used[bond.atom2] ?: 0) + bond.order
        }
        // Узел молекулы не хранит по-атомный заряд → трактуем атом нейтральным (electrons = details.p).
        nodes.associate { it.localId to (it.isotope.valence(it.isotope.details.p) - (used[it.localId] ?: 0)) }
    }

    /**
     * Узнаем есть ли еще валентные слоты у конкретного атома в молекуле (localId - номер узла)
     * Если > 0 значит этот атом в молекуле еще может образовать новую валентную связь, либо усилить сузествующую связь
     * Например, когда Углерод + Углерод -> C-C то теперь либо связь усилится С=С
     * либо образуется еще связь H-C-C
     */
    fun freeValence(localId: Int): Int =
        freeValenceById[localId] ?: error("Узла с localId=$localId нет в графе")

    /** Есть ли в молекуле хоть один незакрытый валентный слот (есть куда расти / что усиливать). */
    val hasFreeValence: Boolean = nodes.any { freeValence(it.localId) > 0 }

    val firstFreeValenceAtomNode: AtomNode? = nodes.filter { freeValence(it.localId) > 0 }.minByOrNull { it.localId }

    /**
     * Слияние двух молекул в новую молекулу
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
     * Связи, которые можно усилить: `order < 3` И у ОБОИХ концов есть свободный слот (усиление
     * order→order+1 занимает по одному слоту у каждого атома). Так, O–O усиливаема (по слоту на каждом O),
     * а звено цепи O–O–O — нет (средний атом насыщен). Пусто → усиливать нечего.
     */
    val strengthenableBonds: List<Bond> =
        bonds.filter { it.order < 3 && freeValence(it.atom1) > 0 && freeValence(it.atom2) > 0 }

    /**
     * Кандидаты на замыкание кольца: пары атомов, у которых у ОБОИХ свободный слот и которые соединены
     *

     */
    val ringClosureCandidates: List<RingClosureCandidate> = run {
        val freeAtoms = nodes.map { it.localId }.filter { (freeValenceById[it] ?: 0) > 0 }
        if (freeAtoms.size < 2) return@run emptyList()
        val adjacency = nodes.associate { it.localId to mutableListOf<Int>() }
        for (bond in bonds) {
            adjacency.getValue(bond.atom1).add(bond.atom2)
            adjacency.getValue(bond.atom2).add(bond.atom1)
        }
        val result = mutableListOf<RingClosureCandidate>()
        for (start in freeAtoms) {
            val dist = HashMap<Int, Int>().apply { put(start, 0) }   // кратчайшие расстояния от start (BFS)
            val queue = ArrayDeque(listOf(start))
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                val d = dist.getValue(cur)
                for (nb in adjacency.getValue(cur)) if (nb !in dist) { dist[nb] = d + 1; queue.add(nb) }
            }
            for (target in freeAtoms) {
                if (target <= start) continue                       // каждую неупорядоченную пару один раз
                val ringSize = (dist[target] ?: continue) + 1       // недостижим (связный граф — не случается) → пропуск
                if (ringSize >= RING_MIN_SIZE) result.add(RingClosureCandidate(start, target, ringSize))
            }
        }
        result
    }

    /**
     * Усиление связи: вернуть копию графа, где кратность связи между узлами [atom1] и [atom2]
     * увеличена на 1 (O–O → O=O, N=N → N≡N).
     *
     */
    fun strengthenBond(atom1: Int, atom2: Int): MoleculeGraph {
        require(bonds.any { sameBond(it, atom1, atom2) }) { "Связи $atom1–$atom2 нет в графе" }
        val newBonds = bonds.map { if (sameBond(it, atom1, atom2)) Bond(it.atom1, it.atom2, it.order + 1) else it }
        return MoleculeGraph(nodes = nodes, bonds = newBonds)
    }

    /**
     * Замыкание кольца: добавить связь между двумя УЖЕ существующими узлами ОДНОГО графа
     */
    fun closeRing(atom1: Int, atom2: Int): MoleculeGraph {
        require(nodes.any { it.localId == atom1 }) { "Узла atom1=$atom1 нет в графе" }
        require(nodes.any { it.localId == atom2 }) { "Узла atom2=$atom2 нет в графе" }
        require(atom1 != atom2) { "Кольцо из одного узла невозможно: atom1 == atom2 == $atom1" }
        require(bonds.none { sameBond(it, atom1, atom2) }) { "Узлы $atom1–$atom2 уже связаны — это усиление, не кольцо" }
        return MoleculeGraph(nodes = nodes, bonds = bonds + Bond(atom1, atom2, order = 1))
    }

    /**
     * Разрыв связи.
     * Убираем ребро [atom1]–[atom2] и возвращаем связные компоненты — каждую самостоятельным подграфом
     * с переиндексацией localId в 0-based (merge сдвигает номера в свободный диапазон — split компактит
     * обратно). Топология компонент НЕ меняется, это лишь перенумерация меток (см. canonical).
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
    val formula: String = run {
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
        ordered.joinToString("") { symbol ->
            val count = counts.getValue(symbol)
            if (count == 1) symbol else "$symbol$count"
        }
    }

    /**
     * Формула с подстрочными индексами для показа: H2O → H₂O, C2H6O → C₂H₆O.
     * ASCII-форма (formula) остаётся для идентичности/ключей; эта — только для UI.
     */
    val formulaPretty: String = run {
        val subscripts = "₀₁₂₃₄₅₆₇₈₉"
        formula.map { c -> if (c in '0'..'9') subscripts[c - '0'] else c }.joinToString("")
    }


    private val atomOffsets: Map<Int, Position> by lazy { MoleculeGeometry.compute(this) }

    /**
     * Смещение атома [localId] относительно центра молекулы.
     */
    fun atomOffset(localId: Int): Position =
        atomOffsets[localId] ?: error("Узла с localId=$localId нет в графе")



    /**
     * Канонический ключ молекулы — детерминированная строка, ОДИНАКОВАЯ у одной и той же молекулы
     * при любой перенумерации узлов и РАЗНАЯ у разных молекул.
     *
     * Чем отличается от formula:
     *  - formula — это СОСТАВ: сколько каких атомов («C2H6O»). Грубый отпечаток; связность теряется,
     *    поэтому формула НЕ различает изомеры — у этанола и диметилового эфира она одна (C2H6O).
     *  - canonical — это СТРУКТУРА: кто с кем соединён и какой кратностью. Различает изомеры:
     *    этанол (C–C–O) и эфир (C–O–C) дают РАЗНЫЕ ключи. Аналогия: формула — «8 красных кубиков
     *    Lego, 4 синих» (детали), канон — хеш точного чертежа сборки (та же горсть деталей, разная
     *    форма → разный хеш).
     *
     * Зачем нужен: сравнить «это та же молекула?», ключ в Map/реестре, дедупликация, различение изомеров.
     *
     * Реализация — НАИВНАЯ (перебор, §5.1 дока): перебрать все перенумерации узлов, для каждой собрать
     * сериализацию (изотопы в новом порядке + рёбра, перемапленные/нормализованные/отсортированные,
     * с кратностью), взять лексикографически минимальную. Точно и просто, но O(n!) — годится только для
     * малых молекул; для крупных позже заменим на Морган-подобный алгоритм (Стадия 2).
     *
     * Токен узла — полный изотоп ([Element.name]), поэтому канон РАЗЛИЧАЕТ изотопы (²H ≠ H, ¹³C ≠ ¹²C),
     * в отличие от формулы, которая их схлопывает. Заряд молекулы в ключ не входит — это динамическое
     * состояние сущности; канон описывает структуру (как изотоп атома не меняется от ионизации).
     *
     * Кэш на графе (`by lazy`, граф иммутабелен → канон инвариантен): считается ≤1 раз, при первом
     * обращении, и только у молекул, кому реально понадобился (мимолётные осколки не платят). Крупные
     * (> [CANONICAL_MAX_NODES]) и пустые графы → `""` = «нет канонической идентичности» (наивный перебор
     * не тянет — до Моргана). `""` не совпадёт ни с одним реальным ключом → такие молекулы просто
     * остаются анонимными (в реестре не находятся), а не роняют вызывающего исключением.
     */
    val canonical: String by lazy {
        val n = nodes.size
        if (n > CANONICAL_MAX_NODES) return@lazy ""   // крупная (до Моргана) → без канона

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
        best!!
    }

    companion object {
        /**
         * Симметричная форма [merge]: связь кратности [bondOrder] между узлом [node1] графа [graph1] и
         * узлом [node2] графа [graph2]. Перенумерацию узлов второго графа делает instance-merge.
         *
         * Зачем дубль к методу: слияние симметрично («этот» и «другой» тут равноправны), а в делегирующем
         * вызове конструктора [Molecule] выбирать, у кого из двух молекул звать метод, неестественно.
         */
        fun merge(graph1: MoleculeGraph, node1: Int, graph2: MoleculeGraph, node2: Int, bondOrder: Int): MoleculeGraph =
            graph1.merge(graph2, thisNode = node1, otherNode = node2, bondOrder = bondOrder)
    }
}

/** Потолок наивного перебора O(n!) в MoleculeGraph.canonical; выше канон = "" (до Моргана, Стадия 2). */
private const val CANONICAL_MAX_NODES = 9

/**
 * Минимальный размер кольца, который MoleculeGraph.ringClosureCandidates вообще предлагает. Напряжённые
 * 3–4-кольца отсекаем полностью (иначе преждевременный циклопропан из голой тройки атомов); 5+ отдаём на
 * выбор энергетическому weight (см. RingClosure), где ringStrain делает 5–6 выгоднее 7+.
 */
private const val RING_MIN_SIZE = 5

/**
 * «Голый» символ элемента без масс-индекса и заряда: ²H→H, ¹²C→C, ³He→He.
 * Каталог elementDetails() кодирует букву элемента ASCII-символами, а масс-индекс/заряд —
 * надстрочными; оставив только ASCII-буквы, получаем химический символ. Переиспользуем каталог
 * как единственный источник правды — без отдельной таблицы Менделеева.
 */
private fun bareSymbol(element: Element): String =
    element.details.symbol.filter { it in 'A'..'Z' || it in 'a'..'z' }