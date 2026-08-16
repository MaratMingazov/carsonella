package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import maratmingazovr.ai.carsonella.world.World
import maratmingazovr.ai.carsonella.world.renderers.EntityRenderer

/**
 * Игровой экран: живой мир, палитра сверху, канва с правой панелью.
 *
 * Мир живёт ровно столько, сколько живёт этот экран: `World.start()` крутит вечный цикл в
 * rememberCoroutineScope, а тот отменяется при уходе экрана из композиции. Выход в меню
 * останавливает симуляцию, вход создаёт мир заново — с чистым холстом.
 */
@Composable
fun GameScreen(onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val renderer = remember { EntityRenderer(textMeasurer) }
    val world = remember { World(scope).apply { start() } }

    // Единый монотонный «часовой» источник (секунды с запуска). Каждый эффект берёт свою частоту
    // (VIB_HZ/STAR_HZ/SLOT_HZ в рендере). Монотонность (НЕ заворачивается) → плавное непрерывное
    // вращение слотов без скачков, в отличие от прежних зацикленных phase/phase2.
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) { withFrameNanos { now -> value = (now - start) / 1_000_000_000f } }
    }

    var hoverPos by remember { mutableStateOf<Offset?>(null) } // это координаты моего курсора на канве
    // hoveredId здесь НЕТ намеренно: «что под курсором» — не состояние, а функция от hoverPos и
    // положения частиц. SceneCanvas вычисляет его сам (см. hitTest), поэтому хранить и обновлять
    // его снаружи нечего.
    var selectedId by remember { mutableStateOf<Long?>(null) }

    // Прогресс по лестнице. Цель живёт в рейле слева, награда — окном поверх холста.
    var levelIndex by remember { mutableStateOf(0) }
    var reward by remember { mutableStateOf(false) }
    var welcome by remember { mutableStateOf(true) }   // приветствие на входе; холст под ним пуст
    val level = LEVELS.getOrNull(levelIndex)

    // Цель засчитывается по тому же событию, что рисует всплывающее имя: известная молекула родилась.
    // Ждём факта через first { it } и дальше от списка не зависим — плашка своё событие вскоре удалит,
    // и производное состояние успело бы погаснуть прямо посреди задержки.
    LaunchedEffect(levelIndex) {
        val current = LEVELS.getOrNull(levelIndex) ?: return@LaunchedEffect
        world.setInventory(current.inventory)   // палитра уровня = его инвентарь
        snapshotFlow { world.moleculeEvents.any { it.known.id == current.goalMoleculeId } }.first { it }
        delay(1500)                  // дать увидеть саму молекулу, а не накрыть её окном мгновенно
        reward = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            // Именно preview: канва забирает фокус на любое наведение и съедает все KeyDown
            // (см. RightPanel), поэтому обычный onKeyEvent у предка до Esc бы не дожил.
            .onPreviewKeyEvent { e ->
                if (e.type == KeyDown && e.key == Key.Escape) { onExit(); true } else false
            }
    ) {
        DragDropContainer {
            Column(Modifier.fillMaxSize()) {
                RightPanel(
                    modifier = Modifier.weight(1f),   // канва берёт всю высоту, кроме палитры под ней
                    // Принимаем только то, что ещё осталось в инвентаре уровня.
                    accept = { data -> world.palette.any { it.item == data.item && it.count > 0 } },
                    onDrop = { data, localPos -> world.spawnFromPalette(data.item, Position(localPos.x, localPos.y)) },
                    hoverPos = hoverPos,
                    onHover = { hoverPos = it },
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    world = world,
                    entities = world.entities,
                    renderer = renderer,
                    time = time,
                    onSetEnergy = { id, energy -> world.setEntityEnergy(id, energy) },
                    onMoleculeAction = { id, selection -> world.requestMoleculeAction(id, selection) },
                )

                PaletteBar(palette = world.palette, level = level)
            }
        }

        // Окон одновременно не бывает: пока висит приветствие, собрать что-либо всё равно нельзя.
        if (welcome) {
            WelcomeCard { welcome = false }
        }
        // Награда за уровень: дальше игрок идёт кликом, холст чистится тогда же.
        else if (reward && level != null) {
            LevelReward(level) {
                world.requestClear()
                levelIndex++
                reward = false
            }
        }

        // Лестница кончилась: пятый уровень пока последний, дальше — только в меню.
        if (level == null) {
            MenuLayout(title = text(UiString.CHAPTER_DONE_TITLE), entries = listOf(MenuEntry(text(UiString.MENU_CLOSE), onExit))) {
                Text(
                    text(UiString.CHAPTER_DONE_TEXT),
                    fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 18.sp,
                    color = Color(0xFF9A9A9A),
                )
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}