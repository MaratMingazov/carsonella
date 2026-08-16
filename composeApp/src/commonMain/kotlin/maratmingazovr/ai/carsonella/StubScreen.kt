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

@Composable
fun AboutScreen(onBack: () -> Unit) {
    MenuLayout(title = text(UiString.ABOUT_TITLE), entries = listOf(MenuEntry(text(UiString.MENU_BACK), onBack)), onBack = onBack) {
        Text(text(UiString.ABOUT_TEXT), style = bodyStyle(), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
        Spacer(Modifier.height(48.dp))
    }
}

/**
 * Выбор языка. Языки подписаны на самих себе ([Lang.ownName]) — так их найдёт и тот, кто текущего языка
 * не знает; текущий выключен, потому что выбирать его нечего.
 */
@Composable
fun LanguageScreen(current: Lang, onPick: (Lang) -> Unit, onBack: () -> Unit) {
    val entries = Lang.entries.map { lang ->
        MenuEntry(lang.ownName, onClick = { onPick(lang) }, enabled = lang != current)
    } + MenuEntry(text(UiString.MENU_BACK), onBack)
    MenuLayout(title = text(UiString.LANGUAGE_TITLE), entries = entries, onBack = onBack)
}