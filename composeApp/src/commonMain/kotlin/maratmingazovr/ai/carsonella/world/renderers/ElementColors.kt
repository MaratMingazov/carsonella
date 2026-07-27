package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.ElementType
import maratmingazovr.ai.carsonella.chemistry.Species

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

    fun glow(species: Species): Color = when (species) {
        is Species.Molecular -> MOLECULE
        is Species.Elemental -> glowElement(species.element)
    }

    private fun glowElement(element: Element): Color = when (element) {
        Element.PHOTON -> PHOTON
        Element.ELECTRON -> ELECTRON
        Element.POSITRON -> POSITRON
        Element.NEUTRON -> NEUTRON
        Element.Proton -> PROTON
        else -> byZ[element.details.p] ?: DEFAULT_HEAVY
    }

    // --- МИНИМАЛИЗМ (Sokobond): плоская ПАСТЕЛЬНАЯ палитра для СВЕТЛОГО фона ---
    // Приглушённые, светлые тона (порядок CPK, но мягче): читаются на белом с чёрной обводкой.
    // Исключения ради контраста: H — белый (виден за счёт обводки), C — тёмный графит (буква белая).
    private val fillByZ: Map<Int, Color> = mapOf(
        1 to Color(0xFFFFFFFF),  2 to Color(0xFFCDECEC),
        3 to Color(0xFFD9C7F5),  4 to Color(0xFFDCEBB0),  5 to Color(0xFFF6CFC6),
        6 to Color(0xFF595959),  7 to Color(0xFFA3B4E6),  8 to Color(0xFFF2A0A0),
        9 to Color(0xFFBEE3A8), 10 to Color(0xFFC9E9F0),
        11 to Color(0xFFD3BEF0), 12 to Color(0xFFBFEBB4), 13 to Color(0xFFDDCFCB),
        14 to Color(0xFFEFDCBB), 15 to Color(0xFFF7C9A0), 16 to Color(0xFFF3E9A8),
        17 to Color(0xFFB4E3B0), 18 to Color(0xFFCBE6EC), 19 to Color(0xFFD3BEEA),
        20 to Color(0xFFC0E6AE),
    )
    private val FILL_DEFAULT = Color(0xFFD5D5D5)  // молекула-фолбэк / неизвестный элемент — мягкий серый

    /** Сплошная заливка кружка атома (плоский стиль). */
    fun fill(species: Species): Color = when (species) {
        is Species.Molecular -> FILL_DEFAULT
        is Species.Elemental -> fillElement(species.element)
    }

    // Заливка по идентичности: субатомы — по типу частицы (иначе позитрон/протон с p=1 случайно
    // получили бы белый водорода из fillByZ), атомы/ядра — по Z, остальное — фолбэк.
    private fun fillElement(element: Element): Color = when (element) {
        Element.PHOTON -> Color(0xFFFFE9A8) // тёплый бледно-жёлтый (свет)
        Element.ELECTRON -> Color(0xFFAFD3F2) // голубой (−)
        Element.POSITRON -> Color(0xFFF6B8C4) // розовый (+)
        Element.NEUTRON -> Color(0xFFD3D9DD) // нейтральный серый
        Element.Proton -> Color(0xFFFAD0A0) // тёплый (голое положительное ядро)
        else -> fillByZ[element.details.p] ?: FILL_DEFAULT
    }
}

/** Цвет символа поверх заливки: чёрный на светлой, белый на тёмной (по яркости заливки). */
fun onFillTextColor(fill: Color): Color = if (fill.luminance() > 0.55f) Color.Black else Color.White

/** Символ В ЦЕНТРЕ кружка (минимализм Sokobond). [fontSizeSp] — размер шрифта в sp. */
fun DrawScope.drawCenteredSymbol(
    textMeasurer: TextMeasurer,
    center: Offset,
    text: String,
    color: Color,
    fontSizeSp: Float,
) {
    val layout = textMeasurer.measure(text = text, style = TextStyle(color = color, fontSize = fontSizeSp.sp))
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
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

/**
 * Подпись частицы, всплывающая НАД ней (а не поверх ядра), с тёмной подложкой для читаемости.
 * Символы приводятся к ASCII (в вебе нет глифов надстрочных/подстрочных).
 */
fun DrawScope.drawFloatingLabel(
    textMeasurer: TextMeasurer,
    center: Offset,
    aboveRadius: Float,
    text: String,
) {
    val layout = textMeasurer.measure(
        text = text,
        style = TextStyle(color = Color.White, fontSize = 11.sp),
    )
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val x = center.x - w / 2f
    val y = center.y - aboveRadius - 6f - h
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(x - 4f, y - 2f),
        size = Size(w + 8f, h + 4f),
        cornerRadius = CornerRadius(4f, 4f),
    )
    drawText(layout, topLeft = Offset(x, y))
}