package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.rememberTextMeasurer
import maratmingazovr.ai.carsonella.chemistry.DEFAULT_PHOTON_ENERGY_EV
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.world.World
import maratmingazovr.ai.carsonella.world.renderers.EntityRenderer
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
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


        // collectAsState вызывается в цикле, поэтому каждый элемент оборачиваем в key(id):
        // слот подписки привязан к сущности (по id), а не к позиции в списке. Без этого при
        // рождении/смерти частиц слоты «съезжают» и часть подписок теряется → сущность (в т.ч.
        // звезда) может перестать перерисовываться.
        val entitiesState = world.entities.map { atom ->
            key(atom.state().value.id) {
                val atomsState by atom.state().collectAsState(); atomsState
            }
        }

        DragDropContainer {
            Column(Modifier.fillMaxSize().background(Color.White)) {
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
                    entitiesState = entitiesState,
                    renderer = renderer,
                    time = time,
                    onSetEnergy = { id, energy -> world.setEntityEnergy(id, energy) },
                    onMoleculeAction = { id, selection -> world.requestMoleculeAction(id, selection) },
                )

            }
        }

    }
}

fun Position.toOffset(): Offset = Offset(x, y)
fun Offset.toPosition(): Position = Position(x, y)















