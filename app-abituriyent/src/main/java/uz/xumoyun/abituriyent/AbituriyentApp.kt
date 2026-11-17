package uz.xumoyun.abituriyent

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.xumoyun.abituriyent.data.AbiSettings
import uz.xumoyun.abituriyent.data.ContentLoader
import uz.xumoyun.abituriyent.reminder.DailyReminder
import javax.inject.Inject

@HiltAndroidApp
class AbituriyentApp : Application() {
    @Inject lateinit var loader: ContentLoader
    @Inject lateinit var settings: AbiSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _contentReady = MutableStateFlow(false)
    val contentReady: StateFlow<Boolean> = _contentReady.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            loader.ensureLoaded()
            _contentReady.value = true
            // Serverda yangi savollar bo'lsa — fonda yuklab, bazani yangilaydi.
            loader.checkRemoteUpdate()
        }
        scope.launch { settings.reminderMinutes.collect { DailyReminder.schedule(this@AbituriyentApp, it) } }
    }
}
