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

/**
 * Здесь мы храним просто текст на разных языках. Чтобы потом в зависимости от языка игры подставить текст на нужной языке
 */
data class Description(val ru: String, val en: String) {
    fun of(lang: Lang): String = when (lang) {
        Lang.RU -> ru
        Lang.EN -> en
    }
}