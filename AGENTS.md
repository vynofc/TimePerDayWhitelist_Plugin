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
- Creates one `PlayerTimeManager` (central facade), one `AdminMenuService`, one `UserSettingsMenuService`,
  and one `BorderManager`.
- Registers `PlayerListener`, `AdminMenuListener`, `UserSettingsMenuListener`, and `BorderListener`
  as Bukkit event listeners.
- Registers commands `tpdadmin`, `tpd`, `tpddebug` (see `plugin.yml`).
- `DebugTimeCommand` takes both `PlayerTimeManager` and `TimePerDayPlugin` — the plugin
  reference is needed for the `tpddebug` config gate (see Debug Command Gate section below).
- Runs a **global** fixed-rate scheduler tick (1s, `getGlobalRegionScheduler().runAtFixedRate`) that
  calls `timeManager.tickOnlinePlayers()`. Because this is Folia-compatible, per-player actions
  triggered from the global tick must be delegated to the player's own region scheduler.
- Runs a second **global** fixed-rate tick for `BorderCheckTask` — interval configurable via
  `border-options.check-interval` (default 20 ticks). Per-player checks delegate to the player's
  region scheduler.
- Runs a third **global** fixed-rate tick (1 tick) for the border particle visualizer
  (`startBorderVisualizer()`) — enabled/disabled via `border-options.visualizer-enabled`.

### Manager split (`manager/` package)

`PlayerTimeManager` is a thin facade holding shared state (`ConcurrentHashMap`s for played time,
limits, whitelist, session points, total level, kit claim dates, action bar preference) and delegates
real logic to:

- `PlayerPersistenceManager` — load/save/reload of `playerdata.yml` and spawn-kit config parsing.
- `PlayerProgressionManager` — session point calculation (`calculateInventorySessionPoints`), total
  level finalization, daily kit granting.
- `PlayerResetManager` — day-over flow, per-player/global reset, pending-reset bookkeeping.
- `PlayerTickManager` — the 1-second tick loop, warning thresholds, timeout/kick handling, action bar.
- `PlayerMessageManager` — join/kick/warning messages with placeholder substitution (MiniMessage).
- `PlayerTimeSnapshot` — plain data holder for a point-in-time snapshot of a player's time/progression.
- `WorldRegenerationManager` — daily world regeneration on day reset (Chunky pre-generation,
  server.properties editing, deletion markers).

### Border system (`border/` package)

Full border system accessible via `/tpdadmin border ...`:

- `BorderData` — POJO: world, centerX/Z, radiusX/Z, shape, wrap, bypassPlayers, bounding math.
- `BorderShape` — Enum: SQUARE, RECTANGLE, CIRCLE, ELLIPSE.
- `BorderWrapType` — Enum: NONE, DEFAULT, BOTH, RADIAL, X, Z, EARTH.
- `PlayerData` — Per-player runtime state: last valid location, bypass flag.
- `BorderManager` — Load/save `borders.yml`, config loading, dirty-flag save strategy,
  add/remove/modify, player data tracking.
- `BorderCommand` — Command handler: add, remove, list, bypass, shape, wrap, setcenter, setradius.
- `BorderCheckTask` — Movement check at configurable interval (default 20 ticks, via
  `border-options.check-interval`), wrap logic, per-player region scheduler delegation.
- `BorderListener` — Teleport redirect (enderpearl/chorus gated by config), creature spawn
  blocking (config-gated), block break/place, player quit cleanup.
- `util/Particles` — DUST-particle border visualizer, renders border edges around each player.
- `util/BorderColor` — Visualizer color config (hex) with rainbow fallback.

### GUI packages (`gui/`)

- `AdminMenuService` / `AdminMenuHolder` / `AdminMenuListener` / `MenuType` — multi-page admin GUI
  for player management, limits, levels, whitelist, global reset.
- `UserSettingsMenuService` / `UserSettingsHolder` / `UserSettingsMenuListener` — player settings GUI
  (`/tpd settings`) with action bar toggle (slot 11) and placeholder slots for future settings.

### Command package (`command/`)

- `TimeCommand` — `/tpd [time|settings]`: shows time info or opens settings GUI.
- `AdminTimeCommand` — `/tpdadmin <set|info|setlevel|addlevel|reset|resetplayer|setdefault|whitelist|gui|border|reload>`.
- `DebugTimeCommand` — `/tpddebug <dayover|warn|timeout|allow>` with config gate.

### Listener package (`listener/`)

- `PlayerListener` — onPlayerJoin: handles world check, border auto-creation fallback
  (`ensureBorderExists`), pending day-over reset, kit granting, join messages, timeout kick.

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

## User Settings GUI (`/tpd settings`)

- Opened via `/tpd settings` or `/tpd` (no args shows time info, then user can navigate).
- 27-slot inventory, `UserSettingsHolder` with owner UUID for security.
- Slot 11: Action Bar toggle (LIME_DYE when on, GRAY_DYE when off). State stored in
  `showActionBar` ConcurrentHashMap, persisted via `PlayerPersistenceManager` in
  `playerdata.yml` section `show-action-bar`.
- Slots 13, 15: Placeholder settings for future updates.
- Slot 22: Close button.

## Action Bar Display

- When enabled per player, `PlayerTickManager` sends `"Verbleibend: Xh Xm Xs"` via
  `player.sendActionBar()` every tick (1 second).
- Off by default; player must enable via `/tpd settings` GUI.

## World Regeneration System

- `WorldRegenerationManager` implements a daily world reset state machine:
  - `onPreGenerationTime()` at 23:30: creates new world `world_YYYYMMDD`, starts Chunky pre-generation.
  - `onDayOver()` at 00:00: cancels Chunky, teleports players, updates `server.properties`, schedules
    old world deletion (30 min delay).
  - `debugDayOver()`: separate logic for debug — kicks all players, creates world with random UUID
    suffix, updates `server.properties`, immediately marks old world for deletion.
- Config keys under `world-regeneration`: `enabled`, `world-name-prefix`, `chunk-radius`,
  `chunky-quiet-ms`, `default-border-size`.
- `server.properties` editing: line-by-line read/write with atomic move, preserves comments.
- Deletion marker system: `worldregeneration_deletion.txt` for worlds that can't be unloaded at
  runtime; processed on next startup.
- `chunkyRunning` is `volatile` for Folia thread safety.
- On new world creation, automatically applies a border via `BorderManager.addBorder()` with
  `default-border-size`.

## Border System (`/tpdadmin border ...`)

Commands:
```
/tpdadmin border add <Welt> <RadiusX> <RadiusZ> [CenterX] [CenterZ] [Shape] [Wrap]
/tpdadmin border remove <Welt>
/tpdadmin border list
/tpdadmin border bypass <Welt> <Spieler>
/tpdadmin border shape <Welt> <square|rectangle|circle|ellipse>
/tpdadmin border wrap <Welt> <none|default|both|radial|x|z|earth>
/tpdadmin border setcenter <Welt> <CenterX> <CenterZ>
/tpdadmin border setradius <Welt> <RadiusX> <RadiusZ>
```

Permissions:
- `timeperday.border.bypass.move` — bypass movement checks
- `timeperday.border.bypass.break` — bypass block break enforcement
- `timeperday.border.bypass.place` — bypass block place enforcement

Border enforcement:
- `BorderCheckTask` runs at configurable interval, delegates per-player checks to the player's
  region scheduler (Folia-compatible). If outside and wrap is set, wraps the player; otherwise
  teleports to last known valid location or spawn.
- Crossing effects: configurable particle effect (`border-options.effect`) and sound
  (`border-options.sound`), plus a message via action bar or chat (`border-options.message`,
  `border-options.use-action-bar`).
- `BorderListener` handles: teleport redirect (enderpearl/chorus fruit gated by
  `border-options.prevent-enderpearl`/`border-options.prevent-chorus-fruit`), creature
  spawn blocking (gated by `border-options.prevent-mob-spawns`), block break/place blocking.
- Visual border: particle-based visualizer rendering DUST particles along the border edges
  (`border-options.visualizer-enabled`, `border-options.visualizer-range`,
  `border-options.visualizer-color`). No vanilla `WorldBorder` API is used.
- Auto-creation: on first player join to a world without a border, a default border is
  created using `world-regeneration.default-border-size`.
- `BorderManager` uses the same dirty-flag save strategy as `PlayerTimeManager`:
  `markDirty()` on mutations, `flushIfDirty()` periodic, `forceSave()` on critical points.

## Debug Command Gate (`tpddebug`)

The `DebugTimeCommand` (`/tpddebug`) has a two-step activation model:

- Permission `timeperday.debug` (default `op`) is required to use the command at all.
- Even with permission, all destructive subcommands (`dayover`, `warn`, `timeout`) are blocked until
  an op/console runs `/tpddebug allow`, which sets `config.yml` key `tpddebug` to `true`.
  The `tpddebug` config key defaults to `false` and is persisted via `saveConfig()`.
- The `DebugTimeCommand` constructor takes both `PlayerTimeManager` and `TimePerDayPlugin`
  (the plugin reference is needed for `getConfig()`/`saveConfig()`).
- The `allow` subcommand is not shown in tab completion; it is a hidden bootstrap command.

This prevents accidental debug-triggering even by ops — the gate must be explicitly toggled on first.

## Config File Structure (`config.yml`)

- `tpddebug` — debug command gate (boolean, default false)
- `default-limit-minutes` — default daily limit (default 60)
- `reset-timezone` — IANA timezone for day reset
- `warnings` — list of remaining-second thresholds for warning messages
- `border-options` — check-interval, message, use-action-bar, effect, sound, prevent-mob-spawns,
  prevent-enderpearl, prevent-chorus-fruit, visualizer-enabled, visualizer-range, visualizer-color
- `world-regeneration` — enabled, world-name-prefix, chunk-radius, chunky-quiet-ms, default-border-size
- `messages` — MiniMessage templates for kick, warning, join messages
- `show-on-join` — whether to show join info message
- `progression` — items (level per material), experience (level-per-level), spawn-kits (level → items)

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

## Versioning

Version lives in `pom.xml` (`<version>`) and is injected into `plugin.yml` via resource filtering.
Current scheme uses `-beta#N` suffixes (e.g. `1.2.0-beta#11`). Built jars are archived under
`.archiv/<major.minor>/` (and `.archiv/<major.minor>/beta/` for betas) — this is a manual archive, not
part of the build process.