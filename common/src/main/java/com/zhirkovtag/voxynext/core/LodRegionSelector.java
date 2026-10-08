package com.zhirkovtag.voxynext.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Selects world-space LOD regions in concentric bands.
 * Each LOD has a deterministic region grid, preventing overlapping regions from
 * multiple levels while keeping the render list bounded.
 */
public final class LodRegionSelector {
    public List<VisibleRegion> select(double cameraX, double cameraZ, int renderDistanceChunks, int maxRegions) {
        int radiusBlocks = Math.max(128, Math.max(1, renderDistanceChunks) * 16);
        int budget = Math.max(1, maxRegions);
        List<VisibleRegion> result = new ArrayList<>(Math.min(budget, 4096));

        for (LodLevel level : LodLevel.values()) {
            int scale = level.scale();
            double inner = level == LodLevel.LOD0 ? 0.0 : radiusForPrevious(level);
            double outer = Math.min(radiusBlocks, radiusForLevel(level));
            if (outer <= inner) continue;

            long span = 32L * scale;
            long minRx = Math.floorDiv((long) Math.floor(cameraX - outer), span);
            long maxRx = Math.floorDiv((long) Math.floor(cameraX + outer), span);
            long minRz = Math.floorDiv((long) Math.floor(cameraZ - outer), span);
            long maxRz = Math.floorDiv((long) Math.floor(cameraZ + outer), span);

            for (long rz = minRz; rz <= maxRz; rz++) {
                for (long rx = minRx; rx <= maxRx; rx++) {
                    double centerX = rx * (double) span + span * 0.5;
                    double centerZ = rz * (double) span + span * 0.5;
                    double d2 = distanceSquared(centerX, centerZ, cameraX, cameraZ);
                    double d = Math.sqrt(d2);
                    double halfDiagonal = Math.sqrt(2.0) * span * 0.5;
                    if (d + halfDiagonal <= inner || d - halfDiagonal > outer) continue;
                    result.add(new VisibleRegion(rx, rz, level, d2));
                }
            }
        }

        result.sort(Comparator.comparingDouble(VisibleRegion::distanceSquared));
        if (result.size() > budget) return new ArrayList<>(result.subList(0, budget));
        return result;
    }

    private static double radiusForPrevious(LodLevel level) {
        return switch (level) {
            case LOD1 -> 128.0;
            case LOD2 -> 256.0;
            case LOD3 -> 512.0;
            case LOD4 -> 1024.0;
            case LOD5 -> 2048.0;
            case LOD6 -> 4096.0;
            case LOD7 -> 8192.0;
            default -> 0.0;
        };
    }

    private static double radiusForLevel(LodLevel level) {
        return switch (level) {
            case LOD0 -> 128.0;
            case LOD1 -> 256.0;
            case LOD2 -> 512.0;
            case LOD3 -> 1024.0;
            case LOD4 -> 2048.0;
            case LOD5 -> 4096.0;
            case LOD6 -> 8192.0;
            case LOD7 -> Double.POSITIVE_INFINITY;
        };
    }

    private static double distanceSquared(double x, double z, double cx, double cz) {
        double dx = x - cx;
        double dz = z - cz;
        return dx * dx + dz * dz;
    }
}
