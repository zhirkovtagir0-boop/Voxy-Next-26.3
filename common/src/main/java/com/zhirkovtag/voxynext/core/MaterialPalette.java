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
}
