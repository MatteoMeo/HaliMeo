package it.halimeo.app.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val syncState: String = "PENDING",
    val photoUri: String? = null,
    val photoUpdatedAt: Long = 0L,
    val photoDeletedAt: Long? = null,
    val photoSyncState: String = "SYNCED"
)

data class DiaryEntryDto(
    val id: String, val title: String, val body: String, val category: String,
    val memoryDate: String, val createdAt: Long, val updatedAt: Long, val deletedAt: Long? = null,
    val photoUpdatedAt: Long = 0L, val photoDeletedAt: Long? = null
)

fun DiaryEntry.toDto() = DiaryEntryDto(id,title,body,category,memoryDate,createdAt,updatedAt,deletedAt,photoUpdatedAt,photoDeletedAt)
fun DiaryEntryDto.toEntity() = DiaryEntry(id,title,body,category,memoryDate,createdAt,updatedAt,deletedAt,"SYNCED",null,photoUpdatedAt,photoDeletedAt,"PENDING")

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE deletedAt IS NULL ORDER BY memoryDate DESC, updatedAt DESC")
    fun observeAll(): Flow<List<DiaryEntry>>
    @Query("SELECT * FROM diary_entries WHERE id=:id LIMIT 1")
    suspend fun get(id: String): DiaryEntry?
    @Query("SELECT * FROM diary_entries WHERE syncState != 'SYNCED' ORDER BY updatedAt")
    suspend fun pending(): List<DiaryEntry>
    @Query("SELECT * FROM diary_entries WHERE photoSyncState != 'SYNCED' ORDER BY photoUpdatedAt")
    suspend fun pendingPhotos(): List<DiaryEntry>
    @Query("UPDATE diary_entries SET photoSyncState='SYNCED' WHERE id=:id AND photoUpdatedAt=:photoUpdatedAt")
    suspend fun markPhotoSynced(id: String, photoUpdatedAt: Long)
    @Query("UPDATE diary_entries SET photoUpdatedAt=:revision, photoDeletedAt=:deletedAt, photoSyncState='SYNCED' WHERE id=:id AND photoUpdatedAt=:expectedRevision")
    suspend fun confirmPhotoRevision(id: String, expectedRevision: Long, revision: Long, deletedAt: Long?)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(entry: DiaryEntry)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertAll(entries: List<DiaryEntry>)
    @Query("UPDATE diary_entries SET syncState='SYNCED' WHERE id=:id AND updatedAt=:updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT * FROM diary_entries ORDER BY updatedAt DESC")
    suspend fun allForBackup(): List<DiaryEntry>
}

@Database(entities=[DiaryEntry::class], version=3, exportSchema=true)
abstract class HaliMeoDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE diary_entries ADD COLUMN photoUri TEXT")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE diary_entries ADD COLUMN photoUpdatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE diary_entries ADD COLUMN photoDeletedAt INTEGER")
                database.execSQL("ALTER TABLE diary_entries ADD COLUMN photoSyncState TEXT NOT NULL DEFAULT 'SYNCED'")
            }
        }
        @Volatile private var instance: HaliMeoDatabase? = null
        fun get(context: android.content.Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HaliMeoDatabase::class.java, "halimeo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance=it }
        }
    }
}
