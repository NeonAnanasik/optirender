package dev.optirender;

import dev.optirender.config.OptiRenderConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OptiRenderClient implements ClientModInitializer {
    public static final String MOD_ID = "optirender";
    public static final Logger LOGGER = LoggerFactory.getLogger("OptiRender");

    private static OptiRenderConfig config;

    @Override
    public void onInitializeClient() {
        config = OptiRenderConfig.load(FabricLoader.getInstance().getConfigDir().resolve("optirender.json"));
        LOGGER.info("[OptiRender] loaded (entities={}, blockEntities={}, particles={}, leaves={}, debugCache={})",
                config.entityCulling, config.blockEntityCulling, config.particleCulling,
                config.smartLeafCulling, config.cacheDebugOverlay);
    }

    public static OptiRenderConfig config() {
        return config;
    }
}
