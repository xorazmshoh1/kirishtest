package uz.xumoyun.abituriyent.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AbiSettings @Inject constructor(private val store: DataStore<Preferences>) {

    /** Tanlangan yo'nalish id si; 0 — faqat majburiy fanlar rejimi. */
    val directionId: Flow<Int> = store.data.map { it[DIRECTION] ?: 0 }.distinctUntilChanged()

    /** Kunlik eslatma vaqti (daqiqa, 00:00 dan); -1 — o'chiq. */
    val reminderMinutes: Flow<Int> = store.data.map { it[REMINDER] ?: DEFAULT_REMINDER }.distinctUntilChanged()

    /**
     * To'liq simulyatsiya davomiyligi (daqiqa). TZ: relizdan oldin rasmiy manbadan tekshirilib
     * shu yerda o'zgartiriladi.
     */
    val simulationMinutes: Flow<Int> = store.data.map { it[SIM_MINUTES] ?: DEFAULT_SIM_MINUTES }.distinctUntilChanged()

    suspend fun setDirection(id: Int) = store.edit { it[DIRECTION] = id }
    suspend fun setReminder(minutes: Int) = store.edit { it[REMINDER] = minutes }
    suspend fun setSimulationMinutes(m: Int) = store.edit { it[SIM_MINUTES] = m.coerceIn(30, 300) }
    suspend fun currentDirection(): Int = directionId.first()
    suspend fun currentSimulationMinutes(): Int = simulationMinutes.first()
    suspend fun currentReminder(): Int = reminderMinutes.first()

    suspend fun contentVersion(): Int? = store.data.first()[CONTENT_VERSION]
    suspend fun setContentVersion(v: Int) = store.edit { it[CONTENT_VERSION] = v }

    companion object {
        const val DEFAULT_SIM_MINUTES = 180
        const val DEFAULT_REMINDER = 19 * 60
        private val DIRECTION = intPreferencesKey("direction")
        private val REMINDER = intPreferencesKey("reminder_minutes")
        private val SIM_MINUTES = intPreferencesKey("simulation_minutes")
        private val CONTENT_VERSION = intPreferencesKey("content_version")
    }
}
