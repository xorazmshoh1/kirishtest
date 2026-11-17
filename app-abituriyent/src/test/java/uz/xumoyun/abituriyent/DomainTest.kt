package uz.xumoyun.abituriyent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.xumoyun.abituriyent.data.AbiContent
import uz.xumoyun.abituriyent.data.ContentLoader
import uz.xumoyun.abituriyent.data.ContentValidator
import uz.xumoyun.abituriyent.domain.Part
import uz.xumoyun.abituriyent.domain.ScoredAnswer
import uz.xumoyun.abituriyent.domain.Scoring
import uz.xumoyun.abituriyent.domain.Streak
import uz.xumoyun.abituriyent.reminder.DailyReminder
import uz.xumoyun.core.data.AppJson
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class DomainTest {

    private fun fullTest(mandatory: Int, s1: Int, s2: Int): List<ScoredAnswer> =
        List(30) { ScoredAnswer(it < mandatory, Part.MANDATORY.weight) } +
            List(30) { ScoredAnswer(it < s1, Part.SPECIAL_1.weight) } +
            List(30) { ScoredAnswer(it < s2, Part.SPECIAL_2.weight) }

    @Test fun maximumIs189() {
        val all = fullTest(30, 30, 30)
        assertEquals(189.0, Scoring.score(all), 0.0)
        assertEquals(189.0, Scoring.maxScore(all), 0.0)
        assertEquals(33.0, Scoring.calculate(30, 0, 0), 0.0)
        assertEquals(93.0, Scoring.calculate(0, 30, 0), 0.0)
        assertEquals(63.0, Scoring.calculate(0, 0, 30), 0.0)
    }

    @Test fun scoreIsSumOfWeightsWithoutFloatErrors() {
        // 25×1,1 + 22×3,1 + 18×2,1 = 27,5 + 68,2 + 37,8 = 133,5
        assertEquals(133.5, Scoring.score(fullTest(25, 22, 18)), 0.0)
        assertEquals(133.5, Scoring.calculate(25, 22, 18), 0.0)
        // 3 × 1,1 = 3,3 (oddiy double qo'shishda 3.3000000000000003 bo'lardi)
        assertEquals(3.3, Scoring.score(List(3) { ScoredAnswer(true, 1.1) }), 0.0)
        assertEquals(0.0, Scoring.score(fullTest(0, 0, 0)), 0.0)
    }

    @Test fun calculatorClampsInput() {
        assertEquals(189.0, Scoring.calculate(99, 99, 99), 0.0)
        assertEquals(0.0, Scoring.calculate(-5, -1, -2), 0.0)
    }

    @Test fun formatting() {
        assertEquals("142", Scoring.format(142.0, Locale.ROOT))
        assertEquals("142.6", Scoring.format(142.6, Locale.ROOT))
        assertEquals("142,6", Scoring.format(142.6, Locale("uz")))
    }

    @Test fun streakCountsConsecutiveDays() {
        val today = LocalDate.of(2026, 11, 17)
        assertEquals(0, Streak.current(emptySet(), today))
        assertEquals(3, Streak.current(setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4)), today))
        // Bugun hali yechilmagan bo'lsa ham kechagi seriya saqlanadi.
        assertEquals(2, Streak.current(setOf(today.minusDays(1), today.minusDays(2)), today))
        assertEquals(0, Streak.current(setOf(today.minusDays(2)), today))
    }

    @Test fun reminderDelay() {
        val now = LocalDateTime.of(2026, 11, 17, 18, 30)
        assertEquals(Duration.ofMinutes(30), DailyReminder.initialDelay(now, 19 * 60))
        assertEquals(Duration.ofHours(24).minusMinutes(30), DailyReminder.initialDelay(now, 18 * 60))
    }

    @Test fun bundledContentIsValid() {
        val c = AppJson.decodeFromString(AbiContent.serializer(), File("src/main/assets/content.json").readText())
        assertEquals(emptyList<String>(), ContentValidator.validate(c))
        assertEquals(10, c.subjects.size)
        assertEquals(3, c.directions.size)
        c.subjects.forEach { s -> assertTrue("${s.uz}: savol yo'q", c.questions.any { it.subject == s.id }) }
    }

    @Test fun optionLabelsAreShuffledButStable() {
        val positions = (1..60).map { ContentLoader.displayOrder(it, 4)[0] }.toSet()
        assertTrue(positions.size > 1)
        assertEquals(ContentLoader.displayOrder(5, 4), ContentLoader.displayOrder(5, 4))
        assertEquals(0xFF3949ABL, ContentLoader.parseColor("#3949AB"))
    }
}
