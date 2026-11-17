package uz.xumoyun.abituriyent.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.xumoyun.abituriyent.BuildConfig
import uz.xumoyun.abituriyent.data.db.AbiDao
import uz.xumoyun.abituriyent.data.db.DirectionEntity
import uz.xumoyun.abituriyent.data.db.OptionEntity
import uz.xumoyun.abituriyent.data.db.QuestionEntity
import uz.xumoyun.abituriyent.data.db.SubjectEntity
import uz.xumoyun.abituriyent.data.db.TopicEntity
import uz.xumoyun.abituriyent.domain.Block
import uz.xumoyun.core.data.AssetSource
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AbiContent(
    val version: Int,
    val subjects: List<SubjectJson>,
    val directions: List<DirectionJson>,
    val topics: List<TopicJson>,
    val questions: List<QuestionJson>,
)

@Serializable
data class SubjectJson(val id: Int, val uz: String, val ru: String, val block: Block, val color: String)

@Serializable
data class DirectionJson(val id: Int, val uz: String, val ru: String, val subject1: Int, val subject2: Int)

@Serializable
data class TopicJson(val id: Int, val subject: Int, val uz: String, val ru: String)

@Serializable
data class QuestionJson(
    val id: Int,
    val subject: Int,
    val topic: Int,
    val text: String,
    val latex: String? = null,
    val image: String? = null,
    val options: List<String>,
    val correct: Int,
    val explanation: String,
    val difficulty: Int = 1,
    val source: String,
)

object ContentValidator {
    fun validate(c: AbiContent): List<String> = buildList {
        val subjectIds = c.subjects.map { it.id }.toSet()
        val topicSubject = c.topics.associate { it.id to it.subject }
        c.directions.forEach { d ->
            if (d.subject1 !in subjectIds || d.subject2 !in subjectIds) add("yo'nalish ${d.id}: noma'lum fan")
        }
        val ids = mutableSetOf<Int>()
        c.questions.forEach { q ->
            if (!ids.add(q.id)) add("${q.id}: takroriy id")
            if (q.subject !in subjectIds) add("${q.id}: noma'lum fan")
            if (topicSubject[q.topic] != q.subject) add("${q.id}: mavzu boshqa fanga tegishli")
            if (q.options.size != 4) add("${q.id}: 4 ta variant bo'lishi kerak (A, B, C, D)")
            if (q.correct !in q.options.indices) add("${q.id}: to'g'ri javob indeksi noto'g'ri")
            if (q.options.toSet().size != q.options.size) add("${q.id}: takroriy variantlar")
            if (q.source.isBlank()) add("${q.id}: manba ko'rsatilmagan")
        }
    }
}

/**
 * Savollar bazasi: ichki assets/content.json yoki serverdan (Firebase Storage) yuklangan yangi versiya.
 * Versiya raqami oshsa, Room bazasi yangilanadi — ilovani qayta chiqarish shart emas.
 */
@Singleton
class ContentLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assets: AssetSource,
    private val json: Json,
    private val dao: AbiDao,
    private val settings: AbiSettings,
) {
    private val cacheFile get() = File(context.filesDir, "content.json")

    fun parse(text: String): AbiContent = json.decodeFromString(AbiContent.serializer(), text)

    /** Eng yangi mavjud kontent: yuklab olingan (agar versiyasi kattaroq bo'lsa) yoki ichki. */
    private fun bestLocal(): AbiContent {
        val bundled = parse(assets.readText("content.json"))
        val cached = runCatching { parse(cacheFile.readText()) }.getOrNull()
        return if (cached != null && cached.version > bundled.version) cached else bundled
    }

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        val content = bestLocal()
        if (settings.contentVersion() != content.version || dao.questionCount() == 0) apply(content)
    }

    /** Serverdan yangilanishni tekshiradi. Internet bo'lmasa yoki URL berilmagan bo'lsa — jim o'tadi. */
    suspend fun checkRemoteUpdate(): Boolean = withContext(Dispatchers.IO) {
        val url = BuildConfig.CONTENT_URL.takeIf { it.isNotBlank() } ?: return@withContext false
        val text = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000
                readTimeout = 20_000
                try { inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
            }
        }.getOrNull() ?: return@withContext false
        val remote = runCatching { parse(text) }.getOrNull() ?: return@withContext false
        if (remote.version <= (settings.contentVersion() ?: 0) || ContentValidator.validate(remote).isNotEmpty()) return@withContext false
        cacheFile.writeText(text)
        apply(remote)
        true
    }

    private suspend fun apply(c: AbiContent) {
        dao.replaceContent(
            subjects = c.subjects.mapIndexed { i, s -> SubjectEntity(s.id, s.uz, s.ru, s.block, parseColor(s.color), i) },
            directions = c.directions.map { DirectionEntity(it.id, it.uz, it.ru, it.subject1, it.subject2) },
            topics = c.topics.map { TopicEntity(it.id, it.subject, it.uz, it.ru) },
            questions = c.questions.map { q -> QuestionEntity(q.id, q.subject, q.topic, q.text, q.latex, q.image, q.explanation, q.difficulty, q.source) },
            options = c.questions.flatMap { q ->
                val order = displayOrder(q.id, q.options.size)
                q.options.mapIndexed { i, text -> OptionEntity(q.id * 10 + i, q.id, LABELS[order[i]], text, i == q.correct) }
            },
        )
        settings.setContentVersion(c.version)
    }

    companion object {
        val LABELS = listOf("A", "B", "C", "D", "E")

        /** Variantlar tartibi savol id si bo'yicha barqaror aralashtiriladi. */
        fun displayOrder(questionId: Int, size: Int): List<Int> =
            (0 until size).shuffled(kotlin.random.Random(questionId * 7919L))

        fun parseColor(hex: String): Long = ("FF" + hex.removePrefix("#")).toLong(16)
    }
}
