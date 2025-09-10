package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.AtomState
import maratmingazovr.ai.carsonella.chemistry.MoleculeState
import maratmingazovr.ai.carsonella.chemistry.SubAtomState
import maratmingazovr.ai.carsonella.toOffset

class EntityRenderer(
    private val textMeasurer: TextMeasurer,
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
            is SubAtomState -> subAtomRenderer.render(drawScope, entityState, phase)
            is AtomState -> drawEntity(drawScope, entityState, phase)
            is MoleculeState -> drawEntity(drawScope, entityState, phase)
        }
    }

    fun drawEntity(
        drawScope: DrawScope,
        entityState: EntityState<*>,
        phase: Float,
    ) {
        // параметры вибрации
        val amp = 2f                  // амплитуда в пикселях
        val idSeed = (entityState.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val dx = amp * kotlin.math.cos(phase + 0.7f * idSeed)
        val dy = amp * kotlin.math.sin(1.6f * phase + 0.37f * idSeed)
        val originalPosition = entityState.position.toOffset()
        val position = entityState.position.toOffset()  + Offset(dx, dy)

        with(drawScope) {
            drawCircle(
                color = Color.Black,
                center = position,
                radius = entityState.element.radius,
                style = Stroke(
                    width = 1f,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(10f, 5f), // длина штриха, длина пробела
                        phase = 0f // смещение начала узора
                    )
                )
            )

            val textLayoutResult = textMeasurer.measure(text = entityState.element.symbol, style = TextStyle(color = Color.Black, fontSize = 10.sp))

            drawText(
                textLayoutResult,
                topLeft = Offset(position.x - textLayoutResult.size.width / 2, position.y - textLayoutResult.size.height / 2)
            )
        }
    }
}