# Voxy Next 26.3

Clean-room, cross-loader distant-terrain renderer for Minecraft 26.3.

## Targets

- Fabric 26.3 / Java 25
- NeoForge 26.3 / Java 25
- Shared loader-neutral LOD/storage core

## Rendering direction

Voxy Next is designed around Minecraft 26.3's modern Renderpearl/Blaze3D render abstraction rather than raw OpenGL. The renderer will keep near terrain on the normal Minecraft/Sodium path and use a separate voxel LOD representation for distant terrain.

Planned pipeline:

1. Chunk extraction on background workers.
2. Compact material/height voxel aggregation.
3. Hierarchical LOD regions with hysteresis.
4. Visibility/frustum selection.
5. GPU upload with persistent buffers where the active backend permits it.
6. Dithered LOD transitions, fog integration and material-aware coloring.
7. Optional Sodium integration without making Sodium a hard dependency.

## Current milestone

**Milestone 3 — asynchronous voxel LOD pipeline + cross-loader renderer:** both Fabric and NeoForge now ingest client chunks into a shared asynchronous LOD cache, select nested distance bands, and render the resulting terrain through their native 26.3 rendering paths. Explored surface snapshots are also persisted per server/dimension so unloaded terrain can remain available to the distant renderer.

The current representation is a compact height-field surface rather than a full volumetric block store. The next rendering pass focuses on GPU mesh caching, frustum culling, smooth LOD transitions and material-aware shading. CI builds both loader targets with Java 25 / Gradle 9.6.

## Important

This project is an independent implementation. It does not copy or redistribute Voxy source code.
