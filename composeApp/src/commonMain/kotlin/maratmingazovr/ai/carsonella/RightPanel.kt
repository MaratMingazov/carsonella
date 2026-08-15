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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MoleculeBond
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.SubAtom
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

    // Вторая ступень выбора: атомы (localId) ВНУТРИ выбранной молекулы. Состояние локальное — за ним ходят только сцена и Info-карточка, обе живут здесь.
    var selectedAtoms by remember { mutableStateOf(emptyList<Int>()) }
    LaunchedEffect(selectedId) { selectedAtoms = emptyList() }   // сменили частицу — выбор атомов не переносим

    // --- фокус для приёма клавиатуры ---
    val focusRequester = remember { FocusRequester() }
    val onSelectUpToDate = rememberUpdatedState(onSelect) // чтобы замыкание не устаревало

    DropTarget(accept = accept, onDrop = onDrop) { dropModifier ->
        Column(modifier = modifier.then(dropModifier).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // .background(Color(0xFF03040A))  // прежний тёмный фон контейнера
                    .background(Color.White)            // МИНИМАЛИЗМ (Sokobond)
                    .padding(4.dp)
                    // Границы мира = эта канва: без этого частицу можно увести за край и потерять.
                    .onSizeChanged { world.requestArea(it.width.toFloat(), it.height.toFloat()) }
                    .focusRequester(focusRequester) // для обработки клавиш клавиутуры
                    .focusable() // важно!
                    .onKeyEvent { e ->
                        when (e.type) {
                            KeyDown -> {
                                keys = keys + e.key
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
                    selectedAtoms = selectedAtoms,
                    onSelectAtoms = { selectedAtoms = it },
                    modifier = Modifier.matchParentSize()
                )
                // Тихие имена образовавшихся молекул — поверх сцены, в тех же координатах канвы.
                MoleculeToasts(world.moleculeEvents, Modifier.matchParentSize())
//                TemperatureBadge(world.updateTemperatureGame())

                // Info-оверлей: карточка в углу канвы при выборе частицы (клик по пустому месту снимает выбор)
                SelectedEntityPanel(
                    selectedElementId = selectedId,
                    selectedAtoms = selectedAtoms,
                    onSelectAtoms = { selectedAtoms = it },
                    entities = entities,
                    onSetEnergy = onSetEnergy,
                    onMoleculeAction = onMoleculeAction,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = 170.dp),
                )
            }
            // Консоль убрана из MVP: в белом минимализме она шумит. Долг — вернуть сигнал «реакция
            // произошла» тихой строкой сбоку (ROADMAP, побочные открытия). Сами логи в World живут.
//            ConsolePanel(
//                logs = world.logs,
//                onClear = { world.logs.clear() },
//                height = 100.dp
//            )
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
    selectedAtoms: List<Int>, // какие атомы выбранной молекулы выбраны (localId)
    onSelectAtoms: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {

    // ПОДПИСКА. Ниже значения читаются прямо из сущностей (entity.kinematics, entity.alive), но БЕЗ этого блока
    // Compose не узнает об изменениях: сцена замрёт при живом мире — логи в консоли пойдут, картинка нет.
    // Строка «работает тем, что существует», результат никому не нужен — не удалять.
    // key(id) нужен потому, что collectAsState зовётся в цикле: слот подписки привязан к сущности, а не
    // к позиции в списке, иначе при рождении/смерти частиц слоты «съезжают» и часть подписок теряется.
    // Имя key занято расширением KeyEvent.key, поэтому зовём по полному имени.
    // .value обязателен: зависимость регистрирует ЧТЕНИЕ значения, а не создание State. Без него
    // поток собирается, но композиция об этом не узнаёт.
    entities.forEach { entity ->
        androidx.compose.runtime.key(entity.id) { entity.changes.collectAsState().value }
    }

    val hoveredEntityId = hoverPos?.let { hitTest(entities, it) } // Находится ли под курсором какой то элемент?
    val selectedEntity = selectedId?.let { id -> entities.firstOrNull { it.id == id } } // Какой элемент сейчас выбран.
    val selectedMolecule = selectedEntity as? Molecule
    val selectedAtomsLive = liveAtoms(selectedMolecule, selectedAtoms) // выбор без протухших localId
    val hoveredBond = hoverPos?.let { strengthenableBondAt(selectedEntity, it) } // на какую связь молекулы навел курсор
    val hoveredAtom = hoverPos?.let { atomAt(selectedMolecule, it) } // на какой атом выбранной молекулы навел курсор
    val ringPreview = closableRing(selectedMolecule, selectedAtomsLive) // пара, на которой замкнётся кольцо

    // pointerInput ниже с ключом Unit (чтобы жест перетаскивания не прерывался каждый кадр),
    // поэтому замыкание должно читать «свежие» значения через rememberUpdatedState.
    val entitiesLatest = rememberUpdatedState(entities)
    val onHoverLatest = rememberUpdatedState(onHover)
    val onSelectLatest = rememberUpdatedState(onSelect)
    val selectedLatest = rememberUpdatedState(selectedEntity)
    val selectedAtomsLatest = rememberUpdatedState(selectedAtomsLive)
    val onSelectAtomsLatest = rememberUpdatedState(onSelectAtoms)

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

                        // Событие уже съели поверх нас (модальное окно) — канва его не видит вообще.
                        if (change.isConsumed) continue

                        // hover: всегда обновляем позицию курсора
                        onHoverLatest.value(change.position)

                        // нажали — запоминаем, что под курсором (но ещё не поднимаем)
                        if (change.changedToDown()) {
                            candidateId = hitTest(entitiesLatest.value, change.position)
                            holding = false
                        }

                        // потащили: на первом смещении «поднимаем» частицу, дальше она следует за курсором
                        if (candidateId != null && change.pressed && change.positionChanged()) {
                            if (!holding) { world.pickUpEntity(candidateId); holding = true }
                            world.moveEntityBy(candidateId, change.positionChange().toVec2D())
                            change.consume()
                        }

                        // отпустили
                        if (change.changedToUp()) {
                            val atom = atomAt(selectedLatest.value as? Molecule, change.position)
                            val bond = strengthenableBondAt(selectedLatest.value, change.position)
                            if (holding) {
                                world.dropHeldEntity()            // положили — снова взаимодействует
                            } else if (atom != null) {
                                // Клик по атому ВЫБРАННОЙ молекулы = вторая ступень выбора. Модификатор читаем
                                // у события, а не из набора keys: тот собирается по фокусу и может отставать.
                                val additive = e.keyboardModifiers.isCtrlPressed || e.keyboardModifiers.isMetaPressed
                                onSelectAtomsLatest.value(withAtom(selectedAtomsLatest.value, atom, additive))
                            } else if (bond != null) {
                                // Клик по связи ВЫБРАННОЙ молекулы = усилить именно её (механика «лего»).
                                world.requestMoleculeAction(
                                    selectedLatest.value!!.id,
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

        // граница корневого environment — эллипс по размеру канвы (на белом фоне светло-серый)
        val envCenter = world.environment.getEnvCenter().toOffset()
        val envRadiusX = world.environment.getEnvRadius()
        val envRadiusY = world.environment.getEnvRadiusY()
        drawOval(
            color = Color.Black.copy(alpha = 0.12f),
            topLeft = Offset(envCenter.x - envRadiusX, envCenter.y - envRadiusY),
            size = Size(envRadiusX * 2f, envRadiusY * 2f),
            style = Stroke(width = 1f)
        )

//        // размеры мира
//        world.environment.setWorldWidth(size.width)
//        world.environment.setWorldHeight(size.height)

        // отрисовка сущностей; символ показываем только у наведённой/выбранной
        entities
            .sortedBy { if (it.id == world.heldEntityId) 1 else 0 }   // выделенную частицу рисуем поверх остальных
            .forEach { entity ->
                val id = entity.id
                val isHoveredOrSelectedEntity = id == hoveredEntityId || id == selectedId
                val isSelected = id == selectedId   // выбор атомов и связи — только у выбранной молекулы
                renderer.render(
                    this, entity, time,
                    Highlight(
                        entity = isHoveredOrSelectedEntity,
                        bond = if (isSelected) hoveredBond else null,
                        selectedAtoms = if (isSelected) selectedAtomsLive.toSet() else emptySet(),
                        hoveredAtom = if (isSelected) hoveredAtom else null,
                        ringPreview = if (isSelected) ringPreview else null,
                    ),
                )
            }
    }
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
 * Кто из entities находится под точкой cursor — id ближайшей сущности, если курсор в неё попал,
 * иначе null. Один на всех: наведение, клик-выбор и захват при перетаскивании, поэтому подсветка
 * предсказывает, что именно выберется.
 *
 *
 * slop — кайма вокруг силуэта, запас на промах: без неё в электрон (r = 15) почти не попасть.
 * Строгое `<` оставляет победу первому в списке при ничьей.
 */
private fun hitTest(
    entities: List<Entity>,
    cursorPosition: Offset,
    slop: Float = 20f
): Long? {
    val myPosition = cursorPosition.toPosition()
    var bestId: Long? = null
    var bestDistance = Float.MAX_VALUE
    for (entity in entities) {
        val distance = entity.distanceToSurface(myPosition)
        if (distance <= slop && distance < bestDistance) {
            bestDistance = distance
            bestId = entity.id
        }
    }
    return bestId
}


/**
 * Какой атом молекулы под точкой — его localId, иначе null. Спрашиваем только у ВЫБРАННОЙ молекулы:
 * атом — вторая ступень выбора, до неё игрок выбирает саму молекулу.
 *
 * slop меньше, чем в hitTest: внутри молекулы атомы стоят вплотную, широкая кайма склеивала бы соседей.
 */
private fun atomAt(
    molecule: Molecule?,
    at: Offset,
    slop: Float = 6f,
): Int? {
    val mol = molecule ?: return null
    val point = at.toPosition()
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    for (atom in mol.atoms) {
        val distance = point.distanceTo(atom.kinematics.position) - atom.radius
        if (distance <= slop && distance < bestDistance) {
            bestDistance = distance
            best = atom.localId
        }
    }
    return best
}

/**
 * Выбор атомов после клика по localId. Клик по уже выбранному атому снимает его.
 * additive (Ctrl/Cmd) — набрать пару, иначе выбор заменяется целиком.
 *
 * Больше MAX_SELECTED_ATOMS не набрать: реакция адресуется парой, поэтому третий атом вытесняет
 * самый старый — так игроку не приходится снимать выбор руками.
 */
private fun withAtom(current: List<Int>, localId: Int, additive: Boolean): List<Int> = when {
    localId in current -> current - localId
    !additive -> listOf(localId)
    else -> (current + localId).takeLast(MAX_SELECTED_ATOMS)
}

private const val MAX_SELECTED_ATOMS = 2

// Пара выбранных атомов, на которой можно замкнуть кольцо, иначе null. Один вопрос на двоих: им живут и
// превью на молекуле, и кнопка в панели, поэтому кнопка не может предложить то, чего сцена не показала.
private fun closableRing(molecule: Molecule?, atoms: List<Int>): Pair<Int, Int>? {
    if (molecule == null || atoms.size != 2) return null
    val (localId1, localId2) = atoms
    return if (molecule.canCloseRing(localId1, localId2)) localId1 to localId2 else null
}

// Выбор атомов без протухших localId: замыкание кольца номера сохраняет, а слияние/распад — нет.
private fun liveAtoms(molecule: Molecule?, localIds: List<Int>): List<Int> {
    if (molecule == null || localIds.isEmpty()) return emptyList()
    val alive = molecule.atoms.mapTo(HashSet()) { it.localId }
    return localIds.filter { it in alive }
}

/**
 * Тут мы определяем навели ли мы курсов на молекулярную связь молекулы
 * Чтобы потом смогли усилить именно эту связь
 */
private fun strengthenableBondAt(
    molecule: Entity?,
    at: Offset,
    slop: Float = 10f,
): MoleculeBond? {
    val mol = molecule as? Molecule ?: return null
    val point = at.toPosition()
    var best: MoleculeBond? = null
    var bestDistance = Float.MAX_VALUE
    for (bond in mol.strengthenableBonds) {
        val atom1 = mol.atom(bond.localId1)
        val atom2 = mol.atom(bond.localId2)
        if (point.distanceTo(atom1.kinematics.position) <= atom1.radius) continue   // это клик по атому
        if (point.distanceTo(atom2.kinematics.position) <= atom2.radius) continue
        val distance = distanceToSegment(point, atom1.kinematics.position, atom2.kinematics.position)
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
private fun PanelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
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
    selectedAtoms: List<Int>, // выбранные атомы молекулы (localId): ими игрок адресует реакцию
    onSelectAtoms: (List<Int>) -> Unit,
    entities: List<Entity>,
    onSetEnergy: (Long, Float) -> Unit,
    onMoleculeAction: (Long, ReactionSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedEntity = entities.firstOrNull { it.id == selectedElementId } ?: return
    val selectedElement by selectedEntity.changes.collectAsState() // подписываем на элемент. Чтобы при изменении состояния этого элемента Compose перерисовал панель

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

        // Выбранные атомы молекулы: символ с номером узла и сколько связей атом ещё может образовать.
        val atoms = liveAtoms(selectedEntity as? Molecule, selectedAtoms)
        if (selectedEntity is Molecule && atoms.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Atoms:", style = MaterialTheme.typography.labelSmall, color = Color.Black)
            Text(
                atoms.joinToString(", ") { localId ->
                    val atom = selectedEntity.atom(localId)
                    "${atom.isotope.bareSymbol}$localId (${selectedEntity.freeValence(atom)} free)"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Действия «лего» по молекуле: форсим правило через ReactionSelection (см. World.requestMoleculeAction).
        // Кольцо адресует ПАРА выбранных атомов, поэтому кнопка ждёт, пока игрок наберёт годную пару.
        if (selectedEntity is Molecule && selectedEntity.canCloseRing) {
            val ring = closableRing(selectedEntity, atoms)
            Spacer(Modifier.height(8.dp))
            PanelButton(
                text = "Close ring",
                enabled = ring != null,
                onClick = {
                    ring?.let { (localId1, localId2) ->
                        onMoleculeAction(selectedEntity.id, ReactionSelection.CloseRing(localId1, localId2))
                        onSelectAtoms(emptyList())   // пару отработали — выбор снимаем
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Редактор энергии (пока только фотон).
        if (selectedEntity is SubAtom && selectedEntity.element == Element.PHOTON) {
            Spacer(Modifier.height(8.dp))
            EnergyEditor(
                energyEv = selectedEntity.energy,
                onApply = { energy -> onSetEnergy(selectedEntity.id, energy) },
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



