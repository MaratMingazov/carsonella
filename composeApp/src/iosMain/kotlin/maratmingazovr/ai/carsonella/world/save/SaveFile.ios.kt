package maratmingazovr.ai.carsonella.world.save

// Заглушка: позже — запись в Documents через NSFileManager.
actual fun writeSaveFile(name: String, content: String): String =
    "ios: сохранение в файл пока не поддержано ($name)"

actual fun readSaveFile(name: String): String? = null