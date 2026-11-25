package dev.optirender.culling;

import dev.optirender.OptiRenderClient;
import dev.optirender.config.OptiRenderConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Окклюзия по воксельной сетке (алгоритм Amanatides & Woo).
 * Горячий путь без аллокаций: один MutableBlockPos, примитивные карты, бюджет на кадр.
 * Вызывается только из render-thread.
 */
public final class OcclusionEngine {
    private static ClientLevel level;
    private static double camX, camY, camZ;
    private static int checksThisFrame;

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final Int2ObjectOpenHashMap<Vis> ENTITY_CACHE = new Int2ObjectOpenHashMap<>(256);
    private static final Long2ObjectOpenHashMap<Vis> BLOCK_CACHE = new Long2ObjectOpenHashMap<>(256);

    private static final class Vis {
        byte state;            // 0 unknown, 1 visible, 2 hidden
        long lastCheck;
        double cx, cy, cz;     // позиция камеры в момент проверки
        double tx, ty, tz;     // позиция цели в момент проверки
    }

    private OcclusionEngine() {}

    /** Вызывается раз в кадр из LevelExtractor.extract. */
    public static void beginFrame(ClientLevel lvl, double cx, double cy, double cz) {
        level = lvl;
        camX = cx; camY = cy; camZ = cz;
        checksThisFrame = 0;
    }

    public static boolean ready() {
        return level != null;
    }

    public static boolean entityVisible(int id, AABB box, double ex, double ey, double ez) {
        OptiRenderConfig cfg = OptiRenderClient.config();
        long now = Util.getMillis();
        Vis v = ENTITY_CACHE.get(id);
        if (v == null) { v = new Vis(); ENTITY_CACHE.put(id, v); }

        if (v.state != 0
                && now - v.lastCheck < cfg.visibilityCacheMillis
                && sq(v.cx - camX) + sq(v.cy - camY) + sq(v.cz - camZ) < 0.25
                && sq(v.tx - ex) + sq(v.ty - ey) + sq(v.tz - ez) < 0.25) {
            return v.state == 1;
        }
        if (checksThisFrame >= cfg.maxOcclusionChecksPerFrame) return v.state != 2;
        checksThisFrame++;

        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double ix = Math.min(0.2, (box.maxX - box.minX) * 0.25);
        double iy = Math.min(0.2, (box.maxY - box.minY) * 0.25);
        double iz = Math.min(0.2, (box.maxZ - box.minZ) * 0.25);

        boolean visible = !rayBlocked(camX, camY, camZ, cx, cy, cz)
                || !rayBlocked(camX, camY, camZ, box.minX + ix, box.minY + iy, box.minZ + iz)
                || !rayBlocked(camX, camY, camZ, box.maxX - ix, box.maxY - iy, box.maxZ - iz);

        v.state = (byte) (visible ? 1 : 2);
        v.lastCheck = now;
        v.cx = camX; v.cy = camY; v.cz = camZ;
        v.tx = ex; v.ty = ey; v.tz = ez;
        return visible;
    }

    public static boolean blockVisible(long key, double bx, double by, double bz) {
        OptiRenderConfig cfg = OptiRenderClient.config();
        long now = Util.getMillis();
        Vis v = BLOCK_CACHE.get(key);
        if (v == null) { v = new Vis(); BLOCK_CACHE.put(key, v); }

        if (v.state != 0
                && now - v.lastCheck < cfg.visibilityCacheMillis
                && sq(v.cx - camX) + sq(v.cy - camY) + sq(v.cz - camZ) < 0.25) {
            return v.state == 1;
        }
        if (checksThisFrame >= cfg.maxOcclusionChecksPerFrame) return v.state != 2;
        checksThisFrame++;

        boolean visible = !rayBlocked(camX, camY, camZ, bx, by, bz)
                || !rayBlocked(camX, camY, camZ, bx - 0.35, by - 0.35, bz - 0.35)
                || !rayBlocked(camX, camY, camZ, bx + 0.35, by + 0.35, bz + 0.35);

        v.state = (byte) (visible ? 1 : 2);
        v.lastCheck = now;
        v.cx = camX; v.cy = camY; v.cz = camZ;
        v.tx = bx; v.ty = by; v.tz = bz;
        return visible;
    }

    /** Для частиц: без кэша (они движутся), только бюджет + дистанция. */
    public static boolean pointVisible(double x, double y, double z) {
        OptiRenderConfig cfg = OptiRenderClient.config();
        double dSq = sq(x - camX) + sq(y - camY) + sq(z - camZ);
        if (dSq < cfg.particleMinDistance * cfg.particleMinDistance) return true;
        if (dSq > cfg.maxOcclusionDistance * cfg.maxOcclusionDistance) return true;
        if (checksThisFrame >= cfg.maxOcclusionChecksPerFrame) return true;
        checksThisFrame++;
        return !rayBlocked(camX, camY, camZ, x, y, z);
    }

    /** true = луч перекрыт непрозрачным полным кубом до достижения целевого вокселя. */
    private static boolean rayBlocked(double x0, double y0, double z0, double x1, double y1, double z1) {
        int ix = Mth.floor(x0), iy = Mth.floor(y0), iz = Mth.floor(z0);
        int tx = Mth.floor(x1), ty = Mth.floor(y1), tz = Mth.floor(z1);
        if (ix == tx && iy == ty && iz == tz) return false;

        // Камера внутри непрозрачного блока -> ничего не прячем.
        if (isOpaque(ix, iy, iz)) return false;

        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        double tMaxX = dx == 0 ? Double.POSITIVE_INFINITY : ((stepX > 0 ? (ix + 1 - x0) : (x0 - ix)) * tDeltaX);
        double tMaxY = dy == 0 ? Double.POSITIVE_INFINITY : ((stepY > 0 ? (iy + 1 - y0) : (y0 - iy)) * tDeltaY);
        double tMaxZ = dz == 0 ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? (iz + 1 - z0) : (z0 - iz)) * tDeltaZ);

        for (int i = 0; i < 256; i++) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) { ix += stepX; tMaxX += tDeltaX; }
            else if (tMaxY < tMaxZ)             { iy += stepY; tMaxY += tDeltaY; }
            else                                { iz += stepZ; tMaxZ += tDeltaZ; }

            if (ix == tx && iy == ty && iz == tz) return false; // дошли до цели
            if (isOpaque(ix, iy, iz)) return true;
        }
        return false;
    }

    private static boolean isOpaque(int x, int y, int z) {
        CURSOR.set(x, y, z);
        BlockState state = level.getBlockState(CURSOR);
        // Непрозрачный полный куб: стекло/листва/плиты не окклюдят (консервативно).
        return state.canOcclude() && state.isCollisionShapeFullBlock(level, CURSOR);
    }

    private static double sq(double d) { return d * d; }
}
