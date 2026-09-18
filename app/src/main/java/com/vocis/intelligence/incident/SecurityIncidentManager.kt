package com.vocis.intelligence.incident

import com.vocis.core.data.dao.SecurityIncidentDao
import com.vocis.core.data.entity.SecurityIncidentEntity
import com.vocis.core.domain.model.IncidentStatus
import com.vocis.core.domain.model.IncidentType
import com.vocis.core.domain.model.RiskLevel
import com.vocis.intelligence.identity.CallerIdentity
import com.vocis.intelligence.risk.EvidenceFactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class SecurityIncidentManager(
    private val dao: SecurityIncidentDao? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        const val GROUPING_WINDOW_MS: Long = 900_000L // 15 minutes
    }

    private val mutex = Mutex()
    private val _activeIncident = MutableStateFlow<SecurityIncidentEntity?>(null)
    val activeIncident: StateFlow<SecurityIncidentEntity?> = _activeIncident.asStateFlow()

    init {
        if (dao != null) {
            scope.launch {
                dao.getActiveIncidentFlow().collect { incident ->
                    _activeIncident.value = incident
                }
            }
        }
    }

    // In-memory cache for recent open incidents when DAO is mocked or offline
    private val openIncidents = mutableListOf<SecurityIncidentEntity>()

    suspend fun reportThreat(
        riskScore: Int,
        riskLevel: RiskLevel,
        incidentType: IncidentType,
        interactionId: String,
        callerIdentity: CallerIdentity?,
        evidenceFactors: List<EvidenceFactor>
    ): SecurityIncidentEntity = mutex.withLock {
        val now = System.currentTimeMillis()
        val callerNumber = callerIdentity?.phoneNumber

        // Check if an open incident exists within the 15-minute window for the same type & caller
        val existing = findMatchingOpenIncident(incidentType, callerNumber, now)

        val incident = if (existing != null) {
            val updatedInteractions = if (existing.relatedInteractionIds.contains(interactionId)) {
                existing.relatedInteractionIds
            } else {
                "${existing.relatedInteractionIds},$interactionId".trim(',')
            }

            val newStatus = when {
                riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL -> IncidentStatus.CONFIRMED
                existing.status == IncidentStatus.NEW -> IncidentStatus.INVESTIGATING
                else -> existing.status
            }

            val mergedExplanation = buildExplanation(evidenceFactors)

            existing.copy(
                severity = if (riskScore > existing.riskScore) riskLevel else existing.severity,
                status = newStatus,
                updatedAt = now,
                riskScore = maxOf(riskScore, existing.riskScore),
                explanation = mergedExplanation,
                relatedInteractionIds = updatedInteractions
            )
        } else {
            val initialStatus = when {
                riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL -> IncidentStatus.CONFIRMED
                else -> IncidentStatus.NEW
            }

            SecurityIncidentEntity(
                incidentId = UUID.randomUUID().toString(),
                incidentType = incidentType,
                severity = riskLevel,
                status = initialStatus,
                createdAt = now,
                updatedAt = now,
                resolvedAt = null,
                riskScore = riskScore,
                explanation = buildExplanation(evidenceFactors),
                recommendedActions = recommendActions(incidentType, riskLevel),
                relatedInteractionIds = interactionId,
                callerPhoneNumber = callerNumber,
                callerDisplayName = callerIdentity?.displayName,
                callerIdentityType = callerIdentity?.source,
                callerIdentitySource = callerIdentity?.source,
                repLevel = callerIdentity?.reputationLevel?.name
            )
        }

        // Persist to DAO and in-memory list
        dao?.insert(incident)
        openIncidents.removeAll { it.incidentId == incident.incidentId }
        if (incident.status != IncidentStatus.RESOLVED && incident.status != IncidentStatus.DISMISSED) {
            openIncidents.add(incident)
            _activeIncident.value = incident
        } else if (_activeIncident.value?.incidentId == incident.incidentId) {
            _activeIncident.value = null
        }

        return incident
    }

    suspend fun confirmIncident(incidentId: String): SecurityIncidentEntity? = mutex.withLock {
        val current = dao?.getById(incidentId) ?: openIncidents.find { it.incidentId == incidentId } ?: return null
        val updated = current.copy(
            status = IncidentStatus.CONFIRMED,
            updatedAt = System.currentTimeMillis()
        )
        dao?.update(updated)
        openIncidents.removeAll { it.incidentId == incidentId }
        openIncidents.add(updated)
        _activeIncident.value = updated
        return updated
    }

    suspend fun resolveIncident(incidentId: String): SecurityIncidentEntity? = mutex.withLock {
        val current = dao?.getById(incidentId) ?: openIncidents.find { it.incidentId == incidentId } ?: return null
        val now = System.currentTimeMillis()
        val updated = current.copy(
            status = IncidentStatus.RESOLVED,
            resolvedAt = now,
            updatedAt = now
        )
        dao?.update(updated)
        openIncidents.removeAll { it.incidentId == incidentId }
        if (_activeIncident.value?.incidentId == incidentId) {
            _activeIncident.value = null
        }
        return updated
    }

    suspend fun dismissIncident(incidentId: String): SecurityIncidentEntity? = mutex.withLock {
        val current = dao?.getById(incidentId) ?: openIncidents.find { it.incidentId == incidentId } ?: return null
        val now = System.currentTimeMillis()
        val updated = current.copy(
            status = IncidentStatus.DISMISSED,
            resolvedAt = now,
            updatedAt = now
        )
        dao?.update(updated)
        openIncidents.removeAll { it.incidentId == incidentId }
        if (_activeIncident.value?.incidentId == incidentId) {
            _activeIncident.value = null
        }
        return updated
    }

    suspend fun getRecentIncidents(limit: Int = 20): List<SecurityIncidentEntity> {
        return dao?.getRecent(limit) ?: openIncidents.takeLast(limit).reversed()
    }

    private fun findMatchingOpenIncident(
        type: IncidentType,
        callerNumber: String?,
        now: Long
    ): SecurityIncidentEntity? {
        return openIncidents.find {
            it.incidentType == type &&
            it.callerPhoneNumber == callerNumber &&
            (it.status == IncidentStatus.NEW || it.status == IncidentStatus.INVESTIGATING || it.status == IncidentStatus.CONFIRMED) &&
            (now - it.createdAt) < GROUPING_WINDOW_MS
        }
    }

    private fun buildExplanation(factors: List<EvidenceFactor>): String {
        if (factors.isEmpty()) return "Suspicious activity detected"
        return factors.joinToString(separator = "; ") { "${it.source}: ${it.description} (+${it.weight})" }
    }

    private fun recommendActions(type: IncidentType, level: RiskLevel): String {
        return when (type) {
            IncidentType.DIGITAL_ARREST -> "Do not transfer funds. Law enforcement does not conduct arrests via video/audio calls. Disconnect immediately."
            IncidentType.OTP_THEFT -> "Never share OTP or verification passwords with callers. Hang up immediately."
            IncidentType.REMOTE_ACCESS_SCAM -> "Uninstall remote screen sharing apps (AnyDesk, TeamViewer) immediately. Terminate call."
            IncidentType.FINANCIAL_FRAUD -> "Verify independently with your official bank or service provider before making any payments."
            IncidentType.VOICE_CLONE -> "Synthetic voice signature detected. Verify identity with the family member using an alternate channel."
            IncidentType.OTHER -> "Exercise caution. Do not disclose confidential credentials or approve requests."
        }
    }
}
