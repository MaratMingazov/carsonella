package maratmingazovr.ai.carsonella.chemistry.behavior


/**
 * C помощью этого поведения объект может сообщить внешнему миру о том, что он умер
 */
interface DeathNotifiable {
    fun setOnDeath(callback: () -> Unit)
    fun notifyDeath() // вызвать callback
}

class OnDeathSupport : DeathNotifiable {

    /**
     * Мы тут указываем какой метод должен вызвать объект, когда он умирает
     * По умолчанию, он вызывает пустую лямду, ничего не делает
     */
    private var onDeath: () -> Unit = {}

    /**
     * Здесь внешний мир может указать какой метод должен вызывать объект, когда умрет
     */
    override fun setOnDeath(callback: () -> Unit) { onDeath = callback }

    /**
     * Здесь объект во время смерти вызывает этот метод
     */
    override fun notifyDeath() { onDeath() }
}