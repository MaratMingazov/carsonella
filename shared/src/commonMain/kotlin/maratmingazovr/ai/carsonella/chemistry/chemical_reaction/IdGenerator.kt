package maratmingazovr.ai.carsonella.chemistry.chemical_reaction

// Обычный var, не mutableStateOf: счётчик читают только сохранение и загрузка, из композиции — никто.
class IdGenerator {
    private var current = 0L

    fun nextId(): Long = current++

    /** Следующий id, который выдаст nextId() — для сохранения состояния генератора. */
    fun peekNext(): Long = current

    /** Восстановить счётчик при загрузке сохранения — чтобы новые id не конфликтовали с загруженными. */
    fun resetTo(value: Long) { current = value }
}