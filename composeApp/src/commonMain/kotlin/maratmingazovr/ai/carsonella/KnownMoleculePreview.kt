package maratmingazovr.ai.carsonella

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.chemistry.KnownMoleculeDetails
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGeometry
import maratmingazovr.ai.carsonella.world.renderers.ElementColors
import maratmingazovr.ai.carsonella.world.renderers.drawBondLines
import maratmingazovr.ai.carsonella.world.renderers.drawElementCircle
import maratmingazovr.ai.carsonella.world.renderers.drawValenceSlots

private const val BOND_LINE_WIDTH = 2.5f
private const val BOND_LINE_SPACING = 15f          // сдвиг параллельных линий двойной/тройной связи
private const val OUTLINE_WIDTH = 2.5f

/**
 * Эталонная молекула как картинка: кружки атомов, кратные связи, свободные валентные слоты — то же,
 * что игрок видит на холсте, но по курируемой раскладке из реестра (в мире этой молекулы ещё нет).
 *
 * [scale] — во сколько раз мельче холста: 1f = ровно как в игре, вместе с радиусами и линиями.
 */
@Composable
fun KnownMoleculePreview(knownMoleculeDetails: KnownMoleculeDetails, scale: Float = 1f, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    // Доля длины связи из раскладки — в пикселях. Берём длину покоя пружин (r1 + r2 + BOND_PX) по самой
    // длинной связи: раскладка задаёт ОДИН масштаб на всю картинку, и по максимуму никто не налезет.
    val elementById = knownMoleculeDetails.graph.nodes.associate { it.localId to it.isotope }
    val unitPx = scale * knownMoleculeDetails.graph.bonds.maxOf { bond ->
        MoleculeGeometry.bondLengthPx(elementById.getValue(bond.atom1), elementById.getValue(bond.atom2), bond.order)
    }
    val positions = knownMoleculeDetails.offsets.mapValues { (_, offset) -> Offset(offset.x * unitPx, offset.y * unitPx) }
    val maxRadius = knownMoleculeDetails.graph.nodes.maxOf { it.isotope.details.radius } * scale

    // Размер бокса — по габаритам раскладки плюс радиус крайних атомов и запас на слоты.
    val minX = positions.values.minOf { it.x } - maxRadius - 8f
    val maxX = positions.values.maxOf { it.x } + maxRadius + 8f
    val minY = positions.values.minOf { it.y } - maxRadius - 8f
    val maxY = positions.values.maxOf { it.y } + maxRadius + 8f
    val widthDp = with(LocalDensity.current) { (maxX - minX).toDp() }
    val heightDp = with(LocalDensity.current) { (maxY - minY).toDp() }

    Canvas(modifier.size(widthDp, heightDp)) {
        val origin = Offset(-minX, -minY)   // сдвиг раскладки в положительные координаты канвы
        knownMoleculeDetails.graph.bonds.forEach { bond ->
            val a = positions.getValue(bond.atom1) + origin
            val b = positions.getValue(bond.atom2) + origin
            drawBondLines(a, b, bond.order, lineWidth = BOND_LINE_WIDTH * scale, spacing = BOND_LINE_SPACING * scale)
        }
        knownMoleculeDetails.graph.nodes.forEach { node ->
            drawPreviewAtom(
                textMeasurer = textMeasurer,
                center = positions.getValue(node.localId) + origin,
                element = node.isotope,
                freeSlots = knownMoleculeDetails.graph.freeValence(node.localId),
                scale = scale,
            )
        }
    }
}

private fun DrawScope.drawPreviewAtom(
    textMeasurer: TextMeasurer,
    center: Offset,
    element: AtomElement,
    freeSlots: Int,
    scale: Float,
) {
    val radius = element.details.radius * scale
    val outlineWidth = OUTLINE_WIDTH * scale
    drawElementCircle(textMeasurer, center, radius, ElementColors.fill(element), element.bareSymbol, Stroke(outlineWidth))
    // Слоты стоят от «двенадцати часов»: картинка статичная, крутить их, как на холсте, нечем.
    drawValenceSlots(center, radius, freeSlots, startAngle = -kotlin.math.PI.toFloat() / 2f, outlineWidth = outlineWidth)
}
