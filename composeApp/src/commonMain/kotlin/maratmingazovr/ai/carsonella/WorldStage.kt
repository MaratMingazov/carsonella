package maratmingazovr.ai.carsonella

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.rememberTextMeasurer
import maratmingazovr.ai.carsonella.world.World
import maratmingazovr.ai.carsonella.world.renderers.EntityRenderer

/**
 * Сцена живого мира: холст с правой панелью и палитра под ним. Одна на два экрана — кампанию
 * ([GameScreen]) и свободный мир ([SandboxScreen]). Всё, чем они отличаются, приходит снаружи:
 * палитру наполняет вызывающий, а [overlay] кладётся поверх холста (задание, подсказки).
 */
@Composable
fun WorldStage(world: World, overlay: @Composable BoxScope.() -> Unit = {}) {
    val textMeasurer = rememberTextMeasurer()
    val renderer = remember { EntityRenderer(textMeasurer) }

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

    DragDropContainer {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                RightPanel(
                    modifier = Modifier.fillMaxSize(),   // канва берёт всю высоту, кроме палитры под ней
                    // Принимаем только то, что ещё осталось в палитре.
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
                overlay()
            }

            PaletteBar(palette = world.palette)
        }
    }
}