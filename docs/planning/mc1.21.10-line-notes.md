# support/mc1.21.10 — line creation notes + decisions log

**Created 2026-08-22** (user request) as a LATERAL port: cut from
`support/mc1.21.11-v0.12` @ b21a67fc — the fully-prepared v0.12.0 tip,
including the ramp window-limited fix + both 2026-08-22 panel fold rounds —
one MC patch DOWN. MC 1.21.10 and 1.21.11 are adjacent patch releases, so the
entire MC-facing surface (mappings renames, count-short NativeSectionShape,
ScopedCarrier, gametest attribute names, split-dir resolver) is the 1.21.11
line's verbatim; the port is a dependency retarget plus two ecosystem-forced
deltas. Tier: **correct, not perfect** (the parent line's tier — full builds +
T1/T2 + representative smokes, no live rig).

## Retarget (mechanical)

- `gradle.properties`: minecraft_version/minecraft_dependency 1.21.10,
  fabric_version 0.138.4+1.21.10, neoforge_version 21.10.64 (loader 0.19.3 and
  loom 1.17.13 unchanged — both span the patch pair).
- `paper/build.gradle`: paperDevBundle 1.21.10-R0.1-SNAPSHOT (exists upstream,
  verified).
- `.github/line.env`: all identities 1.21.10; LINE_NEOFORGE_NAME retargeted;
  LINE_SHIP_NEOFORGE stays false (no client pairing on this line either).
- `fabric.mod.json`: fabric-api floor lowered to >=0.138.0 — the parent's
  >=0.141.0 is UNSATISFIABLE on 1.21.10 (fabric-api tops out at
  0.138.4+1.21.10).
- Benchmark-arm dev pins: moonrise-opt 0.8.0-beta.4+c0e63e9, c2me-fabric
  0.3.6+alpha.0.11+1.21.10 (runtime-only A/B arms).

## Decision 1 (dated 2026-08-22): NO Folia on this line

Folia publishes no MC 1.21.10 build (its version list jumps 1.21.8 → 1.21.11;
verified against fill.papermc.io at line creation). Per the R-7 direction-flip
doctrine, presence of `folia-supported: true` would advertise a platform with
no loadable host, so this line declares **`folia-supported: false`** and drops
`folia` from LINE_PAPER_LOADERS ("paper purpur"). Pinned three ways:
`PluginYmlContractTest.foliaSupportedIsFalseBecauseFoliaSkips12110` (the
inverted flavor test), the `ReleaseWorkflowContractTest` loaders pin, and
`release_check.py`'s inverted raw-line grep (with a selftest arm catching a
resurrected `true`). The Folia code paths (regionized probing, lifecycle
mailbox, `FoliaWiringContractTest`) ship dormant and stay maintained — the
single plugin jar is shared across lines. The SOAK_PLATFORM=folia lane and
`test-server.sh run-folia` are inoperable here by upstream absence, not by
cut.

## Decision 2 (dated 2026-08-22, the §6.2-style cut record): Sodium options page CUT

Sodium for MC 1.21.10 tops out at **0.7.3**, which predates the structured
config API (`net.caffeinemc.mods.sodium.api.config.*`) the LSS options page
binds — the same reality that cut the page on the frozen 1.21.8 line. Cut
surface: `LSSConfigMenu.java` deleted, the `sodium:config_api_user`
entrypoint removed from fabric.mod.json, the sodium modCompileOnly dropped.
Kept: `RateSliderStops` (Sodium-import-free; ConfigValidationTest classloads
it), the ModMenu integration (modmenu 16.0.1 exists for 1.21.10), and every
config KEY — the JSON config files carry the full surface, so nothing
functional is lost, only the in-game Sodium page. The release notes must name
this cut. Revisit only if Sodium backports 0.8 to 1.21.10 (they will not).

## Gates run at creation

Recorded in the v0.12.0 release plan §12.10 as they complete: line
`release_check --selftest` (88 — the new folia-true arm), full clean
pre-flight at `-Pmod_version=0.12.0` + `release_check.py --version 0.12.0`,
T1/T2 via `:fabric:build -x runClientGameTest`, `:paper:test`,
`:neoforge:build`, CI on push, and a 2-Opus review pair (the per-line
discipline this release used everywhere).
