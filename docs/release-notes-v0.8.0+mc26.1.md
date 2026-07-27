### Compatibility

- **The full v0.8.0 feature set, rebuilt for Minecraft 26.1.x** — This release updates the 26.1 support line straight from v0.5.1 to the current feature set: everything the main line shipped in v0.6.x–v0.8.0 (except the Minecraft 26.2 update itself). v0.8.0 releases for three Minecraft versions at once — this build for MC 26.1.x, plus `v0.8.0` (MC 26.2) and `v0.8.0+mc1.21.11` from their own lines. The Fabric mod runs on MC 26.1–26.1.2 (Java 25+, Fabric API 0.146+); the Paper plugin needs a 26.1.2 server.
- **Works with older versions — update in any order** — v0.8.0 uses a newer LOD networking protocol than the last published 26.1 builds, but it stays fully compatible both ways with every published 26.1 release back to v0.4.x: a v0.8.0 server keeps serving older clients (the server translates their protocol on the fly, at their old pace), and a v0.8.0 client renders LODs from servers still on v0.4.x–v0.5.1 — including driving generation of new terrain on demand. Update the client, the server, or both — in any order.
- **Folia support continues (experimental)** — This line keeps `folia-supported`, so the plugin loads on Folia 26.1.2 as before (unlike the 26.2 line, which waits for a Folia 26.2 build to exist). Folia support remains experimental: validated in single-player soak runs; busy multi-region servers are untested.
- **Not published as "Voxy Server Side"** — The separate Voxy Server Side Modrinth listing tracks the Minecraft 26.2 line only. On 26.1, install LOD Server Support (this mod) — the two are wire-compatible, so 26.2 VSS players and 26.1 LSS servers still interoperate where Minecraft versions allow.

### New Features

- **Faster, more reliable LOD loading** — LOD terrain streams in with fewer stalls and no more permanently missing patches: the client now declares everything it still wants once per second, so any request the server had to drop under load is retried automatically within a second instead of timing out, backing off, or silently never arriving.
- **Server performance is protected while LODs stream** — Players loading normal chunks no longer compete with LOD streaming: LOD disk reads run below vanilla's own chunk loads on all platforms (on Fabric servers with chunk-IO overhaul mods like C2ME an adaptive throttle takes over instead), and LOD terrain generation runs at low priority on Paper so player-driven world loading always wins. Set `useBackgroundReadPriority: false` to restore the old foreground disk reads (the Paper generation priority is not configurable).
- **Terrain generation fills near-to-far, even while flying** — LOD-driven terrain generation now follows strict ordering rules: it never overtakes closer terrain that is still loading, stays within a couple of rings of the nearest missing area, and no longer chases ahead of fast-moving players.
- **Anti-xray protection now covers LOD data** — On servers running anti-xray (Paper's built-in, or the AntiXray mod on Fabric), distant LOD columns used to reveal real ore locations. LSS now detects the anti-xray engine automatically and masks the same ores in LOD data. Configurable via `xrayObfuscation` (`"auto"`/`"on"`/`"off"`).
- **New diagnostics for admins** — `/lss trace` records a per-event log of the client's LOD activity (scans, movement, received columns with their serve source), and `/lsslod diag` shows generation-ordering counters and the read-throttle state.

### Bug Fixes

- **Anti-xray masking no longer leaks hidden ore ids in the raw data** — Masked LOD columns replaced hidden blocks visually but still carried their ids inside the chunk data's palette, so a modified client could read the ore list back out. Masked sections are now rebuilt from scratch and ship without any trace of the hidden blocks.
- **LOD sessions start reliably on Paper and Folia** — A client's very first chunk-request batch could arrive before the server finished registering the player and be silently dropped (self-healed within a second, but measurable at every join — and routine on Folia, where registration crosses threads). The server now completes registration before inviting requests. Folia support remains experimental.
- **Fixes server crash with the AntiXray mod (Fabric)** — Running DrexHD's AntiXray alongside LSS crashed the server as soon as LOD data was served. The two now work together.
- **Flying no longer pauses LOD loading** — Crossing chunk borders faster than once per second used to silently stop all LOD requests until you stood still. Terrain now streams in along your flight path.
- **Heavy building no longer stalls LOD loading** — A steady stream of world edits could previously delay LOD requests indefinitely on busy servers; change notifications no longer affect the request cadence.
- **Quieter console under heavy disk load** — A slow disk no longer floods the server console: LOD read timeouts are summarized in one throttled warning line per minute, saturation warnings are rate-limited, and the routine cache-save message is demoted to debug. (All remain harmless — affected chunks retry automatically.)

### Configuration

- **`enableV16Compat`** — New server option (default `true`) controlling the legacy-client compatibility layer. Set `false` to turn it off; older (v0.4.x–v0.6.x protocol) clients then get no LOD session, like any other version mismatch.
- **`useBackgroundReadPriority`** — New server option (default `true`) putting LOD disk reads below vanilla's own chunk loads. This is a behavior change on every server — set `false` to restore the old foreground reads.
- **`missMemoTtlSeconds`** — New server option (default `30`, clamped 0–60) controlling how long the server remembers that a chunk is not generated yet, so waiting requests skip redundant disk lookups. `0` turns the memo off.
- **`xrayObfuscation` / `xrayHiddenBlocks` / `xrayMaxBlockHeight`** — New server options for LOD anti-xray masking (default `"auto"` detects the server's anti-xray engine and adopts its hidden-block list).
- **`syncOnLoadConcurrencyLimitPerPlayer` retired** — The per-player disk-read limit is now a fixed constant (200). The old key is ignored and removed from the config file on the next save.

### Performance

- **Cuts duplicate chunk sends while loading LODs** — The server occasionally re-read and re-sent chunks it had just delivered. It now skips these duplicates, reducing disk and network load while players fill in their LOD view.
- **Lower server overhead with many players** — Loaded-chunk serialization now skips columns already queued to send, is shared fairly across players each tick, and is capped globally so many players backfilling at once cannot stretch the server tick.
