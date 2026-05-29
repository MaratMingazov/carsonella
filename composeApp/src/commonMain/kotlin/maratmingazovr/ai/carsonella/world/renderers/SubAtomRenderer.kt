package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.SubAtomState
import maratmingazovr.ai.carsonella.toOffset

class SubAtomRenderer(
    private val textMeasurer: TextMeasurer,
) {
    fun render(
        drawScope: DrawScope,
        state: SubAtomState,
        phase: Float,
    ) {
        when (state.element) {
            Element.PHOTON -> drawPhoton(drawScope, state)
            Element.ELECTRON -> drawElectron(drawScope, state)
            Element.POSITRON -> drawPositron(drawScope, state)
            Element.Proton -> drawProton(drawScope, state, phase)
            Element.NEUTRON -> drawNeutron(drawScope, state, phase)
            else -> throw NotImplementedError()
        }
    }

    private fun drawPhoton(
        drawScope: DrawScope,
        state: SubAtomState,
    ) {
        val p = state.position.toOffset()
        with(drawScope) {
            drawCircle(color = Color.Black, center = p, radius = 5f)
        }
    }

    private fun drawElectron(
        drawScope: DrawScope,
        state: SubAtomState,
    ) {
        val p = state.position.toOffset()
        val radius = 7f
        with(drawScope) {
            drawCircle(color = Color.Blue, center = p, radius = radius)
            val textLayoutResult = textMeasurer.measure(
                text = "−",
                style = TextStyle(color = Color.White, fontSize = 10.sp),
            )
            drawText(
                textLayoutResult,
                topLeft = Offset(p.x - textLayoutResult.size.width / 2, p.y - textLayoutResult.size.height / 2),
            )
        }
    }

    private fun drawPositron(
        drawScope: DrawScope,
        state: SubAtomState,
    ) {
        val p = state.position.toOffset()
        val radius = 7f
        with(drawScope) {
            drawCircle(color = Color.Red, center = p, radius = radius)
            val textLayoutResult = textMeasurer.measure(
                text = "+",
                style = TextStyle(color = Color.White, fontSize = 10.sp),
            )
            drawText(
                textLayoutResult,
                topLeft = Offset(p.x - textLayoutResult.size.width / 2, p.y - textLayoutResult.size.height / 2),
            )
        }
    }

    private fun drawProton(
        drawScope: DrawScope,
        state: SubAtomState,
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

    // Нейтрон: тот же серый «нуклон»-градиент с вибрацией, что у протона, плюс белая «n»
    // поверх — чтобы визуально различать заряженный/нейтральный нуклоны.
    private fun drawNeutron(
        drawScope: DrawScope,
        state: SubAtomState,
        phase: Float,
    ) {
        val amp = 2f
        val idSeed = (state.id % 1000).toFloat()
        val dx = amp * kotlin.math.cos(phase + 0.7f * idSeed)
        val dy = amp * kotlin.math.sin(1.6f * phase + 0.37f * idSeed)
        val p = state.position.toOffset() + Offset(dx, dy)

        with(drawScope) {
            val radius = 10f
            val brush = Brush.radialGradient(
                colors = listOf(Color.Gray, Color.Gray.copy(alpha = 0.5f), Color.Transparent),
                center = p,
                radius = radius,
            )
            drawCircle(brush = brush, center = p, radius = radius)
            val textLayoutResult = textMeasurer.measure(
                text = "n",
                style = TextStyle(color = Color.White, fontSize = 10.sp),
            )
            drawText(
                textLayoutResult,
                topLeft = Offset(p.x - textLayoutResult.size.width / 2, p.y - textLayoutResult.size.height / 2),
            )
        }
    }
}