package maratmingazovr.ai.carsonella

interface IEnvironment {
    fun getCenter(): Position
    fun getRadius() : Float
    fun getTemperature() : Float

    fun setCenter(position: Position)
    fun setRadius(radius: Float)
    fun setTemperature(temperature: Float)
}