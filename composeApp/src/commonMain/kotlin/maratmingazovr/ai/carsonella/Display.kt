package maratmingazovr.ai.carsonella

// Надстрочные и подстрочные юникод-символы → ASCII.
// В вебе (Wasm/Skia) у дефолтного шрифта нет их глифов → рисуются «крестиками».
// Заменяем при ОТОБРАЖЕНИИ; доменные symbol/label в shared остаются красивыми.
private val SUPER_SUB: Map<Char, Char> = mapOf(
    '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
    '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9',
    '⁺' to '+', '⁻' to '-',
    '₀' to '0', '₁' to '1', '₂' to '2', '₃' to '3', '₄' to '4',
    '₅' to '5', '₆' to '6', '₇' to '7', '₈' to '8', '₉' to '9',
    '₊' to '+', '₋' to '-',
)

fun String.toAsciiSymbols(): String = map { SUPER_SUB[it] ?: it }.joinToString("")