package maratmingazovr.ai.carsonella

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Каталог строк интерфейса: константа — ключ, параметры — переводы. Оба языка на одной строке, поэтому
 * пропуск или расхождение видно глазом, а новый язык компилятор потребует у каждой константы.
 *
 * Не XML и не compose-resources: там локаль подменить снаружи нельзя, см. [Lang].
 *
 * Сюда идут только константные строки. Множественные числа и подстановки — отдельными функциями ниже:
 * им нужен код на каждый язык, а не колонка в конструкторе.
 */
enum class UiString(val en: String, val ru: String) {
    MENU_PLAY("play", "играть"),
    MENU_MAP("map", "карта"),
    MENU_LANGUAGE("language", "язык"),
    MENU_ABOUT("about", "об игре"),
    MENU_BACK("back", "назад"),
    MENU_CLOSE("close", "закрыть"),

    MAP_TITLE("map", "карта"),

    WELCOME_TITLE("Welcome to Carsonella", "Добро пожаловать в Carsonella"),
    WELCOME_START("start", "начать"),

    REWARD_CAPTION("molecule obtained", "получена молекула"),
    REWARD_CAPTION_ATOM("atom obtained", "получен атом"),
    REWARD_NEXT("next", "дальше"),

    CHAPTER_DONE_TITLE("well done", "молодец"),
    CHAPTER_DONE_TEXT("chapter one levels are complete", "уровни первой главы пройдены"),

    ABOUT_TITLE("about", "об игре"),
    ABOUT_TEXT("soon...", "скоро..."),
    LANGUAGE_TITLE("language", "язык"),
    ;

    fun of(lang: Lang): String = when (lang) {
        Lang.EN -> en
        Lang.RU -> ru
    }
}

/** Выбранный язык. Кладёт в композицию [App], меняет экран языка. */
val LocalLang = staticCompositionLocalOf { Lang.EN }

/** Строка текущего языка — короткая запись для разметки: `Text(t(MENU_PLAY))`. */
@Composable
@ReadOnlyComposable
fun text(string: UiString): String = string.of(LocalLang.current)

@Composable
@ReadOnlyComposable
fun text(text: TranslatedText): String = text.of(LocalLang.current)

//@Composable
//@ReadOnlyComposable
//fun text(text: TranslatedText?): String? = text?.of(LocalLang.current)