package com.rhett.cobbledollarsmarket

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Common initialization class for CobbleDollars Market mod.
 * Platform-specific modules (Fabric/NeoForge) call this during initialization.
 */
object CobbleDollarsMarketCommon {
    const val MOD_ID = "cobbledollarsmarket"

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    @JvmStatic
    fun init() {
        LOGGER.info("CobbleDollars Market initializing...")
    }
}
