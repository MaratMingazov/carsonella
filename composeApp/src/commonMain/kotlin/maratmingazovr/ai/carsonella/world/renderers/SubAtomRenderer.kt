package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.sub_atoms.SubAtomState
import maratmingazovr.ai.carsonella.toOffset

class SubAtomRenderer(
    private val textMeasurer: TextMeasurer,
) {
    fun render(
        drawScope: DrawScope,
        state: SubAtomState<*>,
        phase: Float,
    ) {
        when (state.element) {
            Element.Photon -> drawPhoton(drawScope, state)
            Element.Electron -> drawElectron(drawScope, state)
            Element.Proton -> drawProton(drawScope, state, phase)
            else -> throw NotImplementedError()
        }
    }

    private fun drawPhoton(
        drawScope: DrawScope,
        state: SubAtomState<*>,
    ) {
        val p = state.position.toOffset()
        with(drawScope) {
            drawCircle(color = Color.Black, center = p, radius = 5f)
        }
    }

    private fun drawElectron(
        drawScope: DrawScope,
        state: SubAtomState<*>,
    ) {
        val p = state.position.toOffset()
        with(drawScope) {
            drawCircle(color = Color.Black, center = p, radius = 5f)
        }
    }

    private fun drawProton(
        drawScope: DrawScope,
        state: SubAtomState<*>,
        phase: Float,
    ) {

        // параметры вибрации
        val amp = 2f                  // амплитуда в пикселях
        val idSeed = (state.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val dx = amp * kotlin.math.cos(phase + 0.7f * idSeed)
        val dy = amp * kotlin.math.sin(1.6f * phase + 0.37f * idSeed)
        val p = state.position.toOffset()  + Offset(dx, dy)


        with(drawScope) {
            val radius = 10f
            val brush = Brush.radialGradient(
                colors = listOf(Color.Gray, Color.Gray.copy(alpha = 0.5f), Color.Transparent),
                center = p,
                radius = radius
            )
            drawCircle(brush = brush, center = p, radius = radius)
        }
    }
}