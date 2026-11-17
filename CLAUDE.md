# Loyiha: Abituriyent 189 (2-ilova)

Oliy ta'limga kirish testi mashqi real ball tizimi bilan: majburiy 3 fan × 10 savol × 1,1; 1-mutaxassislik 30 × 3,1; 2-mutaxassislik 30 × 2,1. Jami 90 savol, 189 ball.

## Texnologiyalar
Kotlin 2.0, Jetpack Compose, Material 3, MVVM, Hilt, Room, DataStore, Navigation Compose, WorkManager, AdMob, UMP.
minSdk 24, compileSdk/targetSdk 34, `gradle/libs.versions.toml`.

## Modullar
- `core-common`, `core-ui`, `core-data`, `core-ads` — PravaTest loyihasidan olingan umumiy modullar (savol ekrani komponentlari bilan)
- `app-abituriyent` — ilovaning o'zi

## Asosiy joylar
- `domain/Scoring.kt` — ball hisoblash (o'ndan birlarda butun son bilan, suzuvchi nuqta xatosisiz), kalkulyator, streak. Unit test: `DomainTest`.
- `data/Content.kt` — `assets/content.json` (ichki baza) va `CONTENT_URL` (local.properties: `content.url=`) dan yangilash. Firebase Storage'dagi ochiq JSON havolasini qo'yish kifoya; `version` oshsa, baza yangilanadi.
- `data/AbiRepository.kt` — simulyatsiya tarkibi, yo'nalish bo'yicha og'irliklar, kunlik test, Leitner, natijalar.
- `ui/test/TestViewModel.kt` — simulyatsiya (taymer, savollar xaritasi, belgilash) va mashq rejimlari.
- `reminder/DailyReminder.kt` — kunlik test eslatmasi (WorkManager).
- Simulyatsiya davomiyligi sozlamada (standart 180 daqiqa) — relizdan oldin rasmiy manba bilan tekshiring.

## Kontent
- Hozir 10 ta to'plamda jami 60 ta NAMUNAVIY savol (har fanga 6 ta). MVP uchun har to'plamda kamida 300 ta kerak.
- Savollar mualliflar tomonidan yozilishi yoki litsenziya bilan olinishi shart. BMBA yoki boshqa nashrlar testlarini nusxalash MUMKIN EMAS.
- Har savolda `source` majburiy, 4 ta variant (A–D). `ContentValidator` testi tekshiradi.

## Qoidalar
- Matnlar `strings.xml` da: `values` = o'zbekcha (lotin), `values-ru` = ruscha.
- Faqat AdMob TEST ID lari; haqiqiy ID lar `local.properties` dan (faqat release).
- Reklama: banner — bosh sahifa, fanlar, mavzular, tarix, kalkulyator; test ekranida YO'Q. Interstitial — natija ekraniga kirishda. Rewarded — izohni ochish. App open — kuniga 1.
- Har bir ViewModel va hisoblash funksiyasi uchun unit test; build va testlar o'tishi shart; har bosqich — alohida commit.

## Build (JDK 17)
```bash
JAVA_HOME="/c/Users/HP/.jdks/corretto-17.0.5" ./gradlew assembleDebug testDebugUnitTest
```

## Texnik topshiriq
To'liq TZ: docs/TZ.md
