package maratmingazovr.ai.carsonella

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import maratmingazovr.ai.carsonella.world.PaletteItem

// Drag & Drop state + локаль для доступа из детей
data class DragData(val item: PaletteItem) // атом или готовая молекула из палитры

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
                // Призрак перетаскивания — то же, что в палитре/на канве (а не синий placeholder)
                Box(Modifier.offset { IntOffset(state.pos.x.toInt() - 25, state.pos.y.toInt() - 25) }) {
                    PaletteItemView(state.data!!.item)
                }
            }
        }
    }
}


@Composable
fun DragSource(item: PaletteItem, content: @Composable () -> Unit) {
    val dnd = LocalDragDrop.current
    var selfCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        Modifier
            .onGloballyPositioned { selfCoords = it }
            .pointerInput(item) {
                detectDragGestures(
                    onDragStart = { start ->
                        val inWindow = selfCoords?.localToWindow(start)
                        dnd.data = DragData(item)
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