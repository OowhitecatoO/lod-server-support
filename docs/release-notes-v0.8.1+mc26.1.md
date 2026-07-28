### Bug Fixes

- **Fixes a server memory leak that could crash busy servers** — Cache saves could queue up faster than they finished writing, each holding a full in-memory copy (reported as an out-of-memory crash within hours — issue #62). Saves now coalesce to the newest state, so memory stays bounded.
- **Fixes dark faces returning after block edits near LOD terrain** — Re-served columns lost their above-terrain sky light, quietly undoing the v0.8.0 black-faces fix. Sky light now survives every re-serve, and a WorldEdit-cleared column renders bright open sky instead of a black volume.
- **Bounded memory on long flights** — Per-player served-chunk tracking no longer grows for the whole session; far-behind entries are swept periodically.
- **Fair disk scheduling between players** — Under heavy disk load, one player could monopolize LOD disk reads while everyone else's terrain stalled. Service now rotates fairly every cycle.
- **Smoother "Open to LAN"** — The LOD service no longer does its startup disk work on the render thread when a world is opened to LAN.

### Performance

- **Much faster cache saves and loads** — Cache files now use buffered IO instead of one tiny write per entry; large saves drop from seconds to milliseconds, which also speeds up server shutdown and client disconnect.

### Compatibility

- **Patch for the Minecraft 26.1.x line** — The same fixes also ship as `v0.8.1` (MC 26.2) and `v0.8.1+mc1.21.11`. Safe to update straight from v0.8.0 or earlier; client and server can update independently.
