package maratmingazovr.ai.carsonella

interface IEnvironment {
    fun getWorldWidth() : Float
    fun getWorldHeight() : Float
    fun getTemperature() : Float

    fun setTemperature(temperature: Float)
}

class EnvironmentDefault : IEnvironment {
    override fun getWorldWidth() = 0f
    override fun getWorldHeight() = 0f
    override fun getTemperature() = 0f
    override fun setTemperature(temperature: Float) {}
}