package maratmingazovr.ai.carsonella

interface IEnvironment {
    fun getCenter(): Position
    fun getRadius() : Float
    fun getTemperature() : Float

    fun setCenter(position: Position)
    fun setRadius(radius: Float)
    fun setTemperature(temperature: Float)
}

class EnvironmentDefault : IEnvironment {
    override fun getCenter() = Position(0f, 0f)
    override fun getRadius() = 0f
    override fun getTemperature() = 0f

    override fun setCenter(position: Position) {}
    override fun setRadius(radius: Float) {}
    override fun setTemperature(temperature: Float) {}
}