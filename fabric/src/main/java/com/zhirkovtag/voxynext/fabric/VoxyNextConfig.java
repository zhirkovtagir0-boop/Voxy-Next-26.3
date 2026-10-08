package com.zhirkovtag.voxynext.fabric;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zhirkovtag.voxynext.core.DistanceBudget;

/** User-editable Fabric runtime configuration. */
final class VoxyNextConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    int renderDistanceChunks = 256;
    int workerCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    int maxRegionsInMemory = 32_768;

    static VoxyNextConfig load(Path path) {
        VoxyNextConfig config = null;
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                config = GSON.fromJson(reader, VoxyNextConfig.class);
            } catch (Exception ignored) {
                // Fall back to defaults and rewrite a valid file below.
            }
        }
        if (config == null) config = new VoxyNextConfig();
        config.write(path);
        return config;
    }

    DistanceBudget budget() {
        return new DistanceBudget(renderDistanceChunks, workerCount, maxRegionsInMemory);
    }

    private void write(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
            // The mod still runs with in-memory defaults when config persistence is unavailable.
        }
    }
}
