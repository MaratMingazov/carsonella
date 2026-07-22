package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.Species
import kotlin.math.round

@Composable
fun LeftPanel(
    palette: List<Element>,
    selectedElementId: Long?,
    entitiesState: List<EntityState<*>>,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onSetEnergy: (Long, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(Color.White)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val x = size.width - strokeWidth / 2
                drawLine(Color.LightGray, Offset(x, 0f), Offset(x, size.height), strokeWidth)
            }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
            Button(onClick = onLoad, modifier = Modifier.weight(1f)) { Text("Load") }
        }
        ElementsPalette(items = palette)
        Spacer(Modifier.weight(1f))
        SelectedEntityPanel(selectedElementId, entitiesState, onSetEnergy)
    }
}

@Composable
fun ElementsPalette(items: List<Element>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Elements", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            items(items, key = { it.ordinal }) { el ->
                DragSource(element = el) {
                    Box(
                        Modifier
                            .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text(el.label(el.details.p)) }
                }
            }
        }
    }
}

@Composable
fun SelectedEntityPanel(
    selectedElementId: Long?,
    entitiesState: List<EntityState<*>>,
    onSetEnergy: (Long, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().background(Color.White).padding(8.dp).border(1.dp, Color.LightGray)) {

        Text("Info", style = MaterialTheme.typography.labelLarge, color = Color.Black)

        val selectedElement = entitiesState.firstOrNull { it.id == selectedElementId }
        if (selectedElement == null) {
            Spacer(Modifier.height(8.dp))
            Text("", color = Color.Gray)
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        Text(selectedElement.toString(), style = MaterialTheme.typography.bodySmall)

        // Редакторы параметров по типу элемента (пока только фотон — «шов» под будущие параметры).
        val species = selectedElement.species
        if (species is Species.Elemental && species.element == Element.PHOTON) {
            Spacer(Modifier.height(8.dp))
            EnergyEditor(
                energyEv = selectedElement.energy,
                onApply = { energy -> onSetEnergy(selectedElement.id, energy) },
            )
        }
    }
}

// Редактор энергии фотона (эВ). Тип света и длину волны показывает toString выше; здесь — только
// ввод. Энергию ≤ 0 не применяем: у реального фотона энергии-нуля не бывает (см. инвариант в SubAtom).
@Composable
private fun EnergyEditor(
    energyEv: Float,
    onApply: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Локальный буфер ввода. Синхронизируем с внешней энергией (реакция и т.п.), но не затираем
    // ввод, пока поле в фокусе.
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(energyText(energyEv)) }
    LaunchedEffect(energyEv) { if (!focused) text = energyText(energyEv) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text("Energy, eV") },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        Button(
            onClick = { text.trim().toFloatOrNull()?.takeIf { it > 0f }?.let(onApply) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply") }
    }
}

// Текст для поля ввода: округляем до 2 знаков; при E = 0 поле пустое, чтобы задать значение с нуля.
private fun energyText(energyEv: Float): String =
    if (energyEv > 0f) (round(energyEv * 100) / 100).toString() else ""