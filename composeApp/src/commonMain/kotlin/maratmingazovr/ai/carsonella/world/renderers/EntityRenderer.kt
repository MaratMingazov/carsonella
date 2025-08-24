package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.atoms.AtomState
import maratmingazovr.ai.carsonella.chemistry.molecules.MoleculeState
import maratmingazovr.ai.carsonella.chemistry.sub_atoms.SubAtomState

class EntityRenderer(
    textMeasurer : TextMeasurer,
) {

    private val subAtomRenderer = SubAtomRenderer(textMeasurer)
    private val atomRenderer = AtomRenderer(textMeasurer)
    private val moleculeRenderer = MoleculeRenderer(textMeasurer)

    fun render(
        drawScope: DrawScope,
        entityState: EntityState<*>,
        phase: Float,
    ) {
        when (entityState) {
            is SubAtomState<*> -> subAtomRenderer.render(drawScope, entityState, phase)
            is AtomState<*> -> atomRenderer.render(drawScope, entityState)
            is MoleculeState<*> -> moleculeRenderer.render(drawScope, entityState)
        }
    }
}