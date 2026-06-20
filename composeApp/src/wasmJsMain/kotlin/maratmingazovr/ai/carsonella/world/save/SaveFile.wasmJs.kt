package maratmingazovr.ai.carsonella.world.save

// Заглушка: в браузере нет файловой системы. Позже — localStorage или скачивание файла.
actual fun writeSaveFile(name: String, content: String): String =
    "wasmJs: сохранение в файл пока не поддержано ($name)"

actual fun readSaveFile(name: String): String? = null