Summary of Conversation

## Current State

The conversation covered multiple feature implementations for the TimePerDayWhitelist Bukkit/Paper/Folia plugin. The
final task was porting the full ChunkyBorder plugin functionality into the TimePerDayWhitelist plugin as a built-in
border system ( /tpdadmin border ... ), complete with shapes, wrap types, movement checking, teleport/mob/block
enforcement, and automatic border application to newly generated worlds.

All tasks are completed. BUILD SUCCESS.

This session: AGENTS.md was updated to reflect the full current codebase state (border system, user settings GUI,
action bar, world regeneration, all managers, GUI packages, command packages, and config structure).

--------

## Complete File Inventory

### Main class
•  TimePerDayPlugin.java  — Wires all managers, services, listeners, commands, and two global scheduler ticks
  (player tick + border check).

### manager/ package
•  PlayerTimeManager.java  — Central facade: ConcurrentHashMaps (playedToday, playerLimits, whitelist,
  sessionPoints, totalLevel, lastKitClaimDate, showActionBar), pendingDayOverReset, defaultLimitSeconds,
  currentDate, resetZoneId, spawnKits, dirty flag. Delegates to specialized managers.
•  PlayerPersistenceManager.java  — Load/save/reload of playerdata.yml (playtime, limits, whitelist,
  session-points, total-level, last-kit-claim-date, show-action-bar, pending-dayover-reset).
  Parses spawn-kits from config.
•  PlayerProgressionManager.java  — calculateInventorySessionPoints (inventory + armor + offhand + enderchest
  + experience), finalizeSessionProgress, grantDailyKit, getBestKitLevelFor.
•  PlayerResetManager.java  — performDayOverLogic (day-over + debug), resetEverything, resetOnlinePlayerState,
  wipeOnlinePlayerState (clear inventory, armor, enderchest, XP, health, food, fire, fall, game mode, teleport).
•  PlayerTickManager.java  — 1-second tick loop: day change detection, per-player tick (time increment,
  action bar, warning thresholds, timeout kick). Caches warning thresholds as Set<Long>.
•  PlayerMessageManager.java  — sendWarning, kickPlayer, buildJoinKickComponent, buildJoinInfoComponent.
  MiniMessage templates with {placeholder} substitution.
•  PlayerTimeSnapshot.java  — Record: played, limit, remaining, sessionPoints, totalLevel, whitelisted,
  bypassPermission, unlimited.
•  WorldRegenerationManager.java  — State machine: schedulePreGeneration (23:30), onPreGenerationTime
  (create world, Chunky), onDayOver (cancel Chunky, teleport, update server.properties, schedule deletion),
  debugDayOver (kick all, create random world, update server.properties, mark deletion).
  updateServerPropertiesLevelName, markWorldForDeletion/deleteMarkedWorlds, deleteWorldFolder,
  cancelChunkyIfRunning, handlePlayerJoin (teleport to active world).

### border/ package
•  BorderData.java  — POJO: world, centerX/Z, radiusX/Z, shape, wrap, bypassPlayers, isBounding(x,z)
  (square/ellipse math), getSize().
•  BorderShape.java  — Enum: SQUARE, RECTANGLE, CIRCLE, ELLIPSE with fromString().
•  BorderWrapType.java  — Enum: NONE, DEFAULT, BOTH, RADIAL, X, Z, EARTH with fromString().
•  BorderManager.java  — Load/save borders.yml, applyToWorld (Bukkit WorldBorder API),
  addBorder, removeBorder, getBorder, getAllBorders, setBorderRadius, setBorderCenter,
  setBorderShape, setBorderWrap, setBypass, isBypassing.
•  BorderCommand.java  — Full command handler: add, remove, list, bypass, shape, wrap, setcenter, setradius.
  Tab completion for all subcommands.
•  BorderCheckTask.java  — Runnable: checks all online players every second. Wrap logic: DEFAULT (rect→both,
  circle→radial), BOTH, RADIAL, X, Z, EARTH. getSafeY via getHighestBlockYAt.
•  BorderListener.java  — Event handlers: PlayerTeleportEvent (enderpearl/chorus block), CreatureSpawnEvent
  (block outside border), BlockBreakEvent, BlockPlaceEvent. Permission checks for bypass.

### gui/ package
•  AdminMenuHolder.java  — InventoryHolder with MenuType, targetUuid, sourcePage.
•  AdminMenuService.java  — Multi-page admin GUI: MAIN, PLAYER_LIST, PLAYER_ACTIONS, LIMITS, LEVELS,
  GLOBAL_RESET_CONFIRM. Player head items, pagination, limit presets, level presets.
•  AdminMenuListener.java  — Catches InventoryClickEvent/InventoryDragEvent for admin GUI.
•  MenuType.java  — Enum: MAIN, PLAYER_LIST, PLAYER_ACTIONS, LIMITS, LEVELS, GLOBAL_RESET_CONFIRM.
•  UserSettingsHolder.java  — InventoryHolder with owner UUID.
•  UserSettingsMenuService.java  — 27-slot settings inventory: action bar toggle (slot 11, LIME_DYE/GRAY_DYE),
  placeholder slots (13, 15), close button (22).
•  UserSettingsMenuListener.java  — Catches InventoryClickEvent/InventoryDragEvent for user settings GUI.

### command/ package
•  TimeCommand.java  — /tpd [time|settings]: shows time info or opens settings GUI.
•  AdminTimeCommand.java  — /tpdadmin <set|info|setlevel|addlevel|reset|resetplayer|setdefault|whitelist|gui|border|reload>.
  Passes border subcommand to BorderCommand. TabCompleter for all subcommands.
•  DebugTimeCommand.java  — /tpddebug <dayover|warn|timeout|allow>. Config gate (tpddebug key).
  allow subcommand hidden from tab completion.

### listener/ package
•  PlayerListener.java  — onPlayerJoin: handlePlayerJoinWorldCheck, pending day-over reset, kit granting,
  join info message, timeout kick.

### Resources
•  plugin.yml  — Commands: tpdadmin, tpd, tpddebug. Permissions: timeperday.admin, timeperday.bypass,
  timeperday.use, timeperday.debug. folia-supported: true.
•  config.yml  — tpddebug, default-limit-minutes, reset-timezone, warnings, world-regeneration,
  messages (MiniMessage), show-on-join, progression (items, experience, spawn-kits levels 10-150).
•  pom.xml  — Version 1.2.0-beta#11, Java 21, Paper API 1.21.4, maven-shade-plugin, resource filtering.

--------

## Technical Context

• Language: Java 21, Paper API 1.21.4
• Build: Maven with shade plugin
• Scheduler: Folia-compatible GlobalRegionScheduler (runDelayed, runAtFixedRate)
• Persistence: YamlConfiguration for borders.yml, playerdata.yml; Properties for server.properties; text file
  for deletion markers
• Thread safety: ConcurrentHashMap for tick-sensitive maps, volatile for chunkyRunning
• Text API: Kyori Adventure Component throughout
• Chunky integration: Via dispatchCommand() to console (no API dependency)

--------

## Strategy & Approach

• Pattern: Holder → Service → Listener → Command → Plugin wiring (same for all GUI features)
• Border system: Uses Bukkit's built-in WorldBorder API (setCenter(), setSize()) to visually show the border, plus
  custom BorderCheckTask and BorderListener for full enforcement
• World regeneration: Defensive server.properties editing (line-by-line read/write, atomic move), deletion marker
  system for worlds that can't be unloaded at runtime
• Debug commands: debugDayOver() has separate logic from onDayOver() — kicks players, no Chunky, random world names,
  immediate cleanup marker
• Save strategy: Dirty-flag with periodic flush (every 30s) or forceSave() at critical points

--------

## Exact Next Steps

No remaining tasks — all completed. If resuming work, consider:

1. Testing the border system with actual players on a Folia server
2. Adding the default-border-size config to the config.yml comment block explaining the world-regeneration section
3. Consider adding a particle visualizer for the border (like ChunkyBorder's dust particles)
4. The BorderCheckTask uses getHighestBlockYAt() which is synchronous — acceptable for now but could be async on
   Folia
5. Placeholder settings slots (13, 15) in UserSettingsMenuService are ready for future features