package maratmingazovr.ai.carsonella

import androidx.compose.runtime.Composable

// Когда игрок успешно проходит уровень, то появляется модальное окно с поздравлением
@Composable
fun LevelReward(level: Level, onNext: () -> Unit) {
    LevelDetails(level, level.reward.text, text(UiString.REWARD_NEXT), onNext)
}