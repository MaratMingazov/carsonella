package maratmingazovr.ai.carsonella

/**
 * Элементы не могут существовать вне среды.
 * Потом нужно будетрешить, что происходит с элементами, когда среда уничтожается
 */
interface IEnvironment {
    fun getEnvCenter(): Position
    fun getEnvRadius(): Float
    fun getEnvTemperature(): TemperatureMode
}

class Environment(
    private var center: Position,
    private var radius: Float,
    private var temperature: TemperatureMode,
) : IEnvironment {
    override fun getEnvCenter() = center
    override fun getEnvRadius() = radius
    override fun getEnvTemperature() = temperature
}

enum class TemperatureMode { Space, Star }