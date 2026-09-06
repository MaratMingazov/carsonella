package maratmingazovr.ai.carsonella

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import maratmingazovr.ai.carsonella.chemistry.registry.AtomElement
import maratmingazovr.ai.carsonella.chemistry.registry.ElementOrMolecule
import maratmingazovr.ai.carsonella.chemistry.registry.MoleculeElement
import maratmingazovr.ai.carsonella.world.PaletteItem
import maratmingazovr.ai.carsonella.world.renderers.BARE_PROTON_DESCRIPTION
import maratmingazovr.ai.carsonella.world.renderers.BARE_PROTON_TITLE
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val CHIP = 150.dp          // размер карточки узла — квадрат: на кольце они стоят под любым углом
private val CHIP_PITCH = 170.dp    // минимальное расстояние между соседями по кольцу
private val RING_STEP = 200.dp     // и между самими кольцами
private val MAP_MARGIN = 40.dp
private val CHIP_BORDER = Color(0xFFE4E4E4)
private val LINE_COLOR = Color(0xFFDCDCDC)
private val ACCENT = Color(0xFF45BDB5)
private val TEXT_COLOR = Color(0xFF5A5A5A)

private val TWO_PI = (2 * PI).toFloat()
private val TOP = -PI.toFloat() / 2f   // угол «двенадцать часов»: с него начинается первый сектор

/**
 * Опыт: карта не заданий, а реестра. В центре субатомные частицы, за ними кольцо атомов, дальше молекулы —
 * каждая на кольцо дальше своих [maratmingazovr.ai.carsonella.chemistry.KnownMoleculeDetails.basedOn].
 * Ими же нарисованы линии. Заполнен basedOn пока примерно у половины реестра: у кого его нет, тот стоит
 * во втором кольце без единой линии — видно, что дописать.
 */
@Composable
fun MoleculeMapScreen(onBack: () -> Unit) {
    val nodes = remember { mapNodes() }
    val layout = remember { ringLayout(nodes) }
    var opened by remember { mutableStateOf<MapNode?>(null) }
    var zoom by remember { mutableStateOf(0.9f) }   // начальный zoom

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .onPreviewKeyEvent { e ->
                when {
                    e.type != KeyDown -> false
                    e.key == Key.Escape && opened != null -> { opened = null; true }
                    e.key == Key.Escape -> { onBack(); true }
                    e.key == Key.Minus || e.key == Key.NumPadSubtract -> { zoom = zoomOut(zoom, 0.1f); true }
                    e.key == Key.Plus || e.key == Key.Equals || e.key == Key.NumPadAdd -> { zoom = zoomIn(zoom, 1.0f); true }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text(UiString.MENU_MOLECULES),
                    fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 28.sp,
                    letterSpacing = 0.08.em, color = Color(0xFFC8C8C8),
                )
                Spacer(Modifier.weight(1f))
                MapTextLink("−", 22.sp) { zoom = zoomOut(zoom, 0.1f) }
                Spacer(Modifier.width(12.dp))
                Text(
                    "${(zoom * 100).toInt()}%",
                    fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 15.sp,
                    color = Color(0xFFA8A8A8),
                )
                Spacer(Modifier.width(12.dp))
                MapTextLink("+", 22.sp) { zoom = zoomIn(zoom, 0.1f) }
                Spacer(Modifier.width(24.dp))
                MapTextLink(text(UiString.MENU_BACK), 18.sp, onBack)
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val mapSize = ((layout.outerRadius + CHIP / 2 + MAP_MARGIN) * 2) + 100.dp
                // Полотно не меньше окна: на отдалении карта стоит по центру, а не липнет в угол.
                val canvasWidth = (mapSize * zoom).coerceAtLeast(maxWidth)
                val canvasHeight = (mapSize * zoom).coerceAtLeast(maxHeight)
                val hScroll = rememberScrollState()
                val vScroll = rememberScrollState()


                Box(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScroll)
                        .verticalScroll(vScroll),
                ) {
                    Box(Modifier.width(canvasWidth).height(canvasHeight).border(2.dp, Color.Red), contentAlignment = Alignment.Center) {
                        ScaledSquare(unscaledSide = mapSize, scale = zoom) {
                            Box(contentAlignment = Alignment.Center) {
                                BasedOnLines(nodes, layout) // рисуем линии
                                nodes.forEach { node -> // рисуем каждую карточку
                                    val place = layout.positions.getValue(node.item)
                                    Box(Modifier.offset(place.x, place.y)) {
                                        ElementCard(node) { opened = node }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        opened?.let { node -> ElementDetailsCard(node) { opened = null } }
    }
}

private fun zoomIn(zoom: Float, factor: Float): Float = (zoom + factor).coerceIn(0.2f, 1.0f)
private fun zoomOut(zoom: Float, factor: Float): Float = (zoom - factor).coerceIn(0.2f, 1.0f)

// Меряем контент в истинном размере (unscaledSide), а наружу отдаём уже смасштабированный: так родитель
// (прокрутка, центрирование) всегда знает точный размер картинки. Вложенные Modifier.size так не могут —
// когда ребёнок больше родителя, размер родителя "утекает" вверх и центрирование съезжает.
@Composable
private fun ScaledSquare(unscaledSide: Dp, scale: Float, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        val fixed = Constraints.fixed(unscaledSide.roundToPx(), unscaledSide.roundToPx())
        val placeables = measurables.map { it.measure(fixed) }
        val scaledSidePx = (unscaledSide.toPx() * scale).roundToInt()
        layout(scaledSidePx, scaledSidePx) {
            placeables.forEach { placeable ->
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

// Линии зависимостей идут под карточками — те залиты белым, поэтому конец линии не торчит из-под рамки.
@Composable
private fun BasedOnLines(nodes: List<MapNode>, layout: MapLayout) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        nodes.forEach { node ->
            val child = layout.positions[node.item] ?: return@forEach
            node.basedOn.forEach { parentItem ->
                val parent = layout.positions[parentItem] ?: return@forEach
                drawLine(
                    color = LINE_COLOR,
                    start = center + Offset(parent.x.toPx(), parent.y.toPx()),
                    end = center + Offset(child.x.toPx(), child.y.toPx()),
                    strokeWidth = 1.5f,
                )
            }
        }
    }
}

// Это карточка элемент
@Composable
private fun ElementCard(node: MapNode, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier
            .size(CHIP) // размер карточки
            .background(Color.White, shape)   // непрозрачная: карточка перекрывает линии, а не тонет в них
            .border(1.dp, if (hovered) ACCENT else CHIP_BORDER, shape)
            .hoverable(interaction)
            .clickable(interaction, indication = null) { onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { NodeImage(node.item, scale = 0.6f) }
        Spacer(Modifier.height(6.dp))
        Text(
            text(node.title),
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 12.sp,
            lineHeight = 14.sp, color = TEXT_COLOR, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

// Атом рисуем кружком, молекулу — эталонной раскладкой из реестра. Раскладка есть не у всех записей:
// без неё показываем структурную формулу текстом (шрифт меню тут не годится — в нём нет ≡).
@Composable
private fun NodeImage(item: PaletteItem, scale: Float) {
    when (item) {
        is PaletteItem.Atom -> PaletteAtom(item.element, electrons = item.electrons)
        is PaletteItem.KnownMolecule -> {
            val details = item.knownMoleculeId.details
            if (details.offsets.isNotEmpty()) {
                KnownMoleculePreview(details, scale = scale)
            } else {
                Text(
                    details.structuralFormula.ifEmpty { details.graph.formulaPretty },
                    fontFamily = FontFamily.Default, fontSize = 13.sp,
                    color = TEXT_COLOR, textAlign = TextAlign.Center,
                )
            }
        }
    }
}


// Это карточка, когда выбрали конкретную молекулу
@Composable
private fun ElementDetailsCard(node: MapNode, onClose: () -> Unit) {
    ModalCard(onClose = onClose) {
        Spacer(Modifier.height(12.dp))
        Text(
            text(node.title),
            fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 28.sp,
            color = Color.Black, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        NodeImage(node.item, scale = 0.8f)
        val body = text(node.description)
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                body,
                fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = 16.sp,
                lineHeight = 23.sp, color = Color(0xFF8A8A8A), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MapTextLink(label: String, size: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        label,
        fontFamily = menuFontFamily(), fontWeight = FontWeight.Light, fontSize = size,
        color = if (hovered) ACCENT else Color(0xFFA8A8A8),
        modifier = Modifier
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    )
}

// Узел карты. Ключ — сам [PaletteItem]: голый протон и атом водорода отличаются только числом электронов, а basedOn ссылается именно на атом.
private class MapNode(
    val item: PaletteItem,
    val title: TranslatedText,
    val description: TranslatedText,
    val ring: Int,
    val basedOn: List<PaletteItem>,
)

private const val SUB_ATOM_RING = 0   // центр: элементарные частицы
private const val ATOM_RING = 1       // за ними атомы, дальше кольца молекул

// Кольцо 0 — субатомные частицы, 1 — атомы, кольцо молекулы — на шаг дальше её basedOn.
private fun mapNodes(): List<MapNode> {
    val subAtoms = listOf(
        MapNode(PaletteItem.Atom(AtomElement.HYDROGEN, electrons = 0), BARE_PROTON_TITLE, BARE_PROTON_DESCRIPTION, ring = SUB_ATOM_RING, basedOn = emptyList()),
        atomNode(AtomElement.ELECTRON, ring = SUB_ATOM_RING),
        atomNode(AtomElement.NEUTRON, ring = SUB_ATOM_RING),
    )
    val atoms = listOf(AtomElement.HYDROGEN, AtomElement.CARBON_12, AtomElement.NITROGEN_14, AtomElement.OXYGEN_16).map { atomNode(it, ring = ATOM_RING) }
    val rings = HashMap<MoleculeElement, Int>()
    val molecules = MoleculeElement.entries.map { id ->
        MapNode(
            PaletteItem.KnownMolecule(id),
            id.title,
            id.description,
            ring = moleculeRing(id, rings, seen = emptySet()),
            basedOn = id.details.basedOn.map(::itemOf),
        )
    }
    return subAtoms + atoms + molecules
}

// Кольцо молекулы — на единицу дальше самого дальнего из её basedOn: так родитель всегда на кольцо
// ближе к центру, и линия зависимости идёт наружу, а не вдоль кольца. Без basedOn молекула встаёт
// сразу за атомами: сказать про неё пока нечего, и на карте это видно.
private fun moleculeRing(id: MoleculeElement, cache: MutableMap<MoleculeElement, Int>, seen: Set<MoleculeElement>): Int {
    cache[id]?.let { return it }
    if (id in seen) return ATOM_RING + 1   // basedOn закольцевался — дальше не разматываем
    val ring = id.details.basedOn.maxOfOrNull { source ->
        1 + when (source) {
            is AtomElement -> ATOM_RING
            is MoleculeElement -> moleculeRing(source, cache, seen + id)
        }
    } ?: (ATOM_RING + 1)
    cache[id] = ring
    return ring
}

private fun atomNode(element: AtomElement, ring: Int) =
    MapNode(PaletteItem.Atom(element), element.title, element.description, ring, basedOn = emptyList())

private fun itemOf(source: ElementOrMolecule): PaletteItem = when (source) {
    is AtomElement -> PaletteItem.Atom(source)
    is MoleculeElement -> PaletteItem.KnownMolecule(source)
}

// Место каждого узла — смещение от центра карты; [outerRadius] это самое дальнее кольцо.
private class MapLayout(val positions: Map<PaletteItem, DpOffset>, val outerRadius: Dp)

/**
 * Радиальное дерево. Корню достаётся сектор тем шире, чем больше листьев в его ветке
 * Нет единой переменной x/y - стартовая точка получается неявно. Чере полярные координаты (угол, радиус)
 */
private fun ringLayout(nodes: List<MapNode>): MapLayout {
    val byItem = nodes.associateBy { it.item }
    // Ветка у узла одна: ведём его к самому дальнему из basedOn. Остальные родители останутся линиями.
    val parents = HashMap<MapNode, MapNode>()
    nodes.forEach { node -> node.basedOn.mapNotNull { byItem[it] }.maxByOrNull { it.ring }?.let { parents[node] = it } }
    val childrenOf = nodes.filter { it in parents }.groupBy { parents.getValue(it) }

    val leaves = HashMap<MapNode, Int>()
    fun leavesOf(node: MapNode): Int = leaves.getOrPut(node) { childrenOf[node]?.sumOf { leavesOf(it) } ?: 1 }

    val angles = HashMap<MapNode, Float>()
    fun spread(node: MapNode, from: Float, width: Float) {
        angles[node] = from + width / 2f
        var cursor = from
        childrenOf[node]?.forEach { child ->
            val childWidth = width * leavesOf(child) / leavesOf(node)
            spread(child, cursor, childWidth)
            cursor += childWidth
        }
    }

    // Субатомные частицы делят своё кольцо поровну: за ними никто не следует, сектор им ни к чему.
    val subAtoms = nodes.filter { it.ring == SUB_ATOM_RING }
    subAtoms.forEachIndexed { index, node -> angles[node] = TOP + TWO_PI * index / subAtoms.size }

    val roots = nodes.filter { it.ring > SUB_ATOM_RING && it !in parents }
    val total = roots.sumOf { leavesOf(it) }
    var cursor = TOP
    roots.forEach { root ->
        val width = TWO_PI * leavesOf(root) / total
        spread(root, cursor, width)
        cursor += width
    }

    val radiusOf = HashMap<Int, Dp>()
    var radius = 0.dp
    nodes.groupBy { it.ring }.toSortedMap().forEach { (ring, ringNodes) ->
        val needed = ringRadius(ringNodes.map { angles.getValue(it) })
        radius = if (radiusOf.isEmpty()) needed else maxOf(radius + RING_STEP, needed)
        radiusOf[ring] = radius
    }
    val positions = nodes.associate { node ->
        val angle = angles.getValue(node)
        val ringRadius = radiusOf.getValue(node.ring)
        node.item to DpOffset(ringRadius * cos(angle), ringRadius * sin(angle))
    }
    return MapLayout(positions, radius)
}

// Радиус, при котором самые тесные соседи по кольцу расходятся на CHIP_PITCH. Считаем по хорде, а не
// по дуге, иначе на коротких кольцах карточки налезали бы друг на друга. Зазор ограничен снизу, чтобы
// совпавшие углы не дали бесконечный радиус, и сверху половиной круга: одному узлу расходиться не с кем.
private fun ringRadius(angles: List<Float>): Dp {
    val sorted = angles.sorted()
    val gaps = sorted.zipWithNext { previous, next -> next - previous } + (sorted.first() + TWO_PI - sorted.last())
    return CHIP_PITCH / (2f * sin(gaps.min().coerceIn(0.02f, PI.toFloat()) / 2f))
}