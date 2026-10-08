package com.zhirkovtag.voxynext.core;

/** Converts user-facing render distance into a bounded LOD workload. */
public record DistanceBudget(int renderDistanceChunks, int workerCount, int maxRegionsInMemory) {
    public DistanceBudget {
        renderDistanceChunks = Math.max(8, Math.min(4096, renderDistanceChunks));
        workerCount = Math.max(1, Math.min(16, workerCount));
        maxRegionsInMemory = Math.max(128, Math.min(1_000_000, maxRegionsInMemory));
    }

    public long estimatedLodRegions() {
        long diameter = (long) renderDistanceChunks * 2 + 1;
        return Math.min(maxRegionsInMemory, diameter * diameter / 16L + 64L);
    }
}
