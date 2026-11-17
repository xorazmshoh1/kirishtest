package uz.xumoyun.abituriyent.ui.settings

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.xumoyun.abituriyent.R
import uz.xumoyun.abituriyent.data.AbiRepository
import uz.xumoyun.abituriyent.data.AbiSettings
import uz.xumoyun.abituriyent.data.db.DirectionEntity
import uz.xumoyun.abituriyent.ui.AbiPreview
import uz.xumoyun.abituriyent.ui.LocalAds
import uz.xumoyun.abituriyent.ui.loc
import uz.xumoyun.core.common.ThemeMode
import uz.xumoyun.core.data.CommonSettings
import uz.xumoyun.core.ui.AppLocale
import uz.xumoyun.core.ui.components.ChoiceRow
import uz.xumoyun.core.ui.components.SettingsRow
import java.util.Locale
import javax.inject.Inject

data class AbiSettingsState(
    val directions: List<DirectionEntity> = emptyList(),
    val directionId: Int = 0,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminder: Int = AbiSettings.DEFAULT_REMINDER,
    val simMinutes: Int = AbiSettings.DEFAULT_SIM_MINUTES,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    repository: AbiRepository,
    private val settings: AbiSettings,
    private val common: CommonSettings,
) : ViewModel() {
    val state: StateFlow<AbiSettingsState> = combine(
        repository.directions, settings.directionId, common.themeMode, settings.reminderMinutes, settings.simulationMinutes,
    ) { d, id, t, r, m -> AbiSettingsState(d, id, t, r, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AbiSettingsState())

    fun setDirection(id: Int) = viewModelScope.launch { settings.setDirection(id) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { common.setThemeMode(t) }
    fun setReminder(m: Int) = viewModelScope.launch { settings.setReminder(m) }
    fun setSimMinutes(m: Int) = viewModelScope.launch { settings.setSimulationMinutes(m) }
    fun setLanguage(tag: String) = viewModelScope.launch { common.setLanguage(tag) }
    fun finishOnboarding() = viewModelScope.launch { common.setOnboardingDone() }
}

private val Languages = listOf("uz" to "Oʻzbekcha", "ru" to "Русский")
private enum class Dialog { LANGUAGE, DIRECTION, THEME, REMINDER, DURATION }

fun formatMinutes(m: Int): String = String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)

@Composable
fun SettingsRoute(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val ads = LocalAds.current
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val lang = AppLocale.current() ?: if (context.resources.configuration.locales[0].language == "ru") "ru" else "uz"
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsRow(stringResource(R.string.language), value = Languages.first { it.first == lang }.second, icon = Icons.Rounded.Language, onClick = { dialog = Dialog.LANGUAGE })
        SettingsRow(
            stringResource(R.string.direction),
            value = state.directions.firstOrNull { it.id == state.directionId }?.let { loc(it.nameUz, it.nameRu) } ?: stringResource(R.string.mandatory_only),
            icon = Icons.Rounded.Route,
            onClick = { dialog = Dialog.DIRECTION },
        )
        SettingsRow(stringResource(R.string.theme), value = stringResource(themeName(state.theme)), icon = Icons.Rounded.DarkMode, onClick = { dialog = Dialog.THEME })
        SettingsRow(
            stringResource(R.string.reminder_time),
            value = if (state.reminder < 0) stringResource(R.string.off) else formatMinutes(state.reminder),
            icon = Icons.Rounded.Notifications,
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                dialog = Dialog.REMINDER
            },
        )
        SettingsRow(stringResource(R.string.simulation_duration), value = stringResource(R.string.minutes_value, state.simMinutes), icon = Icons.Rounded.AccessTime, onClick = { dialog = Dialog.DURATION })
        if (ads != null && activity != null && ads.privacyOptionsRequired) {
            SettingsRow(stringResource(R.string.privacy_options), icon = Icons.Rounded.PrivacyTip, onClick = { ads.showPrivacyOptions(activity) })
        }
        SettingsRow(stringResource(R.string.privacy_policy), icon = Icons.Rounded.Policy, onClick = {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(context.getString(R.string.privacy_policy_url)))) }
        })
        SettingsRow(stringResource(R.string.rate_app), icon = Icons.Rounded.Star, onClick = {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${context.packageName}"))) }
        })
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }

    val close = { dialog = null }
    when (dialog) {
        Dialog.LANGUAGE -> Choice(stringResource(R.string.language), close) {
            Languages.forEach { (tag, name) -> ChoiceRow(name, lang == tag) { viewModel.setLanguage(tag); AppLocale.set(tag); close() } }
        }
        Dialog.DIRECTION -> Choice(stringResource(R.string.direction), close) {
            DirectionChoices(state.directions, state.directionId) { viewModel.setDirection(it); close() }
        }
        Dialog.THEME -> Choice(stringResource(R.string.theme), close) {
            ThemeMode.entries.forEach { t -> ChoiceRow(stringResource(themeName(t)), state.theme == t) { viewModel.setTheme(t); close() } }
        }
        Dialog.REMINDER -> Choice(stringResource(R.string.reminder_time), close) {
            ChoiceRow(stringResource(R.string.off), state.reminder < 0) { viewModel.setReminder(-1); close() }
            listOf(7, 12, 17, 19, 21).map { it * 60 }.forEach { m ->
                ChoiceRow(formatMinutes(m), state.reminder == m) { viewModel.setReminder(m); close() }
            }
        }
        Dialog.DURATION -> Choice(stringResource(R.string.simulation_duration), close) {
            listOf(60, 90, 120, 150, 180, 210).forEach { m ->
                ChoiceRow(stringResource(R.string.minutes_value, m), state.simMinutes == m) { viewModel.setSimMinutes(m); close() }
            }
        }
        null -> Unit
    }
}

@Composable
fun DirectionChoices(directions: List<DirectionEntity>, selected: Int, onPick: (Int) -> Unit) {
    directions.forEach { d -> ChoiceRow(loc(d.nameUz, d.nameRu), selected == d.id) { onPick(d.id) } }
    ChoiceRow(stringResource(R.string.mandatory_only), selected == 0) { onPick(0) }
}

@Composable
private fun Choice(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun themeName(t: ThemeMode) = when (t) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** Onboarding: til, 3 ta slayd, yo'nalish tanlash. */
@Composable
fun OnboardingRoute(viewModel: SettingsViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            when (step) {
                0 -> {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(24.dp))
                    Languages.forEach { (tag, name) ->
                        Button(onClick = { viewModel.setLanguage(tag); AppLocale.set(tag); step = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 4.dp)) { Text(name) }
                    }
                }
                1 -> Slide(Icons.Rounded.School, stringResource(R.string.slide1_title), stringResource(R.string.slide1_text))
                2 -> Slide(Icons.Rounded.Insights, stringResource(R.string.slide2_title), stringResource(R.string.slide2_text))
                3 -> Slide(Icons.Rounded.LocalFireDepartment, stringResource(R.string.slide3_title), stringResource(R.string.slide3_text))
                else -> Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.choose_direction), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    DirectionChoices(state.directions, state.directionId) { viewModel.setDirection(it) }
                }
            }
        }
        if (step > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (step <= 3) "$step / 3" else "", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    if (step < 4) step++ else { viewModel.finishOnboarding(); onDone() }
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (step < 4) R.string.next else R.string.start))
                }
            }
        }
    }
}

@Composable
private fun Slide(icon: ImageVector, title: String, text: String) {
    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Preview(showBackground = true)
@Composable
private fun SlidePreview() {
    AbiPreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Slide(Icons.Rounded.School, "Real ball tizimi", "90 savol, 5 fan va 189 ballik hisob.")
        }
    }
}

