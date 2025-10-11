package maratmingazovr.ai.carsonella.world

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position

class Environment(
    private var center: Position,
    private var radius: Float,
    private var temperature: Float,
) : IEnvironment {
    override fun getCenter() = center
    override fun getRadius() = radius
    override fun getTemperature() = temperature

    override fun setCenter(position: Position) {
        this.center = position
    }

    override fun setRadius(radius: Float) {
        this.radius = radius
    }

    override fun setTemperature(temperature: Float) {
        this.temperature = temperature
    }

}