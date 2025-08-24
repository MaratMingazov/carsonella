package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element

// Drag & Drop state + локаль для доступа из детей
data class DragData(val element: Element) // "Hydrogen" и т.п.

class DragDropState {
    var isDragging by mutableStateOf(false)
    var data: DragData? by mutableStateOf(null)
    var justReleased by mutableStateOf(false)
    var pos: Offset by mutableStateOf(Offset.Zero) // позиция курсора в окне
}

val LocalDragDrop = compositionLocalOf { DragDropState() }

@Composable
fun DragDropContainer(content: @Composable BoxScope.() -> Unit) {
    val state = remember { DragDropState() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalDragDrop provides state) {
            content()
            if (state.isDragging && state.data != null) {
                Box(
                    Modifier
                        .offset { IntOffset(state.pos.x.toInt()-70, state.pos.y.toInt()-70) }
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Blue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.data!!.element.symbol, color = Color.White)
                }
            }
        }
    }
}


@Composable
fun DragSource(element: Element, content: @Composable () -> Unit) {
    val dnd = LocalDragDrop.current
    var selfCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        Modifier
            .onGloballyPositioned { selfCoords = it }
            .pointerInput(element) {
                detectDragGestures(
                    onDragStart = { start ->
                        val inWindow = selfCoords?.localToWindow(start)
                        dnd.data = DragData(element)
                        dnd.pos = inWindow!!
                        dnd.isDragging = true
                    },
                    onDrag = { change, drag ->
                        dnd.pos += Offset(drag.x, drag.y)
                        change.consume()
                    },
                    onDragEnd = { dnd.justReleased = true },
                    onDragCancel = { dnd.justReleased = true }
                )
            }
    ) { content() }
}


@Composable
fun DropTarget(
    accept: (DragData) -> Boolean,
    onDrop: (DragData, Offset) -> Unit, // локальная позиция в целевом layout
    content: @Composable (Modifier) -> Unit
) {
    val dnd = LocalDragDrop.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val mod = Modifier.onGloballyPositioned { coords = it }
    content(mod)

    LaunchedEffect(dnd.justReleased) {
        if (dnd.isDragging && dnd.justReleased && dnd.data != null && coords != null) {
            val lc = coords!!
            val local = lc.windowToLocal(dnd.pos)
            if (accept(dnd.data!!) && local.x in 0f..lc.size.width.toFloat() && local.y in 0f..lc.size.height.toFloat()) {
                onDrop(dnd.data!!, local)
            }
            dnd.isDragging = false
            dnd.justReleased = false
            dnd.data = null
        }
    }
}