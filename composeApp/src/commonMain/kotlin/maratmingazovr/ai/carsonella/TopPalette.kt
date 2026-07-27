package maratmingazovr.ai.carsonella

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.world.renderers.ElementColors
import maratmingazovr.ai.carsonella.world.renderers.drawCenteredSymbol
import maratmingazovr.ai.carsonella.world.renderers.onFillTextColor

// Мягкий пастельный бежевый фон плашек (палитра сверху + Info-карточка на канве).
internal val PANEL_BG = Color(0xFFF5EFE3)

// Верхняя плашка-палитра: элементы кружочками (как на канве). Тащатся на канву тем же DragSource →
// спавн в DropTarget/App.onDrop.
@Composable
fun TopPalette(palette: List<Element>, modifier: Modifier = Modifier) {
    // Плашка по ширине содержимого, по центру сверху (а не во всю ширину экрана).
    Box(modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .background(PANEL_BG, RoundedCornerShape(12.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            palette.forEach { element ->
                DragSource(element = element) { PaletteAtom(element) }
            }
        }
    }
}

// Плоский кружок элемента — тот же вид, что у частицы на канве (заливка + чёрная обводка + символ),
// но статично и без валентных слотов. Переиспользует ElementColors.fill / drawCenteredSymbol.
@Composable
internal fun PaletteAtom(element: Element, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val species = Species.Elemental(element)
    val fill = ElementColors.fill(species)
    val symbol = element.details.symbol.filter { it.isLetter() }
    val radiusPx = species.radius   // тот же радиус (px), что на канве: атомы 25f, субатомы 15f
    // box в dp под кружок + обводку; toDp переводит px в dp, чтобы Canvas в px вышел ровно 2*radius (+запас)
    val boxDp = with(LocalDensity.current) { (radiusPx * 2f + 5f).toDp() }

    // Наведение → обводка чуть толще (визуальный отклик палитры).
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val outlineWidth = if (hovered) 4f else 2.5f

    Canvas(modifier.size(boxDp).hoverable(interaction)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = fill, radius = radiusPx, center = center)
        drawCircle(color = Color.Black, radius = radiusPx, center = center, style = Stroke(width = outlineWidth))
        drawCenteredSymbol(textMeasurer, center, symbol, onFillTextColor(fill), fontSizeSp = radiusPx * 0.7f)
    }
}