package maratmingazovr.ai.carsonella

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Оболочка модального окна: дымка поверх холста, карточка по центру, плавное проявление. Содержимое
 * передаётся снаружи (приветствие, награда, карточка с карты).
 *
 * Действий бывает два, и оба необязательны: кнопка внизу ([buttonLabel] + [onAction], она же Enter/Space)
 * — это «дальше», а крестик в углу ([onClose]) — «просто закрыть». Окно-рассказ обходится крестиком,
 * окно-шаг — кнопкой, карточка доступного задания показывает оба.
 */
@Composable
fun ModalCard(
    buttonLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    // Проявление: дымка набегает вместе с карточкой, карточка ещё и чуть подрастает — окно приезжает
    // спокойно, а не щёлкает поверх холста.
    var shown by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(900, easing = LinearOutSlowInEasing)) // с какой скоростью всплывает модалка
    LaunchedEffect(Unit) { shown = true; focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.82f * appear))   // дымка, а не затемнение: экран остаётся светлым
            // Мышь дальше не идёт: иначе сквозь дымку можно тащить атомы из палитры и кликать по холсту.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) { awaitPointerEvent().changes.forEach { it.consume() } }
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (onAction != null && e.type == KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Spacebar)) {
                    onAction(); true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        // Крестик лежит в том же боксе, что и колонка, а не в ней: иначе он занимал бы строку
        // и сдвигал заголовок вниз. Проявляется вместе с карточкой — graphicsLayer общий.
        Box(
            Modifier
                .widthIn(min = 300.dp, max = 480.dp)
                .graphicsLayer {
                    alpha = appear
                    scaleX = 0.96f + 0.04f * appear
                    scaleY = 0.96f + 0.04f * appear
                }
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(14.dp)),
        ) {
            Column(
                Modifier.padding(horizontal = 40.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
                if (buttonLabel != null && onAction != null) {
                    Spacer(Modifier.height(24.dp))
                    CardButton(buttonLabel, onAction)
                }
            }
            if (onClose != null) CloseCross(onClose, Modifier.align(Alignment.TopEnd))
        }
    }
}

// Крестик закрытия: серый, на наведении бирюзовый — тот же отклик, что у ссылок меню.
@Composable
private fun CloseCross(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        "×",
        fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 24.sp,
        color = if (hovered) Color(0xFF45BDB5) else Color(0xFFBDBDBD),
        modifier = modifier
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

// Кнопка карточки: та же бирюза, что выделяет пункт меню, но здесь она нужна всегда — это единственное
// действие в окне, поэтому подсвечена без наведения, а наведение лишь усиливает.
@Composable
private fun CardButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        label,
        fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 22.sp,
        color = Color.Black,
        modifier = Modifier
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .background(if (hovered) Color(0xFF35A69F) else Color(0xFF45BDB5), RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp, vertical = 4.dp),
    )
}