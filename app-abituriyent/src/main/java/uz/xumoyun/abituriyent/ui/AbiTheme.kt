package uz.xumoyun.abituriyent.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import uz.xumoyun.abituriyent.data.db.SubjectEntity
import uz.xumoyun.core.ads.AdsManager
import uz.xumoyun.core.ui.theme.AppPalette
import uz.xumoyun.core.ui.theme.AppTheme

/** TZ: asosiy rang indigo #3949AB, urg'u — to'q sariq #FB8C00 (streak va mukofotlar). */
val AbiPalette = AppPalette(seed = Color(0xFF3949AB), accent = Color(0xFFFB8C00))

val LocalAds = staticCompositionLocalOf<AdsManager?> { null }

@Composable
@ReadOnlyComposable
fun loc(uz: String, ru: String): String = if (LocalConfiguration.current.locales[0].language == "ru") ru else uz

@Composable
@ReadOnlyComposable
fun SubjectEntity.title(): String = loc(nameUz, nameRu)

val SubjectEntity.tint: Color get() = Color(color)

@Composable
fun AbiPreview(content: @Composable () -> Unit) {
    AppTheme(AbiPalette) { Surface { content() } }
}
