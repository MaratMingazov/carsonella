package maratmingazovr.ai.carsonella

// Язык интерфейса и контента. Базовый — EN.
enum class Lang(val tag: String, val ownName: String) {
    EN("en", "English"),
    RU("ru", "Русский"),
}

/**
 * Здесь мы храним просто текст на разных языках. Чтобы потом в зависимости от языка игры подставить текст на нужной языке
 */
data class TranslatedText(val ru: String, val en: String) {
    fun of(lang: Lang): String = when (lang) {
        Lang.RU -> ru
        Lang.EN -> en
    }
}