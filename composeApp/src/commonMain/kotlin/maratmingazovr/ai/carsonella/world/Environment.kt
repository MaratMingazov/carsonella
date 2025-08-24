package maratmingazovr.ai.carsonella.world

import maratmingazovr.ai.carsonella.IEnvironment

class Environment(
    private var worldWidth: Float,
    private var worldHeight: Float,
    private var temperature: Float,
) : IEnvironment {

    fun setWorldWidth(width: Float) { this.worldWidth = width }

    fun setWorldHeight(height: Float) { this.worldHeight = height }

    override fun getWorldWidth() = worldWidth

    override fun getWorldHeight() = worldHeight

    override fun getTemperature() = temperature

    override fun setTemperature(temperature: Float) {
        this.temperature = temperature
    }

}