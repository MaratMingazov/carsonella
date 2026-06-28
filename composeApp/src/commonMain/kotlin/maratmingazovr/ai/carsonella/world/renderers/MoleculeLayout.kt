package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Детерминированная раскладка графа молекулы в координаты атомов (пиксели, относительно центра молекулы).
 *
 * Радиальная: корень (атом макс. степени) в центре, соседи — по кольцу вокруг родителя; затем всё
 * центрируем по центроиду (молекула симметрична). Достаточно для малых молекул (H₂, H₂O, CH₄, NH₃);
 * цепи/кольца раскладываются приблизительно — их пока нет. Раскладка не часть идентичности графа,
 * это чисто рендер.
 */
object MoleculeLayout {
    private const val BOND_PX = 24f

    // Мемоизация: раскладка зависит только от структуры графа — считаем один раз на структуру, а не
    // каждый кадр. Ключ — сам граф (data class → структурное равенство), молекулы с одинаковой
    // структурой делят результат. Caveat: при огромном разнообразии структур (органика) кэш растёт —
    // тогда заменить на ограниченный LRU.
    private val cache = HashMap<MoleculeGraph, Map<Int, Offset>>()

    fun layout(graph: MoleculeGraph): Map<Int, Offset> = cache.getOrPut(graph) { compute(graph) }

    private fun compute(graph: MoleculeGraph): Map<Int, Offset> {
        val nodes = graph.nodes
        if (nodes.isEmpty()) return emptyMap()

        val adjacency: Map<Int, List<Int>> = nodes.associate { node ->
            node.localId to graph.bonds.mapNotNull { bond ->
                when (node.localId) {
                    bond.atom1 -> bond.atom2
                    bond.atom2 -> bond.atom1
                    else -> null
                }
            }
        }

        // Корень — атом макс. степени; при ничьей берём наименьший localId (для детерминизма).
        val maxDegree = nodes.maxOf { adjacency.getValue(it.localId).size }
        val rootId = nodes.filter { adjacency.getValue(it.localId).size == maxDegree }.minOf { it.localId }

        val pos = HashMap<Int, Offset>()
        val angleToParent = HashMap<Int, Float>()   // направление от узла к его родителю (рад)
        pos[rootId] = Offset.Zero
        val visited = hashSetOf(rootId)
        val queue = ArrayDeque<Int>().apply { add(rootId) }

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val children = adjacency.getValue(cur).filter { visited.add(it) }   // только непосещённые
            if (children.isEmpty()) continue
            val curPos = pos.getValue(cur)
            val isRoot = cur == rootId
            val awayFromParent = (angleToParent[cur] ?: 0f) + PI.toFloat()

            children.forEachIndexed { i, child ->
                val angle = if (isRoot) {
                    2f * PI.toFloat() * i / children.size                          // корень — равномерно по кругу
                } else if (children.size == 1) {
                    awayFromParent                                                 // продолжаем «от родителя»
                } else {
                    val spread = PI.toFloat()                                      // прочие — полукруг от родителя
                    awayFromParent - spread / 2f + spread * i / (children.size - 1)
                }
                pos[child] = Offset(curPos.x + BOND_PX * cos(angle), curPos.y + BOND_PX * sin(angle))
                angleToParent[child] = angle + PI.toFloat()
                queue.add(child)
            }
        }

        // Центрируем по центроиду.
        val cx = pos.values.map { it.x }.average().toFloat()
        val cy = pos.values.map { it.y }.average().toFloat()
        return pos.mapValues { Offset(it.value.x - cx, it.value.y - cy) }
    }
}