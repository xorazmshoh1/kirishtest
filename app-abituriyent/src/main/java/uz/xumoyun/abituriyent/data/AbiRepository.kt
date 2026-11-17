package uz.xumoyun.abituriyent.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uz.xumoyun.abituriyent.data.db.AbiDao
import uz.xumoyun.abituriyent.data.db.DailyStreakEntity
import uz.xumoyun.abituriyent.data.db.DirectionEntity
import uz.xumoyun.abituriyent.data.db.ExamAnswerEntity
import uz.xumoyun.abituriyent.data.db.ExamEntity
import uz.xumoyun.abituriyent.data.db.OptionEntity
import uz.xumoyun.abituriyent.data.db.QuestionEntity
import uz.xumoyun.abituriyent.data.db.QuestionStatEntity
import uz.xumoyun.abituriyent.data.db.SubjectEntity
import uz.xumoyun.abituriyent.domain.Block
import uz.xumoyun.abituriyent.domain.ExamType
import uz.xumoyun.abituriyent.domain.Leitner
import uz.xumoyun.abituriyent.domain.Part
import uz.xumoyun.abituriyent.domain.ScoredAnswer
import uz.xumoyun.abituriyent.domain.Scoring
import uz.xumoyun.abituriyent.domain.Streak
import uz.xumoyun.core.common.TimeSource
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Test savoli: variantlari (A–D tartibida), qaysi qismga tegishli va og'irligi. */
data class TestQuestion(
    val question: QuestionEntity,
    val options: List<OptionEntity>,
    val weight: Double,
    val isFavorite: Boolean,
) {
    val correctOptionId: Int get() = options.first { it.isCorrect }.id
}

data class UserAnswer(val questionId: Int, val optionId: Int?, val isCorrect: Boolean, val weight: Double)

/** Tanlangan yo'nalish: 5 fan (3 majburiy + 2 mutaxassislik) yoki faqat majburiy fanlar. */
data class Track(val direction: DirectionEntity?, val mandatory: List<SubjectEntity>, val special1: SubjectEntity?, val special2: SubjectEntity?) {
    val subjects: List<SubjectEntity> get() = mandatory + listOfNotNull(special1, special2)
}

@Singleton
class AbiRepository @Inject constructor(
    private val dao: AbiDao,
    private val settings: AbiSettings,
    private val time: TimeSource,
) {
    val subjects = dao.observeSubjects()
    val directions = dao.observeDirections()
    val subjectProgress = dao.observeSubjectProgress()
    val mistakeIds = dao.observeMistakeIds()
    val favoriteIds = dao.observeFavoriteIds()
    val simulations = dao.observeSimulations()

    val track: Flow<Track> = combine(subjects, directions, settings.directionId) { s, d, id ->
        val dir = d.firstOrNull { it.id == id }
        Track(
            direction = dir,
            mandatory = s.filter { it.block == Block.MANDATORY },
            special1 = dir?.let { x -> s.firstOrNull { it.id == x.subject1Id } },
            special2 = dir?.let { x -> s.firstOrNull { it.id == x.subject2Id } },
        )
    }

    val streak: Flow<Int> = dao.observeCompletedDays().map { days ->
        Streak.current(days.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(), time.today())
    }
    val dailyDoneToday: Flow<Boolean> = dao.observeCompletedDays().map { time.today().toString() in it }

    fun topics(subjectId: Int) = combine(dao.observeTopics(subjectId), dao.observeTopicProgress(subjectId)) { t, p ->
        t.map { topic -> topic to p.firstOrNull { it.topicId == topic.id } }
    }

    fun subject(id: Int): Flow<SubjectEntity?> = subjects.map { list -> list.firstOrNull { it.id == id } }

    /** To'liq simulyatsiya: majburiy 3×10 (1,1) + 1-fan 30 (3,1) + 2-fan 30 (2,1). */
    suspend fun buildSimulation(mandatoryOnly: Boolean): List<TestQuestion> {
        val t = track.first()
        val parts = buildList {
            t.mandatory.forEach { add(Triple(it.id, Part.MANDATORY.questions, Part.MANDATORY.weight)) }
            if (!mandatoryOnly) {
                t.special1?.let { add(Triple(it.id, Part.SPECIAL_1.questions, Part.SPECIAL_1.weight)) }
                t.special2?.let { add(Triple(it.id, Part.SPECIAL_2.questions, Part.SPECIAL_2.weight)) }
            }
        }
        return parts.flatMap { (subjectId, count, weight) -> attach(dao.randomBySubject(subjectId, count), weight) }
    }

    suspend fun bySubject(subjectId: Int, count: Int): List<TestQuestion> =
        attach(dao.randomBySubject(subjectId, count), weightFor(subjectId))

    suspend fun byTopic(topicId: Int): List<TestQuestion> {
        val qs = dao.byTopic(topicId)
        return attach(qs, qs.firstOrNull()?.let { weightFor(it.subjectId) } ?: 1.0)
    }

    /** Kunlik test: tanlangan yo'nalish fanlaridan 10 ta eng kam ko'rilgan savol. */
    suspend fun daily(count: Int = 10): List<TestQuestion> {
        val ids = track.first().subjects.map { it.id }.ifEmpty { subjects.first().map { it.id } }
        val qs = dao.leastSeen(ids).take(count)
        return qs.groupBy { it.subjectId }.flatMap { (sid, list) -> attach(list, weightFor(sid)) }
    }

    suspend fun byIds(ids: List<Int>): List<TestQuestion> {
        val qs = dao.byIds(ids).associateBy { it.id }
        val ordered = ids.mapNotNull { qs[it] }
        val weights = ordered.map { it.subjectId }.distinct().associateWith { weightFor(it) }
        return attach(ordered, 1.0).map { it.copy(weight = weights.getValue(it.question.subjectId)) }
    }

    suspend fun mistakes() = byIds(mistakeIds.first())
    suspend fun favorites() = byIds(favoriteIds.first())

    /** Fan og'irligi tanlangan yo'nalishga qarab: majburiy 1,1; 1-fan 3,1; 2-fan 2,1. */
    private suspend fun weightFor(subjectId: Int): Double {
        val t = track.first()
        return when (subjectId) {
            t.special1?.id -> Part.SPECIAL_1.weight
            t.special2?.id -> Part.SPECIAL_2.weight
            else -> if (t.mandatory.any { it.id == subjectId }) Part.MANDATORY.weight else Part.SPECIAL_2.weight
        }
    }

    private suspend fun attach(questions: List<QuestionEntity>, weight: Double): List<TestQuestion> {
        if (questions.isEmpty()) return emptyList()
        val options = dao.optionsFor(questions.map { it.id }).groupBy { it.questionId }
        val favorites = favoriteIds.first().toSet()
        return questions.map { q -> TestQuestion(q, options[q.id].orEmpty().sortedBy { it.label }, weight, q.id in favorites) }
    }

    suspend fun recordAnswer(questionId: Int, correct: Boolean) {
        val now = time.now().toEpochMilli()
        val s = dao.stat(questionId) ?: QuestionStatEntity(questionId)
        val next = Leitner.next(s.box, correct, now)
        dao.upsertStat(s.copy(timesSeen = s.timesSeen + 1, timesWrong = s.timesWrong + if (correct) 0 else 1, box = next.box, nextReviewAt = next.nextReviewAt))
    }

    suspend fun toggleFavorite(questionId: Int): Boolean {
        val s = dao.stat(questionId) ?: QuestionStatEntity(questionId)
        dao.upsertStat(s.copy(isFavorite = !s.isFavorite))
        return !s.isFavorite
    }

    suspend fun saveExam(type: ExamType, startedAt: Long, answers: List<UserAnswer>): Long {
        val scored = answers.map { ScoredAnswer(it.isCorrect, it.weight) }
        val t = track.first()
        val id = dao.insertExam(
            ExamEntity(
                type = type,
                subject1Id = t.special1?.id,
                subject2Id = t.special2?.id,
                startedAt = startedAt,
                finishedAt = time.now().toEpochMilli(),
                score = Scoring.score(scored),
                maxScore = Scoring.maxScore(scored),
            ),
        )
        dao.insertExamAnswers(answers.map { ExamAnswerEntity(id, it.questionId, it.optionId, it.isCorrect, it.weight) })
        if (type == ExamType.DAILY) dao.upsertStreak(DailyStreakEntity(time.today().toString(), completed = true))
        return id
    }

    suspend fun exam(id: Long) = dao.exam(id)
    suspend fun examAnswers(id: Long) = dao.examAnswers(id)
    suspend fun subjectScores(id: Long) = dao.subjectScores(id)
    suspend fun previousSimulation(id: Long) = dao.previousSimulation(id)
    suspend fun bestBefore(id: Long) = dao.bestBefore(id)

    fun lastSimulation(): Flow<Pair<ExamEntity?, Double?>> = simulations.map { list ->
        val last = list.firstOrNull()
        last to list.getOrNull(1)?.let { prev -> last!!.score - prev.score }
    }
}
