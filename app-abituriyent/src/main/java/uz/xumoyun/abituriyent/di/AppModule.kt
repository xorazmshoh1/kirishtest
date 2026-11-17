package uz.xumoyun.abituriyent.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uz.xumoyun.abituriyent.BuildConfig
import uz.xumoyun.abituriyent.data.db.AbiDao
import uz.xumoyun.abituriyent.data.db.AbiDatabase
import uz.xumoyun.core.ads.AdFeatures
import uz.xumoyun.core.ads.AdUnitIds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AbiDatabase =
        Room.databaseBuilder(context, AbiDatabase::class.java, "abituriyent.db").build()

    @Provides fun dao(db: AbiDatabase): AbiDao = db.dao()

    @Provides @Singleton
    fun adUnitIds() = AdUnitIds(
        banner = BuildConfig.AD_BANNER,
        interstitial = BuildConfig.AD_INTERSTITIAL,
        rewarded = BuildConfig.AD_REWARDED,
        appOpen = BuildConfig.AD_APPOPEN,
    )

    @Provides @Singleton
    fun adFeatures() = AdFeatures()
}
