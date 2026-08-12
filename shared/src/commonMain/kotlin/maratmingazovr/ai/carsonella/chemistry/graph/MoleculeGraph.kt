package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element

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


private const val CANONICAL_MAX_NODES = 9 // Потолок наивного перебора O(n!) в MoleculeGraph.canonical; считается по ТЯЖЁЛЫМ атомам (водороды свёрнуты), выше канон = "" (до Моргана, Стадия 2).

private fun Element.isHydrogen() = details.p == 1 // HYDROGEN и DEUTERIUM: одновалентны, значит в молекуле всегда концевые
private const val RING_MIN_SIZE = 3

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
        val unreachable = idSet - reachableFrom(nodes.first().localId) // Проверяем что все атомы связаны между собой
        require(unreachable.isEmpty()) {
            "Граф не связен: узлы $unreachable не соединены с ${nodes.first().localId} — это не одна молекула, а несколько"
        }
    }

    private val isotopeById: Map<Int, Element> = nodes.associate { it.localId to it.isotope }
    private val energyByBond: Map<Bond, Float> = bonds.mapNotNull { bond -> BondEnergy.of(isotopeById.getValue(bond.atom1), isotopeById.getValue(bond.atom2), bond.order)?.let { energy -> bond to energy } }.toMap() // Энергия КАЖДОЙ связи (эВ), посчитанная один раз при рождении графа.
    private val ringBonds: Set<Bond> = bonds.filterTo(HashSet()) { bond -> bond.atom2 in reachableFrom(bond.atom1, without = bond) } // Связи, лежащие в ЦИКЛЕ: разорви такую — граф останется связным, то есть молекула не распадётся, а кольцо раскроется в цепь.
    private val freeValenceById: Map<Int, Int> = run {
        val used = HashMap<Int, Int>()
        for (bond in bonds) {
            used[bond.atom1] = (used[bond.atom1] ?: 0) + bond.order
            used[bond.atom2] = (used[bond.atom2] ?: 0) + bond.order
        }
        // Узел молекулы не хранит по-атомный заряд → трактуем атом нейтральным (electrons = details.p).
        nodes.associate { it.localId to (it.isotope.valence(it.isotope.details.p) - (used[it.localId] ?: 0)) }
    } // Свободные валентные слоты каждого узла

    init {
        for (node in nodes) {
            val free = freeValenceById.getValue(node.localId)
            require(free >= 0) { "У узла ${node.localId} (${node.isotope.details.symbol}) занято на ${-free} связей больше валентности" }
        }
    }

    /////////////////////////////////////////////
    // ПОЛЯ - ДОСТУПНЫЕ НАРУЖУ. ДАННЫЕ ИЗ КЭША //

    val protons: Int = nodes.sumOf { it.isotope.details.p }
    val mass: Float = nodes.sumOf { it.isotope.details.p + it.isotope.details.n }.toFloat() // Масса молекулы — сумма нуклонов (p + n) всех узлов.
    val formula: String = run {
        val counts = HashMap<String, Int>()
        for (node in nodes) {
            val symbol = node.isotope.bareSymbol
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
    } // Формула: H2O, CH4, C2H6O
    val formulaPretty: String = run {
        val subscripts = "₀₁₂₃₄₅₆₇₈₉"
        formula.map { c -> if (c in '0'..'9') subscripts[c - '0'] else c }.joinToString("")
    } // Формула: H2O → H₂O, C2H6O → C₂H₆O.
    val canonical: String by lazy {
        // Канонизируется СКЕЛЕТ: водороды свёрнуты в токен своего тяжёлого атома. Водород одновалентен, то
        // есть всегда концевой, — топология от этого не теряется, а перебор O(n!) идёт по тяжёлым атомам:
        // у бутена C₄H₈ их 4 вместо 12. Изотоп водорода в токене сохраняется, иначе D₂O сошла бы за H₂O.
        val hydrogensOf = HashMap<Int, MutableList<String>>()   // localId тяжёлого → изотопы его водородов
        val folded = HashSet<Int>()                             // localId свёрнутых водородов
        for (node in nodes) {
            if (!node.isotope.isHydrogen()) continue
            val bond = bonds.singleOrNull { it.atom1 == node.localId || it.atom2 == node.localId } ?: continue
            if (bond.order != 1) continue
            val hostId = if (bond.atom1 == node.localId) bond.atom2 else bond.atom1
            if (isotopeById.getValue(hostId).isHydrogen()) continue   // H–H (сам водород): сворачивать некуда
            folded += node.localId
            hydrogensOf.getOrPut(hostId) { mutableListOf() } += node.isotope.name
        }
        val skeleton = nodes.filter { it.localId !in folded }

        val n = skeleton.size
        if (n > CANONICAL_MAX_NODES) return@lazy ""   // крупная (до Моргана) → без канона

        // Токен узла = изотоп + свёрнутые водороды. Отсортированы: порядок обхода узлов не должен влиять.
        val tokens = skeleton.map { node ->
            val hydrogens = hydrogensOf[node.localId]?.sorted()?.joinToString(",")
            if (hydrogens == null) node.isotope.name else "${node.isotope.name}($hydrogens)"
        }
        val localIdToIndex = HashMap<Int, Int>()                   // localId -> позиция 0..n-1
        skeleton.forEachIndexed { i, node -> localIdToIndex[node.localId] = i }
        // Рёбра в терминах индексов скелета — для быстрого перемаппинга на каждой перестановке. Связи к
        // свёрнутым водородам сюда не попадают: они уже учтены в токенах.
        val edges = bonds
            .filter { it.atom1 in localIdToIndex && it.atom2 in localIdToIndex }
            .map { Triple(localIdToIndex.getValue(it.atom1), localIdToIndex.getValue(it.atom2), it.order) }

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
    } // Канонический ключ молекулы — детерминированная строка, ОДИНАКОВАЯ у одной и той же молекулы при любой перенумерации узлов и РАЗНАЯ у разных молекул.
    /**
     * Слабейшая связь молекулы и её энергия — ПОРОГ ДИССОЦИАЦИИ.
     * Слабейшая связь требует меньше всего энергии → рвётся первой.
     * `null`, если связей нет ИЛИ тип связи не в каталоге (для CHNO не случается, но `Float?` честно это выражает).
     *
     * Нам это нужно, чтобы понять какая связь разорветс во время диссоциации.
     * Но если молекула кольцо, тогда после разрыва молекула остается
     */
    val weakestBondAndEnergy: Pair<Bond, Float>? = energyByBond.entries.minByOrNull { it.value }?.let { (bond, energy) -> bond to energy }
    /**
     * Энергетическая лестница молекулы список уровней, где ПОСЛЕДНИЙ = порог (первый потенциал ионизации, IP). Кэш на графе (иммутабелен).
     * Пусто, если ни один атом не ионизируем (пустая атомная лестница — напр. Z>18) — как у атома
     * с пустой лестницей. Для CHNO не случается.
     */
    val energyLevels: List<Float> = listOfNotNull(nodes.mapNotNull { it.isotope.energyLevels(it.isotope.details.p).lastOrNull() }.minOrNull())
    val hasFreeValence: Boolean = nodes.any { freeValence(it.localId) > 0 } // Есть ли в молекуле хоть один незакрытый валентный слот (есть куда расти / что усиливать).
    val strengthenableBonds: List<Bond> = bonds.filter { it.order < 3 && freeValence(it.atom1) > 0 && freeValence(it.atom2) > 0 } // Связи, которые можно усилить: `order < 3` И у ОБОИХ концов есть свободный слот
    private val ringClosureCandidates: List<Pair<Int, Int>> = run {
        val freeAtoms = nodes.map { it.localId }.filter { (freeValenceById[it] ?: 0) > 0 }
        if (freeAtoms.size < 2) return@run emptyList()
        val adjacency = nodes.associate { it.localId to mutableListOf<Int>() }
        for (bond in bonds) {
            adjacency.getValue(bond.atom1).add(bond.atom2)
            adjacency.getValue(bond.atom2).add(bond.atom1)
        }
        val result = mutableListOf<Pair<Int, Int>>()
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
                if (ringSize >= RING_MIN_SIZE) result.add(start to target)
            }
        }
        result
    } // Кандидаты на замыкание кольца: пары атомов, у которых у ОБОИХ свободный слот и которые соединены

    val hasRingClosureCandidate: Boolean = ringClosureCandidates.isNotEmpty() // Есть ли вообще пара, которую можно замкнуть в кольцо.
    fun canCloseRing(atom1: Int, atom2: Int): Boolean = ringClosureCandidates.any { (first, second) -> (first == atom1 && second == atom2) || (first == atom2 && second == atom1) } // Можно ли замкнуть кольцо между ЭТОЙ парой узлов (порядок не важен): пару выбирает игрок, годность — здесь.

    ////////////////////////////////////////////////
    // ФУНКЦИИ - ДОСТУПНЫЕ НАРУЖУ. ДАННЫЕ ИЗ КЭША //

    fun isRingBond(bond: Bond): Boolean = bond in ringBonds // Лежит ли связь в цикле (её разрыв НЕ развалит молекулу).
    fun energyOf(bond: Bond): Float? = energyByBond[bond] // Энергия связи (эВ) из кеша графа; null — тип связи не в каталоге
    fun freeValence(localId: Int): Int = freeValenceById[localId] ?: error("Узла с localId=$localId нет в графе") // Узнаем есть ли еще валентные слоты у конкретного атома в молекуле

    /////////////////////////////////////////////////////
    // ФУНКЦИИ - ВЫЗЫВАЕМ ОДИН РАЗ ПРИ СОЗДАНИИ ГРАФА //

    /**
     * localId всех узлов, достижимых из [start] по связям (BFS). Опора инварианта связности.
     * without — связь, которую при обходе не видим: так проверяется, держится ли граф без неё.
     */
    private fun reachableFrom(start: Int, without: Bond? = null): Set<Int> {
        val neighbours = HashMap<Int, MutableList<Int>>()
        for (bond in bonds) {
            if (bond == without) continue
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



    ////////////////////////////////////
    // ФУНКЦИИ - ПОРОЖДАЮТ НОВЫЙ ГРАФ //
    fun strengthenBond(atom1: Int, atom2: Int): MoleculeGraph {
        require(bonds.any { sameBond(it, atom1, atom2) }) { "Связи $atom1–$atom2 нет в графе" }
        val newBonds = bonds.map { if (sameBond(it, atom1, atom2)) Bond(it.atom1, it.atom2, it.order + 1) else it }
        return MoleculeGraph(nodes = nodes, bonds = newBonds)
    } // Усиливаем связь на 1 (O–O → O=O, N=N → N≡N)
    fun removeRingBond(atom1: Int, atom2: Int): MoleculeGraph {
        val bond = bonds.firstOrNull { sameBond(it, atom1, atom2) } // проверяем есть ли такая связь вообще?
        requireNotNull(bond) { "Связи $atom1–$atom2 нет в графе" }
        require(isRingBond(bond)) { "Связь $atom1–$atom2 не в цикле: её разрыв развалит молекулу — это split" }
        return MoleculeGraph(nodes = nodes, bonds = bonds - bond)
    } // Разрыв КОЛЬЦЕВОЙ связи
    /**
     * На столько [merge] сдвинет номера узлов другого графа. Наружу — потому что тот, кто строит
     * молекулу по слитому графу, должен знать, в какой узел переехал какой атом. Правило одно на двоих.
     */
    fun mergeOffset(): Int = nodes.maxOf { it.localId } + 1

    fun merge(other: MoleculeGraph, thisNode: Int, otherNode: Int, bondOrder: Int): MoleculeGraph {
        require(nodes.any { it.localId == thisNode }) { "Узла thisNode=$thisNode нет в этом графе" }
        require(other.nodes.any { it.localId == otherNode }) { "Узла otherNode=$otherNode нет в other" }
        require(this.freeValence(thisNode) > 0) { "Узел $thisNode в ${this.formula} насыщен: связь образовать нечем" }
        require(other.freeValence(otherNode) > 0) { "Узел $otherNode в ${other.formula} насыщен: связь образовать нечем" }
        val offset = mergeOffset()
        val shiftedNodes = other.nodes.map { AtomNode(it.localId + offset, it.isotope) }
        val shiftedBonds = other.bonds.map { Bond(it.atom1 + offset, it.atom2 + offset, it.order) }
        val newBond = Bond(thisNode, otherNode + offset, bondOrder)
        return MoleculeGraph(nodes = nodes + shiftedNodes, bonds = bonds + shiftedBonds + newBond)
    } // Слияние двух молекул в новую молекулу
    fun closeRing(atom1: Int, atom2: Int): MoleculeGraph {
        require(nodes.any { it.localId == atom1 }) { "Узла atom1=$atom1 нет в графе" }
        require(nodes.any { it.localId == atom2 }) { "Узла atom2=$atom2 нет в графе" }
        require(atom1 != atom2) { "Кольцо из одного узла невозможно: atom1 == atom2 == $atom1" }
        require(bonds.none { sameBond(it, atom1, atom2) }) { "Узлы $atom1–$atom2 уже связаны — это усиление, не кольцо" }
        return MoleculeGraph(nodes = nodes, bonds = bonds + Bond(atom1, atom2, order = 1))
    } // Замыкание кольца: добавить связь между двумя УЖЕ существующими узлами ОДНОГО графа
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

        // Каждую компоненту — в самостоятельный подграф, СОХРАНЯЯ номера узлов (порядок узлов — из nodes).
        return components.map { componentIds ->
            val idSet = componentIds.toHashSet()
            MoleculeGraph(
                nodes = nodes.filter { it.localId in idSet },
                bonds = remaining.filter { it.atom1 in idSet && it.atom2 in idSet },
            )
        }
    } // Разрываем связь между двумя атомами молекулы


    //////////////////////////////////////
    // ВСПОМОГАТЕЛЬНЫЕ ПРИВАТНЫЕ МЕТОДЫ //
    private fun sameBond(bond: Bond, a: Int, b: Int): Boolean = (bond.atom1 == a && bond.atom2 == b) || (bond.atom1 == b && bond.atom2 == a)

    //////////////////////

}
