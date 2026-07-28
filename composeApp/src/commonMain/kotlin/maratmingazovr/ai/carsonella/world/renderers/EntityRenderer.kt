package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.ElementType
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGeometry
import maratmingazovr.ai.carsonella.toOffset



// Единый тайминг анимаций от монотонного time (секунды). Меняешь скорость здесь, в одном месте.
internal const val ANIM_TWO_PI = 6.2831855f
internal const val VIB_HZ = 1f        // вибрация частиц: 1 цикл/сек (как прежний phase, 2π за 1с)
internal const val STAR_HZ = 0.2f     // пульс звезды: 1 цикл/5с (как прежний phase2, 2π за 5с)
internal const val SLOT_HZ = 1f / 15f  // вращение свободных слотов: 1 оборот/15с — медленно, плавно, без скачка


data class VibrationParams(
    val entityState: EntityState,
    val time: Float,
) {
    private val amp = 1f  // амплитуда в пикселях
    val idSeed = (entityState.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
    private val vibration = time * ANIM_TWO_PI * VIB_HZ * entityState.energy.coerceAtMost(10f)   // вибрация растёт с энергией, но не выше 10
    private val vibrationDx = amp * kotlin.math.cos(vibration + idSeed) // это вибрация каждого атома в зависимости от энергии
    private val vibrationDy = amp * kotlin.math.sin(vibration + idSeed) // это вибрация каждого атома в зависимости от энергии

    val positionOffset = Offset(vibrationDx, vibrationDy)
    val slotAngle = time * ANIM_TWO_PI * SLOT_HZ + idSeed   // + idSeed: у каждого атома свой стартовый угол
}

class EntityRenderer(
    private val textMeasurer: TextMeasurer,
) {

    fun render(
        drawScope: DrawScope,
        entityState: EntityState,
        time: Float,
        highlighted: Boolean = false,
    ) {
        val vibrationParams = VibrationParams(entityState, time) // параметры вибрации
        val species = entityState.species
        when (species) {
            is Species.Molecular -> drawMolecule(drawScope, entityState, highlighted, vibrationParams)
            is Species.Elemental -> drawElemental(drawScope, entityState, highlighted, vibrationParams)
        }
    }

    fun drawElemental(
        drawScope: DrawScope,
        entityState: EntityState,
        highlighted: Boolean, // пользователь может навести или выбрать элемент
        vibrationParams: VibrationParams,
    ) {

        val position = entityState.position.toOffset()  + vibrationParams.positionOffset
        val element = (entityState.species as Species.Elemental).element
        val radius = entityState.species.radius
        val fillColor = ElementColors.fill(entityState.species)
        val symbol = element.details.symbol.filter { it.isLetter() }

        with(drawScope) {
            when (element.details.type) {
                ElementType.SubAtom -> drawSubAtom(position, radius, fillColor, symbol, vibrationParams.slotAngle, highlighted = highlighted)
                ElementType.Atom -> drawAtom(position, radius, fillColor, symbol, element.valence(entityState.electrons), vibrationParams.slotAngle, highlighted = highlighted)
                ElementType.Star -> throw RuntimeException("STAR Rendering is not implemented yet")
            }
        }
    }

    // Молекула рисуется структурно: атомы-кружки по раскладке графа + связи-линии (кратность = число линий).
    fun drawMolecule(
        drawScope: DrawScope,
        entityState: EntityState,
        highlighted: Boolean,
        vibrationParams: VibrationParams,
    ) {
        val graph = (entityState.species as Species.Molecular).graph
        val centerPosition = entityState.position.toOffset() + vibrationParams.positionOffset

        // Геометрия молекулы живёт в shared (ею пользуется и физика); здесь только переводим
        // смещения атомов относительно центра в экранные координаты.
        val offsets = MoleculeGeometry.atomOffsets(graph)
        fun atomScreenPos(localId: Int) = centerPosition + offsets.getValue(localId).toOffset()

        with(drawScope) {

            graph.bonds.forEach { bond ->
                drawBond(atomScreenPos(bond.atom1), atomScreenPos(bond.atom2), bond.order)
            }

            graph.nodes.forEach { node ->
                val atomPosition = atomScreenPos(node.localId)
                val fill = ElementColors.fill(Species.Elemental(node.isotope))
                val symbol = node.isotope.details.symbol.filter { it.isLetter() }
                val nodeSlotAngle = vibrationParams.slotAngle + vibrationParams.idSeed + node.localId * 1.3f
                drawAtom(atomPosition, node.isotope.details.radius, fill, symbol, graph.freeSlots(node.localId), nodeSlotAngle, highlighted = highlighted)
            }
        }
    }



    // Связь: order параллельных линий (двойная/тройная — со сдвигом перпендикулярно связи).
    private fun DrawScope.drawBond(a: Offset, b: Offset, order: Int) {
        val dir = b - a
        val len = dir.getDistance()
        val perp = if (len > 1e-3f) Offset(-dir.y / len, dir.x / len) else Offset(0f, 1f)
        val firstShift = -(order - 1) / 2f
        val lineWidth =  2.5f // ширина линии
        val BOND_LINE_SPACING = 8f       // сдвиг параллельных линий для двойных/тройных связей
        val BOND_COLOR = Color(0xFF212121)     // МИНИМАЛИЗМ: почти чёрная связь (Sokobond, на белом)
        for (i in 0 until order) {
            val shift = perp * ((firstShift + i) * BOND_LINE_SPACING)
            drawLine(color = BOND_COLOR, start = a + shift, end = b + shift, strokeWidth = lineWidth)
        }
    }




    private fun DrawScope.drawAtom(
        center: Offset,
        radius: Float,
        fill: Color,
        symbol: String,
        freeSlots: Int, // валентность атома, сколько электронов на внешем слое
        slotAngle: Float, //
        highlighted: Boolean, // наведён/выбран → штриховая оконтовка
    ) {
        val outlineWidth =  2.5f // ширина обводки атома
        drawCircle(color = fill, center = center, radius = radius) // сам круг
        val outlineStyle = if (highlighted) // обводка выделенного атома рисуется штриховкой
            Stroke(
                width = outlineWidth,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(5f, 5f),   // штрих/пропуск в пикселях,
                    slotAngle * radius * 0.5f // скорость вращения пунктирный обводки атома при выделении
                )
            )
        else Stroke(width = outlineWidth)
        drawCircle(color = Color.Black, center = center, radius = radius, style = outlineStyle) // обводка
        drawCenteredSymbol(textMeasurer, center, symbol, onFillTextColor(fill), fontSizeSp = radius * 0.7f)

        if (freeSlots > 0) {
            val slotR = (radius * 0.18f).coerceIn(2.5f, 6f)   // размер маркера слота
            val base = slotAngle.toDouble()
            for (i in 0 until freeSlots) {
                val a = base + 2.0 * kotlin.math.PI * i / freeSlots
                val p = center + Offset(kotlin.math.cos(a).toFloat() * radius, kotlin.math.sin(a).toFloat() * radius)
                drawCircle(color = Color.White, center = p, radius = slotR)   // белый кружок слота
                drawCircle(color = Color.Black, center = p, radius = slotR, style = Stroke(outlineWidth)) // обводка
            }
        }
    }

    private fun DrawScope.drawSubAtom(
        center: Offset,
        radius: Float,
        fill: Color,
        symbol: String,
        slotAngle: Float, //
        highlighted: Boolean, // наведён/выбран → штриховая оконтовка
    ) {
        val outlineWidth =  2.5f // ширина обводки атома
        drawCircle(color = fill, center = center, radius = radius) // сам круг
        val outlineStyle = if (highlighted) // обводка выделенного атома рисуется штриховкой
            Stroke(
                width = outlineWidth,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(5f, 5f),   // штрих/пропуск в пикселях,
                    slotAngle * radius * 0.5f // скорость вращения пунктирный обводки атома при выделении
                )
            )
        else Stroke(width = outlineWidth)
        drawCircle(color = Color.Black, center = center, radius = radius, style = outlineStyle) // обводка
        drawCenteredSymbol(textMeasurer, center, symbol, onFillTextColor(fill), fontSizeSp = radius * 0.7f)
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

}