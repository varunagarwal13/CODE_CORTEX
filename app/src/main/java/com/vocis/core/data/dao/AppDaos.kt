package com.vocis.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vocis.core.data.entity.AttackContextEntity
import com.vocis.core.data.entity.AuditLogEntity
import com.vocis.core.data.entity.CallerIdentityEntity
import com.vocis.core.data.entity.FamilyContactEntity
import com.vocis.core.data.entity.InteractionEntity
import com.vocis.core.data.entity.ProtectionPolicyEntity
import com.vocis.core.data.entity.SecurityEventEntity
import com.vocis.core.data.entity.SecurityIncidentEntity
import com.vocis.core.data.entity.ThreatRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interaction: InteractionEntity)

    @Update
    suspend fun update(interaction: InteractionEntity)

    @Query("SELECT * FROM interactions WHERE id = :id")
    suspend fun getById(id: String): InteractionEntity?

    @Query("SELECT * FROM interactions ORDER BY timestampMs DESC")
    fun getAllFlow(): Flow<List<InteractionEntity>>

    @Query("SELECT * FROM interactions ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<InteractionEntity>

    @Query("SELECT * FROM interactions WHERE callerPhoneNumber = :number ORDER BY timestampMs DESC")
    suspend fun getByCaller(number: String): List<InteractionEntity>
}

@Dao
interface SecurityEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SecurityEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<SecurityEventEntity>)

    @Query("SELECT * FROM security_events WHERE timestamp >= :timestampMs ORDER BY timestamp DESC")
    suspend fun getEventsSince(timestampMs: Long): List<SecurityEventEntity>

    @Query("SELECT * FROM security_events WHERE interactionId = :interactionId")
    suspend fun getByInteractionId(interactionId: String): List<SecurityEventEntity>

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SecurityEventEntity>
}

@Dao
interface SecurityIncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: SecurityIncidentEntity)

    @Update
    suspend fun update(incident: SecurityIncidentEntity)

    @Query("SELECT * FROM security_incidents WHERE incidentId = :id")
    suspend fun getById(id: String): SecurityIncidentEntity?

    @Query("SELECT * FROM security_incidents WHERE status IN ('NEW', 'INVESTIGATING', 'CONFIRMED') ORDER BY createdAt DESC LIMIT 1")
    fun getActiveIncidentFlow(): Flow<SecurityIncidentEntity?>

    @Query("SELECT * FROM security_incidents ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<SecurityIncidentEntity>>

    @Query("SELECT * FROM security_incidents ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SecurityIncidentEntity>
}

@Dao
interface CallerIdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(identity: CallerIdentityEntity)

    @Query("SELECT * FROM caller_identities WHERE phoneNumber = :phoneNumber")
    suspend fun getByPhoneNumber(phoneNumber: String): CallerIdentityEntity?

    @Query("SELECT * FROM caller_identities")
    suspend fun getAll(): List<CallerIdentityEntity>
}

@Dao
interface ProtectionPolicyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPolicy(policy: ProtectionPolicyEntity)

    @Query("SELECT * FROM protection_policies WHERE policyId = 'default_policy' LIMIT 1")
    suspend fun getPolicy(): ProtectionPolicyEntity?

    @Query("SELECT * FROM protection_policies WHERE policyId = 'default_policy' LIMIT 1")
    fun getPolicyFlow(): Flow<ProtectionPolicyEntity?>
}

@Dao
interface AttackContextDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(context: AttackContextEntity)

    @Query("SELECT * FROM attack_contexts WHERE isActive = 1 AND expiresAt > :nowMs")
    suspend fun getActiveContexts(nowMs: Long): List<AttackContextEntity>

    @Query("UPDATE attack_contexts SET isActive = 0 WHERE contextId = :contextId")
    suspend fun deactivateContext(contextId: String)
}

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AuditLogEntity>
}

@Dao
interface FamilyContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: FamilyContactEntity)

    @Delete
    suspend fun delete(contact: FamilyContactEntity)

    @Query("SELECT * FROM family_contacts ORDER BY name ASC")
    suspend fun getAll(): List<FamilyContactEntity>

    @Query("SELECT * FROM family_contacts ORDER BY name ASC")
    fun getAllFlow(): Flow<List<FamilyContactEntity>>
}

@Dao
interface ThreatRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ThreatRuleEntity>)

    @Query("SELECT * FROM threat_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<ThreatRuleEntity>

    @Query("SELECT * FROM threat_rules")
    suspend fun getAll(): List<ThreatRuleEntity>
}

@Dao
interface TrustedCallerDao {
    @Query("SELECT * FROM trusted_callers ORDER BY addedAt DESC")
    fun getTrustedCallersFlow(): Flow<List<com.vocis.core.data.entity.TrustedCallerEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM trusted_callers WHERE phoneNumber = :phoneNumber)")
    suspend fun isTrusted(phoneNumber: String): Boolean

    @Query("SELECT * FROM trusted_callers WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getTrustedCallerSync(phoneNumber: String): com.vocis.core.data.entity.TrustedCallerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrustedCaller(caller: com.vocis.core.data.entity.TrustedCallerEntity)

    @Query("DELETE FROM trusted_callers WHERE phoneNumber = :phoneNumber")
    suspend fun deleteTrustedCaller(phoneNumber: String)
}

