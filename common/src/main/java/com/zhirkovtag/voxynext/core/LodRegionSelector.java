package com.zhirkovtag.voxynext.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Selects non-overlapping world-space LOD regions in concentric bands.
 * A small hysteresis margin prevents cells from constantly changing LOD while
 * the camera moves around a boundary.
 */
public final class LodRegionSelector {
    private static final double HYSTERESIS = 24.0;

    public List<VisibleRegion> select(double cameraX, double cameraZ, int renderDistanceChunks, int maxRegions) {
        double radiusBlocks = Math.max(128.0, Math.max(1, renderDistanceChunks) * 16.0);
        int budget = Math.max(1, maxRegions);
        List<VisibleRegion> result = new ArrayList<>(Math.min(budget, 4096));

        for (LodLevel level : LodLevel.values()) {
            double inner = level == LodLevel.LOD0 ? 0.0 : radiusForPrevious(level) - HYSTERESIS;
            double outer = Math.min(radiusBlocks, radiusForLevel(level) + HYSTERESIS);
            if (outer <= inner) continue;

            long span = level.blockSpan();
            long minRx = Math.floorDiv((long)Math.floor(cameraX - outer), span);
            long maxRx = Math.floorDiv((long)Math.floor(cameraX + outer), span);
            long minRz = Math.floorDiv((long)Math.floor(cameraZ - outer), span);
            long maxRz = Math.floorDiv((long)Math.floor(cameraZ + outer), span);

            for (long rz = minRz; rz <= maxRz; rz++) {
                for (long rx = minRx; rx <= maxRx; rx++) {
                    double centerX = rx * (double)span + span * 0.5;
                    double centerZ = rz * (double)span + span * 0.5;
                    double d = Math.sqrt(distanceSquared(centerX, centerZ, cameraX, cameraZ));
                    double halfDiagonal = Math.sqrt(2.0) * span * 0.5;
                    if (d + halfDiagonal <= inner || d - halfDiagonal > outer) continue;
                    result.add(new VisibleRegion(rx, rz, level, d * d));
                }
            }
        }

        result.sort(Comparator.comparingDouble(VisibleRegion::distanceSquared)
                .thenComparingInt(v -> v.level().ordinal()));
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
