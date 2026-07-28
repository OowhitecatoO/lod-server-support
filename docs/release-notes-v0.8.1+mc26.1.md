### Bug Fixes

- **Fixes a server memory leak that could grow into an out-of-memory crash** — On busy servers, timestamp-cache saves could be scheduled faster than they finished writing, and each queued save held its own full in-memory copy of the cache (reported as 1.6 GB retained and an OOM within hours — issue #62). Saves now always coalesce to the newest state, so memory stays bounded no matter how slow the disk is.

### Performance

- **Cache files are written and read far faster** — The server timestamp cache and the client column cache now use buffered file IO instead of one tiny disk write per entry. Large cache saves drop from seconds to milliseconds, which also reduces disk pressure on busy servers and speeds up server shutdown and client disconnect.

### Compatibility

- **Patch for the Minecraft 26.1.x line** — The same fix also ships as `v0.8.1` (MC 26.2) and `v0.8.1+mc1.21.11`. Safe to update straight from v0.8.0 or any earlier install; client and server can update independently.
