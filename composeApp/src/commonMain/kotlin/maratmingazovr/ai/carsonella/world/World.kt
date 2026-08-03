package maratmingazovr.ai.carsonella.world

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maratmingazovr.ai.carsonella.Environment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Entity
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

class World(
    private val _scope: CoroutineScope,
) {

    private val _idGen: IdGenerator = IdGenerator()
    private val _pendingRequests = mutableListOf<ReactionRequest>()
    private val _seed = 1L
    val random = kotlin.random.Random(_seed)
    val environment = Environment(Position(1000f, 600f), 500f, TemperatureMode.Space)
    val palette =  mutableStateListOf(
        Element.PHOTON,
        Element.ELECTRON,
        Element.HYDROGEN,
        Element.OXYGEN_16,
        Element.CARBON_12,
    )
    val entities =  mutableStateListOf<Entity>()
    val logs =  mutableStateListOf<String>()
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

                tick++

                // снимок, чтобы не падать на ConcurrentModificationException
                // если кто-то добавит сущность во время шага
                val snapshot = entities.toList()
                snapshot.forEach { entity ->
                    if (entity.state().value.alive && entity.id != heldEntityId) entity.step()
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

    fun applyForceToEntity(entityId: Long, force: Vec2D) {
        entities.find { it.id == entityId }?.run { applyForce(force) }
    }

    // Игрок задаёт энергию выбранной частице из панели (например, энергию фотона).
    // setEnergy клампит энергию ≥ 0. Прямая мутация из UI — как и applyForceToEntity/moveEntityTo.
    fun setEntityEnergy(entityId: Long, energy: Float) {
        entities.find { it.id == entityId }?.setEnergy(energy)
    }

    // Игрок форсит реакцию у выбранной молекулы из панели (усиление связи / замыкание кольца, механика
    // «лего»): кладём forced-запрос напрямую в очередь — resolve выполнит именно это правило, минуя
    // weight-конкуренцию (см. ReactionSelection). Тот же диспетчер, что у тика (Compose Main) → без гонки,
    // как setEntityEnergy/moveEntityTo. Реагент — сама молекула (self-request).
    fun requestMoleculeAction(entityId: Long, selection: ReactionSelection) {
        val entity = entities.find { it.id == entityId } ?: return
        _pendingRequests.add(ReactionRequest(listOf(entity), selection))
    }

    // Игрок перетаскивает частицу мышью: ставим её в указанную точку.
    // Так можно вручную свести e⁻ к иону → на следующем тике сработает рекомбинация.
    fun moveEntityTo(entityId: Long, position: Position) {
        entities.find { it.id == entityId }?.moveTo(position)
    }

    // Игрок удаляет выбранную частицу с канвы (клавиша Delete). Убиваем через тот же destroy(),
    // что и реакции: onDeath-callback уберёт её из среды и из entities. Если она была «в руке» —
    // снимаем held, чтобы тик не остался с ссылкой на удалённую частицу.
    fun removeEntity(entityId: Long) {
        val entity = entities.find { it.id == entityId } ?: return
        if (heldEntityId == entityId) heldEntityId = null
        entity.destroy()
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
        val saved = entities.toList().filter {it.state().value.alive }
        val savedIds = saved.mapTo(mutableSetOf()) { it.id }

        val entityDtos = saved.map { entity ->
            val entityState = entity.state().value
            // Родитель-сущность (Star) реализует и Entity, и IEnvironment. Корневой Environment — не Entity.
            // Если родитель не попал в слепок (напр. это модуль) — считаем сущность лежащей в корне (null).
            val parentId = (entity.getEnvironment() as? Entity)?.id?.takeIf { it in savedIds }
            EntityDto(
                id = entity.id,
                element = entity.saveKey,
                alive = entityState.alive,
                x = entityState.kinematics.position.x, y = entityState.kinematics.position.y,
                dirX = entityState.kinematics.direction.x, dirY = entityState.kinematics.direction.y,
                velocity = entityState.kinematics.velocity,
                energy = entityState.energy,
                electrons = entityState.electrons,
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
            byId[e.id] = entityGenerator.createEntityWithId(
                id = e.id,
                element = element,
                position = Position(e.x, e.y),
                direction = Vec2D(e.dirX, e.dirY),
                velocity = e.velocity,
                energy = e.energy,
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

        result.consumed.forEach { it.destroy() }
        val products = result.spawn.map { it() }
        result.updateState.forEach { it.mutate() }

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
