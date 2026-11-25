package dev.optirender.mixin;

import dev.optirender.OptiRenderClient;
import dev.optirender.config.OptiRenderConfig;
import dev.optirender.culling.OcclusionEngine;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Shadow
    public abstract <E extends BlockEntity> BlockEntityRenderer<E> getRenderer(E blockEntity);

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void optirender$occlude(BlockEntity blockEntity, float partialTicks,
                                    ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, boolean isGloballyRendered,
                                    CallbackInfoReturnable<BlockEntityRenderState> cir) {
        OptiRenderConfig cfg = OptiRenderClient.config();
        if (cfg == null || !cfg.blockEntityCulling || isGloballyRendered || !OcclusionEngine.ready()) return;
        if (blockEntity == null) return;

        BlockEntityRenderer<BlockEntity> renderer = getRenderer(blockEntity);
        if (renderer == null || renderer.shouldRenderOffScreen()) return; // маяки и т.п. не трогаем

        BlockPos pos = blockEntity.getBlockPos();
        double bx = pos.getX() + 0.5, by = pos.getY() + 0.5, bz = pos.getZ() + 0.5;
        double dSq = OcclusionEngine.distanceSqToCamera(bx, by, bz);
        if (dSq < cfg.minCullingDistance * cfg.minCullingDistance) return;
        if (dSq > cfg.maxOcclusionDistance * cfg.maxOcclusionDistance) return;

        if (!OcclusionEngine.blockVisible(pos.asLong(), bx, by, bz)) {
            cir.setReturnValue(null);
        }
    }
}
