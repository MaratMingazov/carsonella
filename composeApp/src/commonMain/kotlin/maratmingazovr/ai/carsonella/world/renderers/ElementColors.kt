package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.ElementType

/**
 * Светящаяся палитра (CPK-вдохновлённая), подобранная под тёмный фон.
 * Цвет = идентичность частицы: субатомные — по типу, атомы/ядра — по числу протонов (Z),
 * молекулы — отдельным цветом (до появления граф-созвездий).
 */
object ElementColors {
    private val PHOTON = Color(0xFFFFF3C4)        // тёплый бело-жёлтый свет
    private val ELECTRON = Color(0xFF4FC3F7)      // голубой
    private val POSITRON = Color(0xFFFF6E8A)      // розово-красный
    private val NEUTRON = Color(0xFFB0BEC5)       // нейтральный серо-голубой
    private val PROTON = Color(0xFFFFC07A)        // тёплый (голое положительное ядро)
    private val MOLECULE = Color(0xFF80DEEA)      // мягкий бирюзовый
    private val DEFAULT_HEAVY = Color(0xFFE0A86B) // тёплый «металл» для тяжёлых ядер

    // CPK-вдохновлённые цвета по Z (атомный номер), осветлённые для свечения на тёмном
    private val byZ: Map<Int, Color> = mapOf(
        1 to Color(0xFFF5F7FF),  // H — почти белый
        2 to Color(0xFF6EE6F2),  // He — голубой
        3 to Color(0xFFB99CFF),  // Li — фиолетовый
        4 to Color(0xFFA6E22E),  // Be — зелёный
        5 to Color(0xFFFFB59E),  // B — лосось
        6 to Color(0xFF9FB3C8),  // C — светлый графит
        7 to Color(0xFF6E9BFF),  // N — синий
        8 to Color(0xFFFF5E5E),  // O — красный
        9 to Color(0xFF8CE68C),  // F — зелёный
        10 to Color(0xFF7CE0F0), // Ne — голубой (благородный)
        11 to Color(0xFFC59CFF), // Na — фиолетовый
        12 to Color(0xFF8CE89A), // Mg — зелёный
        13 to Color(0xFFCBB5B0), // Al — серо-розовый
        14 to Color(0xFFE5C77A), // Si — песочный
        15 to Color(0xFFFFA85C), // P — оранжевый
        16 to Color(0xFFFFE25C), // S — жёлтый
        17 to Color(0xFF7CE68C), // Cl — зелёный
        18 to Color(0xFF74E2EE), // Ar — голубой
        19 to Color(0xFFC59CFF), // K — фиолетовый
        20 to Color(0xFFA8C0A0), // Ca — серо-зелёный
    )

    fun glow(element: Element): Color = when (element) {
        Element.PHOTON -> PHOTON
        Element.ELECTRON -> ELECTRON
        Element.POSITRON -> POSITRON
        Element.NEUTRON -> NEUTRON
        Element.Proton -> PROTON
        else -> when (element.details.type) {
            ElementType.Molecule -> MOLECULE
            else -> byZ[element.details.p] ?: DEFAULT_HEAVY
        }
    }
}

/**
 * Рисует мягкое свечение: яркое ядро → полупрозрачное halo → прозрачный край.
 * [intensity] усиливает яркость (например, для возбуждённых частиц).
 */
fun DrawScope.drawGlow(center: Offset, radius: Float, color: Color, intensity: Float = 1f) {
    val brush = Brush.radialGradient(
        colorStops = arrayOf(
            0.0f to color.copy(alpha = (0.95f * intensity).coerceAtMost(1f)),
            0.4f to color.copy(alpha = (0.45f * intensity).coerceAtMost(0.9f)),
            1.0f to color.copy(alpha = 0f),
        ),
        center = center,
        radius = radius,
    )
    drawCircle(brush = brush, radius = radius, center = center)
}