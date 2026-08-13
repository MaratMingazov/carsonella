package maratmingazovr.ai.carsonella

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Текст на второстепенных экранах — тот же Jost, что и в меню, но обычного размера.
@Composable
private fun bodyStyle(): TextStyle {
    val family = menuFontFamily()
    return remember(family) { TextStyle(fontFamily = family, fontSize = 18.sp, fontWeight = FontWeight.Light, lineHeight = 30.sp, color = Color.DarkGray) }
}

private const val ABOUT_TEXT =
    "soon..."

@Composable
fun AboutScreen(onBack: () -> Unit) {
    MenuLayout(title = "about", entries = listOf(MenuEntry("back", onBack)), onBack = onBack) {
        Text(ABOUT_TEXT, style = bodyStyle(), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // Настраивать пока нечего — экран честно пустой, строки появятся вместе с настройками.
    MenuLayout(title = "settings", entries = listOf(MenuEntry("back", onBack)), onBack = onBack) {
        Text("soon", style = bodyStyle(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
    }
}