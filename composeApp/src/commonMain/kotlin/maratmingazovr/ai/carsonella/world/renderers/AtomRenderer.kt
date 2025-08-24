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
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.atoms.AtomState
import maratmingazovr.ai.carsonella.chemistry.atoms.HYDROGEN_ATOM_RADIUS
import maratmingazovr.ai.carsonella.toOffset

class AtomRenderer(
    private val textMeasurer: TextMeasurer,
) {
    fun render(
        drawScope: DrawScope,
        atomState: AtomState<*>) {
        when (atomState.element) {
            Element.H -> drawHydrogen( drawScope,atomState)
            Element.O -> drawOxygen( drawScope, atomState.position.toOffset())
            else -> throw NotImplementedError()
        }
    }

    private fun drawHydrogen(
        drawScope: DrawScope,
        hydrogenState: AtomState<*>,
    ) {
        val position = hydrogenState.position.jitter().toOffset()
        with(drawScope) {
            drawCircle(
                color = Color.Black,
                center = position,
                radius = HYDROGEN_ATOM_RADIUS,
                style = Stroke(
                    width = 1f,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(10f, 5f), // длина штриха, длина пробела
                        phase = 0f // смещение начала узора
                    )
                )
            )

            val textLayoutResult = textMeasurer.measure(text = "Н", style = TextStyle(color = Color.Black, fontSize = 10.sp))

            drawText(
                textLayoutResult,
                topLeft = Offset(position.x - textLayoutResult.size.width / 2, position.y - textLayoutResult.size.height / 2)
            )
        }
    }

    private fun drawOxygen(
        drawScope: DrawScope,
        position: Offset
    ) {
//        with(drawScope) {
//            drawCircle(
//                color = Color.Black,
//                center = position,
//                radius = HYDROGEN_ATOM_RADIUS,
//                style = Stroke(
//                    width = 1f,
//                    pathEffect = PathEffect.dashPathEffect(
//                        intervals = floatArrayOf(10f, 5f), // длина штриха, длина пробела
//                        phase = 0f // смещение начала узора
//                    )
//                )
//            )
//
//            val annotated = AnnotatedString.Builder().apply {
//                append("H")
//                pushStyle(SpanStyle(fontSize = 5.sp, baselineShift = BaselineShift.Subscript))
//                append("2")
//                pop()
//            }.toAnnotatedString()
//
//            val layout = textMeasurer.measure(
//                text = annotated,
//                style = TextStyle(color = Color.Black, fontSize = 10.sp)
//            )
//
//            drawText(
//                textLayoutResult = layout,
//                topLeft = Offset(x = position.x - layout.size.width / 2f, y = position.y - layout.size.height / 2f)
//            )
//        }
    }
}