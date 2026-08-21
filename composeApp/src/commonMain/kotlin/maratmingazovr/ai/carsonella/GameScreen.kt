package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.graph.KnownMoleculeId
import maratmingazovr.ai.carsonella.world.World
import maratmingazovr.ai.carsonella.world.renderers.EntityRenderer

/**
 * Игровой экран: живой мир, палитра сверху, канва с правой панелью.
 *
 */
@Composable
fun GameScreen(
    completed: Set<LevelId>,
    onDiscover: (KnownMoleculeId) -> Unit,
    onComplete: (LevelId) -> Unit,
    onExit: () -> Unit,
) {
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

    var reward by remember { mutableStateOf(false) }
    // Приветствие — только в самом начале игры, пока не пройдено ни одного уровня; холст под ним пуст.
    var welcome by remember { mutableStateOf(completed.isEmpty()) }
    val level = availableLevels(completed).firstOrNull()
    // Пузырь с заданием игрок может свернуть, прочитав. Ключ по уровню: новое задание — новый текст,
    // его надо показать, иначе свернувший однажды больше никогда его не увидит.
    var taskOpen by remember(level?.id) { mutableStateOf(true) }

    // Цель засчитывается по тому же событию, что рисует всплывающее имя: известная молекула родилась.
    // Ждём факта через first { it } и дальше от списка не зависим — плашка своё событие вскоре удалит,
    // и производное состояние успело бы погаснуть прямо посреди задержки.
    LaunchedEffect(level?.id) {
        val current = level ?: return@LaunchedEffect
        world.setInventory(current.inventory)   // палитра уровня = его инвентарь
        world.setWorldArea(current.worldArea)   // и его же размер мира
        world.setInitialEntities(current.initialEntities)   // и стартовая расстановка
        // У молекулы событие есть, у атома нет — его ищем среди живых частиц. Заряд в условии значим:
        // протон это тот же HYDROGEN, и без проверки электронов задание закрылось бы сразу.
        when (val goal = current.levelGoal) {
            is LevelGoal.CreateMolecule -> snapshotFlow { world.moleculeEvents.any { it.knownMoleculeId == goal.knownMoleculeId } }
            is LevelGoal.CreateAtom -> snapshotFlow {
                world.entities.count { it is Atom && it.alive && it.element == goal.element && it.electrons == goal.electrons } >= goal.count
            }
        }.first { it }
        delay(1500)                  // дать увидеть саму молекулу, а не накрыть её окном мгновенно
        reward = true
    }

    // Журнал открытий питается тем же событием, что и всплывающие имена. Одно и то же приходит по
    // многу раз, и это нормально: discover() идемпотентен, дедупликация тут не нужна.
    LaunchedEffect(Unit) {
        snapshotFlow { world.moleculeEvents.map { it.knownMoleculeId }.toSet() }
            .collect { ids -> ids.forEach(onDiscover) }
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
                Box(Modifier.weight(1f)) {
                    RightPanel(
                        modifier = Modifier.fillMaxSize(),   // канва берёт всю высоту, кроме палитры под ней
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

                    // Задание накладкой поверх холста, а не строкой в колонке: строкой оно отъедало
                    // у мира высоту. Фон и рамка мышь не ловят — клики и перетаскивание идут сквозь.
                    // Сдвиг от центра, а не от края экрана: пузырь должен стоять рядом с палитрой,
                    // чтобы стрелка приходила в неё, — иначе на широком окне он уезжает в даль.
                    if (level != null) TaskBubble(
                        level,
                        open = taskOpen,
                        onToggle = { taskOpen = !taskOpen },
                        modifier = Modifier.align(Alignment.BottomCenter).offset(x = (-160).dp),
                    )
                }

                PaletteBar(palette = world.palette)
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
                onComplete(level.id)
                reward = false
            }
        }

        // Лестница кончилась: все цели пройдены, дальше — только в меню.
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