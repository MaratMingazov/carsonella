package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.radius
import maratmingazovr.ai.carsonella.chemistry.displaySymbol
import maratmingazovr.ai.carsonella.chemistry.AtomState
import maratmingazovr.ai.carsonella.chemistry.MoleculeState
import maratmingazovr.ai.carsonella.chemistry.StarState
import maratmingazovr.ai.carsonella.chemistry.SubAtomState
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.toOffset

private const val ATOM_RADIUS = 8f             // радиус кружка атома внутри молекулы
private const val BOND_LINE_SPACING = 3f       // сдвиг параллельных линий для двойных/тройных связей
private const val LABEL_ABOVE = 26f            // на сколько поднять подпись над молекулой
private val BOND_COLOR = Color(0xFFB0BEC5)     // нейтральный цвет связи

class EntityRenderer(
    private val textMeasurer: TextMeasurer,
) {

    private val subAtomRenderer = SubAtomRenderer(textMeasurer)

    fun render(
        drawScope: DrawScope,
        entityState: EntityState<*>,
        phase: Float,
        phase2: Float,
        showLabel: Boolean = false,
    ) {
        when (entityState) {
            is SubAtomState -> subAtomRenderer.render(drawScope, entityState, phase, showLabel)
            is AtomState -> drawEntity(drawScope, entityState, phase, showLabel)
            is MoleculeState -> drawMolecule(drawScope, entityState, phase, showLabel)
            is StarState -> drawStar(drawScope, entityState, phase, phase2)

        }
    }

    fun drawEntity(
        drawScope: DrawScope,
        entityState: EntityState<*>,
        phase: Float,
        showLabel: Boolean,
    ) {
        // параметры вибрации
        val amp = 1f + entityState.energy                  // амплитуда в пикселях
        val idSeed = (entityState.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val dx = amp * kotlin.math.cos(phase + idSeed)
        val dy = amp * kotlin.math.sin(phase + idSeed)
        val position = entityState.position.toOffset()  + Offset(dx, dy)

        val color = ElementColors.glow(entityState.species)
        val baseRadius = entityState.species.radius()

        with(drawScope) {
            drawAtomOrb(position, baseRadius, color, entityState.energy)

            // символ — только при наведении/выборе, всплывает над частицей
            if (showLabel) {
                drawFloatingLabel(textMeasurer, position, baseRadius * 1.5f, entityState.species.displaySymbol(entityState.electrons))
            }
        }
    }

    fun drawStar(
        drawScope: DrawScope,
        entityState: EntityState<*>,
        phase: Float,
        phase2: Float,
    ) {
        // параметры вибрации
        val amp = 1f + entityState.energy                  // амплитуда в пикселях
        val idSeed = (entityState.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val dx = amp * kotlin.math.cos(phase2 + idSeed)
        val dy = amp * kotlin.math.sin(phase2 + idSeed)
        val position = entityState.position.toOffset()  + Offset(dx, dy)

        // пульсирующий радиус для границы
        val baseRadius = entityState.species.radius() + 5f   // базовый радиус круга
        val pulse = 10f * kotlin.math.abs(kotlin.math.sin(phase2 + idSeed)) // амплитуда пульса
        val pulsingRadius = baseRadius + pulse

        with(drawScope) {
            // тёплое светило: бело-жёлтое ядро → оранжевый → красный → прозрачность, с пульсом
            val gradientBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFFFFF6E0),
                    0.3f to Color(0xFFFFC04D),
                    0.6f to Color(0xFFFF6A3D),
                    1.0f to Color.Transparent,
                ),
                center = position,
                radius = pulsingRadius
            )

            // рисуем пульсирующий градиентный круг
            drawCircle(
                brush = gradientBrush,
                center = position,
                radius = pulsingRadius
            )
        }
    }

    // Молекула рисуется структурно: атомы-кружки по раскладке графа + связи-линии (кратность = число линий).
    fun drawMolecule(
        drawScope: DrawScope,
        entityState: MoleculeState,
        phase: Float,
        showLabel: Boolean,
    ) {
        val species = entityState.species
        if (species !is Species.Molecular) { drawEntity(drawScope, entityState, phase, showLabel); return }
        val graph = species.graph

        val amp = 1f + entityState.energy
        val idSeed = (entityState.id % 1000).toFloat()
        val jitter = Offset(amp * kotlin.math.cos(phase + idSeed), amp * kotlin.math.sin(phase + idSeed))
        val center = entityState.position.toOffset() + jitter

        val offsets = MoleculeLayout.layout(graph)

        with(drawScope) {
            // связи (под атомами)
            graph.bonds.forEach { bond ->
                drawBond(center + offsets.getValue(bond.atom1), center + offsets.getValue(bond.atom2), bond.order)
            }
            // атомы — цвет по элементу; тот же «светящийся шар», что у одиночного атома, только компактный
            graph.nodes.forEach { node ->
                val p = center + offsets.getValue(node.localId)
                val color = ElementColors.glow(Species.Elemental(node.isotope))
                drawAtomOrb(p, ATOM_RADIUS, color, entityState.energy)
            }
            // подпись-формула над молекулой при наведении/выборе
            if (showLabel) {
                drawFloatingLabel(textMeasurer, center, LABEL_ABOVE, species.displaySymbol(entityState.electrons))
            }
        }
    }

    // Атом как «светящийся шар»: широкое мягкое гало + маленькое плотное ядро. Единый стиль для
    // одиночного атома и для узла молекулы — отличается только базовый радиус (в молекуле он компактный,
    // ATOM_RADIUS, чтобы связи-линии не тонули в гало).
    private fun DrawScope.drawAtomOrb(center: Offset, baseRadius: Float, color: Color, energy: Float) {
        drawGlow(center, baseRadius * 1.5f, color, intensity = 1f + energy * 0.05f)          // мягкое свечение
        drawCircle(color = color.copy(alpha = 0.9f), center = center, radius = baseRadius * 0.35f)  // плотное ядро
    }

    // Связь: order параллельных линий (двойная/тройная — со сдвигом перпендикулярно связи).
    private fun DrawScope.drawBond(a: Offset, b: Offset, order: Int) {
        val dir = b - a
        val len = dir.getDistance()
        val perp = if (len > 1e-3f) Offset(-dir.y / len, dir.x / len) else Offset(0f, 1f)
        val firstShift = -(order - 1) / 2f
        for (i in 0 until order) {
            val shift = perp * ((firstShift + i) * BOND_LINE_SPACING)
            drawLine(color = BOND_COLOR, start = a + shift, end = b + shift, strokeWidth = 2f)
        }
    }
}