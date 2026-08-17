package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.graph.KnownMolecule
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeId
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry

private val CARD_WIDTH = 128.dp
private val CARD_BORDER = Color(0xFFE4E4E4)
private val CARD_OPEN_FILL = Color(0xFFF1FBFA)   // открытая карточка светится бирюзой, а не только рамкой
private val LAYER_LABEL = Color(0xFFC0C0C0)
private val NAME_COLOR = Color(0xFF5A5A5A)
private val FORMULA_COLOR = Color(0xFF45BDB5)

/**
 * Карта открытий: весь реестр, разложенный слоями по числу действий игрока от атомов
 * ([MoleculeRegistry.buildSteps]). Слева направо — усложнение: атомы, потом первая связь, потом всё
 * остальное. Ничего не прописано руками: узлы и слои выводятся из реестра, и карта не может соврать.
 *
 * Открытое подсвечено журналом игрока. Слоёв доступности (доступное → силуэт → туман) пока нет, поэтому
 * весь реестр виден сразу.
 */
@Composable
fun DiscoveryMapScreen(discovered: Set<MoleculeId>, onBack: () -> Unit) {
    // Язык в ключе: карточки сортируются по имени, а порядок имён у языков разный.
    val lang = LocalLang.current
    val layers = remember(lang) { buildLayers(lang) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyDown && e.key == Key.Escape) { onBack(); true } else false
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text(UiString.MAP_TITLE),
                    fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 28.sp,
                    letterSpacing = 0.08.em, color = Color(0xFFC8C8C8),
                )
                Spacer(Modifier.weight(1f))
                MapLink(text(UiString.MENU_BACK), onBack)
            }

            // Оси две: колонки уходят вправо (усложнение), карточки внутри колонки — вниз.
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                layers.forEach { layer -> LayerColumn(layer, discovered) }
            }
        }
    }
}

// Слой карты: сколько действий от атомов и что на этом расстоянии стоит.
private class MapLayer(val steps: Int, val atoms: List<Element>, val molecules: List<KnownMolecule>)

private fun buildLayers(lang: Lang): List<MapLayer> {
    val bySteps = MoleculeRegistry.all
        .sortedBy { it.name(lang) }
        .groupBy { MoleculeRegistry.buildSteps(it.id) }
    val zero = MapLayer(0, MoleculeRegistry.atomsInUse, emptyList())
    val rest = bySteps.keys.sorted().map { steps -> MapLayer(steps, emptyList(), bySteps.getValue(steps)) }
    return listOf(zero) + rest
}

@Composable
private fun LayerColumn(layer: MapLayer, discovered: Set<MoleculeId>) {
    Column(
        Modifier.width(CARD_WIDTH),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (layer.steps == 0) text(UiString.MAP_ATOMS) else mapSteps(LocalLang.current, layer.steps),
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 13.sp,
            letterSpacing = 0.1.em, color = LAYER_LABEL,
        )
        layer.atoms.forEach { element -> AtomCard(element) }
        layer.molecules.forEach { known -> MoleculeCard(known, open = known.id in discovered) }
    }
}

@Composable
private fun AtomCard(element: Element) {
    MapCard {
        PaletteAtom(element)
        Spacer(Modifier.height(8.dp))
        CardName(element.details.description.ifEmpty { element.bareSymbol })
    }
}

@Composable
private fun MoleculeCard(known: KnownMolecule, open: Boolean) {
    MapCard(open) {
        // Картинка, если раскладка нарисована; иначе структурная формула текстом.
        val picture = MoleculeRegistry.picture(known.id)
        if (picture != null) MoleculePicturePreview(picture, scale = 0.5f)
        else Text(
            known.structuralFormula.ifEmpty { "?" },
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 16.sp,
            color = FORMULA_COLOR, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        CardName(known.name(LocalLang.current))
    }
}

@Composable
private fun MapCard(open: Boolean = false, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(CARD_WIDTH)
            .background(if (open) CARD_OPEN_FILL else Color.Transparent, shape)
            .border(1.dp, if (open) FORMULA_COLOR else CARD_BORDER, shape)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

// «назад» строкой: экран и без того плотный, кнопка тут была бы лишним пятном.
@Composable
private fun MapLink(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        label,
        fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 18.sp,
        color = if (hovered) FORMULA_COLOR else Color(0xFFA8A8A8),
        modifier = Modifier
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    )
}

@Composable
private fun CardName(name: String) {
    Text(
        name,
        fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 14.sp,
        lineHeight = 18.sp, color = NAME_COLOR, textAlign = TextAlign.Center,
    )
}
