package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.Entity

/**
 * Элементы не могут существовать вне среды.
 * Потом нужно будетрешить, что происходит с элементами, когда среда уничтожается
 */
interface IEnvironment {
    fun getEnvCenter(): Position
    fun getEnvRadius(): Float

    /** Вертикальный радиус: по умолчанию среда круглая (звезда), корневой мир — эллипс по канве. */
    fun getEnvRadiusY(): Float = getEnvRadius()
    fun getEnvTemperature(): TemperatureMode
    fun getEnvChildren(): List<Entity>
    fun addEnvChild(entity: Entity)
    fun removeEnvChild(entity: Entity)
}

/**
 * Насколько точка вышла за границу среды: `>1` — снаружи, `1` — ровно на кромке, `<1` — внутри.
 * Мера эллиптическая (у круглой среды сводится к обычной окружности); `sqrt` от неё — тот множитель,
 * которым точку возвращают на границу. Одна формула на всех, кто про границу спрашивает.
 * Границы ещё не заданы (канва не измерена) — считаем, что снаружи никого нет.
 */
fun IEnvironment.outsideFactor(point: Position): Float {
    val radiusX = getEnvRadius()
    val radiusY = getEnvRadiusY()
    if (radiusX <= 0f || radiusY <= 0f) return 0f
    val center = getEnvCenter()
    val dx = point.x - center.x
    val dy = point.y - center.y
    return (dx / radiusX) * (dx / radiusX) + (dy / radiusY) * (dy / radiusY)
}

class Environment(
    private var center: Position = Position(0f, 0f),
    private var radius: Float = 0f,
    private var temperature: TemperatureMode = TemperatureMode.Space,
    private var children: MutableList<Entity> = mutableListOf(),
) : IEnvironment {
    private var radiusY: Float = radius

    override fun getEnvCenter() = center
    override fun getEnvRadius() = radius
    override fun getEnvRadiusY() = radiusY

    /** Границы мира — эллипс, вписанный в холст; зовётся из тика, когда канва знает свой размер. */
    fun setEnvArea(center: Position, radiusX: Float, radiusY: Float) {
        this.center = center
        this.radius = radiusX
        this.radiusY = radiusY
    }
    override fun getEnvTemperature() = temperature
    override fun getEnvChildren(): List<Entity> { return children }
    override fun addEnvChild(entity: Entity) { children.add(entity) }
    override fun removeEnvChild(entity: Entity) { children.remove(entity) }
}

enum class TemperatureMode { Space, Star }