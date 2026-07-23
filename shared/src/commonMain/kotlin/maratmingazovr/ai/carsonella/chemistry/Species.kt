package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.math.round

/**
 * Идентичность сущности (§3b, docs/molecule-graph.md): чем «является» частица.
 *
 * - [Elemental] — задаётся изотопом [Element] (атом, субатомная частица, звезда, модуль) — как сейчас.
 * - [Molecular] — задаётся графом [MoleculeGraph] (молекула). Своего [Element] у неё нет: идентичность
 *   и агрегаты (масса/формула/символ) вычисляются из графа.
 *
 * Граф у молекулы non-null by construction: молекула — это [Molecular], атом — [Elemental].
 * На время миграции у [EntityState] остаётся шов `element`, работающий для [Elemental]
 * (весь не-молекулярный код продолжает читать `.element` как раньше).
 */
sealed interface Species {
    data class Elemental(val element: Element) : Species
    data class Molecular(val graph: MoleculeGraph) : Species
}

// --- агрегаты по Species (мост §3b): Elemental читает Element/Details, Molecular — граф ---

// Пока константа (как старый дефолт Details.radius); при желании позже выведем из размера графа.
private const val MOLECULE_RADIUS = 20f

/** Масса: для атома/частицы — p+n (электрон — особый случай 1f); для молекулы — сумма по графу. */
fun Species.mass(): Float = when (this) {
    is Species.Elemental -> if (element == Element.ELECTRON) 1f else (element.details.p + element.details.n).toFloat()
    is Species.Molecular -> graph.mass
}

/** Сумма протонов: из Details (Elemental) или из графа (Molecular). */
fun Species.protons(): Int = when (this) {
    is Species.Elemental -> element.details.p
    is Species.Molecular -> graph.protons
}

/** Радиус для физики/рендера: из Details (Elemental) или константа (Molecular). */
fun Species.radius(): Float = when (this) {
    is Species.Elemental -> element.details.radius
    is Species.Molecular -> MOLECULE_RADIUS
}

/** Символ для показа: атом/частица — символ элемента с зарядом; молекула — формула с подстрочными + заряд. */
fun Species.displaySymbol(electrons: Int): String = when (this) {
    is Species.Elemental -> element.symbol(electrons)
    // Заряд молекулы — динамика (protons − electrons), а не структура: formulaPretty остаётся чистой
    // формулой, суффикс заряда добавляем здесь. Зеркало атома (baseSymbol + chargeSuffix), тот же хелпер.
    is Species.Molecular -> graph.formulaPretty + chargeSuffix(graph.protons - electrons)
}

/**
 * Человекочитаемое описание сущности для инфо-панели. Живёт здесь (рядом с mass()/displaySymbol()),
 * потому что зависит от типа Species: так все EntityState могут вернуть один и тот же toString и стать
 * идентичными (шаг к их объединению). Динамику (energy/electrons/position/velocity) берём из [s].
 * Внутри Elemental различаем атом/субатом/звезду по details.type — отдельные Species-варианты не нужны.
 */
fun Species.describe(s: EntityState): String = when (this) {
    is Species.Molecular -> """
        |${graph.formulaPretty}
        |Energy ${round(s.energy * 100) / 100}
    """.trimMargin()

    is Species.Elemental -> when (element.details.type) {
        ElementType.Atom -> """
            |${element.label(s.electrons)}
            |Protons: ${element.details.p}
            |Neutrons: ${element.details.n}
            |Electrons: ${s.electrons}
            |Energy ${round(s.energy * 100) / 100}
        """.trimMargin()

        ElementType.SubAtom -> {
            val base = """
                |${element.label(s.electrons)}: ${s.id}
                |Position (${s.position.x.toInt()}, ${s.position.y.toInt()})
                |Velocity ${round(s.velocity * 100) / 100}
                |Energy ${round(s.energy * 100) / 100}
            """.trimMargin()
            // Спектр осмыслен только у фотона (у него energy — это E=hν) — см. SubAtom.
            if (element == Element.PHOTON) "$base\nСпектр: ${lightBandFromEnergyEv(s.energy).label}" else base
        }

        ElementType.Star -> """
            |${element.label(s.electrons)}: ${s.id}
            |Position (${s.position.x.toInt()}, ${s.position.y.toInt()})
            |Velocity ${round(s.velocity * 100) / 100}
            |Energy ${round(s.energy * 100) / 100}
        """.trimMargin()

        ElementType.Molecule -> error("Молекула — это Species.Molecular, не Elemental")
    }
}