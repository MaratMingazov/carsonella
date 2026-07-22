package maratmingazovr.ai.carsonella

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.rememberTextMeasurer
import maratmingazovr.ai.carsonella.chemistry.DEFAULT_PHOTON_ENERGY_EV
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.world.World
import maratmingazovr.ai.carsonella.world.renderers.EntityRenderer
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.PI

@Composable
@Preview
fun App() {
    MaterialTheme {
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
        val phase2 by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 5000, easing = LinearEasing))
        )

        var hoverPos by remember { mutableStateOf<Offset?>(null) } // это координаты моего курсора на канве
        var hoveredId by remember { mutableStateOf<Long?>(null) }
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
            Row(Modifier.fillMaxSize()) {
                LeftPanel(
                    palette = world.palette,
                    selectedElementId = selectedId,
                    entitiesState = entitiesState,
                    onSave = { world.save() },
                    onLoad = { world.load() },
                    onSetEnergy = { id, energy -> world.setEntityEnergy(id, energy) },
                )

                RightPanel(
                    accept = { it.element in Element.entries },
                    // Фотон не должен рождаться с нулевой энергией (её не бывает у реального фотона) —
                    // даём дефолт H-α; остальным элементам 0f (основное состояние) корректно.
                    onDrop = { data, localPos ->
                        val energy = if (data.element == Element.PHOTON) DEFAULT_PHOTON_ENERGY_EV else 0f
                        world.entityGenerator.createEntity(element = data.element, Position(localPos.x, localPos.y), direction = randomDirection(world.random), velocity = 0f, energy = energy, environment = world.environment, electrons = data.element.details.p)
                    },
                    hoverPos = hoverPos,
                    onHover = { hoverPos = it },
                    hoveredId = hoveredId,
                    onSelectHoverId = { hoveredId = it },
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    world = world,
                    entitiesState = entitiesState,
                    renderer = renderer,
                    phase = phase,
                    phase2 = phase2,
                )

            }
        }

    }
}

fun Position.toOffset(): Offset = Offset(x, y)















