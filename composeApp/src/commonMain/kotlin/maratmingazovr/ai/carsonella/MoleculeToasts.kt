package maratmingazovr.ai.carsonella

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.world.MoleculeEvent

private const val LIFETIME_MS = 4200 
private const val FADE_IN = 0.08f     // доля жизни на проявление (~340 мс)
private const val HOLD_UNTIL = 0.5f   // до этой доли держим полную непрозрачность, дальше гаснем
private const val LIFT_PX = 60f       // старт над молекулой: у атома радиус 25, иначе имя лежит на нём
private const val RISE_PX = 34f       // насколько плашка всплывает за свою жизнь

/**
 * Тихие имена образовавшихся молекул: всплывают над самой молекулой и тают. Безымянные (нет в
 * реестре) сюда не попадают вовсе — событие рождается только для известных (см. World.runReaction).
 */
@Composable
fun MoleculeToasts(events: MutableList<MoleculeEvent>, modifier: Modifier = Modifier) {
    Box(modifier) {
        // toList(): идём по снимку, потому что догоревшая плашка выкидывает себя из этого же списка
        events.toList().forEach { event ->
            key(event.id) { MoleculeToast(event) { events.remove(event) } }
        }
    }
}

@Composable
private fun MoleculeToast(event: MoleculeEvent, onFaded: () -> Unit) {
    val life = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        life.animateTo(1f, tween(LIFETIME_MS, easing = LinearEasing))
        onFaded()
    }
    val progress = life.value
    val alpha = when {
        progress < FADE_IN -> progress / FADE_IN
        progress < HOLD_UNTIL -> 1f
        else -> 1f - (progress - HOLD_UNTIL) / (1f - HOLD_UNTIL)
    }

    // Ширину знаем только после замера, поэтому центрируем по факту: до первого замера прячем.
    var width by remember { mutableStateOf(0) }
    Text(
        text = event.known.nameRu,
        color = Color(0xFF8A8A8A),
        fontFamily = menuFontFamily(),
        fontWeight = FontWeight.Light,
        fontSize = 15.sp,
        modifier = Modifier
            .onSizeChanged { width = it.width }
            .offset {
                IntOffset(
                    x = (event.position.x - width / 2f).toInt(),
                    y = (event.position.y - LIFT_PX - RISE_PX * progress).toInt(),
                )
            }
            .alpha(if (width == 0) 0f else alpha),
    )
}