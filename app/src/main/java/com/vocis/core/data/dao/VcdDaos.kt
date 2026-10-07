package com.vocis.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vocis.core.data.entity.ContactVoiceprintEntity
import com.vocis.core.data.entity.VcdCallHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactVoiceprintDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(voiceprint: ContactVoiceprintEntity): Long

    @Update
    suspend fun update(voiceprint: ContactVoiceprintEntity)

    @Delete
    suspend fun delete(voiceprint: ContactVoiceprintEntity)

    @Query("SELECT * FROM contact_voiceprints WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getByPhoneNumber(phoneNumber: String): ContactVoiceprintEntity?

    @Query("SELECT * FROM contact_voiceprints WHERE contactId = :id")
    suspend fun getById(id: Long): ContactVoiceprintEntity?

    @Query("SELECT * FROM contact_voiceprints ORDER BY name ASC")
    fun getAllFlow(): Flow<List<ContactVoiceprintEntity>>

    @Query("SELECT * FROM contact_voiceprints ORDER BY name ASC")
    suspend fun getAll(): List<ContactVoiceprintEntity>
}

@Dao
interface VcdCallHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(callSession: VcdCallHistoryEntity): Long

    @Query("SELECT * FROM vcd_call_history ORDER BY startedAtEpochMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<VcdCallHistoryEntity>

    @Query("SELECT * FROM vcd_call_history ORDER BY startedAtEpochMs DESC")
    fun getAllFlow(): Flow<List<VcdCallHistoryEntity>>
}
