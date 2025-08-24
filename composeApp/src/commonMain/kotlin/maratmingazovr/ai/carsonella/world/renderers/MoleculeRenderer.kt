package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.atoms.HYDROGEN_ATOM_RADIUS
import maratmingazovr.ai.carsonella.chemistry.molecules.MoleculeState
import maratmingazovr.ai.carsonella.toOffset

class MoleculeRenderer(
    private val textMeasurer: TextMeasurer,
) {
    fun render(
        drawScope: DrawScope,
        moleculeState: MoleculeState<*>
    ) {
        drawDiHydrogen(drawScope, moleculeState.position().toOffset())
    }

    private fun drawDiHydrogen(
        drawScope: DrawScope,
        position: Offset,
    ) {
        with(drawScope) {


            val offset = HYDROGEN_ATOM_RADIUS * 0.6f // насколько смещать круги друг к другу

            // Координаты двух атомов
            val leftAtom = Offset(position.x - offset, position.y)
            val rightAtom = Offset(position.x + offset, position.y)

            // Левый атом
            drawCircle(
                color = Color.Black,
                center = leftAtom,
                radius = HYDROGEN_ATOM_RADIUS,
                style = Stroke(
                    width = 1f,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(10f, 5f), // длина штриха, длина пробела
                        phase = 0f // смещение начала узора
                    )
                )
            )

            // Правый атом
            drawCircle(
                color = Color.Black,
                center = rightAtom,
                radius = HYDROGEN_ATOM_RADIUS,
                style = Stroke(
                    width = 1f,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(10f, 5f), // длина штриха, длина пробела
                        phase = 0f // смещение начала узора
                    )
                )
            )

            // Подпись "H₂" в центре между атомами
            val annotated = AnnotatedString.Builder().apply {
                append("H")
                pushStyle(SpanStyle(fontSize = 5.sp, baselineShift = BaselineShift.Subscript))
                append("2")
                pop()
            }.toAnnotatedString()

            val layout = textMeasurer.measure(
                text = annotated,
                style = TextStyle(color = Color.Black, fontSize = 10.sp)
            )

            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = position.x - layout.size.width / 2f,
                    y = position.y - layout.size.height / 2f
                )
            )




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
        }
    }
}