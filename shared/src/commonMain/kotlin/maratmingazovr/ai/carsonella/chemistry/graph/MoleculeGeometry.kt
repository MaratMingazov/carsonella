package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Геометрия молекулы: детерминированная раскладка её графа в координаты атомов (относительно центра
 * молекулы).
 *
 * Радиальная: корень (атом макс. степени) в центре, соседи — по кольцу вокруг родителя; затем всё
 * центрируем по центроиду (молекула симметрична). Достаточно для малых молекул (H₂, H₂O, CH₄, NH₃);
 * цепи/кольца раскладываются приблизительно — их пока нет.
 *
 * Координаты НЕ хранятся в [AtomNode] и не являются частью идентичности: граф — это структура (ядра
 * и связи), а положение — производная от неё величина. Хранить его в узле нельзя ещё и технически:
 * [MoleculeGraph] — data class, его структурное равенство служит ключом кэша ниже и ответом на вопрос
 * «это та же молекула?», а `merge`/`split` перенумеровывают узлы, после чего сохранённые координаты
 * протухли бы.
 *
 * Живёт в `shared`, а не в рендере, потому что нужна ОБОИМ: рендер по ней рисует, а правилам реакций
 * она нужна, чтобы выбирать узел по геометрии («ближайший к партнёру»), а не по номеру
 * (см. [MoleculeGraph.firstFreeSlotAtomNode]). Единицы — мировые, они же пиксельные: перевод
 * `Position.toOffset()` в рендере тождественный (x, y как есть).
 *
 * `internal` и без своего кэша: наружу это отдаёт [MoleculeGraph.atomOffsets], там же и мемоизация
 * (`by lazy` на самом графе). Здесь — только алгоритм, вынесенный из графа, чтобы не раздувать его.
 */
internal object MoleculeGeometry {
    private const val BOND_PX = 20f // расстояние между двумя атомами

    /** Считает раскладку. Звать через [MoleculeGraph.atomOffsets] — он кэширует результат. */
    fun compute(graph: MoleculeGraph): Map<Int, Position> {
        val nodes = graph.nodes
        if (nodes.isEmpty()) return emptyMap()

        // Инцидентные РЁБРА (а не просто соседи): длина связи зависит и от её кратности, и от изотопов концов.
        val adjacency: Map<Int, List<Bond>> = nodes.associate { node ->
            node.localId to graph.bonds.filter { node.localId == it.atom1 || node.localId == it.atom2 }
        }
        val isotopeById: Map<Int, Element> = nodes.associate { it.localId to it.isotope }

        // Корень — атом макс. степени; при ничьей берём наименьший localId (для детерминизма).
        val maxDegree = nodes.maxOf { adjacency.getValue(it.localId).size }
        val rootId = nodes.filter { adjacency.getValue(it.localId).size == maxDegree }.minOf { it.localId }

        val pos = HashMap<Int, Position>()
        val angleToParent = HashMap<Int, Float>()   // направление от узла к его родителю (рад)
        pos[rootId] = Position(0f, 0f)
        val visited = hashSetOf(rootId)
        val queue = ArrayDeque<Int>().apply { add(rootId) }

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val bonds = adjacency.getValue(cur).filter { visited.add(it.other(cur)) }   // только к непосещённым
            if (bonds.isEmpty()) continue
            val curPos = pos.getValue(cur)
            val isRoot = cur == rootId
            val awayFromParent = (angleToParent[cur] ?: 0f) + PI.toFloat()

            bonds.forEachIndexed { i, bond ->
                val child = bond.other(cur)
                val angle = if (isRoot) {
                    2f * PI.toFloat() * i / bonds.size                             // корень — равномерно по кругу
                } else if (bonds.size == 1) {
                    awayFromParent                                                 // продолжаем «от родителя»
                } else {
                    val spread = PI.toFloat()                                      // прочие — полукруг от родителя
                    awayFromParent - spread / 2f + spread * i / (bonds.size - 1)
                }
                val length = bondLengthPx(isotopeById.getValue(cur), isotopeById.getValue(child), bond.order) // длина связи двух атомов
                pos[child] = Position(curPos.x + length * cos(angle), curPos.y + length * sin(angle))
                angleToParent[child] = angle + PI.toFloat()
                queue.add(child)
            }
        }

        // Центрируем по центроиду.
        val cx = pos.values.map { it.x }.average().toFloat()
        val cy = pos.values.map { it.y }.average().toFloat()
        return pos.mapValues { Position(it.value.x - cx, it.value.y - cy) }
    }

    /** Второй конец связи, если известен первый. */
    private fun Bond.other(localId: Int): Int = if (localId == atom1) atom2 else atom1


    private fun bondLengthPx(a: Element, b: Element, order: Int): Float {
        // по идее чем больше кратность связи order, тем короче должно быть, позже сделаем
        return a.details.radius + b.details.radius + BOND_PX
    }

}