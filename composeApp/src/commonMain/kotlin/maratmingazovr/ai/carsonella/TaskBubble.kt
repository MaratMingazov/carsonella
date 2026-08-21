package maratmingazovr.ai.carsonella

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BUBBLE_WIDTH = 240.dp
private val BUBBLE_BORDER = Color(0xFFE0E0E0)
private val BUBBLE_TEXT = Color(0xFF5A5A5A)
private val ARROW_COLOR = Color(0xFF45BDB5)
private const val ARROW_WIDTH = 2.2f

/**
 * Задание всплывашкой рядом с палитрой: подсказка стоит там, где руки игрока — атомы он берёт отсюда.
 * Внутри тот же эталон молекулы, что был в карточке уровня; стрелка нарисована дугой и уходит в палитру.
 */
@Composable
fun TaskBubble(level: Level, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    // Стрелка сбоку, а не под пузырём: пузырь стоит далеко слева, и дуга идёт от его правого бока в палитру.
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (!open) {
            TaskBadge(onToggle)
            return@Row
        }
        Box {
            Column(
                Modifier
                    .widthIn(max = BUBBLE_WIDTH)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, BUBBLE_BORDER, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text(level.description),
                    fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 17.sp,
                    lineHeight = 24.sp, color = BUBBLE_TEXT, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                LevelImage(level.image, scale = 0.6f)
            }
            // Крестик живёт в отступе карточки, куда текст не заходит, поэтому наложения нет.
            // Кликабелен только он: повесить clickable на весь пузырь — значит перекрыть холст под ним.
            Text(
                "×",
                Modifier.align(Alignment.TopEnd).clickable(onClick = onToggle).padding(horizontal = 5.dp, vertical = 1.dp),
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 16.sp, color = BUBBLE_BORDER,
            )
        }
        HintArrow()
    }
}

// Свёрнутое задание. Не «закрыто»: в описании лежит подсказка, и вернуть её нужно уметь.
@Composable
private fun TaskBadge(onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, BUBBLE_BORDER, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "?",
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 15.sp, color = BUBBLE_TEXT,
        )
    }
}

// Дуга «бери отсюда»: выходит из правого бока пузыря и уходит вниз-вправо, кончиком в палитру.
@Composable
private fun HintArrow() {
    Canvas(Modifier.size(width = 56.dp, height = 46.dp)) {
        val w = size.width
        val h = size.height
        val end = Offset(w * 0.86f, h * 0.94f)
        val curve = Path().apply {
            moveTo(0f, h * 0.28f)
            quadraticTo(w * 0.60f, h * 0.30f, end.x, end.y)
        }
        drawPath(curve, ARROW_COLOR, style = Stroke(width = ARROW_WIDTH, cap = StrokeCap.Round))
        drawLine(ARROW_COLOR, end, Offset(end.x - w * 0.22f, end.y - h * 0.10f), ARROW_WIDTH, StrokeCap.Round)
        drawLine(ARROW_COLOR, end, Offset(end.x - w * 0.04f, end.y - h * 0.28f), ARROW_WIDTH, StrokeCap.Round)
    }
}