package maratmingazovr.ai.carsonella

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.world.PaletteItem

// Здесь мы рисуем картинку уровня
@Composable
fun LevelImage(image: List<PaletteItem>, scale: Float = 1f, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        image.forEach { PaletteItemView(it, scale) }
    }
}