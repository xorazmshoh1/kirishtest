package uz.xumoyun.abituriyent

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uz.xumoyun.abituriyent.data.AbiRepository
import uz.xumoyun.abituriyent.data.AbiSettings
import uz.xumoyun.abituriyent.data.db.AbiDao
import uz.xumoyun.abituriyent.data.db.DailyStreakEntity
import uz.xumoyun.abituriyent.data.db.DirectionEntity
import uz.xumoyun.abituriyent.data.db.ExamAnswerEntity
import uz.xumoyun.abituriyent.data.db.ExamEntity
import uz.xumoyun.abituriyent.data.db.OptionEntity
import uz.xumoyun.abituriyent.data.db.QuestionEntity
import uz.xumoyun.abituriyent.data.db.QuestionStatEntity
import uz.xumoyun.abituriyent.data.db.SubjectEntity
import uz.xumoyun.abituriyent.data.db.SubjectProgress
import uz.xumoyun.abituriyent.data.db.SubjectScore
import uz.xumoyun.abituriyent.data.db.TopicEntity
import uz.xumoyun.abituriyent.data.db.TopicProgress
import uz.xumoyun.abituriyent.domain.Block
import uz.xumoyun.abituriyent.domain.ExamType
import uz.xumoyun.abituriyent.ui.test.TestViewModel
import uz.xumoyun.core.common.FixedTimeSource
import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * Xotiradagi soxta DAO: 3 majburiy fan (har birida 10 savol) va yo'nalish fanlari 4, 5 (har birida 30 savol).
 * Har savolda 4 variant, to'g'risi — A (id*10).
 */
class FakeAbiDao : AbiDao {
    val subjects = listOf(
        SubjectEntity(1, "Ona tili", "", Block.MANDATORY, 0, 0),
        SubjectEntity(2, "Matematika", "", Block.MANDATORY, 0, 1),
        SubjectEntity(3, "Tarix", "", Block.MANDATORY, 0, 2),
        SubjectEntity(4, "Matematika S", "", Block.SPECIAL, 0, 3),
        SubjectEntity(5, "Fizika", "", Block.SPECIAL, 0, 4),
    )
    val questions = buildList {
        var id = 1
        listOf(1 to 10, 2 to 10, 3 to 10, 4 to 30, 5 to 30).forEach { (s, n) ->
            repeat(n) { add(QuestionEntity(id++, s, s * 100, "q", null, null, "e", 1, "test")) }
        }
    }
    val options = questions.flatMap { q -> (0..3).map { OptionEntity(q.id * 10 + it, q.id, "ABCD"[it].toString(), "o$it", it == 0) } }
    val stats = MutableStateFlow<Map<Int, QuestionStatEntity>>(emptyMap())
    val exams = mutableListOf<ExamEntity>()
    val examAnswers = mutableListOf<ExamAnswerEntity>()
    val streak = MutableStateFlow<List<String>>(emptyList())

    override suspend fun insertSubjects(items: List<SubjectEntity>) = Unit
    override suspend fun insertDirections(items: List<DirectionEntity>) = Unit
    override suspend fun insertTopics(items: List<TopicEntity>) = Unit
    override suspend fun insertQuestions(items: List<QuestionEntity>) = Unit
    override suspend fun insertOptions(items: List<OptionEntity>) = Unit
    override suspend fun clearOptions() = Unit
    override suspend fun clearQuestions() = Unit
    override suspend fun clearTopics() = Unit
    override suspend fun clearDirections() = Unit
    override suspend fun clearSubjects() = Unit
    override suspend fun questionCount() = questions.size
    override fun observeSubjects(): Flow<List<SubjectEntity>> = MutableStateFlow(subjects)
    override fun observeDirections(): Flow<List<DirectionEntity>> = MutableStateFlow(listOf(DirectionEntity(1, "M+F", "", 4, 5)))
    override fun observeTopics(subjectId: Int): Flow<List<TopicEntity>> = MutableStateFlow(emptyList())
    override suspend fun randomBySubject(subjectId: Int, limit: Int) = questions.filter { it.subjectId == subjectId }.take(limit)
    override suspend fun byTopic(topicId: Int) = questions.filter { it.topicId == topicId }
    override suspend fun byIds(ids: List<Int>) = questions.filter { it.id in ids }
    override suspend fun optionsFor(ids: List<Int>) = options.filter { it.questionId in ids }
    override suspend fun leastSeen(subjectIds: List<Int>) = questions.filter { it.subjectId in subjectIds }.sortedBy { stats.value[it.id]?.timesSeen ?: 0 }
    override suspend fun stat(id: Int) = stats.value[id]
    override suspend fun upsertStat(stat: QuestionStatEntity) { stats.value = stats.value + (stat.questionId to stat) }
    override fun observeMistakeIds() = stats.map { m -> m.values.filter { it.timesWrong > 0 && it.box < 3 }.map { it.questionId } }
    override fun observeFavoriteIds() = stats.map { m -> m.values.filter { it.isFavorite }.map { it.questionId } }
    override fun observeSubjectProgress(): Flow<List<SubjectProgress>> = MutableStateFlow(emptyList())
    override fun observeTopicProgress(subjectId: Int): Flow<List<TopicProgress>> = MutableStateFlow(emptyList())
    override suspend fun insertExam(exam: ExamEntity): Long { val id = exams.size + 1L; exams += exam.copy(id = id); return id }
    override suspend fun insertExamAnswers(items: List<ExamAnswerEntity>) { examAnswers += items }
    override suspend fun exam(id: Long) = exams.firstOrNull { it.id == id }
    override suspend fun examAnswers(id: Long) = examAnswers.filter { it.examId == id }
    override fun observeSimulations(): Flow<List<ExamEntity>> = MutableStateFlow(exams.toList())
    override suspend fun previousSimulation(id: Long) = exams.lastOrNull { it.id < id && it.type == ExamType.SIMULATION }
    override suspend fun bestBefore(id: Long) = exams.filter { it.id < id }.maxOfOrNull { it.score }
    override suspend fun subjectScores(examId: Long): List<SubjectScore> = emptyList()
    override suspend fun upsertStreak(day: DailyStreakEntity) { streak.value = streak.value + day.date }
    override fun observeCompletedDays(): Flow<List<String>> = streak
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeAbiDao()
    private val time = FixedTimeSource(Instant.parse("2026-11-17T10:00:00Z"))
    private val settings = AbiSettings(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(Files.createTempDirectory("a").toFile(), "s.preferences_pb") },
        ),
    )
    private val repository = AbiRepository(dao, settings, time)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        runBlocking { settings.setDirection(1) }
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(type: ExamType, subject: Int = 0, count: Int = 0) =
        TestViewModel(SavedStateHandle(mapOf("type" to type.name, "subject" to subject, "topic" to 0, "count" to count)), repository, settings, time)

    @Test fun simulationHas90QuestionsAndMax189() = runTest(dispatcher) {
        val vm = vm(ExamType.SIMULATION)
        awaitLoaded(vm)
        val s = vm.state.value
        assertEquals(90, s.questions.size)
        assertEquals(180 * 60, s.secondsLeft)
        // Hammasiga to'g'ri javob.
        s.questions.forEachIndexed { i, q -> vm.goTo(i); vm.select(q.correctOptionId) }
        assertEquals(90, vm.state.value.answeredCount)
        vm.finish()
        awaitFinished(vm)
        val exam = dao.exam(vm.state.value.finishedExamId!!)!!
        assertEquals(189.0, exam.score, 0.0)
        assertEquals(189.0, exam.maxScore, 0.0)
    }

    @Test fun simulationUnansweredCountAsWrong() = runTest(dispatcher) {
        val vm = vm(ExamType.SIMULATION)
        awaitLoaded(vm)
        // Faqat 1-mutaxassislik fanidan 10 ta to'g'ri: 10 × 3,1 = 31
        vm.state.value.questions.withIndex().filter { it.value.question.subjectId == 4 }.take(10).forEach { (i, q) ->
            vm.goTo(i); vm.select(q.correctOptionId)
        }
        vm.finish()
        awaitFinished(vm)
        val exam = dao.exam(vm.state.value.finishedExamId!!)!!
        assertEquals(31.0, exam.score, 0.0)
        assertEquals(90, dao.examAnswers(exam.id).size)
        assertEquals(80, repository.mistakeIds.first().size)
    }

    @Test fun subjectPracticeRevealsAndUsesWeight() = runTest(dispatcher) {
        val vm = vm(ExamType.SUBJECT, subject = 5, count = 10)
        awaitLoaded(vm)
        val s = vm.state.value
        assertEquals(null, s.secondsLeft)
        assertEquals(10, s.questions.size)
        assertTrue(s.questions.all { it.weight == 2.1 })
        vm.select(s.current!!.options.last().id)
        assertTrue(vm.state.value.currentRevealed)
        vm.finish()
        awaitFinished(vm)
        assertEquals(1, dao.examAnswers(vm.state.value.finishedExamId!!).size)
    }

    @Test fun dailyTestMarksStreak() = runTest(dispatcher) {
        val vm = vm(ExamType.DAILY)
        awaitLoaded(vm)
        assertEquals(10, vm.state.value.questions.size)
        vm.select(vm.state.value.current!!.correctOptionId)
        vm.finish()
        awaitFinished(vm)
        assertNotNull(vm.state.value.finishedExamId)
        assertEquals(listOf("2026-11-17"), dao.streak.value)
        assertEquals(1, repository.streak.first())
    }

    @Test fun mandatoryOnlyIs30QuestionsAnd33Points() = runTest(dispatcher) {
        val vm = vm(ExamType.MANDATORY_ONLY)
        awaitLoaded(vm)
        val s = vm.state.value
        assertEquals(30, s.questions.size)
        assertEquals(60 * 60, s.secondsLeft)
        s.questions.forEachIndexed { i, q -> vm.goTo(i); vm.select(q.correctOptionId) }
        vm.finish()
        awaitFinished(vm)
        assertEquals(33.0, dao.exam(vm.state.value.finishedExamId!!)!!.score, 0.0)
    }

    @Test fun timerEndsSimulation() = runTest(dispatcher) {
        val vm = vm(ExamType.MANDATORY_ONLY)
        awaitLoaded(vm)
        advanceTimeBy(60 * 60 * 1000L + 2000)
        assertEquals(0, vm.state.value.secondsLeft)
        awaitFinished(vm)
        assertNotNull(vm.state.value.finishedExamId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.awaitLoaded(vm: TestViewModel) {
    repeat(500) {
        runCurrent()
        if (!vm.state.value.loading) return
        Thread.sleep(10)
    }
    error("ViewModel yuklanmadi")
}

/** Natijani saqlash yo'nalishni DataStore'dan (IO) o'qiydi: tugaguncha kutamiz. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.awaitFinished(vm: TestViewModel) {
    repeat(500) {
        runCurrent()
        if (vm.state.value.finishedExamId != null) return
        Thread.sleep(10)
    }
    error("Test yakunlanmadi")
}
