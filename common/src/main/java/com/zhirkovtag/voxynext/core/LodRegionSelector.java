package com.zhirkovtag.voxynext.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Selects LOD regions around a camera using a bounded quadtree-like ring layout.
 * The selector is deliberately allocation-light; platform renderers can feed candidates
 * directly into GPU extraction later.
 */
public final class LodRegionSelector {
    public List<VisibleRegion> select(double cameraX, double cameraZ, int renderDistanceChunks, int maxRegions) {
        int radius = Math.max(1, renderDistanceChunks);
        int regionRadius = Math.max(1, (radius + 31) / 32);
        List<VisibleRegion> result = new ArrayList<>(Math.min(maxRegions, regionRadius * regionRadius * 4));

        int min = -regionRadius, max = regionRadius;
        for (int rz = min; rz <= max; rz++) {
            for (int rx = min; rx <= max; rx++) {
                double cx = (rx + 0.5) * 512.0;
                double cz = (rz + 0.5) * 512.0;
                double dx = cx - cameraX;
                double dz = cz - cameraZ;
                double d2 = dx * dx + dz * dz;
                int distanceChunks = (int) Math.sqrt(d2) / 16;
                if (distanceChunks > radius) continue;
                result.add(new VisibleRegion(rx, rz, LodLevel.forDistance(distanceChunks * 16.0), d2));
            }
        }
        result.sort(Comparator.comparingDouble(VisibleRegion::distanceSquared));
        if (result.size() > maxRegions) return result.subList(0, maxRegions);
        return result;
    }
}
