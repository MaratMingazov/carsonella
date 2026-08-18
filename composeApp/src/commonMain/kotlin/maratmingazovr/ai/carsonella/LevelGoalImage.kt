package maratmingazovr.ai.carsonella

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// В результате каждого раунда игрок должен получить атом/молекулу. Тут мы рисуем его, чтобы игрок визуально видел что нужно получить
@Composable
fun LevelGoalImage(levelGoal: LevelGoal, scale: Float = 1f, modifier: Modifier = Modifier) {
    when (levelGoal) {
        is LevelGoal.CreateAtom -> PaletteAtom(levelGoal.element, modifier, levelGoal.electrons) // атом рисуем так же как и в палитре
        is LevelGoal.CreateMolecule -> KnownMoleculePreview(levelGoal.id.details, scale = scale, modifier = modifier)
    }
}