package maratmingazovr.ai.carsonella.world


import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Entity
import kotlin.math.ceil
import kotlin.math.floor


class Grid2D<T : Entity<*>>(
    private val gridBoxSize: Float
) {
    // Зная ячейку в сетке можем получить список сущностей в этой ячейке
    private val grid = HashMap<GridBox, MutableSet<T>>()
    private val mutex = Mutex()

    suspend fun put(entity: T) = mutex.withLock {
        // определяем в какую ячейку должен попасть наш объект
        val entityGridBox = gridBoxOf(entity.state().value.position)

        // получим список объектов в этой ячейке и добавим туда наш объект
        val bucket = grid.getOrPut(entityGridBox) { mutableSetOf() }
        bucket += entity
    }

    /**
     * Удаляет объект из ячейки и при необходимости убирает пустую ячейку.
     */
    suspend fun remove(entity: T) = mutex.withLock {
        val entityGridBox = gridBoxOf(entity.state().value.position)
        grid[entityGridBox]?.remove(entity)
        if (grid[entityGridBox]?.isEmpty() == true) grid.remove(entityGridBox)
    }

    /**
     * Перемещает объект из старой ячейки в новую, только если он вышел за её пределы.
     */
    suspend fun move(entity: T, oldPosition: Position) = mutex.withLock {
        val entityGridBoxOld = gridBoxOf(oldPosition)
        val entityGridBoxNew = gridBoxOf(entity.state().value.position)

        if (entityGridBoxOld == entityGridBoxNew) return@withLock

        grid[entityGridBoxOld]?.remove(entity)
        if (grid[entityGridBoxOld]?.isEmpty() == true) grid.remove(entityGridBoxOld)

        put(entity)
    }

    /**
     * Ищет объекты вокруг позиции нашего объекта в радиусе r в пикселях.
     * Сначала переводит радиус из пикселей в радиус в ячейках (rGridBox).
     * Берёт все объекты из ячеек в этом диапазоне.
     */
    suspend fun findEntitiesAround(entity: T, r: Float): List<T> = mutex.withLock {
        val entityGridBox = gridBoxOf(entity.state().value.position)
        val rGridBox = ceil(r / gridBoxSize).toInt().coerceAtLeast(1)
        buildList {
            for (gridBoxAround in gridBoxesAround(entityGridBox, rGridBox)) {
                grid[gridBoxAround]?.let { addAll(it) }
            }
        }
    }

    /**
     * 	Вход: позиция нашего объекта в пикселях и размер одной ячейки (gridBoxSize).
     * 	Выход: целочисленные координаты ячейки, в которой находится объект.
     */
    private fun gridBoxOf(position: Position) = GridBox(floor(position.x / gridBoxSize).toInt(), floor(position.y / gridBoxSize).toInt())

    /**
     * Мы тут для заданной ячейки ищем определяем ячейки вокруг
     * Вход: это нужная нам ячейка и радиус в ячейках
     * Выход: список ячеек вокруг
     * Пример: если gridBox = (5, 10) и rGridBox = 1, то вернутся 9 ячеек:
     * (4,9) (5,9) (6,9)
     * (4,10) (5,10) (6,10)
     * (4,11) (5,11) (6,11)
     */
    private fun gridBoxesAround(gridBox: GridBox, rGridBox: Int): List<GridBox> {
        val result = mutableListOf<GridBox>()
        for (di in -rGridBox..rGridBox) {
            for (dj in -rGridBox..rGridBox) {
                result.add(GridBox(gridBox.i + di, gridBox.j + dj))
            }
        }
        return result
    }
}

/**
 * Это координаты ячейки сетки, а не пикселей.
 * Например, если мир разбит на квадраты по cellSize = 100 px, то атом в позиции (250, 430) окажется в ячейке:
 * 		i = floor(250 / 100) = 2
 * 		j = floor(430 / 100) = 4
 */
data class GridBox(val i: Int, val j: Int) {


}