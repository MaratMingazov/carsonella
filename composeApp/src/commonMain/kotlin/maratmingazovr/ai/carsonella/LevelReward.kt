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


// Когда игрок успешно проходит уровень, то появляется модальное окно с поздравлением
@Composable
fun LevelReward(level: Level, onNext: () -> Unit) {
    ModalCard(buttonLabel = text(UiString.REWARD_NEXT), onAction = onNext) {
        Spacer(Modifier.height(12.dp))
        Text(
            text(level.levelGoal.goalElementTitle),
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 28.sp,
            color = Color.Black, textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))
        LevelImage(level.image)
        val levelRewardText = text(level.reward.text)
        if (levelRewardText.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                levelRewardText,
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 16.sp,
                lineHeight = 23.sp, color = Color(0xFF8A8A8A), textAlign = TextAlign.Center,
            )
        }
    }
}