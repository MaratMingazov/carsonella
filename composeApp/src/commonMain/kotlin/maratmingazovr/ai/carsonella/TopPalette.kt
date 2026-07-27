package maratmingazovr.ai.carsonella

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.world.renderers.ElementColors
import maratmingazovr.ai.carsonella.world.renderers.drawCenteredSymbol
import maratmingazovr.ai.carsonella.world.renderers.onFillTextColor

// Верхняя плашка-палитра: элементы кружочками (как на канве). Тащатся на канву тем же DragSource →
// спавн в DropTarget/App.onDrop.
@Composable
fun TopPalette(palette: List<Element>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.White)
            .drawBehind {   // нижняя граница-разделитель (как рамка прежней LeftPanel)
                val sw = 1.dp.toPx()
                val y = size.height - sw / 2
                drawLine(Color.LightGray, Offset(0f, y), Offset(size.width, y), sw)
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        palette.forEach { element ->
            DragSource(element = element) { PaletteAtom(element) }
        }
    }
}

// Плоский кружок элемента — тот же вид, что у частицы на канве (заливка + чёрная обводка + символ),
// но статично и без валентных слотов. Переиспользует ElementColors.fill / drawCenteredSymbol.
@Composable
private fun PaletteAtom(element: Element, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val fill = ElementColors.fill(Species.Elemental(element))
    val symbol = element.details.symbol.filter { it.isLetter() }
    Canvas(modifier.size(40.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 2f   // отступ под обводку
        drawCircle(color = fill, radius = radius, center = center)
        drawCircle(color = Color.Black, radius = radius, center = center, style = Stroke(width = 2f))
        drawCenteredSymbol(textMeasurer, center, symbol, onFillTextColor(fill), fontSizeSp = radius * 0.7f)
    }
}