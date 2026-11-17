package uz.xumoyun.abituriyent.domain

/**
 * Oddiy Leitner tizimi (Prava ilovasidagi bilan bir xil): xato — 1-qutiga qaytadi va ertaga qayta chiqadi,
 * to'g'ri — keyingi qutiga o'tadi va oralig'i uzayadi.
 */
object Leitner {
    /** Quti raqami → necha kundan keyin takrorlanadi. */
    private val intervalsDays = mapOf(1 to 1L, 2 to 3L, 3 to 7L, 4 to 14L, 5 to 30L)
    const val MAX_BOX = 5
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    data class Result(val box: Int, val nextReviewAt: Long)

    fun next(currentBox: Int, correct: Boolean, nowMs: Long): Result {
        val box = if (correct) (currentBox + 1).coerceAtMost(MAX_BOX) else 1
        return Result(box, nowMs + intervalsDays.getValue(box) * DAY_MS)
    }

    /** "Xatolarim" ro'yxatida qoladi: hech bo'lmasa bir marta xato qilingan va hali yaxshi o'rganilmagan. */
    fun isMistake(timesWrong: Int, box: Int): Boolean = timesWrong > 0 && box < 3
}
