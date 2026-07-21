# AGENTS.md

Guidance for AI agents working in this repository.

## Project Overview

`TimePerDayWhitelist` is a Paper/Folia Minecraft server plugin (Java 21, Maven) that enforces a daily
playtime limit per player and layers a session-based progression/leveling system on top of it.
Package root: `fun.vynofc.timeperday`. Docs are in German (README, guides, plans); code comments and
identifiers are mostly German/English mixed — match the existing language when editing comments.

## Build / Package

```bash
mvn clean package        # or ./build.sh / build.bat (Windows)
```

- Requires JDK 21+ and Maven. `java.version` / compiler release is pinned to 21 in `pom.xml`.
- Uses `maven-shade-plugin` to produce a shaded jar in `target/`.
- Dependency: `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` (provided scope) from the `papermc` repo.
- Resource filtering is enabled (`filtering=true`), so `${project.version}` in `plugin.yml` is replaced
  at build time — don't hardcode a version string there.
- **No test suite exists** (`src/test` not present). Validation is manual; see
  `docs/testing/test-plan.md` for the full manual test plan and follow it after logic changes.
- SonarScanner config lives in `sonar-project.properties` (expects `src/test/java` and
  `target/classes`, neither of which currently exist for tests — scanning is optional/CI-only).

## Architecture

`TimePerDayPlugin` (main class, `onEnable`/`onDisable`) wires everything together:
- Creates one `PlayerTimeManager` (central facade) and one `AdminMenuService`.
- Registers `PlayerListener` and `AdminMenuListener` as Bukkit event listeners.
- Registers commands `tpdadmin`, `tpd`, `tpddebug` (see `plugin.yml`).
- `DebugTimeCommand` now takes both `PlayerTimeManager` and `TimePerDayPlugin` — the plugin
  reference is needed for the `tpddebug` config gate (see Debug Command Gate section below).
- Runs a **global** fixed-rate scheduler tick (1s, `getGlobalRegionScheduler().runAtFixedRate`) that
  calls `timeManager.tickOnlinePlayers()`. Because this is Folia-compatible, per-player actions
  triggered from the global tick must be delegated to the player's own region scheduler — don't do
  direct per-player Bukkit API calls from the global tick callback without checking for Folia safety
  patterns already used in `PlayerTickManager`.

### Manager split (`manager/` package)

`PlayerTimeManager` is a thin facade holding shared state (`ConcurrentHashMap`s for played time,
limits, whitelist, session points, total level, kit claim dates) and delegates real logic to:

- `PlayerPersistenceManager` — load/save/reload of `playerdata.yml` and spawn-kit config parsing.
- `PlayerProgressionManager` — session point calculation (`calculateInventorySessionPoints`), total
  level finalization, daily kit granting.
- `PlayerResetManager` — day-over flow, per-player/global reset, pending-reset bookkeeping.
- `PlayerTickManager` — the 1-second tick loop, warning thresholds, timeout/kick handling.
- `PlayerMessageManager` — join/kick/warning messages with placeholder substitution (MiniMessage).
- `PlayerTimeSnapshot` — plain data holder for a point-in-time snapshot of a player's time/progression.

When adding a new responsibility, prefer adding a new manager (or a method on the relevant existing
one) rather than growing `PlayerTimeManager` itself — it's intentionally kept as a facade.

Other packages:
- `gui/` — `AdminMenuService` builds inventory-based admin menus (`AdminMenuHolder`, `MenuType`),
  `AdminMenuListener` handles clicks.
- `command/` — `TimeCommand`, `AdminTimeCommand`, `DebugTimeCommand` (with `tpddebug` config gate).
- `listener/` — `PlayerListener` (join/quit/kick hooks).

## Key Conventions & Gotchas

- **Dirty-flag save strategy**: mutating setters call `markDirty()` instead of saving immediately;
  actual writes happen via periodic flush (`AUTO_SAVE_INTERVAL_TICKS = 600` ticks = 30s) or
  `forceSave()` at critical points (`onDisable`, reload, day-over completion). Don't add ad-hoc
  `save()` calls for routine state changes — use `markDirty()` and let the flush cycle handle it.
  See `docs/guides/save-strategy.md` for the design rationale.
- **Timezone-aware day reset**: `reset-timezone` config key (IANA zone, e.g. `Europe/Berlin`) drives
  `resetZoneId`, used everywhere instead of `LocalDate.now()`/`ZoneId.systemDefault()`. If you add new
  date logic, use `LocalDate.now(resetZoneId)`, not the zone-less default, and make sure it's
  refreshed on `reload()`, not only at construction. See `docs/guides/reset-timezone.md`.
- **Warning thresholds are cached**: `PlayerTickManager` caches config `warnings` list as a `Set<Long>`
  via `reloadWarningThresholds()`, called at load/reload — don't read config lists directly inside the
  per-tick loop (perf). See `docs/guides/tick-loop-optimization.md`.
- **Progression / session points**: computed from current inventory + enderchest contents each time
  (`calculateInventorySessionPoints`), NOT accumulated on pickup. Each configured item in
  `progression.items.*.level` contributes its `level` value once per item in inventory. XP level also
  contributes via `progression.experience.level-per-level` (default 0.2), using `player.getLevel()`
  (integer level only, not `getExp()` fractional progress) — see `docs/plans/plan_experience-level.md`
  for the full rationale if extending this further.
- Session points are finalized into `total-level` only at kick/timeout or day reset, never live.
- Spawn kits (`progression.spawn-kits`) are keyed by level thresholds (string keys like `"10"`), only
  the highest matching threshold is granted, once per player per day (tracked via
  `lastKitClaimDate`).
- Messages in `config.yml` use MiniMessage format and are rendered as real Components, not plain text
  — when adding placeholders, follow the existing `{placeholder}` token style and wire substitution in
  `PlayerMessageManager`.
- Config resource filtering means `config.yml`/`plugin.yml` under `src/main/resources` get Maven
  property substitution; avoid introducing literal `${...}` text unless intended as a Maven property.
- Folia support (`folia-supported: true` in `plugin.yml`) — avoid assuming a single global scheduler
  thread; region schedulers are used for player-specific work.

## Documentation Map

- `README.md` — feature list, command table, config keys, GUI overview (authoritative, keep in sync
  with behavior changes).
- `docs/guides/*.md` — German "Lern-Guide" style design docs for specific subsystems (save strategy,
  tick loop, timezone, config validation). Treat these as design references/rationale, not always
  reflecting 100% current code state — verify against source before relying on them.
- `docs/plans/*.md` — implementation plans for specific features (e.g. XP-level progression), useful
  for understanding *why* code looks the way it does.
- `docs/testing/test-plan.md` — manual test plan; run through relevant sections after changes since
  there's no automated test suite.

## Debug Command Gate (`tpddebug`)

The `DebugTimeCommand` (`/tpddebug`) has a two-step activation model:

- Permission `timeperday.debug` (default `op`) is required to use the command at all.
- Even with permission, all destructive subcommands (`dayover`, `warn`, `timeout`) are blocked until
  an op/console runs `/tpddebug allow`, which sets `config.yml` key `tpddebug` to `true`.
  The `tpddebug` config key defaults to `false` and is persisted via `saveConfig()`.
- The `DebugTimeCommand` constructor now takes both `PlayerTimeManager` and `TimePerDayPlugin`
  (the plugin reference is needed for `getConfig()`/`saveConfig()`).
- The `allow` subcommand is not shown in tab completion; it is a hidden bootstrap command.

This prevents accidental debug-triggering even by ops — the gate must be explicitly toggled on first.

## Versioning

Version lives in `pom.xml` (`<version>`) and is injected into `plugin.yml` via resource filtering.
Current scheme uses `-beta#N` suffixes (e.g. `1.2.0-beta#4`). Built jars are archived under
`.archiv/<major.minor>/` (and `.archiv/<major.minor>/beta/` for betas) — this is a manual archive, not
part of the build process.
