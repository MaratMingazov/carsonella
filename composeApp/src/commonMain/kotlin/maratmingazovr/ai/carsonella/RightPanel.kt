package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.sub_atoms.SubAtomState
import maratmingazovr.ai.carsonella.world.World
import maratmingazovr.ai.carsonella.world.renderers.EntityRenderer





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
    modifier: Modifier = Modifier
) {
    DropTarget(accept = accept, onDrop = onDrop) { dropModifier ->
        Column(modifier = dropModifier.fillMaxSize()) {
            SceneCanvas(
                world = world,
                entitiesState = entitiesState,
                renderer = renderer,
                phase = phase,
                hoverPos = hoverPos,
                onHover = onHover,
                hoveredId = hoveredId,
                onSelectHoverId = onSelectHoverId,
                selectedId = selectedId,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
            ConsolePanel(
                logs = world.logs,
                onClear = { world.logs.clear() },
                height = 200.dp
            )
        }
    }
}

@Composable
private fun SceneCanvas(
    world: World,
    entitiesState: List<EntityState<*>>,
    renderer: EntityRenderer,
    phase: Float,
    hoverPos: Offset?,
    onHover: (Offset?) -> Unit,
    hoveredId: Long?,
    onSelectHoverId: (Long?) -> Unit,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(entitiesState) {
                // один цикл на все указательные события
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()               // получаем событие
                        val change = e.changes.firstOrNull() ?: continue

                        // 1) hover: всегда обновляем позицию курсора
                        onHover(change.position)

                        // 2) если это отпускание (клик) — выбираем под курсором
                        if (change.changedToUp()) {
                            val selectedId = hitTest(entitiesState, change.position)
                            onSelect(selectedId) // если оставляешь логику через hoveredId
                        }
                        // по желанию: change.consume() если хочешь прекратить дальнейшую доставку
                    }
                }
            }
    ) {
        // фон
        drawRect(Color.White, size = size)

        // хит-тест по протонам
        val mouse = hoverPos
        onSelectHoverId(null)

        if (mouse != null) {
            val hit = entitiesState
//                .asSequence()
//                .filterIsInstance<SubAtomState<*>>()
//                .filter { it.element == Element.Proton }
                .minByOrNull { s -> (s.position.toOffset() - mouse).getDistance() }

            val hitRadius = 30f
            if (hit != null) {
                val c = hit.position.toOffset()
                if ((c - mouse).getDistance() <= hitRadius) onSelectHoverId(hit.id)
            }
        }

        // выбранную частицу обводим
        selectedId?.let { id ->
            entitiesState.firstOrNull { it.id == id }?.let { s ->
                val c = s.position.toOffset()
                drawCircle(
                    color = Color.Blue.copy(alpha = 0.8f),
                    center = c,
                    radius = 18f,
                    style = Stroke(width = 3f)
                )
            }
        }

        // размеры мира
        world.environment.setWorldWidth(size.width)
        world.environment.setWorldHeight(size.height)

        // отрисовка сущностей
        entitiesState.forEach { renderer.render(this, it, phase) }

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
                    logs[i],
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



