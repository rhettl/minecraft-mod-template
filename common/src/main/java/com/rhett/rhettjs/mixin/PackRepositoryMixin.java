package com.rhett.rhettjs.mixin;

import com.rhett.rhettjs.RhettJSCommon;
import com.rhett.rhettjs.worldgen.RJSFileResourcePack;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    @Inject(method = "openAllSelected", at = @At("RETURN"), cancellable = true)
    private void rhettjs$addRJSDatapack(CallbackInfoReturnable<List<PackResources>> cir) {
        try {
            List<PackResources> original = cir.getReturnValue();
            List<PackResources> modified = new ArrayList<>(original);

            // Get rjs/ directory (same location as server root)
            Path rjsDirectory = Paths.get("rjs");

            // Add RJS datapack to the list
            RJSFileResourcePack rjsPack = new RJSFileResourcePack(rjsDirectory, PackType.SERVER_DATA);
            modified.add(rjsPack);

            RhettJSCommon.LOGGER.info("[RhettJS] Injected RJS datapack into pack list");

            cir.setReturnValue(modified);
        } catch (Exception e) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to inject RJS datapack", e);
        }
    }
}
