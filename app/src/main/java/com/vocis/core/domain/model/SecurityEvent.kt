package com.vocis.core.domain.model

import java.util.UUID

data class SecurityEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: EventType,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    val identity: String,
    val metadata: String = "{}",
    val initialRisk: String = "LOW",
    val interactionId: String? = null
)
