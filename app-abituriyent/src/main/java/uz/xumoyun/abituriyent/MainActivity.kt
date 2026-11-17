package uz.xumoyun.abituriyent

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import uz.xumoyun.core.ads.AdsManager
import uz.xumoyun.core.common.ThemeMode
import uz.xumoyun.core.data.CommonSettings
import uz.xumoyun.core.ui.theme.AppTheme
import uz.xumoyun.abituriyent.ui.LocalAds
import uz.xumoyun.abituriyent.ui.AbiPalette
import uz.xumoyun.abituriyent.ui.AbiRoot
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var ads: AdsManager
    @Inject lateinit var common: CommonSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ads.gatherConsent(this)
        val app = application as AbituriyentApp
        setContent {
            val theme by common.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val onboardingDone by common.onboardingDone.collectAsStateWithLifecycle(null)
            val ready by app.contentReady.collectAsStateWithLifecycle()
            AppTheme(AbiPalette, theme) {
                CompositionLocalProvider(LocalAds provides ads) {
                    Surface {
                        val done = onboardingDone
                        if (done == null || !ready) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        } else {
                            AbiRoot(startWithOnboarding = !done)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ads.showAppOpenIfEligible(this)
    }
}
