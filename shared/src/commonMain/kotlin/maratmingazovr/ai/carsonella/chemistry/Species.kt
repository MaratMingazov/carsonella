package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.Bond
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph




sealed interface Species {

    data class Atomic(val element: Element) : Species

    data class Molecular(val graph: MoleculeGraph) : Species {

        val formula: String get() = graph.formula // Брутто-формула в ASCII («H2O») — для ключей и сохранения

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

    }
}

