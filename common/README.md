# Common core

The common module contains no loader-specific Minecraft classes. It owns the memory model, LOD selection, budgets and worker lifecycle.

The renderer will consume this layer through small platform adapters so Fabric and NeoForge share the same terrain representation and scheduling logic.
