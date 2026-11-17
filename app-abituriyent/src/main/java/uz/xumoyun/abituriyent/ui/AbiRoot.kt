package uz.xumoyun.abituriyent.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import uz.xumoyun.abituriyent.R
import uz.xumoyun.abituriyent.domain.ExamType
import uz.xumoyun.abituriyent.ui.history.CalculatorScreen
import uz.xumoyun.abituriyent.ui.history.HistoryRoute
import uz.xumoyun.abituriyent.ui.home.HomeRoute
import uz.xumoyun.abituriyent.ui.home.SubjectsRoute
import uz.xumoyun.abituriyent.ui.home.TopicsRoute
import uz.xumoyun.abituriyent.ui.result.ResultRoute
import uz.xumoyun.abituriyent.ui.result.ResultViewModel
import uz.xumoyun.abituriyent.ui.result.ReviewRoute
import uz.xumoyun.abituriyent.ui.settings.OnboardingRoute
import uz.xumoyun.abituriyent.ui.settings.SettingsRoute
import uz.xumoyun.abituriyent.ui.settings.SettingsViewModel
import uz.xumoyun.abituriyent.ui.test.TestRoute

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SUBJECTS = "subjects"
    const val TOPICS = "topics/{subject}"
    const val TEST = "test/{type}?subject={subject}&topic={topic}&count={count}"
    const val RESULT = "result/{exam}"
    const val REVIEW = "review/{exam}"
    const val HISTORY = "history"
    const val CALCULATOR = "calculator"
    const val SETTINGS = "settings"

    fun test(type: ExamType, subject: Int = 0, topic: Int = 0, count: Int = 0) = "test/${type.name}?subject=$subject&topic=$topic&count=$count"
}

/** Banner faqat shu ekranlarda; savol ekranida reklama yo'q. */
private val bannerRoutes = setOf(Routes.HOME, Routes.SUBJECTS, Routes.TOPICS, Routes.HISTORY, Routes.CALCULATOR)

@Composable
fun AbiRoot(startWithOnboarding: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val ads = LocalAds.current
    val settingsVm: SettingsViewModel = hiltViewModel()

    Scaffold(
        topBar = { TopBar(route, nav) },
        bottomBar = { if (route in bannerRoutes && ads != null) uz.xumoyun.core.ads.BannerAd(ads) },
    ) { padding ->
        NavHost(nav, if (startWithOnboarding) Routes.ONBOARDING else Routes.HOME, Modifier.fillMaxSize().padding(padding)) {
            composable(Routes.ONBOARDING) {
                OnboardingRoute(settingsVm) { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
            }
            composable(Routes.HOME) {
                HomeRoute(
                    onSimulation = { nav.navigate(Routes.test(ExamType.SIMULATION)) },
                    onSubjects = { nav.navigate(Routes.SUBJECTS) },
                    onMistakes = { nav.navigate(Routes.test(ExamType.MISTAKES)) },
                    onDaily = { nav.navigate(Routes.test(ExamType.DAILY)) },
                    onFavorites = { nav.navigate(Routes.test(ExamType.FAVORITES)) },
                    onHistory = { nav.navigate(Routes.HISTORY) },
                    onCalculator = { nav.navigate(Routes.CALCULATOR) },
                )
            }
            composable(Routes.SUBJECTS) {
                SubjectsRoute(
                    onPractice = { s, n -> nav.navigate(Routes.test(ExamType.SUBJECT, subject = s.id, count = n)) },
                    onTopics = { s -> nav.navigate("topics/${s.id}") },
                )
            }
            composable(Routes.TOPICS, arguments = listOf(navArgument("subject") { type = NavType.IntType })) {
                TopicsRoute(onOpen = { t -> nav.navigate(Routes.test(ExamType.TOPIC, topic = t.id)) })
            }
            composable(
                Routes.TEST,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("subject") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("topic") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("count") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) {
                TestRoute(onFinished = { id -> nav.navigate("result/$id") { popUpTo(Routes.TEST) { inclusive = true } } })
            }
            composable(Routes.RESULT, arguments = listOf(navArgument("exam") { type = NavType.LongType })) { backStack ->
                val vm: ResultViewModel = hiltViewModel(backStack)
                ResultRoute(vm, onReview = { nav.navigate("review/${backStack.arguments?.getLong("exam")}") }, onHome = { nav.popBackStack(Routes.HOME, false) })
            }
            composable(Routes.REVIEW, arguments = listOf(navArgument("exam") { type = NavType.LongType })) { ReviewRoute() }
            composable(Routes.HISTORY) { HistoryRoute(onOpen = { nav.navigate("result/$it") }) }
            composable(Routes.CALCULATOR) { CalculatorScreen() }
            composable(Routes.SETTINGS) { SettingsRoute(settingsVm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(route: String?, nav: NavHostController) {
    if (route == null || route == Routes.ONBOARDING) return
    val title = when (route) {
        Routes.HOME -> R.string.app_name
        Routes.SUBJECTS -> R.string.subjects
        Routes.TOPICS -> R.string.topics
        Routes.RESULT -> R.string.result
        Routes.REVIEW -> R.string.review_mistakes
        Routes.HISTORY -> R.string.history
        Routes.CALCULATOR -> R.string.calculator
        Routes.SETTINGS -> R.string.settings
        else -> R.string.test
    }
    TopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            if (route != Routes.HOME) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
        },
        actions = {
            if (route == Routes.HOME) IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
        },
    )
}
