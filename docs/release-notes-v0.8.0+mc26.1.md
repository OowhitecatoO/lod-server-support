### Compatibility

- **The full v0.8.0 feature set for Minecraft 26.1.x** — Updates this line straight from v0.5.1 with everything since. Fabric: MC 26.1–26.1.2, Java 25+, Fabric API 0.146+. Paper/Purpur: 26.1.2. Folia: 26.1.2 (experimental). Also released: `v0.8.0` (MC 26.2) and `v0.8.0+mc1.21.11`.
- **Works with v0.4.x–v0.5.1 both ways** — Update the client, the server, or both, in any order. A v0.8.0 server keeps serving older clients; a v0.8.0 client still gets LODs (including newly generated terrain) from older servers.
- **Not published as "Voxy Server Side"** — On 26.1, install LOD Server Support (this mod).

### New Features

- **More reliable LOD loading** — Requests the server drops under load retry automatically within a second: no more permanently missing patches, and terrain keeps streaming while you fly.
- **Server performance is protected** — LOD disk reads and LOD terrain generation run below normal player chunk loading, so players always win.
- **LOD terrain fills near-to-far** — Generation no longer jumps ahead of closer missing terrain.
- **Anti-xray now covers LOD data** — The server's anti-xray (Paper's built-in, or the AntiXray mod on Fabric) is detected automatically and the same ores are masked in LOD data, with no trace of the hidden blocks left in it.

### Bug Fixes

- **LOD sessions start reliably on Paper and Folia** — The first batch of LOD requests can no longer be lost at join.
- **Fixes server crash with the AntiXray mod (Fabric)** — The two now work together.
- **Quieter console under heavy disk load** — Slow-disk warnings are summarized instead of flooding the log; affected chunks retry automatically.

### Configuration

- New server options: `enableV16Compat` (default on — keeps v0.4.x–v0.5.1 clients working), `useBackgroundReadPriority` (default on — set `false` to restore the old foreground disk reads), `missMemoTtlSeconds` (default 30), and `xrayObfuscation` / `xrayHiddenBlocks` / `xrayMaxBlockHeight` (default `"auto"` adopts the server's anti-xray settings). The old `syncOnLoadConcurrencyLimitPerPlayer` key is retired and ignored.
