package com.vocis

import com.vocis.intelligence.identity.CallerIdentity
import com.vocis.intelligence.identity.CallerReputationProvider
import com.vocis.intelligence.identity.CallerIdentityResolver
import com.vocis.intelligence.identity.E164Normalizer
import com.vocis.intelligence.identity.ReputationLevel
import com.vocis.intelligence.identity.baseRiskWeight
import com.vocis.core.data.dao.CallerIdentityDao
import com.vocis.core.data.entity.CallerIdentityEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import android.content.Context

class CallerIdentityResolverTest {

    // ── E.164 normalizer ─────────────────────────────────────────────────────

    @Test fun `normalize bare 10-digit adds +91`() {
        assertEquals("+919876543210", E164Normalizer.normalize("9876543210"))
    }

    @Test fun `normalize already E164 unchanged`() {
        assertEquals("+447700900001", E164Normalizer.normalize("+447700900001"))
    }

    @Test fun `normalize null returns UNKNOWN`() {
        assertEquals("UNKNOWN", E164Normalizer.normalize(null))
    }

    @Test fun `normalize blank returns UNKNOWN`() {
        assertEquals("UNKNOWN", E164Normalizer.normalize("   "))
    }

    @Test fun `normalize tel prefix stripped`() {
        assertEquals("+919999999999", E164Normalizer.normalize("tel:9999999999"))
    }

    @Test fun `normalize single digit 2 returns UNKNOWN`() {
        assertEquals("UNKNOWN", E164Normalizer.normalize("2"))
    }

    @Test fun `normalize negative 2 presentation code returns UNKNOWN`() {
        assertEquals("UNKNOWN", E164Normalizer.normalize("-2"))
        assertEquals("UNKNOWN", E164Normalizer.normalize("-1"))
    }

    @Test fun `normalize 11-digit with leading 0 converts to +91`() {
        assertEquals("+919876543210", E164Normalizer.normalize("09876543210"))
    }

    @Test fun `normalize 12-digit with leading 91 adds plus`() {
        assertEquals("+919876543210", E164Normalizer.normalize("919876543210"))
    }

    // ── Risk weights ─────────────────────────────────────────────────────────

    @Test fun `known contact contributes 0 risk`() {
        val identity = CallerIdentity("+911234567890", "Mom", ReputationLevel.TRUSTED, true, "CONTACT")
        assertEquals(0, identity.baseRiskWeight())
    }

    @Test fun `unknown caller contributes 15 risk`() {
        val identity = CallerIdentity("+911234567890", null, ReputationLevel.UNKNOWN, false, "PROVIDER")
        assertEquals(15, identity.baseRiskWeight())
    }

    @Test fun `suspicious caller contributes 50 risk`() {
        val identity = CallerIdentity("+911234567890", null, ReputationLevel.SUSPICIOUS, false, "PROVIDER")
        assertEquals(50, identity.baseRiskWeight())
    }

    @Test fun `high risk caller contributes 80 risk`() {
        val identity = CallerIdentity("+911234567890", null, ReputationLevel.HIGH_RISK, false, "PROVIDER")
        assertEquals(80, identity.baseRiskWeight())
    }

    @Test fun `anonymous number contributes 25 risk`() {
        val identity = CallerIdentity("UNKNOWN", null, ReputationLevel.UNKNOWN, false, "UNKNOWN")
        assertEquals(25, identity.baseRiskWeight())
    }

    // ── Cache behaviour ──────────────────────────────────────────────────────

    @Test fun `cache hit within 24h skips provider`() = runTest {
        val dao = mock(CallerIdentityDao::class.java)
        val provider = mock(CallerReputationProvider::class.java)
        val context = mock(Context::class.java)

        val cachedEntity = CallerIdentityEntity(
            phoneNumber = "+919876543210",
            displayName = "Cached User",
            category = "UNKNOWN",
            reputationLevel = "UNKNOWN",
            isVerified = false,
            lastUpdated = System.currentTimeMillis() - 1000L  // 1 second ago — fresh
        )
        `when`(dao.getByPhoneNumber("+919876543210")).thenReturn(cachedEntity)

        val resolver = CallerIdentityResolver(context, dao, provider)
        val result = resolver.resolve("9876543210")

        assertEquals("CACHE", result.source)
        assertEquals("+919876543210", result.e164Number)
        // Provider must NOT be called when cache is fresh
        verify(provider, never()).lookup("+919876543210")
    }

    @Test fun `stale cache falls through to provider`() = runTest {
        val dao = mock(CallerIdentityDao::class.java)
        val provider = mock(CallerReputationProvider::class.java)
        val context = mock(Context::class.java)

        val staleEntity = CallerIdentityEntity(
            phoneNumber = "+919876543210",
            displayName = null,
            category = "UNKNOWN",
            reputationLevel = "UNKNOWN",
            isVerified = false,
            lastUpdated = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)  // 25 h ago
        )
        `when`(dao.getByPhoneNumber("+919876543210")).thenReturn(staleEntity)

        val providerResult = CallerIdentity("+919876543210", null, ReputationLevel.SUSPICIOUS, false, "PROVIDER")
        `when`(provider.lookup("+919876543210")).thenReturn(providerResult)

        val resolver = CallerIdentityResolver(context, dao, provider)
        val result = resolver.resolve("9876543210")

        assertEquals(ReputationLevel.SUSPICIOUS, result.reputationLevel)
        verify(provider).lookup("+919876543210")
    }
}
