package com.zhirkovtag.voxynext.core;

/** Chooses a stable LOD with hysteresis to avoid camera-distance flicker. */
public final class LodSelector {
    private LodLevel current = LodLevel.LOD0;

    public LodLevel update(double distanceBlocks) {
        LodLevel target = LodLevel.forDistance(distanceBlocks);
        int currentIndex = current.ordinal();
        int targetIndex = target.ordinal();
        if (targetIndex > currentIndex && distanceBlocks < enterDistance(targetIndex)) return current;
        if (targetIndex < currentIndex && distanceBlocks > exitDistance(targetIndex)) return current;
        current = target;
        return current;
    }

    private static double boundaryForEntering(int level) {
        return 128.0 * Math.pow(2.0, level - 1);
    }

    private static double enterDistance(int level) {
        return level <= 0 ? 0.0 : boundaryForEntering(level) * 0.90;
    }

    private static double exitDistance(int level) {
        return level <= 0 ? 0.0 : boundaryForEntering(level) * 1.10;
    }
}
