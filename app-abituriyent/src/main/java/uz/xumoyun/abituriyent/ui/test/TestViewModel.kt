package uz.xumoyun.abituriyent.ui.test

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.xumoyun.abituriyent.data.AbiRepository
import uz.xumoyun.abituriyent.data.AbiSettings
import uz.xumoyun.abituriyent.data.TestQuestion
import uz.xumoyun.abituriyent.data.UserAnswer
import uz.xumoyun.abituriyent.data.db.SubjectEntity
import uz.xumoyun.abituriyent.domain.ExamType
import uz.xumoyun.core.common.TimeSource
import javax.inject.Inject

data class TestState(
    val loading: Boolean = true,
    val type: ExamType = ExamType.SUBJECT,
    val questions: List<TestQuestion> = emptyList(),
    val subjects: Map<Int, SubjectEntity> = emptyMap(),
    val index: Int = 0,
    val selected: Map<Int, Int> = emptyMap(),
    /** Mashqda javob tanlangan zahoti ochiladi. */
    val revealed: Set<Int> = emptySet(),
    /** Simulyatsiyada keyin qaytish uchun belgilangan savollar. */
    val flagged: Set<Int> = emptySet(),
    val secondsLeft: Int? = null,
    val finishedExamId: Long? = null,
    val confirmFinish: Boolean = false,
) {
    val current: TestQuestion? get() = questions.getOrNull(index)
    val isSimulation: Boolean get() = type == ExamType.SIMULATION || type == ExamType.MANDATORY_ONLY
    val answeredCount: Int get() = questions.count { it.question.id in selected }
    val currentRevealed: Boolean get() = current?.let { !isSimulation && it.question.id in revealed } ?: false
}

@HiltViewModel
class TestViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: AbiRepository,
    private val settings: AbiSettings,
    private val time: TimeSource,
) : ViewModel() {

    private val type = ExamType.valueOf(savedState.get<String>(ARG_TYPE) ?: ExamType.SUBJECT.name)
    private val subjectId = savedState.get<Int>(ARG_SUBJECT) ?: 0
    private val topicId = savedState.get<Int>(ARG_TOPIC) ?: 0
    private val count = savedState.get<Int>(ARG_COUNT)?.takeIf { it > 0 } ?: 10
    private val startedAt = time.now().toEpochMilli()
    private var timer: Job? = null

    private val _state = MutableStateFlow(TestState(type = type))
    val state: StateFlow<TestState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val questions = when (type) {
                ExamType.SIMULATION -> repository.buildSimulation(mandatoryOnly = false)
                ExamType.MANDATORY_ONLY -> repository.buildSimulation(mandatoryOnly = true)
                ExamType.SUBJECT -> repository.bySubject(subjectId, count)
                ExamType.TOPIC -> repository.byTopic(topicId)
                ExamType.DAILY -> repository.daily()
                ExamType.MISTAKES -> repository.mistakes()
                ExamType.FAVORITES -> repository.favorites()
            }
            val subjects = repository.subjects.first().associateBy { it.id }
            val minutes = settings.currentSimulationMinutes()
            val simulation = type == ExamType.SIMULATION || type == ExamType.MANDATORY_ONLY
            _state.update {
                it.copy(
                    loading = false,
                    questions = questions,
                    subjects = subjects,
                    // Faqat majburiy fanlar: 30 savol — vaqt ham mutanosib qisqaradi.
                    secondsLeft = if (simulation) (minutes * 60 * questions.size / 90).coerceAtLeast(60) else null,
                )
            }
            if (simulation && questions.isNotEmpty()) startTimer()
        }
    }

    private fun startTimer() {
        timer = viewModelScope.launch {
            while (true) {
                delay(1000)
                val left = (_state.value.secondsLeft ?: return@launch) - 1
                _state.update { it.copy(secondsLeft = left.coerceAtLeast(0)) }
                if (left <= 0) { finish(); return@launch }
            }
        }
    }

    fun select(optionId: Int) {
        val s = _state.value
        val q = s.current ?: return
        if (s.finishedExamId != null || (!s.isSimulation && q.question.id in s.revealed)) return
        _state.update { it.copy(selected = it.selected + (q.question.id to optionId)) }
        if (!s.isSimulation) {
            _state.update { it.copy(revealed = it.revealed + q.question.id) }
            val correct = optionId == q.correctOptionId
            viewModelScope.launch { repository.recordAnswer(q.question.id, correct) }
        }
    }

    fun goTo(index: Int) = _state.update { it.copy(index = index.coerceIn(0, (it.questions.size - 1).coerceAtLeast(0))) }
    fun next() = goTo(_state.value.index + 1)
    fun previous() = goTo(_state.value.index - 1)

    fun toggleFlag() = _state.update { s ->
        val id = s.current?.question?.id ?: return@update s
        s.copy(flagged = if (id in s.flagged) s.flagged - id else s.flagged + id)
    }

    fun toggleFavorite() = viewModelScope.launch {
        val id = _state.value.current?.question?.id ?: return@launch
        val fav = repository.toggleFavorite(id)
        _state.update { s -> s.copy(questions = s.questions.map { if (it.question.id == id) it.copy(isFavorite = fav) else it }) }
    }

    fun askFinish() = _state.update { it.copy(confirmFinish = true) }
    fun cancelFinish() = _state.update { it.copy(confirmFinish = false) }

    fun finish() {
        val s = _state.value
        if (s.finishedExamId != null || s.questions.isEmpty()) return
        timer?.cancel()
        viewModelScope.launch {
            val answers = s.questions.mapNotNull { q ->
                val id = q.question.id
                val opt = s.selected[id]
                if (!s.isSimulation && opt == null) return@mapNotNull null
                UserAnswer(id, opt, opt == q.correctOptionId, q.weight)
            }
            if (s.isSimulation) {
                // Simulyatsiyada statistika oxirida bir marta yangilanadi.
                answers.forEach { repository.recordAnswer(it.questionId, it.isCorrect) }
            }
            val examId = repository.saveExam(s.type, startedAt, answers)
            _state.update { it.copy(finishedExamId = examId, confirmFinish = false) }
        }
    }

    companion object {
        const val ARG_TYPE = "type"
        const val ARG_SUBJECT = "subject"
        const val ARG_TOPIC = "topic"
        const val ARG_COUNT = "count"
    }
}
