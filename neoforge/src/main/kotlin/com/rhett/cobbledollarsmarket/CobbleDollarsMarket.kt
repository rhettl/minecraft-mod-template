package com.rhett.cobbledollarsmarket

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

/**
 * NeoForge entrypoint for CobbleDollars Market mod.
 */
@Mod(CobbleDollarsMarketCommon.MOD_ID)
class CobbleDollarsMarket(modEventBus: IEventBus) {
    init {
        CobbleDollarsMarketCommon.init()
    }
}
