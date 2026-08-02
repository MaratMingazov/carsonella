package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph




sealed interface Species {

    data class Atomic(val element: Element) : Species

    data class Molecular(val graph: MoleculeGraph) : Species
}

