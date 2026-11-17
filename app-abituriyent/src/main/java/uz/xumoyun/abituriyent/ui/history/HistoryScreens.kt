package uz.xumoyun.abituriyent.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import uz.xumoyun.abituriyent.R
import uz.xumoyun.abituriyent.data.AbiRepository
import uz.xumoyun.abituriyent.data.db.ExamEntity
import uz.xumoyun.abituriyent.domain.ExamType
import uz.xumoyun.abituriyent.domain.Part
import uz.xumoyun.abituriyent.domain.Scoring
import uz.xumoyun.abituriyent.ui.AbiPreview
import uz.xumoyun.core.ui.components.EmptyState
import uz.xumoyun.core.ui.theme.TabularNumbers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(repository: AbiRepository) : ViewModel() {
    val simulations: StateFlow<List<ExamEntity>?> = repository.simulations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun HistoryRoute(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val list by viewModel.simulations.collectAsStateWithLifecycle()
    HistoryContent(list ?: return, onOpen)
}

@Composable
fun HistoryContent(list: List<ExamEntity>, onOpen: (Long) -> Unit) {
    if (list.isEmpty()) {
        EmptyState(Icons.Rounded.ShowChart, stringResource(R.string.history_empty), message = stringResource(R.string.history_empty_hint))
        return
    }
    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.score_chart), style = MaterialTheme.typography.titleMedium)
                    ScoreChart(list.reversed().map { it.score })
                }
            }
        }
        items(list, key = { it.id }) { e ->
            Card(onClick = { onOpen(e.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.format(Instant.ofEpochMilli(e.finishedAt).atZone(ZoneId.systemDefault())), Modifier.weight(1f))
                    Text("${Scoring.format(e.score)} / ${Scoring.format(e.maxScore)}", style = TabularNumbers, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Ball o'zgarishi grafigi (chiziqli). */
@Composable
private fun ScoreChart(scores: List<Double>) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(top = 12.dp)) {
        for (i in 0..3) {
            val y = size.height * i / 3
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (scores.isEmpty()) return@Canvas
        val max = Scoring.MAX_SCORE.toFloat()
        val step = if (scores.size == 1) 0f else size.width / (scores.size - 1)
        val points = scores.mapIndexed { i, s -> Offset(if (scores.size == 1) size.width / 2 else i * step, size.height * (1 - s.toFloat() / max)) }
        val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
        points.forEach { drawCircle(color, 5.dp.toPx(), it) }
    }
}

/** Ball kalkulyatori: to'g'ri javoblar sonini kiritib umumiy ballni hisoblash. */
@Composable
fun CalculatorScreen() {
    var m by rememberSaveable { mutableStateOf("") }
    var s1 by rememberSaveable { mutableStateOf("") }
    var s2 by rememberSaveable { mutableStateOf("") }
    val score = Scoring.calculate(m.toIntOrNull() ?: 0, s1.toIntOrNull() ?: 0, s2.toIntOrNull() ?: 0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.calculator_hint), style = MaterialTheme.typography.bodyMedium)
        CountField(stringResource(R.string.calc_mandatory, Part.MANDATORY.weight.toString()), m) { m = it }
        CountField(stringResource(R.string.calc_special1, Part.SPECIAL_1.weight.toString()), s1) { s1 = it }
        CountField(stringResource(R.string.calc_special2, Part.SPECIAL_2.weight.toString()), s2) { s2 = it }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.total_score) + ": ", style = MaterialTheme.typography.titleMedium)
            Text("${Scoring.format(score)} / ${Scoring.format(Scoring.MAX_SCORE)}", style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CountField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(2).let { s -> s.toIntOrNull()?.coerceAtMost(30)?.toString() ?: s }) },
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.from_0_to_30)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun HistoryPreview() {
    AbiPreview {
        HistoryContent(
            listOf(130.4, 136.6, 142.6).mapIndexed { i, s -> ExamEntity(i + 1L, ExamType.SIMULATION, 4, 5, 0, 1_700_000_000_000 + i * 86_400_000L, s, 189.0) }.reversed(),
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun CalculatorPreview() { AbiPreview { CalculatorScreen() } }
