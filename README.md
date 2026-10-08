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

**Milestone 3 — asynchronous voxel LOD pipeline + cross-loader renderer:** Fabric now has a real extraction/drawing path that samples loaded world terrain, builds distance-scaled coarse cells, and submits a distant terrain mesh through a custom render pipeline. The pass is intentionally a stepping stone: its height-field sampler will be replaced by the shared voxel-region cache once the cache is wired to chunk lifecycle events.

NeoForge now has the 26.3 renderer pass as well. The shared core includes published LOD-region caching, compact voxel meshes, and stable material IDs. CI builds both loader targets with Java 25 / Gradle 9.6.

## Important

This project is an independent implementation. It does not copy or redistribute Voxy source code.
