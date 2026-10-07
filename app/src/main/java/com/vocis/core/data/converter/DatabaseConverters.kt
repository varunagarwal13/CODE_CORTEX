package com.vocis.core.data.converter

import androidx.room.TypeConverter
import com.vocis.core.domain.model.EventType
import com.vocis.core.domain.model.IncidentStatus
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.ProtectionAction
import com.vocis.core.domain.model.ProtectionMode
import com.vocis.core.domain.model.RiskLevel

class DatabaseConverters {

    @TypeConverter
    fun fromRiskLevel(value: RiskLevel?): String? = value?.name

    @TypeConverter
    fun toRiskLevel(value: String?): RiskLevel? = value?.let {
        try { RiskLevel.valueOf(it) } catch (e: IllegalArgumentException) { RiskLevel.LOW }
    }

    @TypeConverter
    fun fromEventType(value: EventType?): String? = value?.name

    @TypeConverter
    fun toEventType(value: String?): EventType? = value?.let {
        try { EventType.valueOf(it) } catch (e: IllegalArgumentException) { EventType.SYSTEM_EVENT }
    }

    @TypeConverter
    fun fromIncidentType(value: IncidentType?): String? = value?.name

    @TypeConverter
    fun toIncidentType(value: String?): IncidentType? = value?.let {
        try { IncidentType.valueOf(it) } catch (e: IllegalArgumentException) { IncidentType.OTHER }
    }

    @TypeConverter
    fun fromIncidentStatus(value: IncidentStatus?): String? = value?.name

    @TypeConverter
    fun toIncidentStatus(value: String?): IncidentStatus? = value?.let {
        try { IncidentStatus.valueOf(it) } catch (e: IllegalArgumentException) { IncidentStatus.NEW }
    }

    @TypeConverter
    fun fromProtectionAction(value: ProtectionAction?): String? = value?.name

    @TypeConverter
    fun toProtectionAction(value: String?): ProtectionAction? = value?.let {
        try { ProtectionAction.valueOf(it) } catch (e: IllegalArgumentException) { ProtectionAction.MONITOR_ONLY }
    }

    @TypeConverter
    fun fromProtectionMode(value: ProtectionMode?): String? = value?.name

    @TypeConverter
    fun toProtectionMode(value: String?): ProtectionMode? = value?.let {
        try { ProtectionMode.valueOf(it) } catch (e: IllegalArgumentException) { ProtectionMode.BALANCED }
    }
}
