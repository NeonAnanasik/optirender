package dev.optirender.mixin;

import dev.optirender.OptiRenderClient;
import dev.optirender.config.OptiRenderConfig;
import dev.optirender.culling.OcclusionEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void optirender$occlude(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                    float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        OptiRenderConfig cfg = OptiRenderClient.config();
        if (cfg == null || !cfg.entityCulling || !OcclusionEngine.ready()) return;

        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player || entity == mc.getCameraEntity()) return;
        if (entity.hasEffect(MobEffects.GLOWING)) return;

        double ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();
        double dSq = (ex - camX) * (ex - camX) + (ey - camY) * (ey - camY) + (ez - camZ) * (ez - camZ);
        if (dSq < cfg.minCullingDistance * cfg.minCullingDistance) return;
        if (dSq > cfg.maxOcclusionDistance * cfg.maxOcclusionDistance) return;

        AABB box = entity.getBoundingBox();
        // Крупных боссов не прячем по окклюзии.
        if (box.getXsize() > 16 || box.getYsize() > 16 || box.getZsize() > 16) return;

        if (!OcclusionEngine.entityVisible(entity.getId(), box, ex, ey, ez)) {
            cir.setReturnValue(false);
        }
    }
}
