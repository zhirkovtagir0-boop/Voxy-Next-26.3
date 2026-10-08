package com.zhirkovtag.voxynext.core;

import java.util.List;

public final class LodRegionSelectorTest {
    public static void main(String[] args) {
        List<VisibleRegion> regions = new LodRegionSelector().select(0, 0, 512, 4096);
        for (int i = 0; i < regions.size(); i++) {
            VisibleRegion a = regions.get(i);
            long ax0 = a.regionX() * (long) a.level().blockSpan();
            long az0 = a.regionZ() * (long) a.level().blockSpan();
            long ax1 = ax0 + a.level().blockSpan();
            long az1 = az0 + a.level().blockSpan();

            for (int j = i + 1; j < regions.size(); j++) {
                VisibleRegion b = regions.get(j);
                if (a.level() == b.level()) continue;

                long bx0 = b.regionX() * (long) b.level().blockSpan();
                long bz0 = b.regionZ() * (long) b.level().blockSpan();
                long bx1 = bx0 + b.level().blockSpan();
                long bz1 = bz0 + b.level().blockSpan();

                boolean overlaps = ax0 < bx1 && bx0 < ax1 && az0 < bz1 && bz0 < az1;
                if (overlaps) throw new AssertionError("LOD regions overlap: " + a + " / " + b);
            }
        }
    }
}
