package maratmingazovr.ai.carsonella

import kotlin.random.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val random = Random(1)

data class Position(val x: Float, val y: Float) {
    operator fun plus(p: Position) = Position(x + p.x, y + p.y)
    fun moveRandomly() = Position(x = x +  random.nextInt(-3, 3), y = y + random.nextInt(-3, 3))
    fun addVelocity(velocity: Vec2D) = Position(x = x + velocity.x, y = y + velocity.y)
    fun toPixels(scale: Float) = Position(x = x * scale, y = y * scale)
    fun distanceSquareTo(other: Position): Float {
        val dx = other.x - x
        val dy = other.y - y
        return dx * dx + dy * dy
    }

    // эффект дрожания
    // мы возвращаем новую позицию рядом с текущей
    fun jitter(): Position {
        return Position(
            x = x + random.nextInt(-1, 1),
            y = y + random.nextInt(-1, 1)
        )
    }
}

data class Vec2D(var x: Float, var y: Float) {
    operator fun plus(v: Vec2D) = Vec2D(x + v.x, y + v.y)
    operator fun times(k: Float) = Vec2D(x * k, y * k)
    fun addInPlace(v: Vec2D) { x += v.x; y += v.y }
    fun scaleInPlace(k: Float) { x *= k; y *= k }
    fun divInPlace(k: Float) {
        require(k != 0f) { "Division by zero" }
        x /= k; y /= k
    }
    fun div(k: Float) : Vec2D {
        require(k != 0f) { "Division by zero" }
        return Vec2D(x / k, y / k)
    }

    fun scaleTo(newLength: Float) {
        val l = length()
        if (l > 1e-12) { // чтобы не делить на ноль
            val factor = newLength / l
            x *= factor
            y *= factor
        } else {
            // если длина ≈ 0 — можно, например, задать случайное направление
            val angle = random.nextFloat() * 2.0 * PI
            x = (cos(angle) * newLength).toFloat()
            y = (sin(angle) * newLength).toFloat()
        }
    }
    fun length() = kotlin.math.sqrt(x*x + y*y)
    fun normalizeInPlace() { val L = length(); if (L > 1e-6f) { x /= L; y /= L } }
    companion object {
        fun fromAngle(angleRad: Float, r: Float) = Vec2D(kotlin.math.cos(angleRad) * r, kotlin.math.sin(angleRad) * r)
    }
}

fun randomDirection(): Vec2D {
    val angle = random.nextDouble(0.0, 2 * PI)
    return Vec2D(cos(angle).toFloat(), sin(angle).toFloat())
}

fun chance(probability: Float): Boolean {
    require(probability in 0f..1f) { "Вероятность должна быть от 0f до 1f" }
    return Random.nextFloat() < probability
}