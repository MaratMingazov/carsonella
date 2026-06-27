package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.chemistry.Element

/**
 * Ядро графовой модели молекулы (Стадия 0, см. docs/molecule-graph.md).
 *
 * Молекула = граф: атомы — узлы [AtomNode], связи — рёбра [Bond]. Сущность держит СТРУКТУРУ,
 * а не идентичность; агрегаты (масса/протоны/формула) ВЫЧИСЛЯЮТСЯ из графа, а не хранятся.
 * Так этанол (C–C–O) и диметиловый эфир (C–O–C) станут двумя разными графами с одной формулой
 * C₂H₆O — без дублей-констант [Element].
 *
 * Граф моделирует только ЯДЕРНЫЙ скелет + связи. Электроны (ионизация) — динамическое состояние
 * сущности (`state.electrons`), как у атома сегодня; в графе их нет. Поэтому узел — это лишь изотоп.
 *
 * Изолированное ядро: движок (рендер, силы, реакции, save/load) его пока не использует.
 * Из движка переиспользуем только [Element] — словарь изотопов (протоны/нейтроны/символ через details).
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
 * Дефолта нет НАМЕРЕННО: кратность указывается явно при каждом создании — чтобы не словить баг
 * «забыли проставить и молча получили одинарную».
 *
 * Потолок — тройная (3): для химии CHNO (наш случай — малые молекулы, жизнь) выше не встречается.
 * Четверные–шестерные связи существуют лишь в экзотике переходных металлов (Re₂Cl₈²⁻, Cr–Cr, Mo₂)
 * и нам не нужны; промышленный формат MOL/SDF тоже кодирует реальные кратности как 1/2/3.
 */
data class Bond(
    val atom1: Int,          // localId одного узла
    val atom2: Int,          // localId другого узла
    val order: Int,          // кратность 1..3 (см. выше); дефолта нет — указываем явно
)

/**
 * Молекула как граф. Агрегаты считаются из [nodes]/[bonds] на лету.
 *
 * Инварианты проверяются в init (детерминизм важен — см. воспроизводимость в README):
 * уникальные localId; рёбра ссылаются на существующие узлы; нет петель; корректная кратность.
 */
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

    /** Масса молекулы — сумма нуклонов (p + n) всех узлов, как [Element]-масса для атомов. */
    fun mass(): Float = nodes.sumOf { it.isotope.details.p + it.isotope.details.n }.toFloat()

    /** Сумма протонов всех узлов. */
    fun protons(): Int = nodes.sumOf { it.isotope.details.p }

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
}

/**
 * «Голый» символ элемента без масс-индекса и заряда: ²H→H, ¹²C→C, ³He→He.
 * Каталог elementDetails() кодирует букву элемента ASCII-символами, а масс-индекс/заряд —
 * надстрочными; оставив только ASCII-буквы, получаем химический символ. Переиспользуем каталог
 * как единственный источник правды — без отдельной таблицы Менделеева.
 */
private fun bareSymbol(element: Element): String =
    element.details.symbol.filter { it in 'A'..'Z' || it in 'a'..'z' }