package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.ElementType
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.toOffset

private const val ATOM_RADIUS = 20f             // радиус кружка атома внутри молекулы
private const val BOND_LINE_SPACING = 3f       // сдвиг параллельных линий для двойных/тройных связей
private const val LABEL_ABOVE = 26f            // на сколько поднять подпись над молекулой
// private val BOND_COLOR = Color(0xFFB0BEC5)  // прежний светлый цвет связи (для тёмного фона)
private val BOND_COLOR = Color(0xFF212121)     // МИНИМАЛИЗМ: почти чёрная связь (Sokobond, на белом)
private const val CORE_RADIUS_MAX = 1f         // ядро — крошечный маркер центра (реальное ядро ≈ точка, ~1/100000 атома; не в масштабе)

// Масштаб отрисовки: пикселей на 1 пм. Пока зафиксирован на «пикометровом» (самом мелком)
// масштабе — 1px = 1пм, атомы в натуральную величину. Позже станет параметром (ползунок zoom).
private const val PX_PER_PM = 1f

// Единый тайминг анимаций от монотонного time (секунды). Частоты в Гц (циклов/сек); для слотов —
// оборотов/сек. internal — чтобы SubAtomRenderer брал ту же вибрацию. Меняешь скорость здесь, в одном месте.
internal const val ANIM_TWO_PI = 6.2831855f
internal const val VIB_HZ = 1f        // вибрация частиц: 1 цикл/сек (как прежний phase, 2π за 1с)
internal const val STAR_HZ = 0.2f     // пульс звезды: 1 цикл/5с (как прежний phase2, 2π за 5с)
internal const val SLOT_HZ = 1f / 15f  // вращение свободных слотов: 1 оборот/15с — медленно, плавно, без скачка

class EntityRenderer(
    private val textMeasurer: TextMeasurer,
) {

    private val subAtomRenderer = SubAtomRenderer(textMeasurer)

    fun render(
        drawScope: DrawScope,
        entityState: EntityState,
        time: Float,
        showLabel: Boolean = false,
    ) {
        val species = entityState.species
        if (species is Species.Molecular) { drawMolecule(drawScope, entityState, time, showLabel); return }
        when ((species as Species.Elemental).element.details.type) {
            ElementType.SubAtom -> subAtomRenderer.render(drawScope, entityState, time, showLabel)
            ElementType.Atom -> drawEntity(drawScope, entityState, time, showLabel)
            ElementType.Star -> drawStar(drawScope, entityState, time)
        }
    }

    fun drawEntity(
        drawScope: DrawScope,
        entityState: EntityState,
        time: Float,
        showLabel: Boolean,
    ) {
        // параметры вибрации
        val amp = 1f + entityState.energy                  // амплитуда в пикселях
        val idSeed = (entityState.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val vib = time * ANIM_TWO_PI * VIB_HZ
        val dx = amp * kotlin.math.cos(vib + idSeed)
        val dy = amp * kotlin.math.sin(vib + idSeed)
        val position = entityState.position.toOffset()  + Offset(dx, dy)

        val element = (entityState.species as Species.Elemental).element
        val baseRadius = entityState.species.radius
        val fill = ElementColors.fill(entityState.species)
        val symbol = element.details.symbol.filter { it.isLetter() }
        val slotAngle = time * ANIM_TWO_PI * SLOT_HZ + idSeed   // + idSeed: у каждого атома свой стартовый угол

        with(drawScope) {
            drawFlatAtom(position, baseRadius, fill, symbol, element.valence(), slotAngle)
        }
    }

    fun drawStar(
        drawScope: DrawScope,
        entityState: EntityState,
        time: Float,
    ) {
        // параметры вибрации/пульса — от общего time на частоте STAR_HZ
        val amp = 1f + entityState.energy                  // амплитуда в пикселях
        val idSeed = (entityState.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val ph = time * ANIM_TWO_PI * STAR_HZ
        val dx = amp * kotlin.math.cos(ph + idSeed)
        val dy = amp * kotlin.math.sin(ph + idSeed)
        val position = entityState.position.toOffset()  + Offset(dx, dy)

        // пульсирующий радиус для границы
        val baseRadius = entityState.radius + 5f   // базовый радиус круга
        val pulse = 10f * kotlin.math.abs(kotlin.math.sin(ph + idSeed)) // амплитуда пульса
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
        entityState: EntityState,
        time: Float,
        showLabel: Boolean,
    ) {
        val species = entityState.species
        if (species !is Species.Molecular) { drawEntity(drawScope, entityState, time, showLabel); return }
        val graph = species.graph

        val amp = 1f + entityState.energy
        val idSeed = (entityState.id % 1000).toFloat()
        val vib = time * ANIM_TWO_PI * VIB_HZ
        val jitter = Offset(amp * kotlin.math.cos(vib + idSeed), amp * kotlin.math.sin(vib + idSeed))
        val center = entityState.position.toOffset() + jitter
        val slotAngle = time * ANIM_TWO_PI * SLOT_HZ

        val offsets = MoleculeLayout.layout(graph)

        with(drawScope) {

            graph.bonds.forEach { bond ->
                drawBond(center + offsets.getValue(bond.atom1), center + offsets.getValue(bond.atom2), bond.order)
            }

            graph.nodes.forEach { node ->
                val p = center + offsets.getValue(node.localId)
                val fill = ElementColors.fill(Species.Elemental(node.isotope))
                val symbol = node.isotope.details.symbol.filter { it.isLetter() }
                // + idSeed (на молекулу) + localId (на узел) → у каждого узла свой стартовый угол
                val nodeSlotAngle = slotAngle + idSeed + node.localId * 1.3f
                drawFlatAtom(p, ATOM_RADIUS, fill, symbol, graph.freeSlots(node.localId), nodeSlotAngle)
                /* --- прежний «светящийся шар» узла: ---
                val color = ElementColors.glow(Species.Elemental(node.isotope))
                drawAtomOrb(p, ATOM_RADIUS, color, entityState.energy) */
            }
            // подпись-формула над молекулой при наведении/выборе
            if (showLabel) {
                drawFloatingLabel(textMeasurer, center, LABEL_ABOVE, entityState.displaySymbol)
            }
        }
    }




    private fun DrawScope.drawFlatAtom(center: Offset, radius: Float, fill: Color, symbol: String, freeSlots: Int, slotAngle: Float) {
        drawCircle(color = fill, center = center, radius = radius) // сам круг
        drawCircle(color = Color.Black, center = center, radius = radius, style = Stroke(width = 1.5f)) // оконтовка
        drawCenteredSymbol(textMeasurer, center, symbol, onFillTextColor(fill), fontSizeSp = radius * 0.7f)

        if (freeSlots > 0) {
            val slotR = (radius * 0.18f).coerceIn(2.5f, 6f)   // размер маркера слота
            val base = slotAngle.toDouble()
            for (i in 0 until freeSlots) {
                val a = base + 2.0 * kotlin.math.PI * i / freeSlots
                val p = center + Offset(kotlin.math.cos(a).toFloat() * radius, kotlin.math.sin(a).toFloat() * radius)
                drawCircle(color = Color.White, center = p, radius = slotR)   // белый кружок слота
                drawCircle(color = Color.Black, center = p, radius = slotR, style = Stroke(width = 1f)) // обводка
            }
        }
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