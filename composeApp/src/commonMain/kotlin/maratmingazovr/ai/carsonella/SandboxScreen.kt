package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.graph.KnownMoleculeId
import maratmingazovr.ai.carsonella.world.PaletteItem
import maratmingazovr.ai.carsonella.world.UNLIMITED
import maratmingazovr.ai.carsonella.world.World

// Что лежит в палитре свободного мира. Порядок здесь — он же порядок слотов на экране.
private val SANDBOX_PALETTE: Map<PaletteItem, Int> = mapOf(
    PaletteItem.Atom(Element.HYDROGEN) to UNLIMITED,
    PaletteItem.Atom(Element.OXYGEN_16) to UNLIMITED,
    PaletteItem.Atom(Element.CARBON_12) to UNLIMITED,
)

/**
 * Свободный мир: та же сцена и те же правила, что в кампании, но без лестницы заданий — ни цели,
 * ни награды, ни расхода палитры. Размер мира не задаём: по умолчанию мир занимает весь холст.
 */
@Composable
fun SandboxScreen(onDiscover: (KnownMoleculeId) -> Unit, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val world = remember { World(scope).apply { start() } }

    LaunchedEffect(Unit) { world.setInventory(SANDBOX_PALETTE) }

    // Открытия засчитываем и здесь: собранная руками молекула — такое же открытие, как в задании.
    // discover() идемпотентен, поэтому повторы от одного события безвредны (см. GameScreen).
    LaunchedEffect(Unit) {
        snapshotFlow { world.moleculeEvents.map { it.knownMoleculeId }.toSet() }
            .collect { ids -> ids.forEach(onDiscover) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            // Именно preview: канва забирает фокус на любое наведение и съедает все KeyDown
            // (см. RightPanel), поэтому обычный onKeyEvent у предка до Esc бы не дожил.
            .onPreviewKeyEvent { e ->
                if (e.type == KeyDown && e.key == Key.Escape) { onExit(); true } else false
            },
    ) {
        WorldStage(world)
    }
}