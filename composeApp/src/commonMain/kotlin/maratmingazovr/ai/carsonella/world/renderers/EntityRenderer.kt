package maratmingazovr.ai.carsonella.world.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MolecularAtomFull
import maratmingazovr.ai.carsonella.chemistry.MolecularBond
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.toOffset


// Единый тайминг анимаций от монотонного time (секунды). Меняешь скорость здесь, в одном месте.
internal const val ANIM_TWO_PI = 6.2831855f
internal const val VIB_HZ = 1f        // вибрация частиц: 1 цикл/сек (как прежний phase, 2π за 1с)
internal const val STAR_HZ = 0.2f     // пульс звезды: 1 цикл/5с (как прежний phase2, 2π за 5с)
internal const val SLOT_HZ = 1f / 15f  // вращение свободных слотов: 1 оборот/15с — медленно, плавно, без скачка


data class VibrationParams(
    val id: Long, // чтобы у каждого элемента было свое колебание
    val energy: Float, // больше энергии -> больше колебание
    val time: Float, // единый счетчик времени
) {
    private val amp = 1f  // амплитуда в пикселях
    val idSeed = (id % 1000).toFloat()   // стаб. сдвиг фазы на объект
    private val vibration = time * ANIM_TWO_PI * VIB_HZ * energy.coerceAtMost(10f).coerceAtLeast(0.5f)   // вибрация растёт с энергией, но не выше 10
    private val vibrationDx = amp * kotlin.math.cos(vibration + idSeed) // это вибрация каждого атома в зависимости от энергии
    private val vibrationDy = amp * kotlin.math.sin(vibration + idSeed) // это вибрация каждого атома в зависимости от энергии

    val positionOffset = Offset(vibrationDx, vibrationDy)
    val slotAngle = time * ANIM_TWO_PI * SLOT_HZ + idSeed   // + idSeed: у каждого атома свой стартовый угол
}


data class Highlight(
    val entity: Boolean = false,       // значит пользователь навел мышкой на элемент и нужно его подсветить
    val bond: MolecularBond? = null,   // пользователь навел мышкой на конкретную связь молекулы,
) {
    companion object { val NONE = Highlight() }
}

class EntityRenderer(
    private val textMeasurer: TextMeasurer,
) {

    fun render(
        drawScope: DrawScope,
        entity: Entity,
        time: Float,
        highlight: Highlight = Highlight.NONE,
    ) {
        val vibrationParams = VibrationParams(entity.id, entity.state().value.energy, time) // параметры вибрации
        when (entity) {
            is Molecule -> drawMolecule(drawScope, entity, highlight, vibrationParams, time)
            is Atom -> drawElemental(drawScope, entity, entity.element, highlight, vibrationParams, withValenceSlots = true)
            is SubAtom -> drawElemental(drawScope, entity, entity.element, highlight, vibrationParams, withValenceSlots = false)
            is Star -> drawStar(drawScope, entity, time)
        }
    }

    // Кружок с символом: атом рисуется со свободными валентными слотами, частица — без них.
    private fun drawElemental(
        drawScope: DrawScope,
        entity: Entity,
        element: Element,
        highlight: Highlight,
        vibrationParams: VibrationParams,
        withValenceSlots: Boolean,
    ) {
        val entityState = entity.state().value
        val position = entityState.kinematics.position.toOffset()  + vibrationParams.positionOffset
        val fillColor = ElementColors.fill(element)
        val symbol = element.details.symbol.filter { it.isLetter() }

        with(drawScope) {
            if (withValenceSlots) {
                drawAtom(position, entity.radius, fillColor, symbol, element.valence(entityState.electrons), vibrationParams.slotAngle, highlighted = highlight.entity)
            } else {
                drawSubAtom(position, entity.radius, fillColor, symbol, vibrationParams.slotAngle, highlighted = highlight.entity)
            }
        }
    }

    // Молекула рисуется структурно: атомы-кружки по раскладке графа + связи-линии (кратность = число линий).
    private fun drawMolecule(
        drawScope: DrawScope,
        molecule: Molecule,
        highlight: Highlight,
        vibrationParams: VibrationParams,
        time: Float,
    ) {
        val entityState = molecule.state().value

        // Добавляем дрожание
        fun screenPos(atom: MolecularAtomFull, atomVibrationParams: VibrationParams = vibrationParams) = atom.position.toOffset() + atomVibrationParams.positionOffset

        with(drawScope) {

            molecule.bonds.forEach { bond ->
                // Связь под курсором показываем на кратность выше — пунктирной линией (клик усилит её).
                val potentialOrder = if (bond == highlight.bond) bond.order + 1 else bond.order
                drawBond(screenPos(bond.atom1), screenPos(bond.atom2), bond.order, potentialOrder)
            }

            molecule.atoms.forEach { atom ->
                val atomVibrationParams = VibrationParams(atom.localId.toLong(), entityState.energy, time) // параметры вибрации
                val fill = ElementColors.fill(atom.isotope)
                val symbol = atom.isotope.details.symbol.filter { it.isLetter() }
                val slotAngle = vibrationParams.slotAngle + vibrationParams.idSeed + atom.localId * 1.3f
                drawAtom(screenPos(atom, atomVibrationParams), atom.radius, fill, symbol, atom.freeValence, slotAngle, highlighted = highlight.entity)
            }
        }
    }



    // Связь: order параллельных линий (двойная/тройная — со сдвигом перпендикулярно связи).
    // potentialOrder > order — связь под курсором: недостающие линии рисуем ЗЕЛЁНЫМИ и толще, показывая,
    // что получится после клика. Раскладка считается по potentialOrder, поэтому существующие линии сразу
    // встают на свои будущие места и видна итоговая связь целиком.
    private fun DrawScope.drawBond(a: Offset, b: Offset, order: Int, potentialOrder: Int = order) {
        val dir = b - a
        val len = dir.getDistance()
        val perp = if (len > 1e-3f) Offset(-dir.y / len, dir.x / len) else Offset(0f, 1f)
        val total = maxOf(order, potentialOrder)
        val firstShift = -(total - 1) / 2f
        val lineWidth =  2.5f // ширина линии
        val BOND_LINE_SPACING = 8f       // сдвиг параллельных линий для двойных/тройных связей
        val BOND_COLOR = Color(0xFF212121)     // МИНИМАЛИЗМ: почти чёрная связь (Sokobond, на белом)
        val NEW_BOND_COLOR = Color(0xFF4CAF50) // будущая связь под курсором — зелёная: «кликни, и она появится»
        val NEW_BOND_LINE_WIDTH = 5f           // вдвое толще обычной, чтобы читалась как приглашение к действию
        for (i in 0 until total) {
            val shift = perp * ((firstShift + i) * BOND_LINE_SPACING)
            val isNew = i >= order
            drawLine(
                color = if (isNew) NEW_BOND_COLOR else BOND_COLOR,
                start = a + shift,
                end = b + shift,
                strokeWidth = if (isNew) NEW_BOND_LINE_WIDTH else lineWidth,
            )
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


    private fun drawStar(
        drawScope: DrawScope,
        entity: Star,
        time: Float,
    ) {
        val entityState = entity.state().value
        // параметры вибрации/пульса — от общего time на частоте STAR_HZ
        val amp = 1f + entityState.energy                  // амплитуда в пикселях
        val idSeed = (entity.id % 1000).toFloat()   // стаб. сдвиг фазы на объект
        val ph = time * ANIM_TWO_PI * STAR_HZ
        val dx = amp * kotlin.math.cos(ph + idSeed)
        val dy = amp * kotlin.math.sin(ph + idSeed)
        val position = entityState.kinematics.position.toOffset()  + Offset(dx, dy)

        // пульсирующий радиус для границы
        val baseRadius = entity.radius + 5f   // базовый радиус круга
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