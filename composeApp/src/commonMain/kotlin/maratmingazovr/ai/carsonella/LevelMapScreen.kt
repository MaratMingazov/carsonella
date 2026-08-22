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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val CARD_WIDTH = 168.dp
private val CARD_HEIGHT = 140.dp                  // клетка спирали одна на всех, поэтому высота фиксированная
private val CARD_GAP = 16.dp
private val STEP_X = CARD_WIDTH + CARD_GAP
private val STEP_Y = CARD_HEIGHT + CARD_GAP
private val MAP_MARGIN = 24.dp
private val CARD_BORDER = Color(0xFFE4E4E4)
private val DONE_FILL = Color(0xFFF1FBFA)
private val ACCENT = Color(0xFF45BDB5)
private val TEXT_COLOR = Color(0xFF5A5A5A)
private val LOCKED_COLOR = Color(0xFFD8D8D8)

// * Карта заданий: узлы — задания, слой — глубина по зависимостям. Руками не рисуется: и узлы, и слои выводятся из [LEVELS], поэтому карта показывает ровно то, что игра и запустит.
@Composable
fun LevelMapScreen(playerState: PlayerState, onBack: () -> Unit) {
    val map = remember { spiralMap() }
    val completed = playerState.progress.completedLevels
    var opened by remember { mutableStateOf<Level?>(null) }   // карточка, раскрытая в окно

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            // Esc сначала закрывает окно и только потом уводит с карты: иначе взгляд на карточку
            // выкидывал бы в меню.
            .onPreviewKeyEvent { e ->
                when {
                    e.type != KeyDown || e.key != Key.Escape -> false
                    opened != null -> { opened = null; true }
                    else -> { onBack(); true }
                }
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

            // Карта разворачивается из центра наружу по часовой стрелке.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val mapWidth = STEP_X * map.columns - CARD_GAP
                val mapHeight = STEP_Y * map.rows - CARD_GAP
                // Полотно не меньше окна: пока карта мелкая, она стоит по центру, а не липнет в угол.
                val canvasWidth = (mapWidth + MAP_MARGIN * 2).coerceAtLeast(maxWidth)
                val canvasHeight = (mapHeight + MAP_MARGIN * 2).coerceAtLeast(maxHeight)

                Box(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(Modifier.width(canvasWidth).height(canvasHeight), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(mapWidth).height(mapHeight)) {
                            map.cards.forEach { (level, cell) ->
                                // Туман не кликается: раскрывать в нём нечего, там и на карточке одни «?».
                                val state = stateOf(level, completed)
                                Box(Modifier.offset(x = STEP_X * cell.x, y = STEP_Y * cell.y)) {
                                    LevelCard(level, state, onClick = if (state == LevelStatus.LOCKED) null else ({ opened = level }))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Окно поверх карты, а не внутри прокрутки: иначе оно уезжало бы вместе с содержимым.
        // Пройденный уровень рассказывает, что получилось, доступный — что предстоит сделать.
        opened?.let { level ->
            val done = stateOf(level, completed) == LevelStatus.DONE
            LevelDetails(level, if (done) level.reward.text else level.description, text(UiString.MENU_CLOSE)) { opened = null }
        }
    }
}

private enum class LevelStatus { DONE, OPEN, LOCKED }

private fun stateOf(level: Level, completed: Set<LevelId>): LevelStatus {
    val done = effectiveCompleted(completed)
    return when {
        level.id in done -> LevelStatus.DONE
        done.containsAll(level.requiredLevels) -> LevelStatus.OPEN
        else -> LevelStatus.LOCKED
    }
}

// Слои: задание без зависимостей стоит в нулевом, остальные — на шаг правее самой глубокой зависимости.
private fun levelLayers(): List<List<Level>> {
    val byId = LEVELS.associateBy { it.id }
    val depth = mutableMapOf<LevelId, Int>()
    fun depthOf(level: Level): Int = depth.getOrPut(level.id) {
        level.requiredLevels.mapNotNull { byId[it] }.maxOfOrNull { depthOf(it) + 1 } ?: 0
    }
    val byDepth = LEVELS.groupBy { depthOf(it) }
    return byDepth.keys.sorted().map { byDepth.getValue(it) }
}

// Карточки на клетках сетки; [cards] уже сдвинуты в положительные координаты, отсчёт от левого верхнего угла.
private class SpiralMap(val cards: List<Pair<Level, IntOffset>>, val columns: Int, val rows: Int)

// Порядок обхода — слои зависимостей, внутри слоя порядок [LEVELS]. Угол сам по себе ничего не значит:
// это упаковка, как и вертикаль в прежней раскладке колонками.
private fun spiralMap(): SpiralMap {
    val order = levelLayers().flatten()
    val cells = spiralCells(order.size)
    val minX = cells.minOf { it.x }
    val minY = cells.minOf { it.y }
    return SpiralMap(
        cards = order.zip(cells.map { IntOffset(it.x - minX, it.y - minY) }),
        columns = cells.maxOf { it.x } - minX + 1,
        rows = cells.maxOf { it.y } - minY + 1,
    )
}

// Квадратная спираль из центра: вправо, вниз, влево, вверх — по часовой стрелке при оси y вниз.
// Отрезок удлиняется через поворот, поэтому виток за витком охват растёт на клетку в каждую сторону.
private fun spiralCells(count: Int): List<IntOffset> {
    val cells = ArrayList<IntOffset>(count)
    var x = 0
    var y = 0
    var dx = 1
    var dy = 0
    var run = 1
    var stepsLeft = 1
    var turns = 0
    while (cells.size < count) {
        cells += IntOffset(x, y)
        x += dx
        y += dy
        if (--stepsLeft == 0) {
            val turnedX = -dy
            dy = dx
            dx = turnedX
            if (++turns % 2 == 0) run++
            stepsLeft = run
        }
    }
    return cells
}

// это как мы рисуем карточку уровня на карте. [onClick] пустой у тумана — его раскрывать нечем.
@Composable
private fun LevelCard(level: Level, state: LevelStatus, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    val done = state == LevelStatus.DONE
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .background(if (done) DONE_FILL else Color.Transparent, shape)
            .border(1.dp, if (done || hovered) ACCENT else CARD_BORDER, shape)   // hovered бывает только у кликабельной
            .then(
                // Без ряби: экран нарисован руками, материаловский всплеск тут чужой. Отклик даёт рамка.
                if (onClick == null) Modifier
                else Modifier.hoverable(interaction).clickable(interaction, indication = null) { onClick() }
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LevelImage(level.image, scale = 0.5f) // рисуем саму молекулу или атом, который нужно получить
        Spacer(Modifier.height(10.dp))
        CardText(text(level.title), TEXT_COLOR, 15.sp) // название карточки
//        when (state) {
//            LevelStatus.OPEN -> {
//                LevelImage(level.image, scale = 0.5f)
//                Spacer(Modifier.height(10.dp))
//                CardText(text(level.title), TEXT_COLOR, 13.sp) // название карточки
//            }
//            LevelStatus.DONE -> {
//                LevelImage(level.image, scale = 0.5f) // рисуем саму молекулу или атом, который нужно получить
//                Spacer(Modifier.height(10.dp))
//                CardText(text(level.title), TEXT_COLOR, 15.sp) // название карточки
//            }
//            LevelStatus.LOCKED -> CardText("?", LOCKED_COLOR, 26.sp)
//        }
    }
}

@Composable
private fun CardText(text: String, color: Color, size: TextUnit) {
    Text(
        text,
        fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = size,
        lineHeight = size * 1.35f, color = color, textAlign = TextAlign.Center,
        maxLines = 2, overflow = TextOverflow.Ellipsis,   // высота клетки фиксированная, длинное название её не растянет
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