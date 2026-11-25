package dev.optirender.mixin;

import dev.optirender.culling.OcclusionEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void optirender$beginFrame(DeltaTracker deltaTracker, Camera camera, float worldPartialTicks, CallbackInfo ci) {
        Vec3 pos = camera.position();
        OcclusionEngine.beginFrame(Minecraft.getInstance().level, pos.x(), pos.y(), pos.z());
    }
}
