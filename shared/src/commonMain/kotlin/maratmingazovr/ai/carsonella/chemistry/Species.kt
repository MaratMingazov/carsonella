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
// Агрегаты (мост §3b) — члены sealed-типа: каждый вариант держит свою логику рядом с собой. Это
// ВЫВОДИМЫЕ величины (из element/graph), а не хранимое состояние: element/graph не меняются, поэтому
// значение постоянно; отдельным полем не храним (иначе второй источник правды + шум в equals/copy).
sealed interface Species {
    /** Масса: атом/частица — p+n (электрон — особый случай 1f); молекула — сумма по графу (кэш на графе). */
    val mass: Float

    /** Сумма протонов: из Details (Elemental) или из графа (Molecular). */
    val protons: Int

    /** Радиус для физики/рендера: из Details (Elemental) или константа (Molecular). */
    val radius: Float

    /** Символ для показа: атом/частица — символ элемента с зарядом; молекула — формула + заряд. */
    fun displaySymbol(electrons: Int): String

    data class Elemental(val element: Element) : Species {
        override val mass: Float get() = if (element == Element.ELECTRON) 1f else (element.details.p + element.details.n).toFloat()
        override val protons: Int get() = element.details.p
        override val radius: Float get() = element.details.radius
        override fun displaySymbol(electrons: Int): String = element.symbol(electrons)
    }

    data class Molecular(val graph: MoleculeGraph) : Species {
        override val mass: Float get() = graph.mass
        override val protons: Int get() = graph.protons
        override val radius: Float get() = MOLECULE_RADIUS
        // Заряд молекулы — динамика (protons − electrons), а не структура: formulaPretty остаётся чистой
        // формулой, суффикс заряда добавляем здесь. Зеркало атома (baseSymbol + chargeSuffix), тот же хелпер.
        override fun displaySymbol(electrons: Int): String = graph.formulaPretty + chargeSuffix(graph.protons - electrons)
    }
}

// Пока константа (как старый дефолт Details.radius); при желании позже выведем из размера графа.
private const val MOLECULE_RADIUS = 20f

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
    }
}