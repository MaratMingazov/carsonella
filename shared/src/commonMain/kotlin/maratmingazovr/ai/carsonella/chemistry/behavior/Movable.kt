package maratmingazovr.ai.carsonella.chemistry.behavior

import maratmingazovr.ai.carsonella.IEnvironment
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Kinematics
import kotlin.math.sqrt

interface Movable {

    val kinematics: Kinematics
    val mass: Float // нужна здесь ради applyForce: a = F/m

    fun applyNewPosition()
    fun moveTo(position: Position) // Прямое перемещение (игрок «берёт и кладёт»): скорость обнуляется, чтобы не улетело по инерции
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

    override fun moveTo(position: Position) {
        update(kinematics.copy(position = position, velocity = 0f))
    }

    override fun reduceVelocity() {
        val newVelocity = if (kinematics.velocity < 0.1f) 0f else kinematics.velocity * 0.99f
        update(kinematics.copy(velocity = newVelocity))
    }

    override fun checkBorders(env: IEnvironment) {

        var position = kinematics.position
        var direction = kinematics.direction
        val center = env.getEnvCenter()
        val radius = env.getEnvRadius()

        // Вектор от центра круга к объекту
        val dx = position.x - center.x
        val dy = position.y - center.y


        if (dx * dx + dy * dy > radius * radius) {
            // Расстояние от центра
            val dist = sqrt(dx * dx + dy * dy)
            // Если снаружи — нормализуем вектор и перемещаем на границу круга
            val nx = dx / dist
            val ny = dy / dist
            position =  Position(x = center.x + nx * radius, y = center.y + ny * radius)

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