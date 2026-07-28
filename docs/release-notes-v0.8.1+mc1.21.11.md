### Bug Fixes

- **Fixes a server memory leak that could grow into an out-of-memory crash** — On busy servers, timestamp-cache saves could be scheduled faster than they finished writing, and each queued save held its own full in-memory copy of the cache (reported as 1.6 GB retained and an OOM within hours — issue #62). Saves now always coalesce to the newest state, so memory stays bounded no matter how slow the disk is.
- **Fixes dark faces returning after block edits near LOD terrain** — Re-served columns (after a block change, or a rejoin resync) lost their above-terrain sky light, so the v0.8.0 black-boundary-faces fix silently regressed on the second serve of any column. Sky light now survives every re-serve, and a WorldEdit-cleared column renders bright open sky instead of a black volume.
- **Bounded memory for long roaming sessions** — The server's per-player served-column tracking grew for the whole session (tens of MB per player on long flights). Far-behind entries are now swept periodically; returning to an area re-checks it cheaply.
- **Fair disk scheduling between players** — Under heavy disk load, the player who happened to be processed first could monopolize LOD disk reads while other players' terrain stalled. Service now rotates fairly every cycle.
- **Smoother "Open to LAN"** — The LOD service no longer does its startup disk work on the render thread when a world is opened to LAN.

### Performance

- **Cache files are written and read far faster** — The server timestamp cache and the client column cache now use buffered file IO instead of one tiny disk write per entry. Large cache saves drop from seconds to milliseconds, which also reduces disk pressure on busy servers and speeds up server shutdown and client disconnect.

### Compatibility

- **Patch for the Minecraft 1.21.11 line** — The same fix also ships as `v0.8.1` (MC 26.2) and `v0.8.1+mc26.1`. Safe to update straight from v0.8.0 or any earlier install; client and server can update independently.
