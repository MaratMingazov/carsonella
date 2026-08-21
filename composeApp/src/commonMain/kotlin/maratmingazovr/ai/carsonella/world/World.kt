package maratmingazovr.ai.carsonella.world

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock // с kotlinx-datetime 0.7 часы живут в stdlib (kotlin.time), а не в kotlinx.datetime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.randomDirection
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Kinematics
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MoleculeAtom
import maratmingazovr.ai.carsonella.chemistry.MoleculeBond
import maratmingazovr.ai.carsonella.chemistry.MoleculeShape
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGeometry
import maratmingazovr.ai.carsonella.chemistry.behavior.Movable
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.DEFAULT_PHOTON_ENERGY_EV
import maratmingazovr.ai.carsonella.world.save.EntityDto
import maratmingazovr.ai.carsonella.world.save.EnvironmentDto
import maratmingazovr.ai.carsonella.world.save.WorldJson
import maratmingazovr.ai.carsonella.world.save.WorldSnapshotDto
import maratmingazovr.ai.carsonella.world.save.readSaveFile
import maratmingazovr.ai.carsonella.world.save.writeSaveFile
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ChemicalReactionResolver
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionRequest
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.ReactionSelection
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.EntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IdGenerator

import maratmingazovr.ai.carsonella.chemistry.graph.KnownMoleculeId
/** Образовалась известная молекула: запись реестра и место, где это случилось. */
data class MoleculeEvent(val id: Long, val knownMoleculeId: KnownMoleculeId, val position: Position)

sealed interface PaletteItem {
    data class Atom(val element: Element, val electrons: Int = neutralElectrons(element)) : PaletteItem
    data class KnownMolecule(val knownMoleculeId: KnownMoleculeId) : PaletteItem
}

// Сколько электронов у частицы «по умолчанию»: у свободного электрона он свой, у остальных — нейтраль.
fun neutralElectrons(element: Element): Int = if (element == Element.ELECTRON) 1 else element.details.p

data class PaletteSlot(val item: PaletteItem, val count: Int) // Слот палитры: что игрок может взять и сколько этого осталось на уровне.

// Отступ границ мира от края канвы: радиус самого крупного атома плюс запас на валентные слоты.
private const val EDGE_INSET = 34f

// Размер мира на уровне: весь холст или круг заданного радиуса — чтобы ставить опыты в тесноте.
sealed interface WorldArea {
    data object FitCanvas : WorldArea // мир занимает все доступное место
    data class Circle(val radiusPx: Float) : WorldArea // задаем конкретный радиус
}

class World(
    private val _scope: CoroutineScope,
) {

    private val _idGen: IdGenerator = IdGenerator()
    private val _pendingRequests = mutableListOf<ReactionRequest>()
    private val _seed = 1L
    val random = kotlin.random.Random(_seed)
    // Границы приходят от канвы (requestArea): до первого замера радиусы нулевые, и проверка границ
    // просто не работает — чтобы стенка не оказалась где попало.
    val environment = Environment(Position(0f, 0f), 0f, TemperatureMode.Space)
    // Палитра — это инвентарь текущего уровня: что выдали и сколько осталось. Заполняет уровень
    // (setInventory), расходует spawnFromPalette. Единственный источник материалов на холсте.
    val palette = mutableStateListOf<PaletteSlot>()
    val entities =  mutableStateListOf<Entity>()
    val logs =  mutableStateListOf<String>()
    // Очередь «родилась известная молекула» для тихих всплывающих имён. Пишет тик, снимает UI,
    // когда плашка догорела (как logs с кнопкой Clear).
    val moleculeEvents = mutableStateListOf<MoleculeEvent>()
    private var _eventId = 0L
    // Счётчик тиков — «время симуляции». Один tick = tickMs (16 мс). Растёт каждый кадр цикла.
    // Нужен для сохранений (резюме с того же момента) и анализа динамики «что образовалось со временем».
    var tick: Long = 0L
        private set
    // Частица, которую игрок держит «в руке» (тащит мышью). Пока поднята — не шагает (ничего не
    // инициирует) и убрана из детей среды (её никто не видит как соседа → ни сил, ни реакций).
    var heldEntityId: Long? = null
        private set
    val entityGenerator = EntityGenerator(_idGen, entities, _pendingRequests, log = { logs += "${currentTime()}: $it" }, random)

    // Отложенная загрузка: load() кладёт сюда слепок, а применяется он внутри тика —
    // чтобы «тик оставался единственным писателем мира» (см. README, технические TODO).
    private var _pendingSnapshot: WorldSnapshotDto? = null
    private var _pendingClear = false   // очистка холста между уровнями — по тому же правилу
    private var _pendingArea: EnvArea? = null   // новые границы мира от канвы — тоже через тик

    private data class EnvArea(val center: Position, val radiusX: Float, val radiusY: Float)
    private data class CanvasSize(val width: Float, val height: Float)

    // Последний замер канвы и режим от уровня: границы = функция от них двоих, меняться может любое.
    private var _canvas: CanvasSize? = null
    private var _worldArea: WorldArea = WorldArea.FitCanvas

    private val _chemicalReactionResolver = ChemicalReactionResolver(entityGenerator)

    fun start() {
        //entityGenerator.createEntity(element = Element.Star, position = Position(800f, 400f),  direction = randomDirection(random), velocity = 0f, energy = 0f, environment = environment, electrons = 1)
        //val recombinationModule1 = entityGenerator.createEntity(element = Element.RECOMBINATION_MODULE, position = Position(300f, 250f),  direction = randomDirection(random), velocity = 0f, energy = 0f) as RecombinationModule

//        val module1 = entityGenerator.createEntity(element = Element.SPACE_MODULE, position = Position(300f, 300f),  direction = randomDirection(), velocity = 0f, energy = 0f) as SpaceModule
//        module1.setReagent1Element(Element.HYDROGEN)
//        module1.setReagent2Element(Element.HYDROGEN)




//        entityGenerator.createEntity(element = Element.Photon, position = Position(100f, 150f),  direction = randomDirection(), velocity = 0f, energy = 10.2f)
//        entityGenerator.createEntity(element = Element.H2, position = Position(100f, 350f),  direction = randomDirection(), velocity = 0f, energy = 0f)
//        entityGenerator.createEntity(element = Element.Photon, position = Position(100f, 200f),  direction = randomDirection(), velocity = 0f, energy = 1.89f)
//        entityGenerator.createEntity(element = Element.Photon, position = Position(100f, 250f),  direction = randomDirection(), velocity = 0f, energy = 1.51f)
//        entityGenerator.createEntity(element = Element.Photon, position = Position(100f, 300f),  direction = randomDirection(), velocity = 0f, energy = 12.09f)

        _scope.launch {
            val tickMs = 16L
            while (true) {

                // Load phase — если запрошена загрузка, применяем слепок до шага сущностей
                _pendingSnapshot?.let { snap ->
                    applySnapshot(snap)
                    _pendingSnapshot = null
                }

                if (_pendingClear) { clearNow(); _pendingClear = false }

                // Границы мира приходят из UI (канва знает свой размер) — применяем их здесь же,
                // чтобы писателем мира оставался тик.
                _pendingArea?.let { area ->
                    environment.setEnvArea(area.center, area.radiusX, area.radiusY)
                    _pendingArea = null
                }

                tick++

                // снимок, чтобы не падать на ConcurrentModificationException
                // если кто-то добавит сущность во время шага
                val snapshot = entities.toList()
                snapshot.forEach { entity ->
                    if (entity.alive && entity.id != heldEntityId) entity.step()
                }

                // Resolve phase — группируем запросы по ИНИЦИАТОРУ (первый реагент) и применяем ОДИН
                // лучший исход на инициатора (объект делает ≤1 реакцию за тик). Так рост и усиление одной
                // молекулы конкурируют в одном resolve() и выбираются по weight. groupBy сохраняет порядок
                // первого появления инициатора → детерминизм.
                // - toList() — снимок, чтобы быть устойчивыми, если runReaction сам положит новый запрос (сейчас не кладёт, но защищаемся).
                val requests = _pendingRequests.toList()
                _pendingRequests.clear()
                requests
                    .groupBy { it.reagents.first().id }
                    .forEach { (_, reqs) -> runReaction(reqs) }

                delay(tickMs)
            }
        }

    }

    /** Просьба очистить холст (переход между уровнями). Выполнится в начале следующего тика. */
    fun requestClear() { _pendingClear = true }

    /** Канва сообщает свой размер. Запоминаем: границы пересчитываются и при смене режима. */
    fun requestArea(widthPx: Float, heightPx: Float) {
        _canvas = CanvasSize(widthPx, heightPx)
        recomputeArea()
    }

    // Level задаёт свой размер мира. Канва при смене уровня та же, onSizeChanged не придёт — считаем сами.
    fun setWorldArea(area: WorldArea) {
        _worldArea = area
        recomputeArea()
    }

    /**
     * Режим + размер канвы → границы. Мир вписан в канву с отступом на радиус атома, чтобы частица
     * у границы оставалась видимой целиком: иначе её можно увести за край холста и уже ничем не достать.
     * Кладём в очередь — применит тик, писатель мира остаётся один.
     */
    private fun recomputeArea() {
        val canvas = _canvas ?: return
        val maxRadiusX = canvas.width / 2f - EDGE_INSET
        val maxRadiusY = canvas.height / 2f - EDGE_INSET
        if (maxRadiusX <= 0f || maxRadiusY <= 0f) return   // канва ещё меньше отступа — ждём следующего замера
        val (radiusX, radiusY) = when (val area = _worldArea) {
            WorldArea.FitCanvas -> maxRadiusX to maxRadiusY
            // Круг уровня ужимаем до вписанного: на узком окне заданный радиус вылез бы за холст.
            is WorldArea.Circle -> minOf(area.radiusPx, maxRadiusX, maxRadiusY).let { it to it }
        }
        _pendingArea = EnvArea(Position(canvas.width / 2f, canvas.height / 2f), radiusX, radiusY)
    }

    /** Уровень выдаёт инвентарь. Холст к этому моменту уже очищен, так что старые остатки не нужны. */
    fun setInventory(inventory: Map<PaletteItem, Int>) {
        palette.clear()
        inventory.forEach { (item, count) -> palette.add(PaletteSlot(item, count)) }
    }

    /** Игрок кладёт из палитры: расходуем остаток и рождаем на холсте то, что в слоте. */
    fun spawnFromPalette(item: PaletteItem, position: Position) {
        val slot = palette.indexOfFirst { it.item == item }
        if (slot < 0 || palette[slot].count <= 0) return
        palette[slot] = palette[slot].copy(count = palette[slot].count - 1)

        val entity = when (item) {
            is PaletteItem.Atom -> spawnAtom(item.element, item.electrons, position)
            is PaletteItem.KnownMolecule -> spawnKnownMolecule(item.knownMoleculeId, position)
        } ?: return
        clampEntityIntoBounds(entity.id)   // дроп в угол холста — внутрь границ
    }

    private fun spawnAtom(element: Element, electrons: Int, position: Position): Entity {
        val energy = if (element == Element.PHOTON) DEFAULT_PHOTON_ENERGY_EV else 0f
        return entityGenerator.createAtom(
            element = element, position = position, direction = randomDirection(random),
            velocity = 0f, energy = energy, environment = environment, electrons = electrons,
        )
    }

    // Игрок перетащил известную молекулу на игровое поле. Нужно ее создать
    private fun spawnKnownMolecule(id: KnownMoleculeId, position: Position): Entity? {
        val knownMolecule = id.details
        val graph = knownMolecule.graph
        val isotopeOf = graph.nodes.associate { it.localId to it.isotope }
        val unitPx = graph.bonds.maxOf { bond ->
            MoleculeGeometry.bondLengthPx(isotopeOf.getValue(bond.atom1), isotopeOf.getValue(bond.atom2), bond.order)
        }
        val atoms = graph.nodes.map { node ->
            val offset = knownMolecule.offsets.getValue(node.localId)
            MoleculeAtom(
                localId = node.localId,
                isotope = node.isotope,
                kinematics = Kinematics(
                    position = Position(position.x + offset.x * unitPx, position.y + offset.y * unitPx),
                    direction = randomDirection(random),
                    velocity = 0f,
                ),
            )
        }
        val bonds = graph.bonds.map { MoleculeBond(it.atom1, it.atom2, it.order, graph.energyOf(it), graph.isRingBond(it)) }
        return entityGenerator.createMolecule(
            shape = MoleculeShape(atoms, bonds),
            energy = 0f,
            environment = environment,
            electrons = graph.nodes.sumOf { it.isotope.details.p },   // молекула нейтральна
        )
    }

    /** Прижать частицу внутрь границ: дроп у самого края и перетаскивание за край холста. */
    fun clampEntityIntoBounds(entityId: Long) {
        (entities.find { it.id == entityId } as? Movable)?.checkBorders(environment)
    }

    private fun clearNow() {
        entities.clear()
        environment.getEnvChildren().toList().forEach { environment.removeEnvChild(it) }
        _pendingRequests.clear()
        moleculeEvents.clear()
        heldEntityId = null
    }

    fun applyForceToEntity(entityId: Long, force: Vec2D) {
        (entities.find { it.id == entityId } as? Movable)?.applyForce(force)
    }

    // Игрок задаёт энергию выбранной частице из панели (энергию фотона). Сеттер клампит её ≥ 0.
    fun setEntityEnergy(entityId: Long, energy: Float) {
        // Редактор энергии в панели открыт только для фотона (см. SelectedEntityPanel), поэтому и здесь
        // сужаем до частицы: у прочих классов энергию игрок не задаёт.
        (entities.find { it.id == entityId } as? SubAtom)?.energy = energy
    }

    // Игрок форсит реакцию у выбранной молекулы из панели (усиление связи / замыкание кольца, механика «лего»)
    fun requestMoleculeAction(entityId: Long, selection: ReactionSelection) {
        val entity = entities.find { it.id == entityId } ?: return
        _pendingRequests.add(ReactionRequest(listOf(entity), selection))
    }

    // Игрок перетаскивает частицу мышью: сдвигаем её на столько, на сколько сдвинулся курсор.
    // Сразу прижимаем к границам: частица «в руке» тиком не шагается, значит сама себя не проверит.
    fun moveEntityBy(entityId: Long, delta: Vec2D) {
        val movable = entities.find { it.id == entityId } as? Movable ?: return
        movable.moveBy(delta)
        movable.checkBorders(environment)
    }

    // Игрок удаляет выбранную частицу с канвы (клавиша Delete). Убиваем через тот же destroy(),
    // что и реакции: onDeath-callback уберёт её из среды и из entities. Если она была «в руке» —
    // снимаем held, чтобы тик не остался с ссылкой на удалённую частицу.
    // Материал возвращается в палитру: инвентарь конечен, и безвозвратное удаление плодило бы тупики.
    fun removeEntity(entityId: Long) {
        val entity = entities.find { it.id == entityId } ?: return
        if (heldEntityId == entityId) heldEntityId = null
        returnToPalette(materialsOf(entity))
        entity.destroy()
    }

    // Из чего частица сделана: атом — сам собой, молекула — своим слотом, если её саму выдавали
    // (иначе перекись вернулась бы атомами, которых на этом уровне в палитре нет), иначе атомами.
    // Звёзды и модули в палитре не выдаются, возвращать нечего.
    private fun materialsOf(entity: Entity): List<PaletteItem> = when (entity) {
        is Atom -> listOf(PaletteItem.Atom(entity.element, entity.electrons))
        is SubAtom -> listOf(PaletteItem.Atom(entity.element, entity.electrons))
        is Molecule -> {
            val asWhole = entity.known?.id?.let(PaletteItem::KnownMolecule)
            if (asWhole != null && palette.any { it.item == asWhole }) listOf(asWhole)
            else entity.atoms.map { PaletteItem.Atom(it.isotope) }
        }
        else -> emptyList()
    }

    // Наращиваем только существующие слоты: палитра — это инвентарь уровня, новых слотов в ней
    // появляться не должно (ион водорода вернётся водородом, а фотон — только если его выдавали).
    private fun returnToPalette(items: List<PaletteItem>) {
        items.forEach { item ->
            val slot = palette.indexOfFirst { it.item == item }
            if (slot >= 0) palette[slot] = palette[slot].copy(count = palette[slot].count + 1)
        }
    }

    /** Сброс задания: холст чистим, инвентарь выдаём заново. Спасает от тупика, когда материал кончился. */
    fun resetLevel(inventory: Map<PaletteItem, Int>) {
        requestClear()
        setInventory(inventory)
    }

    // «Поднять» частицу: помечаем held (тик перестаёт её шагать) и убираем из детей среды,
    // чтобы соседи её не видели — пока в руке, она ни с кем не взаимодействует.
    fun pickUpEntity(entityId: Long) {
        val entity = entities.find { it.id == entityId } ?: return
        heldEntityId = entityId
        entity.getEnvironment().removeEnvChild(entity)
    }

    // «Положить»: переселяем частицу в космос (world.environment) и снимаем held — частица снова
    // взаимодействует. Пересчёт среды по позиции дропа пока упрощён: всегда world.environment,
    // поэтому вытащенная из звезды частица реально остаётся в космосе, а не затягивается обратно.
    fun dropHeldEntity() {
        val id = heldEntityId ?: return
        val entity = entities.find { it.id == id }
        entity?.updateMyEnvironment(environment)
        // z = порядок отрисовки: положенную частицу двигаем в конец списка, чтобы осталась ПОВЕРХ остальных
        // (а не вернулась на свой прежний индекс и под соседей). Побочно: теперь она шагает/инициирует последней.
        if (entity != null) { entities.remove(entity); entities.add(entity) }
        heldEntityId = null
    }

    /**
     * Экспоненциальное сглаживание: новый = α*текущее + (1-α)*предыдущее
     * alpha: 0.05..0.3 — мягкое сглаживание; 0.5 — более «живое».
     */
    fun smoothEma(prev: Float, current: Float, alpha: Float = 0.2f): Float =
        alpha * current + (1f - alpha) * prev



    /**
     * Снимок мира для сохранения. Маппинг «живой мир → DTO».
     * Модули (SpaceModule/RecombinationModule) пока пропускаем. Дерево среды передаём через parentId:
     * если родитель сущности — звезда (она же среда), пишем её id; иначе (корневой Environment) — null.
     */
    fun toSnapshot(): WorldSnapshotDto {
        val saved = entities.toList().filter {it.alive }
        val savedIds = saved.mapTo(mutableSetOf()) { it.id }

        val entityDtos = saved.map { entity ->
            // Родитель-сущность (Star) реализует и Entity, и IEnvironment. Корневой Environment — не Entity.
            // Если родитель не попал в слепок (напр. это модуль) — считаем сущность лежащей в корне (null).
            val parentId = (entity.getEnvironment() as? Entity)?.id?.takeIf { it in savedIds }
            // Кинематика в слепок идёт только у того, кто ею владеет. Молекула сюда и так попадает
            // невосстановимой (пишется формула, а не граф), так что нули её не портят.
            val kinematics = (entity as? Movable)?.kinematics
            EntityDto(
                id = entity.id,
                element = entity.saveKey,
                alive = entity.alive,
                x = kinematics?.position?.x ?: 0f, y = kinematics?.position?.y ?: 0f,
                dirX = kinematics?.direction?.x ?: 0f, dirY = kinematics?.direction?.y ?: 0f,
                velocity = kinematics?.velocity ?: 0f,
                electrons = entity.electrons,
                parentId = parentId,
            )
        }

        val summary = saved
            .groupingBy {
                it.saveKey
            }
            .eachCount()

        return WorldSnapshotDto(
            tick = tick,
            seed = _seed,
            idGenNext = _idGen.peekNext(),
            environment = EnvironmentDto(
                centerX = environment.getEnvCenter().x,
                centerY = environment.getEnvCenter().y,
                radius = environment.getEnvRadius(),
                temperature = environment.getEnvTemperature().name,
            ),
            entities = entityDtos,
            summary = summary,
        )
    }

    /** Слепок мира в виде JSON-строки. */
    fun toJson(): String = WorldJson.encode(toSnapshot())

    /**
     * Сохранить мир в файл. Чтение состояния (toSnapshot) — операция только-на-чтение,
     * поэтому делается прямо здесь, без ожидания тика. Возвращает путь к файлу или null при ошибке.
     */
    fun save(name: String = DEFAULT_SAVE_NAME): String? = try {
        val path = writeSaveFile(name, toJson())
        logs += "${currentTime()}: saved → $path"
        path
    } catch (ex: Exception) {
        logs += "${currentTime()}: save error: ${ex.message}"
        null
    }

    /**
     * Запросить загрузку из файла. Сам слепок применяется в начале следующего тика
     * (см. Load phase в start()) — чтобы мир менял только тик.
     */
    fun load(name: String = DEFAULT_SAVE_NAME) {
        val text = readSaveFile(name)
        if (text == null) {
            logs += "${currentTime()}: load: файл $name не найден"
            return
        }
        _pendingSnapshot = try {
            WorldJson.decode(text)
        } catch (ex: Exception) {
            logs += "${currentTime()}: load: ошибка разбора $name: ${ex.message}"
            null
        }
    }

    /**
     * Применить слепок: полностью заменить текущий мир загруженным. Вызывается из тика
     * (и напрямую из тестов — потому internal, а не private).
     * Пересоздаём сущности в два прохода: сначала все в корневой среде, затем проводим дерево
     * (детей звёзд переносим в их родителя по parentId).
     */
    internal fun applySnapshot(dto: WorldSnapshotDto) {
        // 1. Чистим текущий мир (старые сущности и дети корневой среды — под снос)
        entities.clear()
        environment.getEnvChildren().toList().forEach { environment.removeEnvChild(it) }
        _pendingRequests.clear()

        // 2. Пересоздаём живые сущности с их исходными id; пока все в корневой среде
        val byId = mutableMapOf<Long, Entity>()
        dto.entities.filter { it.alive }.forEach { e ->
            val element = try {
                Element.valueOf(e.element)
            } catch (ex: IllegalArgumentException) {
                logs += "${currentTime()}: load: неизвестный элемент ${e.element}, пропущен"
                return@forEach
            }
            byId[e.id] = entityGenerator.createAtomWithId(
                id = e.id,
                element = element,
                position = Position(e.x, e.y),
                direction = Vec2D(e.dirX, e.dirY),
                velocity = e.velocity,
                // Энергия в слепок не попадает (см. EntityDto). Фотону нельзя дать 0: у него energy это E=hν,
                // и wavelengthNmFromEnergyEv на нуле падает по require — поэтому дефолт из палитры.
                energy = if (element == Element.PHOTON) DEFAULT_PHOTON_ENERGY_EV else 0f,
                environment = environment,
                electrons = e.electrons,
            )
        }

        // 3. Проводим дерево среды: детей переносим из корня в родителя (звезду)
        dto.entities.forEach { e ->
            val parentId = e.parentId ?: return@forEach
            val child = byId[e.id] ?: return@forEach
            val parent = byId[parentId] ?: return@forEach
            child.updateMyEnvironment(parent)
        }

        // 4. Восстанавливаем счётчики
        _idGen.resetTo(dto.idGenNext)
        tick = dto.tick

        logs += "${currentTime()}: world loaded (tick=${dto.tick}, entities=${byId.size})"
    }

    // Все запросы ОДНОГО инициатора за тик. Резолвер выберет один лучший исход по weight.
    fun runReaction(requests: List<ReactionRequest>) {
        // Поднятую частицу исключаем из реагентов (страховка от устаревшего запроса прошлого тика);
        // selection (форс игрока / WeightBased) сохраняем через copy.
        val filtered = requests
            .map { req -> req.copy(reagents = req.reagents.filter { it.id != heldEntityId }) }
            .filter { it.reagents.isNotEmpty() }
        val result = _chemicalReactionResolver.resolve(filtered) ?: return

        // Символы участников ДО применения исхода: consumed сейчас умрут, выжившие — изменятся.
        val survivors = result.updateState.map { it.entity }.distinctBy { it.id } // на одну сущность бывает несколько мутаций
        val before = (result.consumed + survivors).map { it.displaySymbol }

        // Кто из выживших кем БЫЛ до мутации: событие даём только на смену личности, иначе «Вода»
        // всплывала бы на каждое поглощение фотона той же водой.
        val knownBefore = survivors.filterIsInstance<Molecule>().associate { it.id to it.known }

        result.consumed.forEach { it.destroy() }
        val products = result.spawn.map { it() }
        result.updateState.forEach { it.mutate() }

        (products + survivors).filterIsInstance<Molecule>()
            .filter { it.alive && it.known != null && it.known != knownBefore[it.id] }
            .forEach { molecule ->
                moleculeEvents += MoleculeEvent(_eventId++, molecule.known!!.id, molecule.kinematics.position)
            }

        // Символы ПОСЛЕ: у выживших они уже новые (H → H⁺), продукты только что родились.
        val after = survivors.map { it.displaySymbol } + products.map { it.displaySymbol }
        logs += "${currentTime()}: ${result.ruleId}: ${before.joinToString(" + ")} -> ${after.joinToString(" + ")}"
    }

    companion object {
        const val DEFAULT_SAVE_NAME = "world.json"
    }
}

fun currentTime(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val h = now.hour.toString().padStart(2, '0')
    val m = now.minute.toString().padStart(2, '0')
    val s = now.second.toString().padStart(2, '0')
    return "$h:$m:$s"
}
