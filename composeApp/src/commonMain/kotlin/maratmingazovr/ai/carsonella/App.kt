package maratmingazovr.ai.carsonella

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.sub_atoms.SubAtomState
import maratmingazovr.ai.carsonella.world.World
import maratmingazovr.ai.carsonella.world.renderers.EntityRenderer
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.PI

@Composable
@Preview
fun App() {
    MaterialTheme {
        // Если хоть одно значение меняется, это сигнал к тому, чтобы перерисовать canvas.
        val scope = rememberCoroutineScope()
        val textMeasurer = rememberTextMeasurer()
        val renderer = remember { EntityRenderer(textMeasurer) }
        val world = remember { World(scope).apply { start() } }


        // это нужно, чтобы анимировать дрожание протона
        val phase by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing))
        )

        var hoverPos by remember { mutableStateOf<Offset?>(null) } // это координаты моего курсора на канве
        var hoveredId by remember { mutableStateOf<Long?>(null) }
        var selectedId by remember { mutableStateOf<Long?>(null) }


        val entitiesState = world.entities.map { atom -> val atomsState by atom.state().collectAsState(); atomsState } // каждый кадр мы обновляем состояние сущностей


        DragDropContainer {
            Row(Modifier.fillMaxSize()) {


                // Левая панель
                Column(
                    Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                        .drawBehind {
                            val strokeWidth = 1.dp.toPx()
                            val x = size.width - strokeWidth / 2
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = strokeWidth
                            )
                        }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text("Elements", style = MaterialTheme.typography.titleMedium)
                    DragSource(element = Element.Photon) {
                        Box(
                            Modifier
                                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(Element.Photon.label) }
                    }
                    DragSource(element = Element.Electron) {
                        Box(
                            Modifier
                                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(Element.Electron.label) }
                    }
                    DragSource(element = Element.Proton) {
                        Box(
                            Modifier
                                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(Element.Proton.label) }
                    }

                    Spacer(Modifier.weight(1f))  // <- толкает всё ниже

                    // панель выбранной сущности
                    SelectedEntityPanel(selectedId, entitiesState)
                }

                // Правая область — Canvas как цель DnD
                DropTarget(
                    accept = { it.element in Element.entries },
                    onDrop = { it, localPos ->
                        when (it.element) {
                            Element.Photon ->  world.subAtomGenerator.createPhoton(Position(localPos.x, localPos.y), randomUnitVec2D())
                            Element.Electron ->  world.subAtomGenerator.createElectron(Position(localPos.x, localPos.y), randomUnitVec2D())
                            Element.Proton ->  world.subAtomGenerator.createProton(Position(localPos.x, localPos.y), randomUnitVec2D())
                            else -> {}
                        }

                    }
                ) { dropModifier ->

                    Column(modifier = dropModifier.fillMaxSize()) {
                        Canvas(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput("hover") {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: continue
                                        hoverPos = change.position // позиция курсора в координатах Canvas
                                    }
                                }
                            }
                            .pointerInput("click") {
                                detectTapGestures(
                                    onTap = {
                                        selectedId = hoveredId
                                        println("selectedId: $selectedId")
                                    }
                                )
                            }
                        ) {
                            drawRect(
                                color = Color.White,
                                size = size // размер Canvas
                            )


                            val mouse = hoverPos
                            hoveredId = null

                            if (mouse != null) {
                                val hit = entitiesState
                                    .asSequence()
                                    .filterIsInstance<SubAtomState<*>>()                 // если протоны — субатомы
                                    .filter { it.element() == Element.Proton }           // только протоны
                                    .minByOrNull { s ->
                                        val c = s.position().toOffset()
                                        (c - mouse).getDistance()
                                    }

                                val hitRadius = 50f // порог попадания (чуть больше визуального радиуса)
                                if (hit != null) {
                                    val c = hit.position().toOffset()
                                    if ((c - mouse).getDistance() <= hitRadius) {
                                        hoveredId = hit.id() // это мы нашли частицу, которую будем выбирать
                                    }
                                }
                            }

                            // выбранную частицу обводим
                            selectedId?.let { id ->
                                entitiesState.firstOrNull { it.id() == id }?.let { s ->
                                    val c = s.position().toOffset()
                                    drawCircle(
                                        color = Color.Blue.copy(alpha = 0.8f),
                                        center = c,
                                        radius = 18f,
                                        style = Stroke(width = 3f)
                                    )
                                }
                            }

                            // мир
                            world.environment.setWorldWidth(size.width)
                            world.environment.setWorldHeight(size.height)
                            entitiesState.forEach { renderer.render(this, it, phase) }



                            // подсвечиваем частицу
                            hoveredId?.let { id ->
                                entitiesState.firstOrNull { it.id() == id }?.let { s ->
                                    val center = s.position().toOffset()
                                    drawCircle(
                                        color = Color.Cyan,
                                        center = center,
                                        radius = 15f,
                                        style = Stroke(width = 1f)
                                    )

                                }
                            }
                        }

                        ConsolePanel(
                            logs = world.logs,
                            onClear = { world.logs.clear() },
                            height = 200.dp
                        )
                    }


                }
            }
        }


    }
}

fun Position.toOffset(): Offset = Offset(x, y)





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
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

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
            Text(
                "Console",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Black
            )
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

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs.size) { i ->
                Text(
                    logs[i],
                    color = Color(0xFF212121),
                    style = MaterialTheme.typography.bodySmall,
                    // моноширинный стиль (если не используешь свою типографику)
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}



@Composable
fun SelectedEntityPanel(
    selectedId: Long?,
    entitiesState: List<EntityState<*>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp)
            .border(1.dp, Color.LightGray)
    ) {

        Text("Selected", style = MaterialTheme.typography.labelLarge, color = Color.Black)

        val st = remember(selectedId, entitiesState) {
            entitiesState.firstOrNull { it.id() == selectedId }
        }

        if (selectedId == null || st == null) {
            Spacer(Modifier.height(8.dp))
            Text("Ничего не выбрано", color = Color.Gray)
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        Text(st.toString(), style = MaterialTheme.typography.bodySmall)
        (st as? SubAtomState<*>)?.let {
//            Text("Element: ${it.element()}")
//            Text("Position: ${it.position()}")
        }
        // для MoleculeState<*> можно добавить состав, связи и т.п.
    }
}















