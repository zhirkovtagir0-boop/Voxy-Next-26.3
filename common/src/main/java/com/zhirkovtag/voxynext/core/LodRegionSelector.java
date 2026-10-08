package com.zhirkovtag.voxynext.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Selects a bounded, non-overlapping set of world-space LOD regions.
 *
 * The selector works in block space and snaps each candidate to its native
 * region grid. Near bands use fine regions; far bands progressively reduce
 * region count. A small overlap margin provides LOD hysteresis.
 */
public final class LodRegionSelector {
    public List<VisibleRegion> select(double cameraX, double cameraZ, int renderDistanceChunks, int maxRegions) {
        double radiusBlocks = Math.max(128.0, Math.max(1, renderDistanceChunks) * 16.0);
        int budget = Math.max(1, maxRegions);
        List<VisibleRegion> result = new ArrayList<>(Math.min(budget, 4096));

        // A region is accepted only if its center is inside the band and its
        // footprint intersects the band. This avoids holes without duplicating
        // whole regions from adjacent LODs.
        for (LodLevel level : LodLevel.values()) {
            double inner = level == LodLevel.LOD0 ? 0.0 : bandRadius(LodLevel.values()[level.ordinal() - 1]);
            double outer = Math.min(radiusBlocks, bandRadius(level));
            if (level == LodLevel.LOD7) outer = radiusBlocks;
            if (outer <= inner) continue;

            int span = level.blockSpan();
            long minRx = Math.floorDiv((long)Math.floor(cameraX - outer), span);
            long maxRx = Math.floorDiv((long)Math.floor(cameraX + outer), span);
            long minRz = Math.floorDiv((long)Math.floor(cameraZ - outer), span);
            long maxRz = Math.floorDiv((long)Math.floor(cameraZ + outer), span);

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

        // Prefer the finest representation when a boundary candidate is
        // unavoidable, then nearest-first for cache locality.
        result.sort(Comparator
                .comparingInt((VisibleRegion v) -> v.level().ordinal())
                .thenComparingDouble(VisibleRegion::distanceSquared));

        if (result.size() > budget) return new ArrayList<>(result.subList(0, budget));
        return result;
    }

    private static double bandRadius(LodLevel level) {
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
