package maratmingazovr.ai.carsonella.chemistry.behavior

import maratmingazovr.ai.carsonella.chemistry.Entity


/**
 * Это поведение позволяет объекту узнавать о том, какие другие объекты окружаются его во внешнем мире
 *
 */
interface NeighborsAware {

    /**
     *  Мир передаёт провайдер актуального списка сущностей.
     */
    fun setNeighbors(provider: () -> List<Entity<*>>)

    /**
     * Получить свежий список соседей на текущий момент.
     * */
    fun getNeighbors(): List<Entity<*>>
}

class NeighborsSupport : NeighborsAware {
    /**
     * По умолчанию никого нет
     */
    private var provider: () -> List<Entity<*>> = { emptyList() }


    override fun setNeighbors(provider: () -> List<Entity<*>>) {

        /**
         * Оборачиваем в снапшот, чтобы избежать ConcurrentModificationException
         *
         * Если не сделать provider().toList(), то потом во время обхода списка
         * for (a in provider()) {
         *     atoms.remove(a) // BOOM: ConcurrentModificationException
         * }
         * Потому что кто-то другой тоже может менять этот список
         *
         * Почему нужно оборачивать в лямбду {provider().toList()} ?
         * Это нужно, чтобы при каждом обращении брать новый список
         * Если сделать просто this.provider = provider().toList()
         * То список возьмется только один раз в момент первого обращения
         */
        this.provider = { provider().toList() }
    }

    override fun getNeighbors(): List<Entity<*>> = provider()
}