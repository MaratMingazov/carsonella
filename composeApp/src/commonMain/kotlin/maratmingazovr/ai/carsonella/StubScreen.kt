package maratmingazovr.ai.carsonella

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Текст на второстепенных экранах — тот же тонкий шрифт, что и в меню, но обычного размера.
private val BodyStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Light, lineHeight = 30.sp, color = Color.DarkGray)

private const val ABOUT_TEXT =
    "Молекулярный конструктор: кладёшь атомы, они связываются сами — по настоящей валентности. " +
    "Кратность связи, кольца и разрыв фотоном — за игроком.\n\n" +
    "Собранное вещество попадает в журнал открытий. Комбинации здесь не выдуманы: " +
    "что собирается, то собирается и в природе."

@Composable
fun AboutScreen(onBack: () -> Unit) {
    MenuLayout(title = "о проекте", entries = listOf(MenuEntry("назад", onBack)), onBack = onBack) {
        Text(ABOUT_TEXT, style = BodyStyle, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // Настраивать пока нечего — экран честно пустой, строки появятся вместе с настройками.
    MenuLayout(title = "настройки", entries = listOf(MenuEntry("назад", onBack)), onBack = onBack) {
        Text("пока пусто", style = BodyStyle, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
    }
}