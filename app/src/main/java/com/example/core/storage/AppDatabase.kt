package com.example.core.storage

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "upload_history")
data class UploadHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val taskId: String,
  val projectName: String,
  val repoFullName: String,
  val branch: String,
  val commitSha: String?,
  val commitUrl: String?,
  val repoUrl: String,
  val fileCount: Int,
  val totalSizeBytes: Long,
  val timestamp: Long,
  val status: String,
  val errorDetails: String?,
  val logs: String,
  val previousCommitSha: String? = null,
  val previousTreeSha: String? = null,
  val newTreeSha: String? = null,
  val updateMode: String? = null,
  val addedCount: Int = 0,
  val modifiedCount: Int = 0,
  val deletedCount: Int = 0,
  val deltaBytes: Long = 0L,
)

@Entity(tableName = "upload_tasks")
data class UploadTaskEntity(
  @PrimaryKey val id: String,
  val projectName: String,
  val repoFullName: String,
  val branch: String,
  val commitMessage: String,
  val totalBytes: Long,
  val uploadedBytes: Long,
  val speedBytesPerSec: Double,
  val etaSeconds: Long,
  val stage: String,
  val fileCount: Int,
  val lfsFileCount: Int,
  val commitSha: String?,
  val errorCode: String?,
  val errorCause: String?,
  val errorFix: String?,
  val logs: String,
  val payloadJson: String,
  val createdAt: Long,
  val updatedAt: Long,
)

@Dao
interface UploadHistoryDao {
  @Query("SELECT * FROM upload_history ORDER BY timestamp DESC")
  fun observeAll(): Flow<List<UploadHistoryEntity>>

  @Query("SELECT COUNT(*) FROM upload_history WHERE status = 'COMPLETED'")
  fun observeCompletedCount(): Flow<Int>

  @Query("SELECT COALESCE(SUM(totalSizeBytes), 0) FROM upload_history WHERE status = 'COMPLETED'")
  fun observeTotalUploadedBytes(): Flow<Long>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: UploadHistoryEntity)

  @Query("DELETE FROM upload_history")
  suspend fun deleteAll()
}

@Dao
interface UploadTaskDao {
  @Query("SELECT * FROM upload_tasks ORDER BY updatedAt DESC")
  fun observeAll(): Flow<List<UploadTaskEntity>>

  @Query("SELECT * FROM upload_tasks WHERE id = :taskId LIMIT 1")
  suspend fun getById(taskId: String): UploadTaskEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: UploadTaskEntity)

  @Query("DELETE FROM upload_tasks WHERE id = :taskId")
  suspend fun deleteById(taskId: String)
}

@Database(
  entities = [UploadHistoryEntity::class, UploadTaskEntity::class],
  version = 2,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun uploadHistoryDao(): UploadHistoryDao
  abstract fun uploadTaskDao(): UploadTaskDao

  companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE upload_history ADD COLUMN previousCommitSha TEXT")
        db.execSQL("ALTER TABLE upload_history ADD COLUMN previousTreeSha TEXT")
        db.execSQL("ALTER TABLE upload_history ADD COLUMN newTreeSha TEXT")
        db.execSQL("ALTER TABLE upload_history ADD COLUMN updateMode TEXT")
        db.execSQL("ALTER TABLE upload_history ADD COLUMN addedCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE upload_history ADD COLUMN modifiedCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE upload_history ADD COLUMN deletedCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE upload_history ADD COLUMN deltaBytes INTEGER NOT NULL DEFAULT 0")
      }
    }
  }
}
