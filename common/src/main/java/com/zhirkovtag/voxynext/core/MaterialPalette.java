package com.zhirkovtag.voxynext.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compact process-local palette used by voxel storage.
 * The renderer stores small integer material ids instead of BlockState objects.
 */
public final class MaterialPalette {
    public static final int AIR = 0;
    private final ConcurrentHashMap<String, Integer> ids = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> names = new ConcurrentHashMap<>();
    private final AtomicInteger next = new AtomicInteger(1);

    public MaterialPalette() {
        ids.put("minecraft:air", AIR);
        names.put(AIR, "minecraft:air");
    }

    public int id(String stableMaterialName) {
        if (stableMaterialName == null || stableMaterialName.isEmpty()) return AIR;
        return ids.computeIfAbsent(stableMaterialName, name -> {
            int value = next.getAndIncrement();
            names.put(value, name);
            return value;
        });
    }

    public String name(int id) {
        return names.getOrDefault(id, "minecraft:air");
    }

    public int size() {
        return next.get();
    }

    /** Stable approximate albedo used by the distant terrain renderer. */
    public int color(int id) {
        String name = name(id).toLowerCase(java.util.Locale.ROOT);
        if (name.contains("water")) return 0x3F78A8;
        if (name.contains("sand")) return 0xC9B56A;
        if (name.contains("snow") || name.contains("ice")) return 0xDDE8EA;
        if (name.contains("grass") || name.contains("leaves") || name.contains("moss")) return 0x5F8F45;
        if (name.contains("stone") || name.contains("deepslate")) return 0x777B7B;
        if (name.contains("dirt") || name.contains("mud")) return 0x806044;
        if (name.contains("wood") || name.contains("log") || name.contains("planks")) return 0x8B6847;
        if (name.contains("lava")) return 0xD96A1A;
        return 0x77705D;
    }
}
