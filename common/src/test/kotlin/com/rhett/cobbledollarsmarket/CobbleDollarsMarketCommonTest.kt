package com.rhett.cobbledollarsmarket

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for CobbleDollarsMarketCommon.
 */
class CobbleDollarsMarketCommonTest {

    @Test
    fun `mod ID is correct`() {
        assertEquals("cobbledollarsmarket", CobbleDollarsMarketCommon.MOD_ID)
    }

    @Test
    fun `logger is not null`() {
        assertNotNull(CobbleDollarsMarketCommon.LOGGER)
    }

    @Test
    fun `init does not throw`() {
        assertDoesNotThrow { CobbleDollarsMarketCommon.init() }
    }
}
