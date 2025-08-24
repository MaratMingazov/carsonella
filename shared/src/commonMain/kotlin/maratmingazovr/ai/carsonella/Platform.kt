package maratmingazovr.ai.carsonella

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform