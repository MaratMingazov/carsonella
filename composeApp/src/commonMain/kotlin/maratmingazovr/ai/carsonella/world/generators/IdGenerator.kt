package maratmingazovr.ai.carsonella.world.generators

import androidx.compose.runtime.mutableStateOf

class IdGenerator() {
    private var current = mutableStateOf(0L)

    fun nextId(): Long {
        return current.value++
    }
}