package maratmingazovr.ai.carsonella.world.save

import java.io.File

// Файлы сохранений лежат в папке ./saves относительно рабочего каталога процесса.
// Абсолютный путь возвращается из writeSaveFile() — по нему всегда видно, куда реально записалось.
private val savesDir = File("saves")

actual fun writeSaveFile(name: String, content: String): String {
    if (!savesDir.exists()) savesDir.mkdirs()
    val file = File(savesDir, name)
    file.writeText(content)
    return file.absolutePath
}

actual fun readSaveFile(name: String): String? {
    val file = File(savesDir, name)
    return if (file.exists()) file.readText() else null
}