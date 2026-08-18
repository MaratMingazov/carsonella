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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Награда за уровень модальным окном: что получилось, эталонная картинка и факт из реестра. Дальше
 * игрок идёт сам — по кнопке, а не по таймеру.
 *
 * Формулировка нейтральная («получена молекула»), потому что рост эмёрджентный: молекула могла
 * собраться и без игрока, и врать об авторстве нельзя.
 */
@Composable
fun LevelReward(level: Level, onNext: () -> Unit) {
    val known = level.goal.known
    ModalCard(buttonLabel = text(UiString.REWARD_NEXT), onAction = onNext) {
        // У атомарной цели имени в реестре нет, поэтому подпись говорит «получен атом», а вместо имени
        // стоит сам кружок с символом — он одинаково читается на обоих языках.
        Text(
            text(if (known != null) UiString.REWARD_CAPTION else UiString.REWARD_CAPTION_ATOM),
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 13.sp,
            letterSpacing = 0.2.em, color = Color(0xFFB0B0B0),
        )
        if (known != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                known.name(LocalLang.current),
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 28.sp,
                color = Color.Black, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        GoalPreview(level.goal)
        // Факт целиком: в окне место есть. Уровень может сказать своё вместо описания из реестра —
        // на разрыве перекиси рассказ про гидроксил уже был бы повтором второго раунда.
        val fact = (level.rewardText ?: known?.description)?.of(LocalLang.current)
        if (!fact.isNullOrEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                fact,
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 16.sp,
                lineHeight = 23.sp, color = Color(0xFF8A8A8A), textAlign = TextAlign.Center,
            )
        }
    }
}