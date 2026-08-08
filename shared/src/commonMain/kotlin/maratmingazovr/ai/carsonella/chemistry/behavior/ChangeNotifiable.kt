package maratmingazovr.ai.carsonella.chemistry.behavior

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * C помощью этого поведения объект может сообщить внешнему миру о том, что он изменился
 */
interface ChangeNotifiable {
    val changes: StateFlow<Long> // Монотонный счётчик изменений, сигнал к тому, что сущность нужно перерисовать
    fun markChanged() // Сказать UI, что сущность поменялась и нужно перерисовать
}

class ChangeSupport : ChangeNotifiable {

    private val _changes = MutableStateFlow(0L)

    /**
     * Наружу поток отдаётся только на чтение: двигать счётчик имеет право один markChanged
     */
    override val changes: StateFlow<Long> get() = _changes

    /**
     * Здесь объект вызывает этот метод, когда хочет сказать, что поменялся
     */
    override fun markChanged() = _changes.update { it + 1 }

}