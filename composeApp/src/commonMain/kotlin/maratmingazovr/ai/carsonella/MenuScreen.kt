package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Стиль меню снят со скриншота Sokobond (SOKOBOND.jpg в корне): белый лист, огромный светло-серый
// титул, чёрные строчные пункты, выделение — оранжевый прямоугольник БЕЗ скругления.
private val TITLE_GRAY = Color(0xFFC8C8C8)
private val SELECTION_ORANGE = Color(0xFFE8A33D)
private val DISABLED_GRAY = Color(0xFFBDBDBD)

// Шрифт пока системный: Light + разрядка дают близкий к референсу тонкий геометрический вид.
// Настоящий геометрический .ttf (Jost/Comfortaa, оба с кириллицей) — отдельный шаг, одна строка здесь.
private val TitleStyle = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.Light, letterSpacing = 0.15.em, color = TITLE_GRAY)
private val ItemStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 0.05.em)

/** Пункт меню: подпись + действие. Выключенный виден, но серый и не выбирается ни мышью, ни стрелками. */
data class MenuEntry(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
fun MenuScreen(
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val entries = listOf(
        MenuEntry("старт", onStart),
        MenuEntry("настройки", onSettings),
        MenuEntry("о проекте", onAbout),
    )
    MenuLayout(title = "Carsonella", entries = entries)
}

/**
 * Общая раскладка «титул + вертикальный список пунктов» — её же переиспользуют экраны-заглушки.
 * Выделение ведут и клавиши (↑/↓/Enter), и мышь (наведение переносит выделение, клик активирует).
 */
@Composable
fun MenuLayout(
    title: String,
    entries: List<MenuEntry>,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    // Выделен первый доступный пункт: у выключенных выделения не бывает.
    var selected by remember(entries.size) { mutableStateOf(entries.indexOfFirst { it.enabled }.coerceAtLeast(0)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Шаг стрелкой: идём в сторону step, перепрыгивая выключенные пункты; упёрлись в край — остаёмся.
    fun move(step: Int) {
        var i = selected + step
        while (i in entries.indices) {
            if (entries[i].enabled) { selected = i; return }
            i += step
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { move(-1); true }
                    Key.DirectionDown -> { move(+1); true }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        entries.getOrNull(selected)?.takeIf { it.enabled }?.onClick?.invoke(); true
                    }
                    Key.Escape -> { onBack?.invoke(); onBack != null }
                    else -> false
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxSize().padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title.uppercase(), style = TitleStyle, textAlign = TextAlign.Center)
            Spacer(Modifier.height(60.dp))
            content()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    MenuItem(
                        entry = entry,
                        selected = index == selected,
                        onHover = { if (entry.enabled) selected = index },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuItem(entry: MenuEntry, selected: Boolean, onHover: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }

    val background = if (selected && entry.enabled) SELECTION_ORANGE else Color.Transparent
    val textColor = if (entry.enabled) Color.Black else DISABLED_GRAY

    Text(
        text = entry.label,
        style = ItemStyle.copy(color = textColor),
        modifier = Modifier
            .hoverable(interaction, enabled = entry.enabled)
            .clickable(enabled = entry.enabled, interactionSource = interaction, indication = null) { entry.onClick() }
            .background(background)
            .padding(horizontal = 16.dp, vertical = 2.dp),
    )
}