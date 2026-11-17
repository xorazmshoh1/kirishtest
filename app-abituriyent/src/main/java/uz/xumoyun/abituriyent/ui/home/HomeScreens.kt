package uz.xumoyun.abituriyent.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import uz.xumoyun.abituriyent.R
import uz.xumoyun.abituriyent.data.AbiRepository
import uz.xumoyun.abituriyent.data.Track
import uz.xumoyun.abituriyent.data.db.SubjectEntity
import uz.xumoyun.abituriyent.data.db.SubjectProgress
import uz.xumoyun.abituriyent.data.db.TopicEntity
import uz.xumoyun.abituriyent.data.db.TopicProgress
import uz.xumoyun.abituriyent.domain.Block
import uz.xumoyun.abituriyent.domain.Scoring
import uz.xumoyun.abituriyent.ui.AbiPreview
import uz.xumoyun.abituriyent.ui.loc
import uz.xumoyun.abituriyent.ui.tint
import uz.xumoyun.abituriyent.ui.title
import uz.xumoyun.core.ui.components.ActionTile
import uz.xumoyun.core.ui.components.SettingsRow
import uz.xumoyun.core.ui.theme.LocalExtraColors
import uz.xumoyun.core.ui.theme.TabularNumbers
import javax.inject.Inject

data class HomeState(
    val lastScore: Double? = null,
    val lastMax: Double? = null,
    val delta: Double? = null,
    val streak: Int = 0,
    val dailyDone: Boolean = false,
    val mistakes: Int = 0,
    val favorites: Int = 0,
    val track: Track? = null,
    val progress: List<SubjectProgress> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(repository: AbiRepository) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        repository.lastSimulation(),
        combine(repository.streak, repository.dailyDoneToday) { s, d -> s to d },
        combine(repository.mistakeIds, repository.favoriteIds) { m, f -> m.size to f.size },
        repository.track,
        repository.subjectProgress,
    ) { (last, delta), (streak, done), (m, f), track, progress ->
        HomeState(last?.score, last?.maxScore, delta, streak, done, m, f, track, progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@Composable
fun HomeRoute(
    onSimulation: () -> Unit,
    onSubjects: () -> Unit,
    onMistakes: () -> Unit,
    onDaily: () -> Unit,
    onFavorites: () -> Unit,
    onHistory: () -> Unit,
    onCalculator: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, onSimulation, onSubjects, onMistakes, onDaily, onFavorites, onHistory, onCalculator)
}

@Composable
fun HomeContent(
    state: HomeState,
    onSimulation: () -> Unit,
    onSubjects: () -> Unit,
    onMistakes: () -> Unit,
    onDaily: () -> Unit,
    onFavorites: () -> Unit,
    onHistory: () -> Unit,
    onCalculator: () -> Unit,
) {
    val extra = LocalExtraColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tepada katta raqam "142 / 189" va o'sish ko'rsatkichi.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.last_score), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.lastScore?.let { Scoring.format(it) } ?: "—",
                    fontSize = 56.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = TabularNumbers,
                )
                Text(" / ${state.lastMax?.let { Scoring.format(it) } ?: Scoring.format(Scoring.MAX_SCORE)}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 10.dp))
            }
            state.delta?.let { d ->
                Text((if (d >= 0) "+" else "") + Scoring.format(d), color = if (d >= 0) extra.success else extra.failure, style = MaterialTheme.typography.titleMedium)
            }
            state.track?.direction?.let { Text(loc(it.nameUz, it.nameRu), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // Kunlik test kartasi olov ikonkasi bilan.
        Card(
            onClick = onDaily,
            colors = CardDefaults.cardColors(containerColor = extra.accent.copy(alpha = 0.14f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = extra.accent) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(10.dp).size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.daily_test), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (state.dailyDone) R.string.daily_done else R.string.daily_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.streak_days, state.streak), style = MaterialTheme.typography.titleMedium, color = extra.accent, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Icons.Rounded.School, stringResource(R.string.simulation), Modifier.weight(1f), subtitle = stringResource(R.string.simulation_subtitle), onClick = onSimulation)
            ActionTile(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.subjects), Modifier.weight(1f), subtitle = stringResource(R.string.subjects_count, state.track?.subjects?.size ?: 0), onClick = onSubjects)
            ActionTile(Icons.Rounded.ErrorOutline, stringResource(R.string.my_mistakes), Modifier.weight(1f), subtitle = "${state.mistakes}", tint = extra.failure, onClick = onMistakes)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            SettingsRow(stringResource(R.string.history), icon = Icons.Rounded.ShowChart, onClick = onHistory)
            SettingsRow(stringResource(R.string.calculator), icon = Icons.Rounded.Calculate, onClick = onCalculator)
            SettingsRow(stringResource(R.string.favorites), value = "${state.favorites}", icon = Icons.Rounded.Star, onClick = onFavorites)
        }
    }
}

@Composable
fun SubjectsRoute(onPractice: (SubjectEntity, Int) -> Unit, onTopics: (SubjectEntity) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val subjects = state.track?.subjects.orEmpty()
    SubjectsContent(subjects, state.progress, onPick = { picking = it.id }, onTopics = onTopics)
    subjects.firstOrNull { it.id == picking }?.let { s ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(s.title()) },
            text = { Text(stringResource(R.string.how_many_questions)) },
            confirmButton = {
                Row {
                    listOf(10, 20, 30).forEach { n -> TextButton(onClick = { picking = null; onPractice(s, n) }) { Text("$n") } }
                }
            },
            dismissButton = { TextButton(onClick = { picking = null; onTopics(s) }) { Text(stringResource(R.string.by_topics)) } },
        )
    }
}

@Composable
fun SubjectsContent(subjects: List<SubjectEntity>, progress: List<SubjectProgress>, onPick: (SubjectEntity) -> Unit, onTopics: (SubjectEntity) -> Unit) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(subjects, key = { it.id }) { s ->
            val p = progress.firstOrNull { it.subjectId == s.id }
            Card(onClick = { onPick(s) }, colors = CardDefaults.cardColors(containerColor = s.tint.copy(alpha = 0.10f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.title(), style = MaterialTheme.typography.titleMedium, color = s.tint, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(if (s.block == Block.MANDATORY) R.string.block_mandatory else R.string.block_special),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    val total = p?.total ?: 0
                    LinearProgressIndicator(progress = { if (total == 0) 0f else (p?.seen ?: 0).toFloat() / total }, color = s.tint, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.subject_progress, p?.seen ?: 0, total, p?.correctPercent ?: 0),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@HiltViewModel
class TopicsViewModel @Inject constructor(savedState: SavedStateHandle, repository: AbiRepository) : ViewModel() {
    private val subjectId: Int = savedState.get<Int>("subject") ?: 0
    val subject = repository.subject(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val topics: StateFlow<List<Pair<TopicEntity, TopicProgress?>>> =
        repository.topics(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun TopicsRoute(onOpen: (TopicEntity) -> Unit, viewModel: TopicsViewModel = hiltViewModel()) {
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val subject by viewModel.subject.collectAsStateWithLifecycle()
    val tint = subject?.tint ?: MaterialTheme.colorScheme.primary
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(topics, key = { it.first.id }) { (t, p) ->
            Card(onClick = { onOpen(t) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(loc(t.nameUz, t.nameRu), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.count_questions, p?.total ?: 0), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${p?.correctPercent ?: 0}%", color = tint, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    AbiPreview { HomeContent(HomeState(142.6, 189.0, 6.0, 5, false, 12, 3), {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun SubjectsPreview() {
    AbiPreview {
        SubjectsContent(
            listOf(SubjectEntity(1, "Ona tili", "", Block.MANDATORY, 0xFF2E7D32, 0), SubjectEntity(5, "Fizika", "", Block.SPECIAL, 0xFF5E35B1, 4)),
            listOf(SubjectProgress(1, 300, 120, 78)), {}, {},
        )
    }
}

