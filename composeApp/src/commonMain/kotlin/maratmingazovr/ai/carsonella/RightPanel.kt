package maratmingazovr.ai.carsonella

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.EntityState
import maratmingazovr.ai.carsonella.chemistry.MolecularBond
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionSelection
import maratmingazovr.ai.carsonella.world.World
import maratmingazovr.ai.carsonella.world.renderers.EntityRenderer
import maratmingazovr.ai.carsonella.world.renderers.Highlight
import kotlin.math.round


@Composable
fun RightPanel(
    accept: (DragData) -> Boolean,
    onDrop: (DragData, Offset) -> Unit,
    hoverPos: Offset?,
    onHover: (Offset?) -> Unit,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    world: World,
    entities: List<Entity>,
    renderer: EntityRenderer,
    time: Float,
    onSetEnergy: (Long, Float) -> Unit,
    onMoleculeAction: (Long, ReactionSelection) -> Unit,
    modifier: Modifier = Modifier
) {

    // Для обработки клавиш клавиатуры
    // 1) локально храним зажатые клавиши
    var keys by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<Key>()) }
    // --- фокус для приёма клавиатуры ---
    val focusRequester = remember { FocusRequester() }
    val onSelectUpToDate = rememberUpdatedState(onSelect) // чтобы замыкание не устаревало

    DropTarget(accept = accept, onDrop = onDrop) { dropModifier ->
        Column(modifier = dropModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // .background(Color(0xFF03040A))  // прежний тёмный фон контейнера
                    .background(Color.White)            // МИНИМАЛИЗМ (Sokobond)
                    .padding(4.dp)
                    .focusRequester(focusRequester) // для обработки клавиш клавиутуры
                    .focusable() // важно!
                    .onKeyEvent { e ->
                        when (e.type) {
                            KeyDown -> {
                                keys = keys + e.key
                                // ► действие по пробелу
                                if (e.key == Key.Spacebar) {
                                    val id = selectedId
                                    val mouse = hoverPos
                                    if (id != null && mouse != null) {
                                        val selected = entities.firstOrNull { it.state().value.id == id }?.state()?.value
                                        if (selected != null) {
                                            val from = selected.centerPosition.toOffset()
                                            val dir = direction(from, mouse)   // единичный вектор к мыши
                                            // Из выбранного элемента стреляем фотоном
                                            world.entityGenerator.createEntity(species = selected.species, Position(selected.centerPosition.x, selected.centerPosition.y),  direction = dir, velocity = 10f, energy = selected.energy, environment = world.environment, electrons = selected.electrons)
                                        }
                                    }
                                }
                                // ► удаление выбранной частицы. На macOS основная клавиша удаления
                                // репортится как Backspace, поэтому ловим и Delete, и Backspace.
                                if (e.key == Key.Delete || e.key == Key.Backspace) {
                                    selectedId?.let { id ->
                                        world.removeEntity(id)
                                        onSelect(null)   // частицы больше нет → снимаем выбор
                                    }
                                }
                                true
                            }
                            KeyUp -> { keys = keys - e.key; true }
                            else -> false
                        }
                    }
            ) {
                SceneCanvas(
                    world = world,
                    entities = entities,
                    renderer = renderer,
                    time = time,
                    hoverPos = hoverPos,
                    onHover = { pos -> onHover(pos); focusRequester.requestFocus() },
                    selectedId = selectedId,
                    onSelect = { onSelect(it); focusRequester.requestFocus() },
                    modifier = Modifier.matchParentSize()
                )
//                TemperatureBadge(world.updateTemperatureGame())

                // Info-оверлей: карточка в углу канвы при выборе частицы (клик по пустому месту снимает выбор)
                SelectedEntityPanel(
                    selectedElementId = selectedId,
                    entities = entities,
                    onSetEnergy = onSetEnergy,
                    onMoleculeAction = onMoleculeAction,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = 170.dp),
                )
            }
            ConsolePanel(
                logs = world.logs,
                onClear = { world.logs.clear() },
                height = 100.dp
            )
        }
    }


    LaunchedEffect(Unit) {
        kotlinx.coroutines.yield()
        focusRequester.requestFocus()
    }
    ControlSelectedWithKeysLoop(selectedId = selectedId, keys = keys) { id, force ->
        world.applyForceToEntity(id, force)
    }
//    HandleKeyboardForSelection(selectedId = selectedId, keys = keys) { key, id ->
//        when (key) {
//            Key.W -> world.applyForceToEntity(id, Vec2D(0f, -1f))
//            Key.DirectionUp -> world.applyForceToEntity(id, Vec2D(0f, -1f))
//            Key.S -> world.applyForceToEntity(id, Vec2D(0f, 1f))
//            Key.DirectionDown -> world.applyForceToEntity(id, Vec2D(0f, 1f))
//            Key.A -> world.applyForceToEntity(id, Vec2D(-1f, 0f))
//            Key.DirectionLeft -> world.applyForceToEntity(id, Vec2D(-1f, 0f))
//            Key.D -> world.applyForceToEntity(id, Vec2D(1f, 0f))
//            Key.DirectionRight -> world.applyForceToEntity(id, Vec2D(1f, 0f))
//        }
//    }
}


// Тусклая фоновая звезда: позиция нормирована (0..1), масштабируется под размер канвы.
private data class StarDot(val nx: Float, val ny: Float, val radius: Float, val alpha: Float)

@Composable
private fun SceneCanvas(
    world: World,
    entities: List<Entity>,
    renderer: EntityRenderer,
    time: Float,
    hoverPos: Offset?, // где находится курсор
    onHover: (Offset?) -> Unit,
    selectedId: Long?, // какой элемент сейчас выбран?
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {

    // ПОДПИСКА. Ниже значения читаются прямо из сущностей (entity.state().value), но БЕЗ этого блока
    // Compose не узнает об изменениях: сцена замрёт при живом мире — логи в консоли пойдут, картинка нет.
    // Строка «работает тем, что существует», результат никому не нужен — не удалять.
    // key(id) нужен потому, что collectAsState зовётся в цикле: слот подписки привязан к сущности, а не
    // к позиции в списке, иначе при рождении/смерти частиц слоты «съезжают» и часть подписок теряется.
    // Имя key занято расширением KeyEvent.key, поэтому зовём по полному имени.
    // .value обязателен: зависимость регистрирует ЧТЕНИЕ значения, а не создание State. Без него
    // поток собирается, но композиция об этом не узнаёт.
    entities.forEach { entity ->
        androidx.compose.runtime.key(entity.state().value.id) { entity.state().collectAsState().value }
    }

    val hoveredEntityId = hoverPos?.let { hitTest(entities, it) } // Находится ли под курсором какой то элемент?
    val selectedEntity = selectedId?.let { id -> entities.firstOrNull { it.state().value.id == id } } // Какой элемент сейчас выбран.
    val hoveredBond = hoverPos?.let { strengthenableBondAt(selectedEntity, it) } // на какую связь молекулы навел курсор

    // pointerInput ниже с ключом Unit (чтобы жест перетаскивания не прерывался каждый кадр),
    // поэтому замыкание должно читать «свежие» значения через rememberUpdatedState.
    val entitiesLatest = rememberUpdatedState(entities)
    val onHoverLatest = rememberUpdatedState(onHover)
    val onSelectLatest = rememberUpdatedState(onSelect)
    val selectedLatest = rememberUpdatedState(selectedEntity)

    // Тусклые звёзды фона: позиции нормированы (0..1), генерируем один раз сидированным RNG.
    val stars = remember {
        val rnd = kotlin.random.Random(42)
        List(140) {
            StarDot(
                nx = rnd.nextFloat(),
                ny = rnd.nextFloat(),
                radius = 0.4f + rnd.nextFloat() * 1.1f,
                alpha = 0.15f + rnd.nextFloat() * 0.5f,
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var candidateId: Long? = null   // частица под курсором в момент нажатия
                    var holding = false             // подняли ли её реально (после начала движения)
                    while (true) {
                        val e = awaitPointerEvent()               // получаем событие
                        val change = e.changes.firstOrNull() ?: continue

                        // hover: всегда обновляем позицию курсора
                        onHoverLatest.value(change.position)

                        // нажали — запоминаем, что под курсором (но ещё не поднимаем)
                        if (change.changedToDown()) {
                            candidateId = hitTest(entitiesLatest.value, change.position)
                            holding = false
                        }

                        // потащили: на первом смещении «поднимаем» частицу, дальше она следует за курсором
                        if (candidateId != null && change.pressed && change.positionChanged()) {
                            if (!holding) { world.pickUpEntity(candidateId!!); holding = true }
                            world.moveEntityTo(candidateId!!, Position(change.position.x, change.position.y))
                            change.consume()
                        }

                        // отпустили
                        if (change.changedToUp()) {
                            val bond = strengthenableBondAt(selectedLatest.value, change.position)
                            if (holding) {
                                world.dropHeldEntity()            // положили — снова взаимодействует
                            } else if (bond != null) {
                                // Клик по связи ВЫБРАННОЙ молекулы = усилить именно её (механика «лего»).
                                world.requestMoleculeAction(
                                    selectedLatest.value!!.state().value.id,
                                    ReactionSelection.StrengthenBond(bond),
                                )
                            } else {
                                onSelectLatest.value(hitTest(entitiesLatest.value, change.position)) // клик без движения = выбор
                            }
                            candidateId = null
                            holding = false
                        }
                    }
                }
            }
    ) {
        // МИНИМАЛИЗМ (Sokobond): белый фон. Прежний тёмный космос закомментирован — вернуть при откате.
        drawRect(color = Color.White, size = size)
        /* --- прежний тёмный космический фон (радиальный градиент + редкие тусклые звёзды): ---
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0B1026), Color(0xFF03040A)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = kotlin.math.max(size.width, size.height) * 0.75f,
            ),
            size = size,
        )
        stars.forEach { s ->
            drawCircle(
                color = Color.White.copy(alpha = s.alpha),
                radius = s.radius,
                center = Offset(s.nx * size.width, s.ny * size.height),
            )
        }
        */

        // граница корневого environment (на белом фоне — светло-серая)
        drawCircle(
            color = Color.Black.copy(alpha = 0.12f),
            center = world.environment.getEnvCenter().toOffset(),
            radius = world.environment.getEnvRadius(),
            style = Stroke(width = 1f)
        )

//        // размеры мира
//        world.environment.setWorldWidth(size.width)
//        world.environment.setWorldHeight(size.height)

        // отрисовка сущностей; символ показываем только у наведённой/выбранной
        entities
            .sortedBy { if (it.state().value.id == world.heldEntityId) 1 else 0 }   // выделенную частицу рисуем поверх остальных
            .forEach { entity ->
                val id = entity.state().value.id
                val isHoveredOrSelectedEntity = id == hoveredEntityId || id == selectedId
                val hoveredBond = if (id == selectedId) hoveredBond else null
                renderer.render(
                    this, entity, time,
                    Highlight(
                        entity = isHoveredOrSelectedEntity,
                        bond = hoveredBond,
                    ),
                )
            }
    }
}

private fun direction(from: Offset, to: Offset): Vec2D {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = Vec2D(dx, dy).length()
    return if (len > 1e-6f) Vec2D(dx / len, dy / len) else Vec2D(0f, 0f)
}

@Composable
private fun ControlSelectedWithKeysLoop(selectedId: Long?, keys: Set<Key>, onImpulse: (Long, Vec2D) -> Unit) {
    LaunchedEffect(selectedId, keys) {
        if (selectedId == null) return@LaunchedEffect
        while (keys.isNotEmpty()) {
            val dir = dirFromKeys(keys)
            onImpulse(selectedId, dir.div(10f))
            kotlinx.coroutines.delay(16)
        }
    }
}

// Направление из WASD/стрелок
private fun dirFromKeys(keys: Set<Key>): Vec2D {
    var dx = 0f; var dy = 0f
    if (Key.W in keys || Key.DirectionUp in keys)    dy -= 1f
    if (Key.S in keys || Key.DirectionDown in keys)  dy += 1f
    if (Key.A in keys || Key.DirectionLeft in keys)  dx -= 1f
    if (Key.D in keys || Key.DirectionRight in keys) dx += 1f
    return Vec2D(dx, dy)
}

@Composable
fun ConsolePanel(
    logs: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    showClear: Boolean = true,
    onClear: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()

    // автопрокрутка к последней строке
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex) }

    Column(
        modifier
            .fillMaxWidth(0.6f)   // консоль уже канвы; долю правишь здесь
            .height(height)
            .padding(8.dp)                                       // отступ — консоль читается как отдельная карточка
            .background(PANEL_BG, RoundedCornerShape(12.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Console", style = MaterialTheme.typography.labelLarge, color = Color(0xFF3E362A))
            if (showClear && onClear != null) {
                Text(
                    "Clear",
                    color = Color(0xFF6B5E4A),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onClear() }
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(logs.size) { i ->
                Text(
                    logs[i],
                    color = Color(0xFF3E362A),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}


/**
 * Кто из [entities] находится под точкой [at] — id ближайшей сущности, если курсор в неё попал,
 * иначе null. Один на всех: наведение, клик-выбор и захват при перетаскивании, поэтому подсветка
 * предсказывает, что именно выберется.
 *
 * Из чего сущность состоит, здесь не знают: [EntityState.distanceToSurface] отвечает одним числом и
 * за частицу, и за молекулу (у неё — по ближайшему атому). Числа сравнимы между разными по размеру
 * сущностями, поэтому «ближайший» получается честным: тот, ВНУТРЬ кого курсор попал (расстояние
 * отрицательное), выигрывает у того, рядом с кем он просто стоит.
 *
 * [slop] — кайма вокруг силуэта, запас на промах: без неё в электрон (r = 15) почти не попасть.
 * Строгое `<` оставляет победу первому в списке при ничьей.
 */
private fun hitTest(
    entities: List<Entity>,
    at: Offset,
    slop: Float = 20f
): Long? {
    val point = at.toPosition()
    var bestId: Long? = null
    var bestDistance = Float.MAX_VALUE
    for (entity in entities) {
        val state = entity.state().value
        val distance = state.distanceToSurface(point)
        if (distance <= slop && distance < bestDistance) {
            bestDistance = distance
            bestId = state.id
        }
    }
    return bestId
}


/**
 * Тут мы определяем навели ли мы курсов на молекулярную связь молекулы
 * Чтобы потом смогли усилить именно эту связь
 */
private fun strengthenableBondAt(
    molecule: Entity?,
    at: Offset,
    slop: Float = 10f,
): MolecularBond? {
    val state = molecule?.state()?.value ?: return null
    val species = state.species as? Species.Molecular ?: return null
    val point = at.toPosition()
    var best: MolecularBond? = null
    var bestDistance = Float.MAX_VALUE
    for (bond in species.strengthenableBonds(state.centerPosition)) {
        if (point.distanceTo(bond.atom1.position) <= bond.atom1.radius) continue   // это клик по атому
        if (point.distanceTo(bond.atom2.position) <= bond.atom2.radius) continue
        val distance = distanceToSegment(point, bond.atom1.position, bond.atom2.position)
        if (distance <= slop && distance < bestDistance) {
            bestDistance = distance
            best = bond
        }
    }
    return best
}

// Расстояние от точки до ОТРЕЗКА (а не до прямой): проекция, прижатая к концам.
private fun distanceToSegment(point: Position, a: Position, b: Position): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lengthSquare = abx * abx + aby * aby
    if (lengthSquare < 1e-6f) return point.distanceTo(a)   // вырожденный отрезок — это точка
    val t = (((point.x - a.x) * abx + (point.y - a.y) * aby) / lengthSquare).coerceIn(0f, 1f)
    return point.distanceTo(Position(a.x + t * abx, a.y + t * aby))
}


// Кнопка в стиле беж-панели: лёгкий OutlinedButton — тёплая тонкая рамка + тёмный текст, скругление
// (перекликается с чёрной обводкой атомов), вместо яркой Material-заливки.
@Composable
private fun PanelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF8A7B60)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3E362A)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

// Карточка Info поверх канвы: показывает выбранную частицу и действия по ней. Ничего не выбрано →
// не рисуется вовсе (ранний return). Перенесена из прежней LeftPanel.
@Composable
private fun SelectedEntityPanel(
    selectedElementId: Long?,
    entities: List<Entity>,
    onSetEnergy: (Long, Float) -> Unit,
    onMoleculeAction: (Long, ReactionSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedEntity = entities.firstOrNull { it.state().value.id == selectedElementId } ?: return
    val selectedElement by selectedEntity.state().collectAsState() // подписываем на элемент. Чтобы при изменении состояния этого элемента Compose перерисовал панель

    Column(
        modifier.fillMaxWidth()
            .background(PANEL_BG, RoundedCornerShape(12.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text("Info", style = MaterialTheme.typography.labelLarge, color = Color.Black)
        Spacer(Modifier.height(8.dp))
        Text(selectedEntity.describe(), style = MaterialTheme.typography.bodySmall)

        // Энергетическая лестница (эВ): уровни возбуждения, последний = порог ионизации. Пусто → не показываем.
        val levels = selectedEntity.energyLevels
        if (levels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Levels, eV:", style = MaterialTheme.typography.labelSmall, color = Color.Black)
            Text(
                levels.joinToString(", ") { (round(it * 100) / 100).toString() },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Действия «лего» по молекуле: форсим правило через ReactionSelection (см. World.requestMoleculeAction).
        val species = selectedElement.species
        if (species is Species.Molecular) {
            if (species.canCloseRing) {
                Spacer(Modifier.height(8.dp))
                PanelButton(
                    text = "Close ring",
                    onClick = { onMoleculeAction(selectedElement.id, ReactionSelection.CloseRing) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // Редактор энергии (пока только фотон).
        if (species is Species.Atomic && species.element == Element.PHOTON) {
            Spacer(Modifier.height(8.dp))
            EnergyEditor(
                energyEv = selectedElement.energy,
                onApply = { energy -> onSetEnergy(selectedElement.id, energy) },
            )
        }
    }
}

// Редактор энергии фотона (эВ). Энергию ≤ 0 не применяем: у реального фотона энергии-нуля не бывает.
@Composable
private fun EnergyEditor(
    energyEv: Float,
    onApply: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Локальный буфер ввода: синхронизируем с внешней энергией, но не затираем, пока поле в фокусе.
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(energyText(energyEv)) }
    LaunchedEffect(energyEv) { if (!focused) text = energyText(energyEv) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall,   // размер вводимого текста (по умолчанию bodyLarge ≈ 16sp)
            label = { Text("Energy, eV") },
            modifier = Modifier.width(100.dp).height(55.dp).onFocusChanged { focused = it.isFocused },
        )
        PanelButton(
            text = "Apply",
            onClick = { text.trim().toFloatOrNull()?.takeIf { it > 0f }?.let(onApply) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Текст для поля ввода: округляем до 2 знаков; при E = 0 поле пустое.
private fun energyText(energyEv: Float): String =
    if (energyEv > 0f) (round(energyEv * 100) / 100).toString() else ""



