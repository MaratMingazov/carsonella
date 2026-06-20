package maratmingazovr.ai.carsonella.world.save

/**
 * Платформенная запись/чтение текстовых слепков мира на диск.
 *
 * Реально работает пока только на JVM Desktop (файл в ./saves/<name>).
 * Wasm (в браузере нет ФС), iOS, Android — заглушки, доделаем при необходимости.
 */
expect fun writeSaveFile(name: String, content: String): String  // вернёт путь к файлу (или сообщение) для лога

expect fun readSaveFile(name: String): String?                   // текст файла или null, если файла нет