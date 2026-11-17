package uz.xumoyun.abituriyent.ui.result

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.xumoyun.abituriyent.R
import uz.xumoyun.abituriyent.data.AbiRepository
import uz.xumoyun.abituriyent.data.TestQuestion
import uz.xumoyun.abituriyent.data.db.ExamEntity
import uz.xumoyun.abituriyent.data.db.SubjectEntity
import uz.xumoyun.abituriyent.data.db.SubjectScore
import uz.xumoyun.abituriyent.domain.Block
import uz.xumoyun.abituriyent.domain.ExamType
import uz.xumoyun.abituriyent.domain.Scoring
import uz.xumoyun.abituriyent.ui.AbiPreview
import uz.xumoyun.abituriyent.ui.LocalAds
import uz.xumoyun.abituriyent.ui.tint
import uz.xumoyun.abituriyent.ui.title
import uz.xumoyun.core.ui.components.AnswerOption
import uz.xumoyun.core.ui.components.EmptyState
import uz.xumoyun.core.ui.components.OptionState
import uz.xumoyun.core.ui.theme.LocalExtraColors
import uz.xumoyun.core.ui.theme.TabularNumbers
import javax.inject.Inject
import kotlin.random.Random

data class SubjectRow(val subject: SubjectEntity, val score: SubjectScore)
data class ReviewItem(val question: TestQuestion, val userOptionId: Int?)

data class ResultState(
    val exam: ExamEntity? = null,
    val rows: List<SubjectRow> = emptyList(),
    val delta: Double? = null,
    val isRecord: Boolean = false,
    val mistakes: List<ReviewItem> = emptyList(),
    val unlocked: Set<Int> = emptySet(),
)

@HiltViewModel
class ResultViewModel @Inject constructor(savedState: SavedStateHandle, private val repository: AbiRepository) : ViewModel() {
    private val examId: Long = savedState.get<Long>(ARG_EXAM) ?: 0L
    private val _state = MutableStateFlow(ResultState())
    val state: StateFlow<ResultState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val exam = repository.exam(examId) ?: return@launch
            val subjects = repository.subjects.first().associateBy { it.id }
            val rows = repository.subjectScores(examId).mapNotNull { s -> subjects[s.subjectId]?.let { SubjectRow(it, s) } }
                .sortedBy { it.subject.order }
            val isSim = exam.type == ExamType.SIMULATION
            val previous = if (isSim) repository.previousSimulation(examId) else null
            val best = if (isSim) repository.bestBefore(examId) else null
            val wrong = repository.examAnswers(examId).filter { !it.isCorrect }
            val questions = repository.byIds(wrong.map { it.questionId })
            _state.value = ResultState(
                exam = exam,
                rows = rows,
                delta = previous?.let { exam.score - it.score },
                // Konfetti faqat shaxsiy rekord yangilanganda (oldingi simulyatsiya bo'lsa).
                isRecord = isSim && best != null && exam.score > best,
                mistakes = questions.map { q -> ReviewItem(q, wrong.first { it.questionId == q.question.id }.optionId) },
            )
        }
    }

    fun unlock(id: Int) = _state.update { it.copy(unlocked = it.unlocked + id) }

    companion object {
        const val ARG_EXAM = "exam"
    }
}

@Composable
fun ResultRoute(viewModel: ResultViewModel, onReview: () -> Unit, onHome: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val ads = LocalAds.current
    LaunchedEffect(Unit) { if (activity != null) ads?.showInterstitial(activity) {} }
    if (state.exam != null) ResultContent(state, onReview, onHome)
}

@Composable
fun ResultContent(state: ResultState, onReview: () -> Unit, onHome: () -> Unit) {
    val exam = state.exam ?: return
    val extra = LocalExtraColors.current
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.total_score), style = MaterialTheme.typography.titleMedium)
            Text(
                "${Scoring.format(exam.score)} / ${Scoring.format(exam.maxScore)}",
                style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            state.delta?.let { d ->
                Text(
                    stringResource(R.string.change_vs_last, (if (d >= 0) "+" else "") + Scoring.format(d)),
                    color = if (d >= 0) extra.success else extra.failure,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.isRecord) Text(stringResource(R.string.new_record), color = extra.accent, style = MaterialTheme.typography.titleLarge)
            if (state.rows.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(stringResource(R.string.subject), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.correct_short), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.score), style = MaterialTheme.typography.labelLarge)
                        }
                        state.rows.forEach { r ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(r.subject.title(), Modifier.weight(1f), color = r.subject.tint, fontWeight = FontWeight.Medium)
                                Text("${r.score.correct}/${r.score.total}", Modifier.padding(horizontal = 8.dp), style = TabularNumbers)
                                Text(Scoring.format(r.score.score), style = TabularNumbers, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (state.mistakes.isNotEmpty()) {
                Button(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.review_mistakes)) }
            }
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.to_home)) }
        }
        if (state.isRecord) Confetti(Modifier.fillMaxSize())
    }
}

/** Oddiy konfetti: tushayotgan rangli bo'laklar. */
@Composable
private fun Confetti(modifier: Modifier) {
    val pieces = remember {
        val colors = listOf(Color(0xFFFB8C00), Color(0xFF3949AB), Color(0xFF43A047), Color(0xFFE53935), Color(0xFFFDD835))
        List(60) { Triple(Random.nextFloat(), Random.nextFloat(), colors[it % colors.size]) }
    }
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3500, easing = LinearEasing)), label = "fall",
    )
    Canvas(modifier) {
        pieces.forEachIndexed { i, (x, offset, color) ->
            val y = ((t + offset) % 1f) * size.height
            val px = x * size.width + kotlin.math.sin((t * 6 + i) * 1.3f) * 20f
            rotate(degrees = (t * 720 + i * 37) % 360, pivot = Offset(px, y)) {
                drawRect(color, Offset(px - 6f, y - 3f), Size(12f, 6f))
            }
        }
    }
}

@Composable
fun ReviewRoute(viewModel: ResultViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val ads = LocalAds.current
    if (state.exam == null) return
    if (state.mistakes.isEmpty()) {
        EmptyState(Icons.Rounded.CheckCircle, stringResource(R.string.no_mistakes))
        return
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(state.mistakes, key = { it.question.question.id }) { item ->
            val q = item.question
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(q.question.text, style = MaterialTheme.typography.titleMedium)
                    q.options.forEach { o ->
                        val s = when {
                            o.isCorrect -> OptionState.CORRECT
                            o.id == item.userOptionId -> OptionState.WRONG
                            else -> null
                        }
                        if (s != null) AnswerOption(o.label, o.text, s, enabled = false, onClick = {})
                    }
                    if (item.userOptionId == null) Text(stringResource(R.string.not_answered), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (q.question.id in state.unlocked) {
                        Text(q.question.explanation, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.source, q.question.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TextButton(onClick = {
                            if (activity != null && ads != null) ads.showRewarded(activity) { viewModel.unlock(q.question.id) } else viewModel.unlock(q.question.id)
                        }) {
                            Icon(Icons.Rounded.Lock, null)
                            Text(" " + stringResource(R.string.unlock_explanation))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultPreview() {
    val math = SubjectEntity(2, "Matematika", "Математика", Block.MANDATORY, 0xFF1565C0, 1)
    val phys = SubjectEntity(5, "Fizika", "Физика", Block.SPECIAL, 0xFF5E35B1, 4)
    AbiPreview {
        ResultContent(
            ResultState(
                exam = ExamEntity(1, ExamType.SIMULATION, 4, 5, 0, 0, 142.6, 189.0),
                rows = listOf(SubjectRow(math, SubjectScore(2, 8, 10, 8.8)), SubjectRow(phys, SubjectScore(5, 22, 30, 46.2))),
                delta = 6.0,
                isRecord = true,
            ),
            {}, {},
        )
    }
}
