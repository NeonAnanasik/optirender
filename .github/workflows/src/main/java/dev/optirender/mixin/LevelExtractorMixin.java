package dev.optirender.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.optirender.OptiRenderClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OptiRenderConfig {
    public boolean entityCulling = true;
    public boolean blockEntityCulling = true;
    public boolean particleCulling = true;
    public boolean smartLeafCulling = true;
    public boolean cacheDebugOverlay = true;

    /** Максимум рейкаст-проверок окклюзии на кадр (бюджет против микрофризов). */
    public int maxOcclusionChecksPerFrame = 48;
    /** Дальше этой дистанции окклюзию не считаем (работает ванильный frustum). */
    public double maxOcclusionDistance = 96.0;
    /** Ближе этой дистанции сущности/BE всегда рендерим (без поп-ина у стен). */
    public double minCullingDistance = 6.0;
    /** Частицы ближе этой дистанции не окклюдим. */
    public double particleMinDistance = 8.0;
    /** TTL кэша видимости, мс. */
    public long visibilityCacheMillis = 100;
    /** Период пересборки строк F3, мс (рисуем каждый кадр, строки обновляем реже). */
    public long debugRefreshMillis = 200;

    public static OptiRenderConfig load(Path path) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        OptiRenderConfig cfg = new OptiRenderConfig();
        try {
            if (Files.exists(path)) {
                OptiRenderConfig read = gson.fromJson(Files.readString(path), OptiRenderConfig.class);
                if (read != null) cfg = read;
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, gson.toJson(cfg));
            }
        } catch (IOException | RuntimeException e) {
            OptiRenderClient.LOGGER.warn("[OptiRender] config fallback to defaults: {}", e.toString());
            cfg = new OptiRenderConfig();
        }
        return cfg;
    }
}
