package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.Element


object ElementColors {


    // --- МИНИМАЛИЗМ (Sokobond): плоская ПАСТЕЛЬНАЯ палитра для СВЕТЛОГО фона ---
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

    // Сплошная заливка кружка (плоский стиль). Субатомы — по типу частицы (иначе позитрон с p=1
    // случайно получил бы белый водорода из fillByZ), атомы/ядра — по Z, остальное — фолбэк.
    fun fill(element: Element): Color = when (element) {
        Element.PHOTON -> Color(0xFFFFE9A8) // тёплый бледно-жёлтый (свет)
        Element.ELECTRON -> Color(0xFFAFD3F2) // голубой (−)
        Element.POSITRON -> Color(0xFFF6B8C4) // розовый (+)
        Element.NEUTRON -> Color(0xFFD3D9DD) // нейтральный серый
        else -> fillByZ[element.details.p] ?: FILL_DEFAULT
    }
}

val BOND_COLOR = Color(0xFF212121)  // МИНИМАЛИЗМ: почти чёрная связь

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
 * Кружок элемента: заливка, чёрная обводка, символ по центру. Один вид на все места, где элемент
 * рисуется — холст, превью реестра, значки в Info-панели.
 *
 * [outline] — стиль обводки: холст подменяет её вращающейся штриховкой, когда элемент выделен.
 * [fontSizeSp] — размер символа; по умолчанию доля радиуса, но панель считает его от dp своего значка.
 */
fun DrawScope.drawElementCircle(
    textMeasurer: TextMeasurer,
    center: Offset,
    radius: Float,
    fill: Color,
    symbol: String,
    outline: Stroke = Stroke(DEFAULT_OUTLINE_WIDTH),
    fontSizeSp: Float = radius * 0.7f,
) {
    drawCircle(color = fill, center = center, radius = radius)
    drawCircle(color = Color.Black, center = center, radius = radius, style = outline)
    drawCenteredSymbol(textMeasurer, center, symbol, onFillTextColor(fill), fontSizeSp)
}

/**
 * Свободные валентные слоты — белые кружки на кромке: «вот куда ещё можно присоединить».
 * [startAngle] задаёт поворот: на холсте он медленно едет, в статичной картинке фиксирован.
 */
fun DrawScope.drawValenceSlots(
    center: Offset,
    radius: Float,
    freeSlots: Int,
    startAngle: Float,
    outlineWidth: Float = DEFAULT_OUTLINE_WIDTH,
) {
    if (freeSlots <= 0) return
    val slotRadius = (radius * 0.18f).coerceIn(2.5f, 6f)
    for (i in 0 until freeSlots) {
        val angle = startAngle + 2.0 * kotlin.math.PI * i / freeSlots
        val p = center + Offset(kotlin.math.cos(angle).toFloat() * radius, kotlin.math.sin(angle).toFloat() * radius)
        drawCircle(color = Color.White, center = p, radius = slotRadius)
        drawCircle(color = Color.Black, center = p, radius = slotRadius, style = Stroke(outlineWidth))
    }
}

/** Связь: [order] параллельных линий (двойная/тройная — со сдвигом перпендикулярно связи). */
fun DrawScope.drawBondLines(
    a: Offset,
    b: Offset,
    order: Int,
    lineWidth: Float = 2.5f,
    spacing: Float = 15f,
    color: Color = BOND_COLOR,
) {
    val dir = b - a
    val length = dir.getDistance()
    val perp = if (length > 1e-3f) Offset(-dir.y / length, dir.x / length) else Offset(0f, 1f)
    val firstShift = -(order - 1) / 2f
    for (i in 0 until order) {
        val shift = perp * ((firstShift + i) * spacing)
        drawLine(color = color, start = a + shift, end = b + shift, strokeWidth = lineWidth)
    }
}

private const val DEFAULT_OUTLINE_WIDTH = 2.5f