package maratmingazovr.ai.carsonella

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Эталон цели: молекулу рисуем курируемой раскладкой из реестра, атом — тем же кружком, что в палитре и
 * на канве. Один вид на три места: задание в пузыре, награда, карточка на карте.
 */
@Composable
fun GoalPreview(goal: Goal, scale: Float = 1f, modifier: Modifier = Modifier) {
    when (goal) {
        is Goal.Atom -> PaletteAtom(goal.element, modifier, goal.electrons)
        is Goal.Molecule -> {
            val knownMolecule = goal.id.knownMoleculeDetails
            if (knownMolecule.offsets.isNotEmpty()) KnownMoleculePreview(knownMolecule, scale = scale, modifier = modifier)
            else Text(
                knownMolecule.structuralFormula.ifEmpty { "?" },
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 16.sp,
                color = Color(0xFF45BDB5), textAlign = TextAlign.Center, modifier = modifier,
            )
        }
    }
}