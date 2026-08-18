package maratmingazovr.ai.carsonella

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.world.PaletteItem
import maratmingazovr.ai.carsonella.world.PaletteSlot
import maratmingazovr.ai.carsonella.world.neutralElectrons
import maratmingazovr.ai.carsonella.world.renderers.BARE_PROTON_FILL
import maratmingazovr.ai.carsonella.world.renderers.BARE_PROTON_SYMBOL
import maratmingazovr.ai.carsonella.world.renderers.ElementColors
import maratmingazovr.ai.carsonella.world.renderers.drawCenteredSymbol
import maratmingazovr.ai.carsonella.world.renderers.isBareProton
import maratmingazovr.ai.carsonella.world.renderers.onFillTextColor

// Мягкий пастельный бежевый фон плашек (палитра + Info-карточка на канве).
internal val PANEL_BG = Color(0xFFF5EFE3)

// Геометрия палитры: числа остатков лежат отдельной строкой и повторяют эти же значения, иначе
// столбики разъедутся.
private val PLATE_PADDING = 12.dp
private val SLOT_GAP = 12.dp
private val MOLECULE_SLOT_WIDTH = 64.dp   // молекула шире кружка: даём ей фиксированную колонку

// Плашка-палитра под холстом: элементы кружочками (как на канве). Тащатся на канву тем же DragSource →
// спавн в DropTarget/App.onDrop. Задание [level] висит всплывашкой слева над палитрой — подсказка там же,
// откуда игрок берёт атомы.
@Composable
fun PaletteBar(palette: List<PaletteSlot>, level: Level?, modifier: Modifier = Modifier) {
    // Плашка по ширине содержимого, по центру снизу (а не во всю ширину экрана).
    Box(modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),   // зазор между пузырём и палитрой
        ) {
            // Сдвиг влево: пузырь стоит не поверх палитры, а сбоку, и стрелка приходит в неё по дуге.
            if (level != null) TaskBubble(level, Modifier.offset(x = (-160).dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier
                        .background(PANEL_BG, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                        .padding(horizontal = PLATE_PADDING, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(SLOT_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    palette.forEach { slot ->
                        // Кончился — гаснет и перестаёт быть DragSource: тащить нечего.
                        Box(Modifier.width(slotWidth(slot.item)), contentAlignment = Alignment.Center) {
                            if (slot.count > 0) DragSource(item = slot.item) { PaletteItemView(slot.item) }
                            else PaletteItemView(slot.item, Modifier.alpha(0.35f))
                        }
                    }
                }
                // Остатки — ПОД плашкой, а не внутри: внутри они растягивали её по высоте. Тот же шаг и
                // те же ширины, что у кружков, поэтому каждое число стоит ровно под своим.
                Spacer(Modifier.height(5.dp))
                Row(
                    Modifier.padding(horizontal = PLATE_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(SLOT_GAP),
                ) {
                    palette.forEach { slot ->
                        Text(
                            "×${slot.count}",
                            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 12.sp,
                            color = Color(0xFF7A7A7A), textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(slotWidth(slot.item))
                                .alpha(if (slot.count > 0) 1f else 0.4f),
                        )
                    }
                }
            }
        }
    }
}

// Что лежит в слоте: кружок элемента или эталонная картинка молекулы (мельче, чем в карточке уровня).
@Composable
internal fun PaletteItemView(paletteItem: PaletteItem, modifier: Modifier = Modifier) {
    when (paletteItem) {
        is PaletteItem.Atom -> PaletteAtom(paletteItem.element, modifier, paletteItem.electrons)
        is PaletteItem.KnownMolecule -> KnownMoleculePreview(paletteItem.knownMoleculeId.details, scale = 0.45f, modifier = modifier)
    }
}

// Ширина слота: и кружок, и число под ним занимают её целиком, поэтому столбики не разъезжаются.
@Composable
private fun slotWidth(item: PaletteItem): Dp = when (item) {
    is PaletteItem.Atom -> paletteAtomBoxDp(item.element, item.electrons)
    is PaletteItem.KnownMolecule -> MOLECULE_SLOT_WIDTH
}

// Ширина бокса кружка: радиус в px переводим в dp, чтобы Canvas вышел ровно 2*radius (+запас на обводку).
@Composable
internal fun paletteAtomBoxDp(element: Element, electrons: Int = neutralElectrons(element)): Dp =
    with(LocalDensity.current) { (atomRadiusPx(element, electrons) * 2f + 5f).toDp() }

// Голый водород — это протон: мельче атома и со своим символом, как на канве (см. EntityRenderer).
private fun atomRadiusPx(element: Element, electrons: Int) =
    if (isBareProton(element, electrons)) Element.ELECTRON.details.radius else element.details.radius

// Плоский кружок элемента — тот же вид, что у частицы на канве (заливка + чёрная обводка + символ),
// но статично и без валентных слотов. Переиспользует ElementColors.fill / drawCenteredSymbol.
@Composable
internal fun PaletteAtom(element: Element, modifier: Modifier = Modifier, electrons: Int = neutralElectrons(element)) {
    val textMeasurer = rememberTextMeasurer()
    val bareProton = isBareProton(element, electrons)
    val fill = if (bareProton) BARE_PROTON_FILL else ElementColors.fill(element)
    val symbol = if (bareProton) BARE_PROTON_SYMBOL else element.bareSymbol
    val radiusPx = atomRadiusPx(element, electrons)
    val boxDp = paletteAtomBoxDp(element, electrons)

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