package com.zhirkovtag.voxynext.core;

/** Immutable render candidate selected by the LOD selector. */
public record VisibleRegion(long regionX, long regionZ, LodLevel level, double distanceSquared) {}
