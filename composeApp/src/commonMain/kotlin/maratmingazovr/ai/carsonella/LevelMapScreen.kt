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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val CARD_WIDTH = 168.dp
private val CARD_BORDER = Color(0xFFE4E4E4)
private val DONE_FILL = Color(0xFFF1FBFA)
private val ACCENT = Color(0xFF45BDB5)
private val TEXT_COLOR = Color(0xFF5A5A5A)
private val LOCKED_COLOR = Color(0xFFD8D8D8)

// * Карта заданий: узлы — задания, слой — глубина по зависимостям. Руками не рисуется: и узлы, и слои выводятся из [LEVELS], поэтому карта показывает ровно то, что игра и запустит.
@Composable
fun LevelMapScreen(completed: Set<LevelId>, onBack: () -> Unit) {
    val layers = remember { levelLayers() }

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

            // Оси две: слои уходят вправо (глубина зависимостей), задания внутри слоя — вниз.
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                layers.forEach { layer ->
                    Column(
                        Modifier.width(CARD_WIDTH),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        layer.forEach { level -> LevelCard(level, stateOf(level, completed)) }
                    }
                }
            }
        }
    }
}

private enum class NodeState { DONE, OPEN, LOCKED }

private fun stateOf(level: Level, completed: Set<LevelId>): NodeState = when {
    level.id in completed -> NodeState.DONE
    completed.containsAll(level.requires) -> NodeState.OPEN
    else -> NodeState.LOCKED
}

// Слои: задание без зависимостей стоит в нулевом, остальные — на шаг правее самой глубокой зависимости.
private fun levelLayers(): List<List<Level>> {
    val byId = LEVELS.associateBy { it.id }
    val depth = mutableMapOf<LevelId, Int>()
    fun depthOf(level: Level): Int = depth.getOrPut(level.id) {
        level.requires.mapNotNull { byId[it] }.maxOfOrNull { depthOf(it) + 1 } ?: 0
    }
    val byDepth = LEVELS.groupBy { depthOf(it) }
    return byDepth.keys.sorted().map { byDepth.getValue(it) }
}

@Composable
private fun LevelCard(level: Level, state: NodeState) {
    val shape = RoundedCornerShape(12.dp)
    val done = state == NodeState.DONE
    Column(
        Modifier
            .width(CARD_WIDTH)
            .background(if (done) DONE_FILL else Color.Transparent, shape)
            .border(1.dp, if (done) ACCENT else CARD_BORDER, shape)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            NodeState.DONE -> {
                GoalPreview(level.goal, scale = 0.5f)
                Spacer(Modifier.height(10.dp))
                // У атомарной цели имени в реестре нет — тогда карточка обходится символом на кружке.
                level.goal.known?.let { CardText(it.name(LocalLang.current), TEXT_COLOR, 15.sp) }
            }
            NodeState.OPEN -> {
                GoalPreview(level.goal, scale = 0.5f)
                Spacer(Modifier.height(10.dp))
                CardText(level.task.of(LocalLang.current), TEXT_COLOR, 13.sp)
            }
            NodeState.LOCKED -> CardText("?", LOCKED_COLOR, 26.sp)
        }
    }
}

@Composable
private fun CardText(text: String, color: Color, size: TextUnit) {
    Text(
        text,
        fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = size,
        lineHeight = size * 1.35f, color = color, textAlign = TextAlign.Center,
    )
}

// «назад» строкой: экран и без того плотный, кнопка тут была бы лишним пятном.
@Composable
private fun MapLink(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        label,
        fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 18.sp,
        color = if (hovered) ACCENT else Color(0xFFA8A8A8),
        modifier = Modifier
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    )
}