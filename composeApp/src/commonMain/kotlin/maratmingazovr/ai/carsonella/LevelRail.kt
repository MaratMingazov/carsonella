package maratmingazovr.ai.carsonella

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeRegistry

private val RAIL_WIDTH = 168.dp
private val DIVIDER = Color(0xFFEDEDED)
private val DONE_GRAY = Color(0xFFBDBDBD)
private val COMING_GRAY = Color(0xFFD8D8D8)
private val CARD_BORDER = Color(0xFFE0E0E0)
private val ACCENT = Color(0xFF45BDB5)
private val CHECK_SIZE = 12.dp

/**
 * Рейл уровней слева: пройденные, текущий с эталонной картинкой и следующие «на подходе». Цель видна
 * всё время — первые уровни это туториал, прятать её в модалку незачем. Дальше отсюда вырастет карта
 * открытий, поэтому колонка, а не полоса: 44 уровня в горизонталь не лягут.
 */
@Composable
fun LevelRail(levels: List<Level>, currentIndex: Int, modifier: Modifier = Modifier) {
    // Окно вокруг текущего: игроку нужны соседи, а не весь список.
    val from = (currentIndex - 2).coerceAtLeast(0)
    val to = (currentIndex + 2).coerceAtMost(levels.lastIndex)

    Column(
        modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(Color.White)
            .drawBehind { drawLine(DIVIDER, Offset(size.width, 0f), Offset(size.width, size.height)) }
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (i in from..to) when {
            i < currentIndex -> DoneLevel(levels[i])
            i == currentIndex -> CurrentLevel(levels[i])
            else -> ComingLevel(levels[i])
        }
    }
}

@Composable
private fun DoneLevel(level: Level) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckMark(DONE_GRAY)
        Spacer(Modifier.width(8.dp))
        Text(
            level.goal.nameRu,
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 15.sp,
            color = DONE_GRAY,
        )
    }
}

@Composable
private fun CurrentLevel(level: Level) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, CARD_BORDER, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "level ${level.number}",
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 12.sp,
            letterSpacing = 0.2.em, color = Color(0xFFB0B0B0),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            level.task,
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 15.sp,
            color = Color.Black, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        // Картинка эталона, если раскладка нарисована; иначе — текстовая структурная формула.
        val picture = MoleculeRegistry.picture(level.goalNameEn)
        if (picture != null) MoleculePicturePreview(picture, scale = 0.6f)
        else Text(
            level.goal.structuralFormula.ifEmpty { level.goal.nameRu },
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 22.sp,
            letterSpacing = 0.08.em, color = ACCENT,
        )
    }
}

@Composable
private fun ComingLevel(level: Level) {
    // Отступ слева равен галочке с зазором — имена пройденных и будущих стоят в одну линию.
    Text(
        level.goal.nameRu,
        fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 15.sp,
        color = COMING_GRAY,
        modifier = Modifier.padding(start = CHECK_SIZE + 8.dp),
    )
}

// Галочку рисуем, а не берём из шрифта: в Jost символа ✓ может не быть.
@Composable
private fun CheckMark(color: Color, boxSize: Dp = CHECK_SIZE) {
    Canvas(Modifier.size(boxSize)) {
        val w = size.width
        val h = size.height
        val corner = Offset(w * 0.38f, h * 0.82f)
        drawLine(color, Offset(w * 0.08f, h * 0.52f), corner, strokeWidth = 1.6f, cap = StrokeCap.Round)
        drawLine(color, corner, Offset(w * 0.92f, h * 0.18f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    }
}