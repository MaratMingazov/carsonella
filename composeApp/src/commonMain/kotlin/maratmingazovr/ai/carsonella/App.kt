package maratmingazovr.ai.carsonella

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview

private sealed interface Screen {
    data object Menu : Screen
    data object Game : Screen
    data object Map : Screen
    data object Language : Screen
    data object About : Screen
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        var screen: Screen by remember { mutableStateOf(Screen.Menu) }
        var player: PlayerState by remember { mutableStateOf(PlayerState()) }

        CompositionLocalProvider(LocalLang provides player.settings.lang) {
            when (screen) {
                Screen.Menu -> MenuScreen(
                    onStart = { screen = Screen.Game },
                    onMap = { screen = Screen.Map },
                    onLanguage = { screen = Screen.Language },
                    onAbout = { screen = Screen.About },
                )
                Screen.Map -> DiscoveryMapScreen(discovered = player.progress.discovered, onBack = { screen = Screen.Menu })
                Screen.Game -> GameScreen(
                    onDiscover = { player = player.copy(progress = player.progress.discover(it)) },
                    onExit = { screen = Screen.Menu },
                )
                Screen.Language -> LanguageScreen(
                    current = player.settings.lang,
                    onPick = { player = player.copy(settings = player.settings.copy(lang = it)) },
                    onBack = { screen = Screen.Menu },
                )
                Screen.About -> AboutScreen(onBack = { screen = Screen.Menu })
            }
        }
    }
}

fun Position.toOffset(): Offset = Offset(x, y)
fun Offset.toPosition(): Position = Position(x, y)
fun Offset.toVec2D(): Vec2D = Vec2D(x, y) // сдвиг курсора (positionChange) — это вектор, не точка