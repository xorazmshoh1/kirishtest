package uz.xumoyun.abituriyent.domain

import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

enum class Block { MANDATORY, SPECIAL }

enum class ExamType { SIMULATION, MANDATORY_ONLY, SUBJECT, TOPIC, DAILY, MISTAKES, FAVORITES }

/** Test qismi: majburiy fanlar, 1- va 2-mutaxassislik fanlari. */
enum class Part(val weightTenths: Int, val questions: Int) {
    MANDATORY(11, 10),  // har fandan 10 ta, 3 fan × 10 × 1,1 = 33
    SPECIAL_1(31, 30),  // 30 × 3,1 = 93
    SPECIAL_2(21, 30);  // 30 × 2,1 = 63

    val weight: Double get() = weightTenths / 10.0
}

data class ScoredAnswer(val isCorrect: Boolean, val weight: Double)

/**
 * Ball hisoblash: score = Σ (isCorrect × weight).
 * Suzuvchi nuqta xatosi bo'lmasligi uchun o'ndan birlarda (butun son) hisoblanadi.
 */
object Scoring {
    const val MAX_SCORE = 189.0

    fun scoreTenths(answers: List<ScoredAnswer>): Int =
        answers.filter { it.isCorrect }.sumOf { (it.weight * 10).roundToInt() }

    fun score(answers: List<ScoredAnswer>): Double = scoreTenths(answers) / 10.0

    fun maxScore(answers: List<ScoredAnswer>): Double = answers.sumOf { (it.weight * 10).roundToInt() } / 10.0

    /** Ball kalkulyatori: to'g'ri javoblar soni → umumiy ball. */
    fun calculate(mandatoryCorrect: Int, special1Correct: Int, special2Correct: Int): Double {
        val m = mandatoryCorrect.coerceIn(0, 30)
        val s1 = special1Correct.coerceIn(0, 30)
        val s2 = special2Correct.coerceIn(0, 30)
        return (m * Part.MANDATORY.weightTenths + s1 * Part.SPECIAL_1.weightTenths + s2 * Part.SPECIAL_2.weightTenths) / 10.0
    }

    /** 142.6 → "142,6"; 142.0 → "142". */
    fun format(score: Double, locale: Locale = Locale.getDefault()): String {
        val tenths = (score * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}" else String.format(locale, "%.1f", score)
    }
}

/** Ketma-ket kunlar seriyasi: bugun yoki kecha tugagan uzluksiz kunlar soni. */
object Streak {
    fun current(completedDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in completedDays) today else today.minusDays(1)
        var count = 0
        while (day in completedDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
