package uz.xumoyun.abituriyent.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uz.xumoyun.abituriyent.domain.Block
import uz.xumoyun.abituriyent.domain.ExamType

@Entity(tableName = "subject")
data class SubjectEntity(
    @PrimaryKey val id: Int,
    val nameUz: String,
    val nameRu: String,
    val block: Block,
    /** ARGB rang (kartalar va grafiklarda). */
    val color: Long,
    val order: Int,
)

@Entity(tableName = "direction")
data class DirectionEntity(@PrimaryKey val id: Int, val nameUz: String, val nameRu: String, val subject1Id: Int, val subject2Id: Int)

@Entity(tableName = "topic", indices = [Index("subjectId")])
data class TopicEntity(@PrimaryKey val id: Int, val subjectId: Int, val nameUz: String, val nameRu: String)

@Entity(tableName = "question", indices = [Index("subjectId"), Index("topicId")])
data class QuestionEntity(
    @PrimaryKey val id: Int,
    val subjectId: Int,
    val topicId: Int,
    val text: String,
    val latex: String?,
    val imageUrl: String?,
    val explanation: String,
    val difficulty: Int,
    val source: String,
)

@Entity(tableName = "option", indices = [Index("questionId")])
data class OptionEntity(@PrimaryKey val id: Int, val questionId: Int, val label: String, val text: String, val isCorrect: Boolean)

@Entity(tableName = "exam")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ExamType,
    val subject1Id: Int?,
    val subject2Id: Int?,
    val startedAt: Long,
    val finishedAt: Long,
    val score: Double,
    val maxScore: Double,
)

@Entity(tableName = "exam_answer", primaryKeys = ["examId", "questionId"])
data class ExamAnswerEntity(val examId: Long, val questionId: Int, val optionId: Int?, val isCorrect: Boolean, val weight: Double)

@Entity(tableName = "question_stat")
data class QuestionStatEntity(
    @PrimaryKey val questionId: Int,
    val timesSeen: Int = 0,
    val timesWrong: Int = 0,
    val box: Int = 0,
    val nextReviewAt: Long = 0,
    val isFavorite: Boolean = false,
)

@Entity(tableName = "daily_streak")
data class DailyStreakEntity(@PrimaryKey val date: String, val completed: Boolean)

data class SubjectProgress(val subjectId: Int, val total: Int, val seen: Int, val correctPercent: Int)
data class TopicProgress(val topicId: Int, val total: Int, val seen: Int, val correctPercent: Int)

/** Fan bo'yicha bitta imtihondagi natija (natija jadvali uchun). */
data class SubjectScore(val subjectId: Int, val correct: Int, val total: Int, val score: Double)

@Dao
interface AbiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSubjects(items: List<SubjectEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDirections(items: List<DirectionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTopics(items: List<TopicEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQuestions(items: List<QuestionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOptions(items: List<OptionEntity>)
    @Query("DELETE FROM option") suspend fun clearOptions()
    @Query("DELETE FROM question") suspend fun clearQuestions()
    @Query("DELETE FROM topic") suspend fun clearTopics()
    @Query("DELETE FROM direction") suspend fun clearDirections()
    @Query("DELETE FROM subject") suspend fun clearSubjects()

    @Transaction
    suspend fun replaceContent(
        subjects: List<SubjectEntity>,
        directions: List<DirectionEntity>,
        topics: List<TopicEntity>,
        questions: List<QuestionEntity>,
        options: List<OptionEntity>,
    ) {
        clearOptions(); clearQuestions(); clearTopics(); clearDirections(); clearSubjects()
        insertSubjects(subjects); insertDirections(directions); insertTopics(topics); insertQuestions(questions); insertOptions(options)
    }

    @Query("SELECT COUNT(*) FROM question") suspend fun questionCount(): Int
    @Query("SELECT * FROM subject ORDER BY `order`") fun observeSubjects(): Flow<List<SubjectEntity>>
    @Query("SELECT * FROM direction ORDER BY id") fun observeDirections(): Flow<List<DirectionEntity>>
    @Query("SELECT * FROM topic WHERE subjectId = :subjectId ORDER BY id") fun observeTopics(subjectId: Int): Flow<List<TopicEntity>>

    @Query("SELECT * FROM question WHERE subjectId = :subjectId ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomBySubject(subjectId: Int, limit: Int): List<QuestionEntity>
    @Query("SELECT * FROM question WHERE topicId = :topicId ORDER BY id") suspend fun byTopic(topicId: Int): List<QuestionEntity>
    @Query("SELECT * FROM question WHERE id IN (:ids)") suspend fun byIds(ids: List<Int>): List<QuestionEntity>
    @Query("SELECT * FROM option WHERE questionId IN (:ids)") suspend fun optionsFor(ids: List<Int>): List<OptionEntity>

    /** Kunlik test uchun: hali ko'rilmagan savollar (keyin — eng kam ko'rilganlar). */
    @Query(
        """SELECT q.* FROM question q LEFT JOIN question_stat s ON s.questionId = q.id
           WHERE q.subjectId IN (:subjectIds) ORDER BY COALESCE(s.timesSeen, 0), q.id""",
    )
    suspend fun leastSeen(subjectIds: List<Int>): List<QuestionEntity>

    @Query("SELECT * FROM question_stat WHERE questionId = :id") suspend fun stat(id: Int): QuestionStatEntity?
    @Upsert suspend fun upsertStat(stat: QuestionStatEntity)
    @Query("SELECT questionId FROM question_stat WHERE timesWrong > 0 AND box < 3 ORDER BY nextReviewAt") fun observeMistakeIds(): Flow<List<Int>>
    @Query("SELECT questionId FROM question_stat WHERE isFavorite = 1") fun observeFavoriteIds(): Flow<List<Int>>

    @Query(
        """SELECT q.subjectId AS subjectId, COUNT(*) AS total,
           SUM(CASE WHEN s.timesSeen > 0 THEN 1 ELSE 0 END) AS seen,
           COALESCE(SUM(s.timesSeen - s.timesWrong) * 100 / MAX(SUM(s.timesSeen), 1), 0) AS correctPercent
           FROM question q LEFT JOIN question_stat s ON s.questionId = q.id GROUP BY q.subjectId""",
    )
    fun observeSubjectProgress(): Flow<List<SubjectProgress>>

    @Query(
        """SELECT q.topicId AS topicId, COUNT(*) AS total,
           SUM(CASE WHEN s.timesSeen > 0 THEN 1 ELSE 0 END) AS seen,
           COALESCE(SUM(s.timesSeen - s.timesWrong) * 100 / MAX(SUM(s.timesSeen), 1), 0) AS correctPercent
           FROM question q LEFT JOIN question_stat s ON s.questionId = q.id WHERE q.subjectId = :subjectId GROUP BY q.topicId""",
    )
    fun observeTopicProgress(subjectId: Int): Flow<List<TopicProgress>>

    @Insert suspend fun insertExam(exam: ExamEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExamAnswers(items: List<ExamAnswerEntity>)
    @Query("SELECT * FROM exam WHERE id = :id") suspend fun exam(id: Long): ExamEntity?
    @Query("SELECT * FROM exam_answer WHERE examId = :id") suspend fun examAnswers(id: Long): List<ExamAnswerEntity>

    @Query("SELECT * FROM exam WHERE type = 'SIMULATION' ORDER BY finishedAt DESC") fun observeSimulations(): Flow<List<ExamEntity>>
    @Query("SELECT * FROM exam WHERE type = 'SIMULATION' AND id < :id ORDER BY id DESC LIMIT 1") suspend fun previousSimulation(id: Long): ExamEntity?
    @Query("SELECT MAX(score) FROM exam WHERE type = 'SIMULATION' AND id < :id") suspend fun bestBefore(id: Long): Double?

    @Query(
        """SELECT q.subjectId AS subjectId, SUM(CASE WHEN a.isCorrect THEN 1 ELSE 0 END) AS correct, COUNT(*) AS total,
           SUM(CASE WHEN a.isCorrect THEN a.weight ELSE 0 END) AS score
           FROM exam_answer a JOIN question q ON q.id = a.questionId WHERE a.examId = :examId GROUP BY q.subjectId""",
    )
    suspend fun subjectScores(examId: Long): List<SubjectScore>

    @Upsert suspend fun upsertStreak(day: DailyStreakEntity)
    @Query("SELECT date FROM daily_streak WHERE completed = 1") fun observeCompletedDays(): Flow<List<String>>
}

@Database(
    entities = [
        SubjectEntity::class, DirectionEntity::class, TopicEntity::class, QuestionEntity::class, OptionEntity::class,
        ExamEntity::class, ExamAnswerEntity::class, QuestionStatEntity::class, DailyStreakEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AbiDatabase : RoomDatabase() {
    abstract fun dao(): AbiDao
}
