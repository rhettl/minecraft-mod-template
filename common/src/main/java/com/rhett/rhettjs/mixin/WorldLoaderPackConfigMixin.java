package com.rhett.rhettjs.mixin;

import com.rhett.rhettjs.RhettJSCommon;
import com.rhett.rhettjs.engine.ScriptRegistry;
import com.rhett.rhettjs.engine.ServerScriptManager;
import net.minecraft.server.WorldLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Mixin to load server scripts during data pack creation phase.
 * This ensures scripts load BEFORE command registration, matching KubeJS's timing.
 */
@Mixin(WorldLoader.PackConfig.class)
public class WorldLoaderPackConfigMixin {

    /**
     * Inject into createResourceManager to load server scripts early.
     * This runs during data pack creation, before commands are registered.
     */
    @Inject(
        method = "createResourceManager",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/server/packs/repository/PackRepository;openAllSelected()Ljava/util/List;",
                 shift = At.Shift.BEFORE)
    )
    private void rhettjs$loadServerScriptsEarly(CallbackInfoReturnable<?> cir) {
        try {
            // Get scripts directory (rjs/)
            Path scriptsDir = Paths.get("rjs");

            // Scan for scripts if not already done
            if (!ScriptRegistry.INSTANCE.hasScanned()) {
                RhettJSCommon.LOGGER.info("[RhettJS] Scanning for scripts (early phase)...");
                ScriptRegistry.INSTANCE.scan(scriptsDir);
            }

            // Load server scripts
            ServerScriptManager.INSTANCE.createAndLoad(scriptsDir);

        } catch (Exception e) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to load server scripts during data pack phase", e);
        }
    }
}
