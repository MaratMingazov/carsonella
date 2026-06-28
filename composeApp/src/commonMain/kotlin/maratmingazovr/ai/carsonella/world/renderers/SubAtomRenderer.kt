package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
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
        showLabel: Boolean,
    ) {
        when (state.element) {
            Element.PHOTON -> drawPhoton(drawScope, state)
            Element.ELECTRON -> drawChargedSubAtom(drawScope, state, radius = 9f, label = "−", showLabel = showLabel)
            Element.POSITRON -> drawChargedSubAtom(drawScope, state, radius = 9f, label = "+", showLabel = showLabel)
            Element.Proton -> drawNucleon(drawScope, state, phase, label = "+", showLabel = showLabel)
            Element.NEUTRON -> drawNucleon(drawScope, state, phase, label = "n", showLabel = showLabel)
            else -> throw NotImplementedError()
        }
    }

    private fun drawPhoton(
        drawScope: DrawScope,
        state: SubAtomState,
    ) {
        val p = state.position.toOffset()
        with(drawScope) {
            // яркая искра света: маленькое свечение + плотное ядро
            drawGlow(p, radius = 8f, color = ElementColors.glow(state.species), intensity = 1.2f)
            drawCircle(color = Color(0xFFFFFDF0), center = p, radius = 2.5f)
        }
    }

    // Электрон / позитрон: свечение цвета заряда + символ по наведению.
    private fun drawChargedSubAtom(
        drawScope: DrawScope,
        state: SubAtomState,
        radius: Float,
        label: String,
        showLabel: Boolean,
    ) {
        val p = state.position.toOffset()
        val color = ElementColors.glow(state.species)
        with(drawScope) {
            drawGlow(p, radius = radius, color = color)
            drawCircle(color = color.copy(alpha = 0.9f), center = p, radius = radius * 0.35f)
            if (showLabel) drawFloatingLabel(textMeasurer, p, radius, label)
        }
    }

    // Протон / нейтрон: «нуклон» со свечением и лёгкой вибрацией; символ по наведению.
    private fun drawNucleon(
        drawScope: DrawScope,
        state: SubAtomState,
        phase: Float,
        label: String,
        showLabel: Boolean,
    ) {
        val amp = 2f
        val idSeed = (state.id % 1000).toFloat()
        val dx = amp * kotlin.math.cos(phase + 0.7f * idSeed)
        val dy = amp * kotlin.math.sin(1.6f * phase + 0.37f * idSeed)
        val p = state.position.toOffset() + Offset(dx, dy)
        val color = ElementColors.glow(state.species)
        with(drawScope) {
            drawGlow(p, radius = 11f, color = color)
            drawCircle(color = color.copy(alpha = 0.9f), center = p, radius = 3.5f)
            if (showLabel) drawFloatingLabel(textMeasurer, p, 11f, label)
        }
    }
}