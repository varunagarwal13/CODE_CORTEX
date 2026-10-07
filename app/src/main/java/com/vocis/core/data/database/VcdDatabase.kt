package com.vocis.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vocis.core.data.dao.ContactVoiceprintDao
import com.vocis.core.data.dao.VcdCallHistoryDao
import com.vocis.core.data.entity.ContactVoiceprintEntity
import com.vocis.core.data.entity.VcdCallHistoryEntity

@Database(
    entities = [
        ContactVoiceprintEntity::class,
        VcdCallHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VcdDatabase : RoomDatabase() {

    abstract fun contactVoiceprintDao(): ContactVoiceprintDao
    abstract fun vcdCallHistoryDao(): VcdCallHistoryDao

    companion object {
        private const val DB_NAME = "vcd.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contact_voiceprints ADD COLUMN baselineSynthetic REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE contact_voiceprints ADD COLUMN variantLabels TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contact_voiceprints ADD COLUMN variantBaselines TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: VcdDatabase? = null

        fun getInstance(context: Context): VcdDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VcdDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
