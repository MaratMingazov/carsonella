package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.rememberTextMeasurer
import maratmingazovr.ai.carsonella.chemistry.DEFAULT_PHOTON_ENERGY_EV
import maratmingazovr.ai.carsonella.chemistry.Element
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

    DragDropContainer {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                // Именно preview: канва забирает фокус на любое наведение и съедает все KeyDown
                // (см. RightPanel), поэтому обычный onKeyEvent у предка до Esc бы не дожил.
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyDown && e.key == Key.Escape) { onExit(); true } else false
                }
        ) {
            TopPalette(palette = world.palette)

            RightPanel(
                accept = { it.element in Element.entries },
                // Фотон не должен рождаться с нулевой энергией (её не бывает у реального фотона) —
                // даём дефолт H-α; остальным элементам 0f (основное состояние) корректно.
                onDrop = { data, localPos ->
                    val energy = if (data.element == Element.PHOTON) DEFAULT_PHOTON_ENERGY_EV else 0f
                    val electrons = if (data.element == Element.ELECTRON) 1 else data.element.details.p
                    world.entityGenerator.createEntity(element = data.element, Position(localPos.x, localPos.y), direction = randomDirection(world.random), velocity = 0f, energy = energy, environment = world.environment, electrons = electrons)
                },
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
        }
    }
}