package maratmingazovr.ai.carsonella

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import maratmingazovr.ai.carsonella.chemistry.graph.KnownMoleculeId
private const val WELCOME_TEXT =
    "Это исследовательская игра, в которой мы будем из простых атомов строить сложные молекулы и структуры. " +
    "Мы начнём с нескольких обучающих раундов, чтобы понять механику игры. " +
    "В каждом раунде нам нужно будет из атомов получить простую молекулу."

/** Приветствие на входе в игру: что это вообще за игра и что сейчас будет происходить. */
@Composable
fun WelcomeCard(onStart: () -> Unit) {
    ModalCard(buttonLabel = text(UiString.WELCOME_START), onAction = onStart) {
        Text(
            text(UiString.WELCOME_TITLE),
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 26.sp,
            color = Color.Black, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            WELCOME_TEXT,
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 17.sp,
            lineHeight = 24.sp, color = Color(0xFF8A8A8A), textAlign = TextAlign.Center,
        )
        // Вода как образец: её же игрок соберёт на третьем уровне.
        KnownMoleculeId.WATER.knownMoleculeDetails.let { water ->
            Spacer(Modifier.height(24.dp))
            KnownMoleculePreview(water)
        }
    }
}