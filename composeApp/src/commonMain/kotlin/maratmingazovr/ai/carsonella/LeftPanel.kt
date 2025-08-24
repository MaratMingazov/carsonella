package maratmingazovr.ai.carsonella

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.sub_atoms.SubAtomState

@Composable
fun LeftPanel(
    palette: List<Element>,
    selectedElementId: Long?,
    entitiesState: List<EntityState<*>>,
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
        ElementsPalette(items = palette)
        Spacer(Modifier.weight(1f))
        SelectedEntityPanel(selectedElementId, entitiesState)
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
                    ) { Text(el.label) }
                }
            }
        }
    }
}

@Composable
fun SelectedEntityPanel(
    selectedElementId: Long?,
    entitiesState: List<EntityState<*>>,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().background(Color.White).padding(8.dp).border(1.dp, Color.LightGray)) {

        Text("Selected", style = MaterialTheme.typography.labelLarge, color = Color.Black)

        val selectedElementFlow = entitiesState.firstOrNull { it.id == selectedElementId }
        if (selectedElementFlow == null) {
            Spacer(Modifier.height(8.dp))
            Text("Ничего не выбрано", color = Color.Gray)
            return@Column
        }

        val selectedElement = selectedElementFlow

        Spacer(Modifier.height(8.dp))
        Text(selectedElement.toString(), style = MaterialTheme.typography.bodySmall)
        (selectedElement as? SubAtomState<*>)?.let {
//            Text("Element: ${it.element()}")
//            Text("Position: ${it.position()}")
        }
    }
}