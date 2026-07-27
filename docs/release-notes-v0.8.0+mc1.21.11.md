### Compatibility

- **The full v0.8.0 feature set for Minecraft 1.21.11** — Updates this line straight from v0.5.0+mc1.21.11 with everything since. Fabric: MC 1.21.11, Java 21+, Fabric API 0.141+. Paper/Purpur: 1.21.11. Folia: 1.21.11 (experimental). Also released: `v0.8.0` (MC 26.2) and `v0.8.0+mc26.1`.
- **Works with v0.5.0+mc1.21.11 both ways** — Update the client, the server, or both, in any order. A v0.8.0 server keeps serving v0.5.0 clients; a v0.8.0 client still gets LODs (including newly generated terrain) from a v0.5.0 server.

### New Features

- **More reliable LOD loading** — Requests the server drops under load retry automatically within a second: no more permanently missing patches, and terrain keeps streaming while you fly.
- **Server performance is protected** — LOD disk reads and LOD terrain generation run below normal player chunk loading, so players always win.
- **LOD terrain fills near-to-far** — Generation no longer jumps ahead of closer missing terrain.
- **Anti-xray now covers LOD data** — The server's anti-xray (Paper's built-in, or the AntiXray mod on Fabric) is detected automatically and the same ores are masked in LOD data, with no trace of the hidden blocks left in it.

### Bug Fixes

- **Fixes black faces at chunk borders and treetops** — LOD data now carries the sky light for the air around terrain, so leaves and cliff sides no longer render black from one side at certain distances. Run `/lss clearcache` once after updating so already-cached LOD data picks up the fix.
- **LOD sessions start reliably on Paper and Folia** — The first batch of LOD requests can no longer be lost at join.
- **Quieter console under heavy disk load** — Slow-disk warnings are summarized instead of flooding the log; affected chunks retry automatically.

### Configuration

- New server options: `enableV16Compat` (default on — keeps v0.5.0 clients working), `useBackgroundReadPriority` (default on — set `false` to restore the old foreground disk reads), `missMemoTtlSeconds` (default 30), and `xrayObfuscation` / `xrayHiddenBlocks` / `xrayMaxBlockHeight` (default `"auto"` adopts the server's anti-xray settings). The old `syncOnLoadConcurrencyLimitPerPlayer` key is retired and ignored.
