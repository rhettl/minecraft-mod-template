package com.rhett.rhettjs.mixin;

import com.rhett.rhettjs.structure.WorldgenStructurePlacementContext;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept height queries during structure placement.
 *
 * When a RhettJS structure placement is active with a custom surface mode,
 * this mixin returns the calculated/scanned height instead of the chunk
 * generator's natural heightmap.
 *
 * This allows structures like villages to follow custom platforms instead
 * of the world's natural terrain.
 */
@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {

    /**
     * Intercept getFirstFreeHeight to return custom heights during structure placement.
     */
    @Inject(
        method = "getFirstFreeHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void rhettjs$overrideHeight(
        int x,
        int z,
        Heightmap.Types heightmapType,
        LevelHeightAccessor heightAccessor,
        RandomState randomState,
        CallbackInfoReturnable<Integer> cir
    ) {
        // Check if we have an active placement context with height overrides
        Integer override = WorldgenStructurePlacementContext.INSTANCE.getHeightOverride(x, z);
        if (override != null) {
            cir.setReturnValue(override);
        }
        // Otherwise, let vanilla handle it
    }
}
