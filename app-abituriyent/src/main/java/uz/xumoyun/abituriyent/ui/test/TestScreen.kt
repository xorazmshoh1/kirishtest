package uz.xumoyun.abituriyent.ui.test

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.xumoyun.abituriyent.R
import uz.xumoyun.abituriyent.data.TestQuestion
import uz.xumoyun.abituriyent.data.db.OptionEntity
import uz.xumoyun.abituriyent.data.db.QuestionEntity
import uz.xumoyun.abituriyent.data.db.SubjectEntity
import uz.xumoyun.abituriyent.domain.Block
import uz.xumoyun.abituriyent.domain.ExamType
import uz.xumoyun.abituriyent.ui.AbiPreview
import uz.xumoyun.abituriyent.ui.tint
import uz.xumoyun.abituriyent.ui.title
import uz.xumoyun.core.common.DurationFormat
import uz.xumoyun.core.ui.components.AnswerOption
import uz.xumoyun.core.ui.components.EmptyState
import uz.xumoyun.core.ui.components.OptionState
import uz.xumoyun.core.ui.components.QuizHeader

@Composable
fun TestRoute(onFinished: (Long) -> Unit, viewModel: TestViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finishedExamId) { state.finishedExamId?.let(onFinished) }
    TestContent(
        state,
        onSelect = viewModel::select,
        onNext = viewModel::next,
        onPrev = viewModel::previous,
        onGoTo = viewModel::goTo,
        onFlag = viewModel::toggleFlag,
        onFavorite = { viewModel.toggleFavorite() },
        onAskFinish = viewModel::askFinish,
        onCancelFinish = viewModel::cancelFinish,
        onFinish = viewModel::finish,
    )
}

@Composable
fun TestContent(
    state: TestState,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onGoTo: (Int) -> Unit,
    onFlag: () -> Unit,
    onFavorite: () -> Unit,
    onAskFinish: () -> Unit,
    onCancelFinish: () -> Unit,
    onFinish: () -> Unit,
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val q = state.current ?: run {
        EmptyState(Icons.Rounded.Inbox, stringResource(R.string.empty_test_title), message = stringResource(R.string.empty_test_message))
        return
    }
    val subject = state.subjects[q.question.subjectId]

    Column(Modifier.fillMaxSize()) {
        // Tepada: fan nomi va taymer.
        if (subject != null) {
            Text(
                subject.title(),
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = subject.tint,
                fontWeight = FontWeight.Bold,
            )
        }
        QuizHeader(
            progressText = stringResource(R.string.question_progress, state.index + 1, state.questions.size),
            timerText = state.secondsLeft?.let { DurationFormat.hms(java.time.Duration.ofSeconds(it.toLong())) },
            timerWarning = (state.secondsLeft ?: Int.MAX_VALUE) <= 5 * 60,
            trailing = {
                if (state.isSimulation) {
                    IconButton(onClick = onFlag) {
                        Icon(
                            if (q.question.id in state.flagged) Icons.Rounded.Flag else Icons.Rounded.OutlinedFlag,
                            stringResource(R.string.flag),
                            tint = if (q.question.id in state.flagged) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (q.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.favorite))
                }
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(q.question.text, style = MaterialTheme.typography.titleMedium)
            q.question.latex?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
            q.options.forEach { o ->
                val selected = state.selected[q.question.id] == o.id
                val s = when {
                    state.currentRevealed && o.isCorrect -> OptionState.CORRECT
                    state.currentRevealed && selected -> OptionState.WRONG
                    selected -> OptionState.SELECTED
                    else -> OptionState.IDLE
                }
                AnswerOption(o.label, o.text, s, enabled = !state.currentRevealed, onClick = { onSelect(o.id) })
            }
        }
        QuestionMap(state, onGoTo)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onPrev, enabled = state.index > 0, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.previous))
            }
            Button(onClick = onAskFinish, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.finish_test, state.answeredCount, state.questions.size))
            }
            OutlinedButton(onClick = onNext, enabled = state.index < state.questions.lastIndex, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.next))
            }
        }
    }

    if (state.confirmFinish) {
        val left = state.questions.size - state.answeredCount
        AlertDialog(
            onDismissRequest = onCancelFinish,
            title = { Text(stringResource(R.string.finish_confirm_title)) },
            text = { if (left > 0) Text(stringResource(R.string.finish_confirm_unanswered, left)) },
            confirmButton = { TextButton(onClick = onFinish) { Text(stringResource(R.string.finish)) } },
            dismissButton = { TextButton(onClick = onCancelFinish) { Text(stringResource(R.string.continue_test)) } },
        )
    }
}

/** Savollar xaritasi: raqamli kvadratchalar — javob berilgan, berilmagan, belgilangan. */
@Composable
private fun QuestionMap(state: TestState, onGoTo: (Int) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(state.index) { list.animateScrollToItem((state.index - 3).coerceAtLeast(0)) }
    LazyRow(state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(state.questions, key = { _, q -> q.question.id }) { i, q ->
            val id = q.question.id
            val answered = id in state.selected
            val flagged = id in state.flagged
            val bg = when {
                answered -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bg,
                border = when {
                    i == state.index -> BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                    flagged -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                    else -> null
                },
                modifier = Modifier.size(40.dp).clickable { onGoTo(i) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (answered) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TestPreview() {
    val qs = (1..12).map { n ->
        TestQuestion(
            QuestionEntity(n, 2, 201, "15 ning 20 foizi nechaga teng?", null, null, "", 1, ""),
            listOf("A" to "3", "B" to "5", "C" to "2", "D" to "4").mapIndexed { i, (l, t) -> OptionEntity(n * 10 + i, n, l, t, i == 0) },
            1.1, false,
        )
    }
    AbiPreview {
        TestContent(
            TestState(
                loading = false, type = ExamType.SIMULATION, questions = qs,
                subjects = mapOf(2 to SubjectEntity(2, "Matematika", "Математика", Block.MANDATORY, 0xFF1565C0, 0)),
                index = 2, selected = mapOf(1 to 10, 2 to 21, 3 to 32), flagged = setOf(5), secondsLeft = 9000,
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
