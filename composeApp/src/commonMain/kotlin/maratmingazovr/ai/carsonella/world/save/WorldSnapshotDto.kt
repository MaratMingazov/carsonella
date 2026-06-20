package maratmingazovr.ai.carsonella.world.save

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Сериализуемый слепок мира (DTO). Намеренно отделён от живой доменной модели:
 * живые Entity держат MutableStateFlow, делегаты поведений и лямбды — их сериализовать нельзя.
 * Тут — только плоские данные. Маппинг «живой мир → DTO» в [maratmingazovr.ai.carsonella.world.World.toSnapshot].
 *
 * SpaceModule/RecombinationModule пока НЕ сохраняем (см. план) — в слепок не попадают.
 */
@Serializable
data class WorldSnapshotDto(
    val version: Int = 1,        // версия формата — на будущее, если поля поменяются
    val tick: Long,              // «время симуляции» на момент сохранения
    val seed: Long,              // сид RNG (для информации; точное состояние RNG не восстанавливается)
    val idGenNext: Long,         // следующий id, чтобы новые сущности после загрузки не конфликтовали
    val environment: EnvironmentDto,
    val entities: List<EntityDto>,
    val summary: Map<String, Int>, // имя элемента → число живых сущностей; для быстрого анализа «что образовалось»
)

@Serializable
data class EnvironmentDto(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val temperature: String,     // TemperatureMode.name
)

@Serializable
data class EntityDto(
    val id: Long,
    val element: String,         // Element.name (читаемое имя enum — удобно для анализа JSON)
    val alive: Boolean,
    val x: Float,
    val y: Float,
    val dirX: Float,
    val dirY: Float,
    val velocity: Float,
    val energy: Float,
    val electrons: Int,
    val parentId: Long? = null,  // id родительской среды (Star); null = корневой Environment
)

/** Единая точка кодирования/декодирования слепка в JSON. */
object WorldJson {
    private val json = Json {
        prettyPrint = true       // человекочитаемо — чтобы файл удобно было анализировать глазами и инструментами
        encodeDefaults = true
    }

    fun encode(dto: WorldSnapshotDto): String = json.encodeToString(dto)
    fun decode(text: String): WorldSnapshotDto = json.decodeFromString(text)
}