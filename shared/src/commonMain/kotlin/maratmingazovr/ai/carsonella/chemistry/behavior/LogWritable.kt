package maratmingazovr.ai.carsonella.chemistry.behavior

/**
 * C помощью этого поведения объект может сообщить внешнему миру лог о том, что у него произошло
 */
interface LogWritable {
    fun setLogger(callback: (log: String) -> Unit)
    fun writeLog(log: String)
}

class LoggingSupport : LogWritable {

    private var logger: (String) -> Unit = { _, -> }

    override fun setLogger(callback: (log: String) -> Unit) {
        logger = callback
    }

    /**
     * Здесь объект вызывает этот метод, когда хочет сообщить лог
     */
    override fun writeLog(log: String) {
        logger(log)
    }

}