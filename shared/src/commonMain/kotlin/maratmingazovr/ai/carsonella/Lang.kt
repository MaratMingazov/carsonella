package maratmingazovr.ai.carsonella

/**
 * Язык интерфейса и контента. Базовый — [EN].
 *
 * Почему свой перечисляемый тип, а не локаль Compose: в compose-resources подмена локали закрыта
 * (ComposeEnvironment и конструктор ResourceEnvironment объявлены internal, «overridden for tests»), и
 * выбрать язык из приложения нельзя — только следовать системному. Нам нужен переключатель внутри игры.
 *
 * [tag] — код BCP 47, понадобится при сохранении выбора и подборе по системному языку.
 * [ownName] — название языка на самом этом языке: такие подписи не переводят.
 */
enum class Lang(val tag: String, val ownName: String) {
    EN("en", "English"),
    RU("ru", "Русский"),
}