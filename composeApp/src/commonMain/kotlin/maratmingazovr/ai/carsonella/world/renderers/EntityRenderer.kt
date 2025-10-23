package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import maratmingazovr.ai.carsonella.chemistry.SpaceModuleState
import maratmingazovr.ai.carsonella.chemistry.StarState
import maratmingazovr.ai.carsonella.chemistry.SubAtomState
import maratmingazovr.ai.carsonella.toOffset

class EntityRenderer(
    private val textMeasurer: TextMeasurer,
) {

    private val subAtomRenderer = SubAtomRenderer(textMeasurer)

    fun render(
        drawScope: DrawScope,
        entityState: EntityState<*>,
        phase: Float,
        phase2: Float,
    ) {
        when (entityState) {
            is SubAtomState -> subAtomRenderer.render(drawScope, entityState, phase)
            is AtomState -> drawEntity(drawScope, entityState, phase)
            is MoleculeState -> drawEntity(drawScope, entityState, phase)
            is StarState -> drawStar(drawScope, entityState, phase, phase2)
            is SpaceModuleState -> drawEntity(drawScope, entityState, phase)

        }
    }

    fun drawEntity(
        drawScope: DrawScope,
        entityState: EntityState<*>,
        phase: Float,
    ) {
        // параметры вибрации
        val amp = 1f + entityState.energy                  // амплитуда в пикселях
        val idSeed = (entityState.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val dx = amp * kotlin.math.cos(phase + idSeed)
        val dy = amp * kotlin.math.sin(phase + idSeed)
        val position = entityState.position.toOffset()  + Offset(dx, dy)

        with(drawScope) {
            drawCircle(
                color = Color.Black,
                center = position,
                radius = entityState.element.details.radius,
                style = Stroke(
                    width = 1f,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(10f, 5f), // длина штриха, длина пробела
                        phase = 0f // смещение начала узора
                    )
                )
            )

            val textLayoutResult = textMeasurer.measure(text = entityState.element.details.symbol, style = TextStyle(color = Color.Black, fontSize = 10.sp))

            drawText(
                textLayoutResult,
                topLeft = Offset(position.x - textLayoutResult.size.width / 2, position.y - textLayoutResult.size.height / 2)
            )
        }
    }

    fun drawStar(
        drawScope: DrawScope,
        entityState: EntityState<*>,
        phase: Float,
        phase2: Float,
    ) {
        // параметры вибрации
        val amp = 1f + entityState.energy                  // амплитуда в пикселях
        val idSeed = (entityState.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val dx = amp * kotlin.math.cos(phase2 + idSeed)
        val dy = amp * kotlin.math.sin(phase2 + idSeed)
        val position = entityState.position.toOffset()  + Offset(dx, dy)

        // пульсирующий радиус для границы
        val baseRadius = entityState.element.details.radius + 5f   // базовый радиус круга
        val pulse = 10f * kotlin.math.abs(kotlin.math.sin(phase2 + idSeed)) // амплитуда пульса
        val pulsingRadius = baseRadius + pulse

        with(drawScope) {

            // радиальный градиент: от красного на границе к прозрачному в центре
            val gradientBrush = Brush.radialGradient(
                colors = listOf(Color.Red, Color.Transparent),
                center = position,
                radius = pulsingRadius
            )

            // рисуем пульсирующий градиентный круг
            drawCircle(
                brush = gradientBrush,
                center = position,
                radius = pulsingRadius
            )

//            drawCircle(
//                color = Color.Black,
//                center = position,
//                radius = entityState.element.radius,
//                style = Stroke(width = 1f,)
//            )
        }
    }
}