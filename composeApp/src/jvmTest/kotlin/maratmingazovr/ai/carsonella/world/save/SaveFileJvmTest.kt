package maratmingazovr.ai.carsonella.world.save

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveFileJvmTest {

    private val name = "test-roundtrip.json"

    @AfterTest
    fun cleanup() {
        File("saves", name).delete()
    }

    @Test
    fun writeThenReadReturnsSameContent() {
        val content = """{"hello":"world"}"""
        val path = writeSaveFile(name, content)
        assertTrue(File(path).exists(), "файл должен существовать: $path")
        assertEquals(content, readSaveFile(name))
    }

    @Test
    fun readMissingFileReturnsNull() {
        assertEquals(null, readSaveFile("definitely-does-not-exist-xyz.json"))
    }
}