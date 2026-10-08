package com.zhirkovtag.voxynext.core;

/** Small immutable render candidate produced by the visibility stage. */
public record VisibleRegion(int regionX, int regionZ, LodLevel level, double distanceSquared) {}
