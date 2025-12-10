package com.rhett.cobbledollarsmarket

import net.fabricmc.api.ModInitializer

/**
 * Fabric entrypoint for CobbleDollars Market mod.
 */
class CobbleDollarsMarketFabric : ModInitializer {
    override fun onInitialize() {
        CobbleDollarsMarketCommon.init()
    }
}
