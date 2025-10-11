package maratmingazovr.ai.carsonella

interface IEnvironment {
    fun getCenter(): Position
    fun getRadius() : Float
    fun getTemperature() : Float

    fun setCenter(position: Position)
    fun setRadius(radius: Float)
    fun setTemperature(temperature: Float)
}

class Environment(
    private var center: Position,
    private var radius: Float,
    private var temperature: Float,
) : IEnvironment {
    override fun getCenter() = center
    override fun getRadius() = radius
    override fun getTemperature() = temperature

    override fun setCenter(position: Position) { this.center = position }
    override fun setRadius(radius: Float) { this.radius = radius }
    override fun setTemperature(temperature: Float) { this.temperature = temperature }

}