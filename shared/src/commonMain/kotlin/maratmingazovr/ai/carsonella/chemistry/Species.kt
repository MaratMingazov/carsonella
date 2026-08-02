package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry
import kotlin.math.round




sealed interface Species {
    val mass: Float
    val protons: Int
    val radius: Float
    fun displaySymbol(electrons: Int): String
    fun energyLevels(electrons: Int): List<Float> // Энергетическая лестница (эВ): уровни возбуждения, последний = порог ионизации.
    fun describe(s: EntityState): String

    data class Atomic(val element: Element) : Species {
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
                |Position (${s.centerPosition.x.toInt()}, ${s.centerPosition.y.toInt()})
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

        /** Брутто-формула в ASCII («H2O») — для ключей и сохранения; для показа есть [displaySymbol]. */
        val formula: String get() = graph.formula

        fun atoms(center: Position): List<MolecularAtom> = graph.nodes.map { place(it, center) }

        /** Один атом по номеру узла — точечная версия [atoms] (связи адресуют атомы по `localId`). */
        fun atom(localId: Int, center: Position): MolecularAtom {
            val node = graph.nodes.firstOrNull { it.localId == localId }
                ?: error("Узла с localId=$localId нет в молекуле")
            return place(node, center)
        }

        // вязи молекулы, поставленные в мир: у каждой оба конца — готовые MolecularAtom.
        fun bonds(center: Position): List<MolecularBond> = place(graph.bonds, center)

        // Связи, которые можно усилить (кратность +1) — поставленные в мир.
        fun strengthenableBonds(center: Position): List<MolecularBond> = place(graph.strengthenableBonds, center)

        // Усиление связи: НОВАЯ молекула, у которой кратность [bond] на 1 больше (O–O → O=O).
        fun strengthenBond(bond: MolecularBond): Molecular = Molecular(graph.strengthenBond(bond.atom1.localId, bond.atom2.localId))

        // Может ли молекула замкнуться?
        val canCloseRing: Boolean get() = graph.ringClosureCandidates.isNotEmpty()



        private fun place(bonds: List<Bond>, center: Position): List<MolecularBond> {
            val placed = atoms(center).associateBy { it.localId }
            return bonds.map { MolecularBond(placed.getValue(it.atom1), placed.getValue(it.atom2), it.order) }
        }

        private fun place(node: AtomNode, center: Position) = MolecularAtom(
            localId = node.localId,
            isotope = node.isotope,
            position = center + graph.atomOffset(node.localId),
            freeValence = graph.freeValence(node.localId),
        )

        override fun describe(s: EntityState): String {
            // Известная молекула из реестра: англ. имя + брутто-формула первой строкой, затем русское имя и
            // структурная формула (связность) — отдельными строками. Аноним → просто брутто-формула.
            val known = MoleculeRegistry.lookup(graph.canonical)
            val lines = mutableListOf(
                if (known != null) "${known.nameEn} (${graph.formulaPretty})" else graph.formulaPretty,
            )
            if (known != null) lines += known.nameRu
            if (known != null && known.structuralFormula.isNotEmpty()) lines += known.structuralFormula
            if (known != null && known.description.isNotEmpty()) lines += known.description
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