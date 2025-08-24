package maratmingazovr.ai.carsonella

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.rememberTextMeasurer
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

        var hoverPos by remember { mutableStateOf<Offset?>(null) } // это координаты моего курсора на канве
        var hoveredId by remember { mutableStateOf<Long?>(null) }
        var selectedId by remember { mutableStateOf<Long?>(null) }


        val entitiesState = world.entities.map { atom -> val atomsState by atom.state().collectAsState(); atomsState } // каждый кадр мы обновляем состояние сущностей

        DragDropContainer {
            Row(Modifier.fillMaxSize()) {
                LeftPanel(palette = world.palette, selectedElementId = selectedId, entitiesState = entitiesState)

                RightPanel(
                    accept = { it.element in Element.entries },
                    onDrop = { data, localPos ->
                        when (data.element) {
                            Element.Photon   -> world.subAtomGenerator.createPhoton(Position(localPos.x, localPos.y), randomUnitVec2D())
                            Element.Electron -> world.subAtomGenerator.createElectron(Position(localPos.x, localPos.y), randomUnitVec2D())
                            Element.Proton   -> world.subAtomGenerator.createProton(Position(localPos.x, localPos.y), randomUnitVec2D())
                            Element.H        -> world.atomGenerator.createHydrogen(Position(localPos.x, localPos.y), randomUnitVec2D(), 0f)
                            else -> Unit
                        }
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
                    phase = phase
                )

            }
        }

    }
}

fun Position.toOffset(): Offset = Offset(x, y)















