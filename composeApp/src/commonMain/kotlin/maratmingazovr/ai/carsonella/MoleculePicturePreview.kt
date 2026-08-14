package maratmingazovr.ai.carsonella

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGeometry
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculePicture
import maratmingazovr.ai.carsonella.world.renderers.ElementColors
import maratmingazovr.ai.carsonella.world.renderers.drawCenteredSymbol
import maratmingazovr.ai.carsonella.world.renderers.onFillTextColor

private const val BOND_LINE_WIDTH = 2.5f
private const val BOND_LINE_SPACING = 8f          // сдвиг параллельных линий двойной/тройной связи
private val BOND_COLOR = Color(0xFF212121)
private const val OUTLINE_WIDTH = 2.5f

/**
 * Эталонная молекула как картинка: кружки атомов, кратные связи, свободные валентные слоты — то же,
 * что игрок видит на холсте, но по курируемой раскладке из реестра (в мире этой молекулы ещё нет).
 *
 * [scale] — во сколько раз мельче холста: 1f = ровно как в игре, вместе с радиусами и линиями.
 */
@Composable
fun MoleculePicturePreview(picture: MoleculePicture, scale: Float = 1f, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    // Доля длины связи из раскладки — в пикселях. Берём длину покоя пружин (r1 + r2 + BOND_PX) по самой
    // длинной связи: раскладка задаёт ОДИН масштаб на всю картинку, и по максимуму никто не налезет.
    val elementById = picture.graph.nodes.associate { it.localId to it.isotope }
    val unitPx = scale * picture.graph.bonds.maxOf { bond ->
        MoleculeGeometry.bondLengthPx(elementById.getValue(bond.atom1), elementById.getValue(bond.atom2), bond.order)
    }
    val positions = picture.offsets.mapValues { (_, offset) -> Offset(offset.x * unitPx, offset.y * unitPx) }
    val maxRadius = picture.graph.nodes.maxOf { it.isotope.details.radius } * scale

    // Размер бокса — по габаритам раскладки плюс радиус крайних атомов и запас на слоты.
    val minX = positions.values.minOf { it.x } - maxRadius - 8f
    val maxX = positions.values.maxOf { it.x } + maxRadius + 8f
    val minY = positions.values.minOf { it.y } - maxRadius - 8f
    val maxY = positions.values.maxOf { it.y } + maxRadius + 8f
    val widthDp = with(LocalDensity.current) { (maxX - minX).toDp() }
    val heightDp = with(LocalDensity.current) { (maxY - minY).toDp() }

    Canvas(modifier.size(widthDp, heightDp)) {
        val origin = Offset(-minX, -minY)   // сдвиг раскладки в положительные координаты канвы
        picture.graph.bonds.forEach { bond ->
            val a = positions.getValue(bond.atom1) + origin
            val b = positions.getValue(bond.atom2) + origin
            drawBondLines(a, b, bond.order, scale)
        }
        picture.graph.nodes.forEach { node ->
            drawPreviewAtom(
                textMeasurer = textMeasurer,
                center = positions.getValue(node.localId) + origin,
                element = node.isotope,
                freeSlots = picture.graph.freeValence(node.localId),
                scale = scale,
            )
        }
    }
}

// Кратность — параллельными линиями, как в EntityRenderer: одна/две/три.
private fun DrawScope.drawBondLines(a: Offset, b: Offset, order: Int, scale: Float) {
    val dir = b - a
    val length = dir.getDistance()
    val perp = if (length > 1e-3f) Offset(-dir.y / length, dir.x / length) else Offset(0f, 1f)
    val firstShift = -(order - 1) / 2f
    for (i in 0 until order) {
        val shift = perp * ((firstShift + i) * BOND_LINE_SPACING * scale)
        drawLine(color = BOND_COLOR, start = a + shift, end = b + shift, strokeWidth = BOND_LINE_WIDTH * scale)
    }
}

private fun DrawScope.drawPreviewAtom(
    textMeasurer: TextMeasurer,
    center: Offset,
    element: Element,
    freeSlots: Int,
    scale: Float,
) {
    val radius = element.details.radius * scale
    val fill = ElementColors.fill(element)
    drawCircle(color = fill, center = center, radius = radius)
    drawCircle(color = Color.Black, center = center, radius = radius, style = Stroke(OUTLINE_WIDTH * scale))
    drawCenteredSymbol(textMeasurer, center, element.bareSymbol, onFillTextColor(fill), fontSizeSp = radius * 0.7f)

    // Свободные слоты — те же белые кружки на кромке, что и в игре: «вот куда ещё можно присоединить».
    if (freeSlots > 0) {
        val slotRadius = (radius * 0.18f).coerceIn(2f, 6f)
        for (i in 0 until freeSlots) {
            val angle = 2.0 * kotlin.math.PI * i / freeSlots - kotlin.math.PI / 2.0
            val p = center + Offset(kotlin.math.cos(angle).toFloat() * radius, kotlin.math.sin(angle).toFloat() * radius)
            drawCircle(color = Color.White, center = p, radius = slotRadius)
            drawCircle(color = Color.Black, center = p, radius = slotRadius, style = Stroke(OUTLINE_WIDTH * scale))
        }
    }
}
