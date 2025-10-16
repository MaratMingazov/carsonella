package maratmingazovr.ai.carsonella

import maratmingazovr.ai.carsonella.chemistry.Entity

/**
 * Элементы не могут существовать вне среды.
 * Потом нужно будетрешить, что происходит с элементами, когда среда уничтожается
 */
interface IEnvironment {
    fun getEnvCenter(): Position
    fun getEnvRadius(): Float
    fun getEnvTemperature(): TemperatureMode
    fun getEnvChildren(): List<Entity<*>>
    fun addEnvChild(entity: Entity<*>)
    fun removeEnvChild(entity: Entity<*>)
}

class Environment(
    private var center: Position = Position(0f, 0f),
    private var radius: Float = 0f,
    private var temperature: TemperatureMode = TemperatureMode.Space,
    private var children: MutableList<Entity<*>> = mutableListOf(),
) : IEnvironment {
    override fun getEnvCenter() = center
    override fun getEnvRadius() = radius
    override fun getEnvTemperature() = temperature
    override fun getEnvChildren(): List<Entity<*>> { return children }
    override fun addEnvChild(entity: Entity<*>) { children.add(entity) }
    override fun removeEnvChild(entity: Entity<*>) { children.remove(entity) }
}

enum class TemperatureMode { Space, Star }