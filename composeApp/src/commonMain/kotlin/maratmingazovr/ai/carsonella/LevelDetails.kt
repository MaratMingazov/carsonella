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

/**
 * Уровень крупным планом: имя, картинка и один текст. Одно окно на два места — награда за пройденный
 * уровень и карточка, раскрытая с карты; отличаются они только текстом и надписью на кнопке.
 */
@Composable
fun LevelDetails(level: Level, body: TranslatedText, buttonLabel: String, onAction: () -> Unit) {
    ModalCard(buttonLabel = buttonLabel, onAction = onAction) {
        Spacer(Modifier.height(12.dp))
        Text(
            text(level.title),
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 28.sp,
            color = Color.Black, textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))
        LevelImage(level.image)

        // Текста может не быть вовсе: у выданных узлов рассказывать пока нечего, остаются имя и картинка.
        val bodyText = text(body)
        if (bodyText.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                bodyText,
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 16.sp,
                lineHeight = 23.sp, color = Color(0xFF8A8A8A), textAlign = TextAlign.Center,
            )
        }
    }
}