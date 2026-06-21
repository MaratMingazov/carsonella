package maratmingazovr.ai.carsonella

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.world.World
import maratmingazovr.ai.carsonella.world.renderers.EntityRenderer
import kotlin.math.roundToInt


@Composable
fun RightPanel(
    accept: (DragData) -> Boolean,
    onDrop: (DragData, Offset) -> Unit,
    hoverPos: Offset?,
    onHover: (Offset?) -> Unit,
    hoveredId: Long?,
    onSelectHoverId: (Long?) -> Unit,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    world: World,
    entitiesState: List<EntityState<*>>,
    renderer: EntityRenderer,
    phase: Float,
    phase2: Float,
    modifier: Modifier = Modifier
) {

    // Для обработки клавиш клавиатуры
    // 1) локально храним зажатые клавиши
    var keys by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<Key>()) }
    // --- фокус для приёма клавиатуры ---
    val focusRequester = remember { FocusRequester() }
    val onSelectUpToDate = rememberUpdatedState(onSelect) // чтобы замыкание не устаревало

    DropTarget(accept = accept, onDrop = onDrop) { dropModifier ->
        Column(modifier = dropModifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF03040A))
                    .padding(4.dp)
                    .focusRequester(focusRequester) // для обработки клавиш клавиутуры
                    .focusable() // важно!
                    .onKeyEvent { e ->
                        when (e.type) {
                            KeyDown -> {
                                keys = keys + e.key
                                // ► действие по пробелу
                                if (e.key == Key.Spacebar) {
                                    val id = selectedId
                                    val mouse = hoverPos
                                    if (id != null && mouse != null) {
                                        val selected = entitiesState.firstOrNull { it.id == id }
                                        if (selected != null) {
                                            val from = selected.position.toOffset()
                                            val dir = direction(from, mouse)   // единичный вектор к мыши
                                            // Из выбранного элемента стреляем фотоном
                                            world.entityGenerator.createEntity(element = selected.element, Position(selected.position.x, selected.position.y),  direction = dir, velocity = 10f, energy = selected.energy, environment = world.environment)
                                        }
                                    }
                                }
                                true
                            }
                            KeyUp -> { keys = keys - e.key; true }
                            else -> false
                        }
                    }
            ) {
                SceneCanvas(
                    world = world,
                    entitiesState = entitiesState,
                    renderer = renderer,
                    phase = phase,
                    phase2 = phase2,
                    hoverPos = hoverPos,
                    onHover = { pos -> onHover(pos); focusRequester.requestFocus() },
                    hoveredId = hoveredId,
                    onSelectHoverId = onSelectHoverId,
                    selectedId = selectedId,
                    onSelect = { onSelect(it); focusRequester.requestFocus() },
                    modifier = Modifier.matchParentSize()
                )
//                TemperatureBadge(world.updateTemperatureGame())
            }
            ConsolePanel(
                logs = world.logs,
                onClear = { world.logs.clear() },
                height = 200.dp
            )
        }
    }


    LaunchedEffect(Unit) {
        kotlinx.coroutines.yield()
        focusRequester.requestFocus()
    }
    ControlSelectedWithKeysLoop(selectedId = selectedId, keys = keys) { id, force ->
        world.applyForceToEntity(id, force)
    }
//    HandleKeyboardForSelection(selectedId = selectedId, keys = keys) { key, id ->
//        when (key) {
//            Key.W -> world.applyForceToEntity(id, Vec2D(0f, -1f))
//            Key.DirectionUp -> world.applyForceToEntity(id, Vec2D(0f, -1f))
//            Key.S -> world.applyForceToEntity(id, Vec2D(0f, 1f))
//            Key.DirectionDown -> world.applyForceToEntity(id, Vec2D(0f, 1f))
//            Key.A -> world.applyForceToEntity(id, Vec2D(-1f, 0f))
//            Key.DirectionLeft -> world.applyForceToEntity(id, Vec2D(-1f, 0f))
//            Key.D -> world.applyForceToEntity(id, Vec2D(1f, 0f))
//            Key.DirectionRight -> world.applyForceToEntity(id, Vec2D(1f, 0f))
//        }
//    }
}

@Composable
private fun TemperatureBadge(temperatureKelvin: Float, modifier: Modifier = Modifier) {
    val tC = ((temperatureKelvin - 273.15f) * 10).roundToInt() / 10f
    val text = "T: $tC °C"

    Row(
        modifier
            .background(
                Color.LightGray.copy(alpha = 0.2f), // 0.0f = полностью прозрачный, 1.0f = непрозрачный
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF212121),
            textAlign = TextAlign.Start
        )
    }
}

// Тусклая фоновая звезда: позиция нормирована (0..1), масштабируется под размер канвы.
private data class StarDot(val nx: Float, val ny: Float, val radius: Float, val alpha: Float)

@Composable
private fun SceneCanvas(
    world: World,
    entitiesState: List<EntityState<*>>,
    renderer: EntityRenderer,
    phase: Float,
    phase2: Float,
    hoverPos: Offset?,
    onHover: (Offset?) -> Unit,
    hoveredId: Long?,
    onSelectHoverId: (Long?) -> Unit,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {

    // pointerInput ниже с ключом Unit (чтобы жест перетаскивания не прерывался каждый кадр),
    // поэтому замыкание должно читать «свежие» значения через rememberUpdatedState.
    val entitiesLatest = rememberUpdatedState(entitiesState)
    val onHoverLatest = rememberUpdatedState(onHover)
    val onSelectLatest = rememberUpdatedState(onSelect)

    // Тусклые звёзды фона: позиции нормированы (0..1), генерируем один раз сидированным RNG.
    val stars = remember {
        val rnd = kotlin.random.Random(42)
        List(140) {
            StarDot(
                nx = rnd.nextFloat(),
                ny = rnd.nextFloat(),
                radius = 0.4f + rnd.nextFloat() * 1.1f,
                alpha = 0.15f + rnd.nextFloat() * 0.5f,
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var candidateId: Long? = null   // частица под курсором в момент нажатия
                    var holding = false             // подняли ли её реально (после начала движения)
                    while (true) {
                        val e = awaitPointerEvent()               // получаем событие
                        val change = e.changes.firstOrNull() ?: continue

                        // hover: всегда обновляем позицию курсора
                        onHoverLatest.value(change.position)

                        // нажали — запоминаем, что под курсором (но ещё не поднимаем)
                        if (change.changedToDown()) {
                            candidateId = hitTest(entitiesLatest.value, change.position)
                            holding = false
                        }

                        // потащили: на первом смещении «поднимаем» частицу, дальше она следует за курсором
                        if (candidateId != null && change.pressed && change.positionChanged()) {
                            if (!holding) { world.pickUpEntity(candidateId!!); holding = true }
                            world.moveEntityTo(candidateId!!, Position(change.position.x, change.position.y))
                            change.consume()
                        }

                        // отпустили
                        if (change.changedToUp()) {
                            if (holding) {
                                world.dropHeldEntity()            // положили — снова взаимодействует
                            } else {
                                onSelectLatest.value(hitTest(entitiesLatest.value, change.position)) // клик без движения = выбор
                            }
                            candidateId = null
                            holding = false
                        }
                    }
                }
            }
    ) {
        // тёмный космический фон: радиальный градиент + редкие тусклые звёзды
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0B1026), Color(0xFF03040A)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = kotlin.math.max(size.width, size.height) * 0.75f,
            ),
            size = size,
        )
        stars.forEach { s ->
            drawCircle(
                color = Color.White.copy(alpha = s.alpha),
                radius = s.radius,
                center = Offset(s.nx * size.width, s.ny * size.height),
            )
        }

        // граница корневого environment
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            center = world.environment.getEnvCenter().toOffset(),
            radius = world.environment.getEnvRadius(),
            style = Stroke(width = 1f)
        )

        // хит-тест по протонам
        val mouse = hoverPos
        onSelectHoverId(null)

        if (mouse != null) {
            // ищем самый ближайший объект
            val hit = entitiesState.minByOrNull { s -> (s.position.toOffset() - mouse).getDistance() }

            val hitRadius = 30f
            if (hit != null) {
                val c = hit.position.toOffset()
                if ((c - mouse).getDistance() <= hitRadius) onSelectHoverId(hit.id)
            }
        }

        // выбранную частицу обводим
        selectedId?.let { id ->
            entitiesState.firstOrNull { it.id == id }?.let { selectedEntity ->
                val selectedEntityPosition = selectedEntity.position.toOffset()
                drawCircle(
                    color = Color(0xFF80E8FF).copy(alpha = 0.9f),
                    center = selectedEntityPosition,
                    radius = 18f,
                    style = Stroke(width = 3f)
                )
            }
        }

//        // размеры мира
//        world.environment.setWorldWidth(size.width)
//        world.environment.setWorldHeight(size.height)

        // отрисовка сущностей; символ показываем только у наведённой/выбранной
        entitiesState.forEach { renderer.render(this, it, phase, phase2, showLabel = it.id == hoveredId || it.id == selectedId) }

        // подсветка ховера
        hoveredId?.let { id ->
            entitiesState.firstOrNull { it.id == id }?.let { s ->
                val center = s.position.toOffset()
                drawCircle(
                    color = Color.Cyan,
                    center = center,
                    radius = 15f,
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}

private fun direction(from: Offset, to: Offset): Vec2D {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = Vec2D(dx, dy).length()
    return if (len > 1e-6f) Vec2D(dx / len, dy / len) else Vec2D(0f, 0f)
}

@Composable
private fun ControlSelectedWithKeysLoop(selectedId: Long?, keys: Set<Key>, onImpulse: (Long, Vec2D) -> Unit) {
    LaunchedEffect(selectedId, keys) {
        if (selectedId == null) return@LaunchedEffect
        while (keys.isNotEmpty()) {
            val dir = dirFromKeys(keys)
            onImpulse(selectedId, dir.div(10f))
            kotlinx.coroutines.delay(16)
        }
    }
}

// Направление из WASD/стрелок
private fun dirFromKeys(keys: Set<Key>): Vec2D {
    var dx = 0f; var dy = 0f
    if (Key.W in keys || Key.DirectionUp in keys)    dy -= 1f
    if (Key.S in keys || Key.DirectionDown in keys)  dy += 1f
    if (Key.A in keys || Key.DirectionLeft in keys)  dx -= 1f
    if (Key.D in keys || Key.DirectionRight in keys) dx += 1f
    return Vec2D(dx, dy)
}

@Composable
fun ConsolePanel(
    logs: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    showClear: Boolean = true,
    onClear: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()

    // автопрокрутка к последней строке
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex) }

    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.White)
            .padding(6.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val x = size.width - strokeWidth / 2
                drawLine(
                    color = Color.LightGray,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = strokeWidth
                )
            }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Console", style = MaterialTheme.typography.labelLarge, color = Color.Black)
            if (showClear && onClear != null) {
                Text(
                    "Clear",
                    color = Color(0xFF1565C0),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onClear() }
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(logs.size) { i ->
                Text(
                    logs[i].toAsciiSymbols(),
                    color = Color(0xFF212121),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}


private fun hitTest(
    entities: List<EntityState<*>>,
    at: Offset,
    radius: Float = 50f
): Long? {
    val hit = entities.asSequence()
        .minByOrNull { s -> (s.position.toOffset() - at).getDistance() }

    return hit?.let { element ->
        val c = element.position.toOffset()
        if ((c - at).getDistance() <= radius) element.id else null
    }
}



