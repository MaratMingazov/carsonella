package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry
import kotlin.math.round


/**
 * Атом молекулы, поставленный в мир: номер узла, изотоп и координата. Ответ [Species.Molecular.atoms].
 *
 * Отличие от `AtomNode` — ровно в третьем поле, и оно принципиальное: узел графа описывает СТРУКТУРУ
 * и координат не имеет (граф не знает, где молекула находится), а здесь структура уже совмещена с
 * положением конкретной сущности. Поэтому координата не хранится, а появляется в момент вопроса:
 * центр передаётся в [Species.Molecular.atoms] аргументом.
 */
data class MolecularAtom(
    val localId: Int,
    val isotope: Element,
    val position: Position,
) {
    val radius: Float get() = isotope.details.radius
}

sealed interface Species {
    val mass: Float
    val protons: Int
    val radius: Float
    fun displaySymbol(electrons: Int): String
    fun energyLevels(electrons: Int): List<Float> // Энергетическая лестница (эВ): уровни возбуждения, последний = порог ионизации.
    fun describe(s: EntityState): String

    data class Elemental(val element: Element) : Species {
        override val mass: Float get() = if (element == Element.ELECTRON) 1f else (element.details.p + element.details.n).toFloat()
        override val protons: Int get() = element.details.p
        override val radius: Float get() = element.details.radius
        override fun displaySymbol(electrons: Int): String = element.symbol(electrons)
        override fun energyLevels(electrons: Int): List<Float> = element.energyLevels(electrons)
        override fun describe(s: EntityState): String = when (element.details.type) {
            ElementType.Atom -> """
                |${element.label(s.electrons)}
                |Protons: ${element.details.p}
                |Neutrons: ${element.details.n}
                |Electrons: ${s.electrons}
                |Energy ${round(s.energy * 100) / 100} eV
            """.trimMargin()

            ElementType.SubAtom -> {
                val base = """
                    |${element.label(s.electrons)}
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

    data class Molecular(val graph: MoleculeGraph) : Species {
        override val mass: Float get() = graph.mass
        override val protons: Int get() = graph.protons
        override val radius: Float get() = MOLECULE_RADIUS
        override fun displaySymbol(electrons: Int): String = graph.formulaPretty + chargeSuffix(graph.protons - electrons)
        override fun energyLevels(electrons: Int): List<Float> = graph.energyLevels

        /**
         * Атомы молекулы, поставленные в мир: [center] — где сейчас находится сущность
         * (`state.position`). Форму знает граф, положение — сущность; здесь они встречаются.
         *
         * `localId` живёт ЗДЕСЬ, а не на [EntityState]: спросить «где атом номер 2» можно только у
         * молекулы, у одиночной частицы такой вопрос бессмыслен. Наружу сущность отдаёт лишь то, что
         * осмысленно для любой: расстояния и «каким атомом связываться».
         */
        fun atoms(center: Position): List<MolecularAtom> = graph.nodes.map { place(it, center) }

        /** Один атом по номеру узла — точечная версия [atoms] (связи адресуют атомы по `localId`). */
        fun atom(localId: Int, center: Position): MolecularAtom {
            val node = graph.nodes.firstOrNull { it.localId == localId }
                ?: error("Узла с localId=$localId нет в молекуле")
            return place(node, center)
        }

        private fun place(node: AtomNode, center: Position) =
            MolecularAtom(node.localId, node.isotope, center + graph.atomOffset(node.localId))

        override fun describe(s: EntityState): String {
            // Известная молекула из реестра: англ. имя + брутто-формула первой строкой, затем русское имя и
            // структурная формула (связность) — отдельными строками. Аноним → просто брутто-формула.
            val known = MoleculeRegistry.lookup(graph.canonical)
            val lines = mutableListOf(
                if (known != null) "${known.nameEn} (${graph.formulaPretty})" else graph.formulaPretty,
            )
            if (known != null) lines += known.nameRu
            if (known != null && known.structuralFormula.isNotEmpty()) lines += known.structuralFormula
            lines += "Energy ${round(s.energy * 100) / 100}"
            graph.weakestBondAndEnergy?.let { (_, energy) ->
                lines += "Weakest bond ${round(energy * 100) / 100} eV"
            }
            return lines.joinToString("\n")
        }
    }
}

// Пока константа (как старый дефолт Details.radius); при желании позже выведем из размера графа.
private const val MOLECULE_RADIUS = 20f