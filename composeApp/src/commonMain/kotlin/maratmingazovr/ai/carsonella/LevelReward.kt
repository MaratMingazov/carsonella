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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry

/**
 * Награда за уровень модальным окном: что получилось, эталонная картинка и факт из реестра. Дальше
 * игрок идёт сам — по кнопке, а не по таймеру.
 *
 * Формулировка нейтральная («получена молекула»), потому что рост эмёрджентный: молекула могла
 * собраться и без игрока, и врать об авторстве нельзя.
 */
@Composable
fun LevelReward(level: Level, onNext: () -> Unit) {
    val goal = level.goal
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
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Spacebar)) {
                    onNext(); true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(min = 300.dp, max = 480.dp)
                .graphicsLayer {
                    alpha = appear
                    scaleX = 0.96f + 0.04f * appear
                    scaleY = 0.96f + 0.04f * appear
                }
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(14.dp))
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "получена молекула",
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 13.sp,
                letterSpacing = 0.2.em, color = Color(0xFFB0B0B0),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                goal.nameRu,
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 28.sp,
                color = Color.Black, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            val picture = MoleculeRegistry.picture(level.goalNameEn)
            if (picture != null) MoleculePicturePreview(picture)
            else Text(
                goal.structuralFormula.ifEmpty { goal.nameRu },
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 34.sp,
                letterSpacing = 0.08.em, color = Color(0xFF45BDB5),
            )
            // Факт целиком: в окне место есть, в отличие от рейла.
            if (goal.description.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    goal.description,
                    fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 14.sp,
                    lineHeight = 20.sp, color = Color(0xFF8A8A8A), textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            CardButton("дальше", onNext)
        }
    }
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
            .background(if (hovered) Color(0xFF35A69F) else Color(0xFF45BDB5))
            .padding(horizontal = 20.dp, vertical = 4.dp),
    )
}