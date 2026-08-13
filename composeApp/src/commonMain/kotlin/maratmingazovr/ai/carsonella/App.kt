package maratmingazovr.ai.carsonella

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview

// Экраны приложения. Дальше сюда добавятся уровни и журнал открытий; «продолжить» появится,
// когда будет что продолжать.
private sealed interface Screen {
    data object Menu : Screen
    data object Game : Screen
    data object Settings : Screen
    data object About : Screen
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        var screen: Screen by remember { mutableStateOf(Screen.Menu) }

        when (screen) {
            Screen.Menu -> MenuScreen(
                onStart = { screen = Screen.Game },
                onSettings = { screen = Screen.Settings },
                onAbout = { screen = Screen.About },
            )
            // Мир создаётся внутри GameScreen и умирает вместе с ним → возврат в меню
            // останавливает симуляцию, повторный «старт» даёт чистый холст.
            Screen.Game -> GameScreen(onExit = { screen = Screen.Menu })
            Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Menu })
            Screen.About -> AboutScreen(onBack = { screen = Screen.Menu })
        }
    }
}

fun Position.toOffset(): Offset = Offset(x, y)
fun Offset.toPosition(): Position = Position(x, y)
fun Offset.toVec2D(): Vec2D = Vec2D(x, y) // сдвиг курсора (positionChange) — это вектор, не точка