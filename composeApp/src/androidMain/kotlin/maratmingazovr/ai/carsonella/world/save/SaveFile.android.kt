package maratmingazovr.ai.carsonella.world.save

// Заглушка: позже — запись в файлы приложения через Context.
actual fun writeSaveFile(name: String, content: String): String =
    "android: сохранение в файл пока не поддержано ($name)"

actual fun readSaveFile(name: String): String? = null