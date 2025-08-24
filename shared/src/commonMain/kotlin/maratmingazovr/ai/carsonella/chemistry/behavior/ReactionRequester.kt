package maratmingazovr.ai.carsonella.chemistry.behavior

import maratmingazovr.ai.carsonella.chemistry.Entity

interface ReactionRequester {
    /** Мир устанавливает обработчик: что делать, когда объект хочет вступить в реакцию с другим. */
    fun setRequestReaction(handler: (List<Entity<*>>) -> Unit)

    /** Вызвать запрос реакции: "я хочу среагировать с other". */
    fun requestReaction(reagents: List<Entity<*>>)
}

class ReactionRequestSupport : ReactionRequester {
    private var handler: (List<Entity<*>>) -> Unit = { _ -> } // пустой обработчик

    override fun setRequestReaction(handler: (List<Entity<*>>) -> Unit) {
        this.handler = handler
    }

    override fun requestReaction(reagents: List<Entity<*>>) {
        handler(reagents)
    }
}