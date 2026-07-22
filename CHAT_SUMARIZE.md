Summary of Conversation

## Current State


The conversation covered multiple feature implementations for the TimePerDayWhitelist Bukkit/Paper/Folia plugin. The
final task was porting the full ChunkyBorder plugin functionality into the TimePerDayWhitelist plugin as a built-in
border system ( /tpdadmin border ... ), complete with shapes, wrap types, movement checking, teleport/mob/block
enforcement, and automatic border application to newly generated worlds.

All tasks are completed. BUILD SUCCESS.


--------

## Files & Changes


### Feature 1: User Settings GUI ( /tpd settings )


New files:

•  gui/UserSettingsHolder.java  — InventoryHolder with owner UUID
•  gui/UserSettingsMenuService.java  — Builds 27-slot inventory, handles clicks
•  gui/UserSettingsMenuListener.java  — Catches InventoryClickEvent/InventoryDragEvent

Changed files:

•  command/TimeCommand.java  —  /tpd  (no args) /  /tpd time  shows time info;  /tpd settings  opens settings GUI
•  TimePerDayPlugin.java  — Wires UserSettingsMenuService, UserSettingsMenuListener
•  plugin.yml  — Updated  tpd  usage to  [time|settings]

### Feature 2: Action Bar Toggle Setting

Changed files:

•  manager/PlayerTimeManager.java  — Added  ConcurrentHashMap<UUID, Boolean> showActionBar ,  isShowActionBarEnabled() ,
setShowActionBar()  with  markDirty()
•  manager/PlayerPersistenceManager.java  — Loads/saves/clears  show-action-bar  section in  playerdata.yml
•  manager/PlayerTickManager.java  — Checks  isShowActionBarEnabled()  in tick loop, sends  "Verbleibend: Xh Xm Xs"  via
sendActionBar()
•  gui/UserSettingsMenuService.java  — Slot 11 is now a real toggle (LIME_DYE/GRAY_DYE) that toggles action bar display

### Feature 3: World Regeneration on Day Reset

New file:

•  manager/WorldRegenerationManager.java  — State machine for daily world reset:
  •  onPreGenerationTime()  at 23:30: creates new world  world_YYYYMMDD , starts Chunky pre-generation
  •  onDayOver()  at 00:00: cancels Chunky, teleports players, updates  server.properties , schedules old world deletion
  •  debugDayOver() : kicks all players, creates new world with random UUID suffix, updates  server.properties , marks
  old world for deletion
  •  updateServerPropertiesLevelName() : line-by-line edit of  server.properties  (preserves comments)
  •  markWorldForDeletion()  /  deleteMarkedWorlds() : marks worlds for deletion on next startup via
  worldregeneration_deletion.txt
  •  chunkyRunning  is  volatile  for Folia thread safety


Changed files:

•  manager/PlayerResetManager.java  —  performDayOverLogic(newDate, logMessage, isDebug)  calls
worldRegenerationManager.onDayOver()  or  debugDayOver()
•  manager/PlayerTimeManager.java  — Creates  WorldRegenerationManager ,  debugTriggerDayOver()  calls
triggerDebugDayOver()
•  TimePerDayPlugin.java  —  onDisable()  calls  timeManager.onDisable()  for Chunky cleanup
•  listener/PlayerListener.java  —  onPlayerJoin  calls  handlePlayerJoinWorldCheck()  to teleport players to active
world
•  config.yml  — Added  world-regeneration  section with  enabled ,  world-name-prefix ,  chunk-radius ,  chunky-quiet-
ms ,  default-border-size

### Feature 4: Full Border System ( /tpdadmin border ... )

New files (package  fun.vynofc.timeperday.border ):

•  BorderData.java  — POJO: world, centerX/Z, radiusX/Z, shape, wrap, bypassPlayers,  isBounding(x,z)  with
square/ellipse math
•  BorderShape.java  — Enum: SQUARE, RECTANGLE, CIRCLE, ELLIPSE
•  BorderWrapType.java  — Enum: NONE, DEFAULT, BOTH, RADIAL, X, Z, EARTH
•  BorderManager.java  — Load/save  borders.yml , apply to Bukkit  WorldBorder  API,
add/remove/setShape/setWrap/setRadius/setCenter/bypass
•  BorderCommand.java  — Full command handler: add, remove, list, bypass, shape, wrap, setcenter, setradius
•  BorderCheckTask.java  — Movement check every second, wrap logic (radial, x, z, both, earth), teleport back on border
violation
•  BorderListener.java  — Teleport redirect (enderpearl/chorus block), creature spawn block, block break/place
enforcement

Changed files:

•  TimePerDayPlugin.java  — Creates  BorderManager , registers  BorderListener ,  BorderCheckTask  runs every second
•  command/AdminTimeCommand.java  — Added  border  subcommand, updated TabCompleter
•  manager/WorldRegenerationManager.java  —  onPreGenerationTime()  and  debugDayOver()  call  plugin.
getBorderManager().addBorder(worldName, 0, 0, defaultBorderSize)  after world creation
•  config.yml  — Added  default-border-size: 1000  under  world-regeneration

Commands:

  /tpdadmin border add <Welt> <RadiusX> <RadiusZ> [CenterX] [CenterZ] [Shape] [Wrap]
  /tpdadmin border remove <Welt>
  /tpdadmin border list
  /tpdadmin border bypass <Welt> <Spieler>
  /tpdadmin border shape <Welt> <square|rectangle|circle|ellipse>
  /tpdadmin border wrap <Welt> <none|default|both|radial|x|z|earth>
  /tpdadmin border setcenter <Welt> <CenterX> <CenterZ>
  /tpdadmin border setradius <Welt> <RadiusX> <RadiusZ>

Permissions:  timeperday.border.bypass.move ,  timeperday.border.bypass.break ,  timeperday.border.bypass.place

--------

## Technical Context

• Language: Java 21, Paper API 1.21.4
• Build: Maven with shade plugin
• Scheduler: Folia-compatible  GlobalRegionScheduler  ( runDelayed ,  runAtFixedRate )
• Persistence:  YamlConfiguration  for  borders.yml ,  playerdata.yml ; Properties for  server.properties ; text file
for deletion markers
• Thread safety:  ConcurrentHashMap  for tick-sensitive maps,  volatile  for  chunkyRunning
• Text API: Kyori Adventure  Component  throughout
• Chunky integration: Via  dispatchCommand()  to console (no API dependency)

--------

## Strategy & Approach

• Pattern: Holder → Service → Listener → Command → Plugin wiring (same for all GUI features)
• Border system: Uses Bukkit's built-in  WorldBorder  API ( setCenter() ,  setSize() ) to visually show the border, plus
custom  BorderCheckTask  and  BorderListener  for full enforcement
• World regeneration: Defensive  server.properties  editing (line-by-line read/write, atomic move), deletion marker
system for worlds that can't be unloaded at runtime
• Debug commands:  debugDayOver()  has separate logic from  onDayOver()  — kicks players, no Chunky, random world names,
immediate cleanup marker

--------

## Exact Next Steps

No remaining tasks — all completed. If resuming work, consider:

1. Testing the border system with actual players on a Folia server
2. Adding the  default-border-size  config to the  config.yml  comment block explaining the world-regeneration section
3. Consider adding a particle visualizer for the border (like ChunkyBorder's dust particles)
4. The  BorderCheckTask  uses  getHighestBlockYAt()  which is synchronous — acceptable for now but could be async on
Folia