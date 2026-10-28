package it.halimeo.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val category: String = "Ricordo",
    val memoryDate: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: String = "PENDING"
)

data class DiaryEntryDto(
    val id: String, val title: String, val body: String, val category: String,
    val memoryDate: String, val createdAt: Long, val updatedAt: Long, val deletedAt: Long? = null
)

fun DiaryEntry.toDto() = DiaryEntryDto(id,title,body,category,memoryDate,createdAt,updatedAt,deletedAt)
fun DiaryEntryDto.toEntity() = DiaryEntry(id,title,body,category,memoryDate,createdAt,updatedAt,deletedAt,"SYNCED")

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE deletedAt IS NULL ORDER BY memoryDate DESC, updatedAt DESC")
    fun observeAll(): Flow<List<DiaryEntry>>
    @Query("SELECT * FROM diary_entries WHERE id=:id LIMIT 1")
    suspend fun get(id: String): DiaryEntry?
    @Query("SELECT * FROM diary_entries WHERE syncState != 'SYNCED' ORDER BY updatedAt")
    suspend fun pending(): List<DiaryEntry>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(entry: DiaryEntry)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertAll(entries: List<DiaryEntry>)
    @Query("UPDATE diary_entries SET syncState='SYNCED' WHERE id=:id AND updatedAt=:updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT * FROM diary_entries ORDER BY updatedAt DESC")
    suspend fun allForBackup(): List<DiaryEntry>
}

@Database(entities=[DiaryEntry::class], version=1, exportSchema=true)
abstract class HaliMeoDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    companion object {
        @Volatile private var instance: HaliMeoDatabase? = null
        fun get(context: android.content.Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HaliMeoDatabase::class.java, "halimeo.db").build().also { instance=it }
        }
    }
}
