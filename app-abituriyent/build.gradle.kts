import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun local(key: String): String? = localProps.getProperty(key)?.takeIf { it.isNotBlank() }

/** Google test ID lari. Haqiqiy ID lar faqat release'da local.properties dan olinadi. */
val testIds = mapOf(
    "appId" to "ca-app-pub-3940256099942544~3347511713",
    "banner" to "ca-app-pub-3940256099942544/9214589741",
    "interstitial" to "ca-app-pub-3940256099942544/1033173712",
    "rewarded" to "ca-app-pub-3940256099942544/5224354917",
    "appOpen" to "ca-app-pub-3940256099942544/9257395921",
)

android {
    namespace = "uz.xumoyun.abituriyent"
    compileSdk = 34

    defaultConfig {
        applicationId = "uz.xumoyun.abituriyent"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
        // Savollar bazasini yangilash uchun JSON manzili (masalan Firebase Storage ochiq havolasi). Bo'sh — faqat ichki baza.
        buildConfigField("String", "CONTENT_URL", "\"${local("content.url") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            local("keystore.file")?.let { storeFile = file(it) }
            storePassword = local("keystore.password")
            keyAlias = local("keystore.alias")
            keyPassword = local("keystore.aliasPassword")
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testIds.getValue("appId")
            listOf("banner", "interstitial", "rewarded", "appOpen").forEach { key ->
                buildConfigField("String", "AD_${key.uppercase()}", "\"${testIds.getValue(key)}\"")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (local("keystore.file") != null) signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["admobAppId"] = local("admob.appId") ?: testIds.getValue("appId")
            listOf("banner", "interstitial", "rewarded", "appOpen").forEach { key ->
                buildConfigField("String", "AD_${key.uppercase()}", "\"${local("admob.$key") ?: testIds.getValue(key)}\"")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources { generateLocaleConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar)
    implementation(project(":core-common"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))
    implementation(project(":core-ads"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
