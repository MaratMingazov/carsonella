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
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry

/**
 * Награда за уровень модальным окном: что получилось, эталонная картинка и факт из реестра. Дальше
 * игрок идёт сам — по кнопке, а не по таймеру.
 *
 * Формулировка нейтральная («получена молекула»), потому что рост эмёрджентный: молекула могла
 * собраться и без игрока, и врать об авторстве нельзя.
 */
@Composable
fun LevelReward(level: Level, onNext: () -> Unit) {
    val goal = level.goal
    ModalCard(buttonLabel = text(UiString.REWARD_NEXT), onAction = onNext) {
        Text(
            text(UiString.REWARD_CAPTION),
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 13.sp,
            letterSpacing = 0.2.em, color = Color(0xFFB0B0B0),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            goal.name(LocalLang.current),
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 28.sp,
            color = Color.Black, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        val picture = MoleculeRegistry.picture(level.goalId)
        if (picture != null) MoleculePicturePreview(picture)
        else Text(
            goal.structuralFormula.ifEmpty { goal.name(LocalLang.current) },
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 34.sp,
            letterSpacing = 0.08.em, color = Color(0xFF45BDB5),
        )
        // Факт целиком: в окне место есть. Уровень может сказать своё вместо описания из реестра —
        // на разрыве перекиси рассказ про гидроксил уже был бы повтором второго раунда.
        val fact = level.rewardText ?: goal.description
        if (fact.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                fact,
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 16.sp,
                lineHeight = 23.sp, color = Color(0xFF8A8A8A), textAlign = TextAlign.Center,
            )
        }
    }
}