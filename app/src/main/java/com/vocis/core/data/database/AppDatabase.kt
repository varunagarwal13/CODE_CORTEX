package com.vocis.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vocis.core.data.converter.DatabaseConverters
import com.vocis.core.data.dao.AttackContextDao
import com.vocis.core.data.dao.AuditLogDao
import com.vocis.core.data.dao.CallerIdentityDao
import com.vocis.core.data.dao.FamilyContactDao
import com.vocis.core.data.dao.InteractionDao
import com.vocis.core.data.dao.ProtectionPolicyDao
import com.vocis.core.data.dao.SecurityEventDao
import com.vocis.core.data.dao.SecurityIncidentDao
import com.vocis.core.data.dao.ThreatRuleDao
import com.vocis.core.data.dao.TrustedCallerDao
import com.vocis.core.data.entity.AttackContextEntity
import com.vocis.core.data.entity.AuditLogEntity
import com.vocis.core.data.entity.CallerIdentityEntity
import com.vocis.core.data.entity.FamilyContactEntity
import com.vocis.core.data.entity.InteractionEntity
import com.vocis.core.data.entity.ProtectionPolicyEntity
import com.vocis.core.data.entity.SecurityEventEntity
import com.vocis.core.data.entity.SecurityIncidentEntity
import com.vocis.core.data.entity.ThreatRuleEntity
import com.vocis.core.data.entity.TrustedCallerEntity

@Database(
    entities = [
        InteractionEntity::class,
        SecurityEventEntity::class,
        SecurityIncidentEntity::class,
        CallerIdentityEntity::class,
        ProtectionPolicyEntity::class,
        AttackContextEntity::class,
        AuditLogEntity::class,
        FamilyContactEntity::class,
        ThreatRuleEntity::class,
        TrustedCallerEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun interactionDao(): InteractionDao
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun securityIncidentDao(): SecurityIncidentDao
    abstract fun callerIdentityDao(): CallerIdentityDao
    abstract fun protectionPolicyDao(): ProtectionPolicyDao
    abstract fun attackContextDao(): AttackContextDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun familyContactDao(): FamilyContactDao
    abstract fun threatRuleDao(): ThreatRuleDao
    abstract fun trustedCallerDao(): TrustedCallerDao

    companion object {
        private const val DB_NAME = "app.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
