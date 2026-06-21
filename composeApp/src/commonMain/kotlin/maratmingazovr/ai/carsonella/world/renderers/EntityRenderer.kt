package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
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
        showLabel: Boolean = false,
    ) {
        when (entityState) {
            is SubAtomState -> subAtomRenderer.render(drawScope, entityState, phase, showLabel)
            is AtomState -> drawEntity(drawScope, entityState, phase, showLabel)
            is MoleculeState -> drawEntity(drawScope, entityState, phase, showLabel)
            is StarState -> drawStar(drawScope, entityState, phase, phase2)
            is SpaceModuleState -> drawEntity(drawScope, entityState, phase, showLabel)

        }
    }

    fun drawEntity(
        drawScope: DrawScope,
        entityState: EntityState<*>,
        phase: Float,
        showLabel: Boolean,
    ) {
        // параметры вибрации
        val amp = 1f + entityState.energy                  // амплитуда в пикселях
        val idSeed = (entityState.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val dx = amp * kotlin.math.cos(phase + idSeed)
        val dy = amp * kotlin.math.sin(phase + idSeed)
        val position = entityState.position.toOffset()  + Offset(dx, dy)

        val color = ElementColors.glow(entityState.element)
        val baseRadius = entityState.element.details.radius

        with(drawScope) {
            // мягкое свечение (ярче, если частица возбуждена)
            drawGlow(position, baseRadius * 1.5f, color, intensity = 1f + entityState.energy * 0.05f)
            // плотное ядро
            drawCircle(color = color.copy(alpha = 0.9f), center = position, radius = baseRadius * 0.35f)

            // символ — только при наведении/выборе, всплывает над частицей
            if (showLabel) {
                drawFloatingLabel(textMeasurer, position, baseRadius * 1.5f, entityState.element.symbol(entityState.electrons))
            }
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
            // тёплое светило: бело-жёлтое ядро → оранжевый → красный → прозрачность, с пульсом
            val gradientBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFFFFF6E0),
                    0.3f to Color(0xFFFFC04D),
                    0.6f to Color(0xFFFF6A3D),
                    1.0f to Color.Transparent,
                ),
                center = position,
                radius = pulsingRadius
            )

            // рисуем пульсирующий градиентный круг
            drawCircle(
                brush = gradientBrush,
                center = position,
                radius = pulsingRadius
            )
        }
    }
}