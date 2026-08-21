package maratmingazovr.ai.carsonella.chemistry.behavior

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.outsideFactor
import maratmingazovr.ai.carsonella.chemistry.Kinematics
import kotlin.math.sqrt

interface Movable {

    val kinematics: Kinematics
    val mass: Float // нужна здесь ради applyForce: a = F/m

    fun applyNewPosition()
    fun moveBy(delta: Vec2D)
    fun reduceVelocity()
    fun checkBorders(env: IEnvironment)
    fun applyForce(force: Vec2D)
}

class PointMovement(
    position: Position,
    direction: Vec2D,
    velocity: Float,
    override val mass: Float,
) : Movable {

    private var onChange: () -> Unit = {}

    fun setOnChange(callback: () -> Unit) { onChange = callback }

    override var kinematics: Kinematics = Kinematics(position, direction, velocity)
        private set

    private fun update(value: Kinematics) {
        if (kinematics == value) return
        kinematics = value
        onChange()
    }

    override fun applyNewPosition() {
        val newPosition = Position(
            x = kinematics.position.x + kinematics.direction.x * kinematics.velocity,
            y = kinematics.position.y + kinematics.direction.y * kinematics.velocity
        )
        update(kinematics.copy(position = newPosition))
    }

    override fun moveBy(delta: Vec2D) {
        update(kinematics.copy(position = kinematics.position.addVelocity(delta), velocity = 0f))
    }

    override fun reduceVelocity() {
        val newVelocity = if (kinematics.velocity < 0.01f) 0f else kinematics.velocity * 0.99f
        update(kinematics.copy(velocity = newVelocity))
    }

    override fun checkBorders(env: IEnvironment) {

        var position = kinematics.position
        var direction = kinematics.direction
        val center = env.getEnvCenter()
        val radiusX = env.getEnvRadius()
        val radiusY = env.getEnvRadiusY()
        if (radiusX <= 0f || radiusY <= 0f) return   // границы ещё не заданы (канва не измерена)

        val dx = position.x - center.x
        val dy = position.y - center.y
        val outside = env.outsideFactor(position)

        if (outside > 1f) {
            // Ставим на границу по тому же лучу из центра
            val k = sqrt(outside)
            position = Position(x = center.x + dx / k, y = center.y + dy / k)

            // Нормаль к эллипсу в этой точке: (dx/rx², dy/ry²) — у окружности это тот же радиус-вектор
            var nx = dx / (radiusX * radiusX)
            var ny = dy / (radiusY * radiusY)
            val normalLength = sqrt(nx * nx + ny * ny)
            if (normalLength > 1e-6f) { nx /= normalLength; ny /= normalLength }

            // Отразить направление относительно нормали
            val dot = direction.x * nx + direction.y * ny
            direction = direction.copy(x = direction.x - 2 * dot * nx, y = direction.y - 2 * dot * ny)
        }

        update(kinematics.copy(position = position, direction = direction))
    }

    override fun applyForce(force: Vec2D) {

        if (mass < 0.001f) return
        val a = force.div(mass)
        val newVelocityVector = kinematics.direction.times(kinematics.velocity).plus(a)
        val newVelocity = newVelocityVector.length()
        val newDirection = if (newVelocity > 0) newVelocityVector.div(newVelocity) else kinematics.direction

        update(kinematics.copy(direction = newDirection, velocity = newVelocity))
    }
}