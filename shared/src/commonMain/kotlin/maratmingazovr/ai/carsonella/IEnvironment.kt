package maratmingazovr.ai.carsonella

/**
 * Элементы не могут существовать вне среды.
 * Потом нужно будетрешить, что происходит с элементами, когда среда уничтожается
 */
interface IEnvironment {
    fun getEnvCenter(): Position
    fun getEnvRadius(): Float
    fun getEnvTemperature(): Float
}

class Environment(
    private var center: Position,
    private var radius: Float,
    private var temperature: Float,
) : IEnvironment {
    override fun getEnvCenter() = center
    override fun getEnvRadius() = radius
    override fun getEnvTemperature() = temperature
}