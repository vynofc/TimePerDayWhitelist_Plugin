package fun.vynofc.timeperday.gui.config;

import fun.vynofc.timeperday.TimePerDayPlugin;
import fun.vynofc.timeperday.border.BorderManager;
import fun.vynofc.timeperday.manager.PlayerTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ConfigMenuService {

    private static final int[] LIMIT_PRESETS = {30, 60, 90, 120, 180, 240};
    private static final int[] CHECK_INTERVAL_PRESETS = {5, 10, 15, 20, 30, 40};
    private static final int[] VISUALIZER_RANGE_PRESETS = {4, 6, 8, 10, 12, 16};
    private static final int[] CHUNK_RADIUS_PRESETS = {8, 12, 16, 20, 24, 32};
    private static final int[] CHUNKY_QUIET_PRESETS = {100, 250, 500, 1000, 2000};
    private static final int[] BORDER_SIZE_PRESETS = {500, 1000, 1500, 2000, 3000, 5000};
    private static final double[] MAX_HEALTH_PRESETS = {20.0, 10.0, 6.0, 2.0};
    private static final double[] STREAK_BONUS_PRESETS = {0.1, 0.25, 0.5, 1.0, 2.0};

    private final TimePerDayPlugin plugin;
    private final PlayerTimeManager timeManager;
    private final BorderManager borderManager;

    public ConfigMenuService(TimePerDayPlugin plugin, PlayerTimeManager timeManager,
                             BorderManager borderManager) {
        this.plugin = plugin;
        this.timeManager = timeManager;
        this.borderManager = borderManager;
    }

    public void openMainPage(Player player) {
        ConfigMenuHolder holder = new ConfigMenuHolder(ConfigPageType.MAIN);
        Inventory inventory = createInventory(holder, 27, "tpd Konfiguration");

        inventory.setItem(0, placeholderItem(Material.GRAY_STAINED_GLASS_PANE, "Hauptmenue",
                "Du bist bereits im Hauptmenue."));
        inventory.setItem(8, namedItem(Material.BARRIER, "Schliessen",
                "Schliesst das Konfigurations-Menue."));

        int limitMinutes = plugin.getConfig().getInt("default-limit-minutes", 60);
        inventory.setItem(10, intCycleItem("default-limit-minutes", "Standard-Tageslimit",
                LIMIT_PRESETS, limitMinutes, Material.CLOCK,
                "Das Standard-Tageslimit in Minuten fuer neue Spieler.",
                "Aktuell: " + limitMinutes + " Minuten",
                "Klicke zum Aendern."));

        boolean showOnJoin = plugin.getConfig().getBoolean("show-on-join", true);
        inventory.setItem(11, booleanToggleItem("show-on-join", "Join-Info anzeigen",
                showOnJoin,
                "Zeigt Spielzeit-Info beim Betreten des Servers an.",
                "Klicke zum Umschalten."));

        boolean tpddebug = plugin.getConfig().getBoolean("tpddebug", false);
        inventory.setItem(12, booleanToggleItem("tpddebug", "Debug-Modus",
                tpddebug,
                "Aktiviert /tpddebug-Befehle fuer OPs.",
                "Klicke zum Umschalten."));

        String timezone = plugin.getConfig().getString("reset-timezone", "Europe/Berlin");
        inventory.setItem(13, displayItem("reset-timezone", "Reset-Zeitzone",
                timezone, Material.PAPER,
                "Zeitzone fuer den taeglichen Reset (IANA-Format).",
                "Nur in config.yml aenderbar."));

        List<Integer> warnings = plugin.getConfig().getIntegerList("warnings");
        String warningsStr = warnings.isEmpty() ? "Keine" : warnings.toString();
        inventory.setItem(14, displayItem("warnings", "Warnungen (Sekunden)",
                warningsStr, Material.BOOK,
                "Liste der verbleibenden Sekunden fuer Warnungen.",
                "Nur in config.yml aenderbar."));

        Double maxHealth = timeManager.getMaxHealth();
        boolean healthEnabled = maxHealth != null;
        String healthDisplay = healthEnabled ? String.valueOf(maxHealth) : "aus";
        inventory.setItem(16, maxHealthCycleItem("max-health", "Max. Leben",
                maxHealth, Material.APPLE,
                "Maximales Spielerleben pro Tag.",
                "Aktuell: " + healthDisplay,
                "Klicke zum Durchschalten."));

        boolean vaultKeep = plugin.getConfig().getBoolean("vault-keep-on-death", false);
        inventory.setItem(15, booleanToggleItem("vault-keep-on-death", "XP beim Tod behalten",
                vaultKeep,
                "Spieler-Level (XP) gehen beim Tod nicht verloren.",
                "Klicke zum Umschalten."));

        boolean streakEnabled = plugin.getConfig().getBoolean("streak-bonus", false);
        inventory.setItem(18, booleanToggleItem("streak-bonus", "Streak-Bonus",
                streakEnabled,
                "Vergibt Bonus-Level fuer taegliches Einloggen.",
                "Klicke zum Umschalten."));

        double streakLevel = plugin.getConfig().getDouble("streak-bonus-level", 0.5D);
        inventory.setItem(19, doubleCycleItem("streak-bonus-level", "Streak-Level pro Tag",
                STREAK_BONUS_PRESETS, streakLevel, Material.EXPERIENCE_BOTTLE,
                "Bonus-Level pro aufeinanderfolgendem Tag.",
                "Aktuell: " + streakLevel,
                "Klicke zum Durchschalten."));

        inventory.setItem(20, namedItem(Material.OAK_FENCE, "→ Border-Einstellungen",
                "Oeffnet die Border-Konfiguration."));
        inventory.setItem(24, namedItem(Material.GRASS_BLOCK, "→ Welt-Reset-Einstellungen",
                "Oeffnet die Welt-Regenerations-Konfiguration."));

        player.openInventory(inventory);
    }

    public void openBorderPage(Player player) {
        ConfigMenuHolder holder = new ConfigMenuHolder(ConfigPageType.BORDER);
        Inventory inventory = createInventory(holder, 27, "Border Konfiguration");

        inventory.setItem(0, namedItem(Material.ARROW, "← Zurueck",
                "Zurueck zum Hauptmenue."));
        inventory.setItem(8, namedItem(Material.BARRIER, "Schliessen",
                "Schliesst das Konfigurations-Menue."));

        int checkInterval = plugin.getConfig().getInt("border-options.check-interval", 20);
        inventory.setItem(10, intCycleItem("border-options.check-interval", "Check-Intervall (Ticks)",
                CHECK_INTERVAL_PRESETS, checkInterval, Material.COMPARATOR,
                "Intervall fuer Border-Movement-Checks in Ticks.",
                "Aktuell: " + checkInterval + " Ticks",
                "Klicke zum Aendern."));

        boolean useActionBar = plugin.getConfig().getBoolean("border-options.use-action-bar", true);
        inventory.setItem(11, booleanToggleItem("border-options.use-action-bar",
                "Nachricht in Action-Bar",
                useActionBar,
                "Zeigt Border-Nachrichten in der Action-Bar statt im Chat.",
                "Klicke zum Umschalten."));

        boolean preventMobSpawns = plugin.getConfig().getBoolean("border-options.prevent-mob-spawns", false);
        inventory.setItem(12, booleanToggleItem("border-options.prevent-mob-spawns",
                "Mob-Spawns blocken",
                preventMobSpawns,
                "Verhindert das Spawnen von Kreaturen ausserhalb der Border.",
                "Klicke zum Umschalten."));

        boolean preventEnderpearl = plugin.getConfig().getBoolean("border-options.prevent-enderpearl", false);
        inventory.setItem(13, booleanToggleItem("border-options.prevent-enderpearl",
                "Enderperlen blocken",
                preventEnderpearl,
                "Verhindert Enderperlen-Teleportation ueber die Border hinaus.",
                "Klicke zum Umschalten."));

        boolean preventChorus = plugin.getConfig().getBoolean("border-options.prevent-chorus-fruit", false);
        inventory.setItem(14, booleanToggleItem("border-options.prevent-chorus-fruit",
                "Chorusfrucht blocken",
                preventChorus,
                "Verhindert Chorusfrucht-Teleportation ueber die Border hinaus.",
                "Klicke zum Umschalten."));

        boolean visualizer = plugin.getConfig().getBoolean("border-options.visualizer-enabled", true);
        inventory.setItem(15, booleanToggleItem("border-options.visualizer-enabled",
                "Partikel-Visualizer",
                visualizer,
                "Aktiviert den Partikel-basierten Border-Visualizer.",
                "Klicke zum Umschalten."));

        int visualizerRange = plugin.getConfig().getInt("border-options.visualizer-range", 8);
        inventory.setItem(16, intCycleItem("border-options.visualizer-range",
                "Visualizer-Reichweite",
                VISUALIZER_RANGE_PRESETS, visualizerRange, Material.SPYGLASS,
                "Maximale Reichweite des Border-Visualizers in Chunks.",
                "Aktuell: " + visualizerRange + " Chunks",
                "Klicke zum Aendern."));

        String effect = plugin.getConfig().getString("border-options.effect", "ender_signal");
        inventory.setItem(19, displayItem("border-options.effect", "Grenz-Effekt",
                effect, Material.FIREWORK_STAR,
                "Partikeleffekt beim Ueberschreiten der Border.",
                "Nur in config.yml aenderbar."));

        String sound = plugin.getConfig().getString("border-options.sound", "entity_enderman_teleport");
        inventory.setItem(20, displayItem("border-options.sound", "Grenz-Sound",
                sound, Material.NOTE_BLOCK,
                "Sound beim Ueberschreiten der Border.",
                "Nur in config.yml aenderbar."));

        String message = plugin.getConfig().getString("border-options.message",
                "&cDu hast das Ende der Welt erreicht!");
        inventory.setItem(21, displayItem("border-options.message", "Grenz-Nachricht",
                message, Material.PAPER,
                "Nachricht beim Ueberschreiten der Border.",
                "Nur in config.yml aenderbar."));

        String color = plugin.getConfig().getString("border-options.visualizer-color", "20A0FF");
        inventory.setItem(22, displayItem("border-options.visualizer-color", "Visualizer-Farbe",
                "#" + color, Material.LIGHT_BLUE_DYE,
                "Hex-Farbe des Border-Visualizers (ohne #).",
                "Nur in config.yml aenderbar."));

        player.openInventory(inventory);
    }

    public void openWorldRegenPage(Player player) {
        ConfigMenuHolder holder = new ConfigMenuHolder(ConfigPageType.WORLD_REGENERATION);
        Inventory inventory = createInventory(holder, 27, "Welt-Reset Konfiguration");

        inventory.setItem(0, namedItem(Material.ARROW, "← Zurueck",
                "Zurueck zum Hauptmenue."));
        inventory.setItem(8, namedItem(Material.BARRIER, "Schliessen",
                "Schliesst das Konfigurations-Menue."));

        boolean enabled = plugin.getConfig().getBoolean("world-regeneration.enabled", true);
        inventory.setItem(10, booleanToggleItem("world-regeneration.enabled",
                "Taeglicher Welt-Reset",
                enabled,
                "Aktiviert die taegliche Welt-Regeneration.",
                "Klicke zum Umschalten."));

        String prefix = plugin.getConfig().getString("world-regeneration.world-name-prefix", "world_");
        inventory.setItem(11, displayItem("world-regeneration.world-name-prefix",
                "Weltnamen-Praefix",
                prefix, Material.NAME_TAG,
                "Praefix fuer neue Welten (z. B. world_).",
                "Nur in config.yml aenderbar."));

        int chunkRadius = plugin.getConfig().getInt("world-regeneration.chunk-radius", 16);
        inventory.setItem(12, intCycleItem("world-regeneration.chunk-radius",
                "Chunk-Radius",
                CHUNK_RADIUS_PRESETS, chunkRadius, Material.MAP,
                "Radius der vorzugenerierenden Chunks.",
                "Aktuell: " + chunkRadius + " Chunks",
                "Klicke zum Aendern."));

        int chunkyQuiet = plugin.getConfig().getInt("world-regeneration.chunky-quiet-ms", 500);
        inventory.setItem(13, intCycleItem("world-regeneration.chunky-quiet-ms",
                "Chunky Pause (ms)",
                CHUNKY_QUIET_PRESETS, chunkyQuiet, Material.REDSTONE,
                "Pause zwischen Chunky-Generationsschritten in ms.",
                "Aktuell: " + chunkyQuiet + " ms",
                "Klicke zum Aendern."));

        int borderSize = plugin.getConfig().getInt("world-regeneration.default-border-size", 1000);
        inventory.setItem(14, intCycleItem("world-regeneration.default-border-size",
                "Standard-Border-Groesse",
                BORDER_SIZE_PRESETS, borderSize, Material.OAK_FENCE,
                "Default-Border-Groesse fuer neue Welten.",
                "Aktuell: " + borderSize + " Bloecke",
                "Klicke zum Aendern."));

        player.openInventory(inventory);
    }

    public void handleClick(Player player, ConfigMenuHolder holder, int slot) {
        if (!player.hasPermission("timeperday.admin")) {
            player.closeInventory();
            player.sendMessage(Component.text("Du hast keine Berechtigung fuer dieses Menue.",
                    NamedTextColor.RED));
            return;
        }

        switch (holder.getPageType()) {
            case MAIN -> handleMainClick(player, holder, slot);
            case BORDER -> handleBorderClick(player, holder, slot);
            case WORLD_REGENERATION -> handleWorldRegenClick(player, holder, slot);
        }
    }

    private void handleMainClick(Player player, ConfigMenuHolder holder, int slot) {
        switch (slot) {
            case 8 -> player.closeInventory();
            case 10 -> {
                int current = plugin.getConfig().getInt("default-limit-minutes", 60);
                int next = cycleIntPreset(current, LIMIT_PRESETS);
                plugin.getConfig().set("default-limit-minutes", next);
                plugin.saveConfig();
                timeManager.setDefaultLimitSeconds(next * 60L);
                player.sendMessage(Component.text()
                        .append(Component.text("Standard-Tageslimit auf ", NamedTextColor.GREEN))
                        .append(Component.text(next + " Minuten", NamedTextColor.YELLOW))
                        .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                        .build());
                openMainPage(player);
            }
            case 11 -> {
                toggleBooleanConfig("show-on-join", player);
                timeManager.reload();
                openMainPage(player);
            }
            case 12 -> {
                toggleBooleanConfig("tpddebug", player);
                openMainPage(player);
            }
            case 16 -> {
                Double current = timeManager.getMaxHealth();
                Double next = cycleMaxHealthPreset(current);
                if (next == null) {
                    plugin.getConfig().set("max-health", "aus");
                } else {
                    plugin.getConfig().set("max-health", String.valueOf(next));
                }
                saveAndReloadAll();
                player.sendMessage(Component.text()
                        .append(Component.text("Max. Leben auf ", NamedTextColor.GREEN))
                        .append(Component.text(next == null ? "aus" : String.valueOf(next),
                                NamedTextColor.YELLOW))
                        .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                        .build());
                openMainPage(player);
            }
            case 15 -> {
                toggleBooleanConfig("vault-keep-on-death", player);
                saveAndReloadAll();
                openMainPage(player);
            }
            case 18 -> {
                toggleBooleanConfig("streak-bonus", player);
                saveAndReloadAll();
                openMainPage(player);
            }
            case 19 -> {
                double current = plugin.getConfig().getDouble("streak-bonus-level", 0.5D);
                double next = cycleDoublePreset(current, STREAK_BONUS_PRESETS);
                plugin.getConfig().set("streak-bonus-level", next);
                saveAndReloadAll();
                player.sendMessage(Component.text()
                        .append(Component.text("Streak-Level pro Tag auf ", NamedTextColor.GREEN))
                        .append(Component.text(String.valueOf(next), NamedTextColor.YELLOW))
                        .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                        .build());
                openMainPage(player);
            }
            case 20 -> openBorderPage(player);
            case 24 -> openWorldRegenPage(player);
            default -> {}
        }
    }

    private void handleBorderClick(Player player, ConfigMenuHolder holder, int slot) {
        switch (slot) {
            case 0 -> openMainPage(player);
            case 8 -> player.closeInventory();
            case 10 -> {
                int current = plugin.getConfig().getInt("border-options.check-interval", 20);
                int next = cycleIntPreset(current, CHECK_INTERVAL_PRESETS);
                plugin.getConfig().set("border-options.check-interval", next);
                saveAndReloadBorder();
                player.sendMessage(Component.text()
                        .append(Component.text("Check-Intervall auf ", NamedTextColor.GREEN))
                        .append(Component.text(next + " Ticks", NamedTextColor.YELLOW))
                        .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                        .build());
                openBorderPage(player);
            }
            case 11 -> {
                toggleBooleanConfig("border-options.use-action-bar", player);
                saveAndReloadBorder();
                openBorderPage(player);
            }
            case 12 -> {
                toggleBooleanConfig("border-options.prevent-mob-spawns", player);
                saveAndReloadBorder();
                openBorderPage(player);
            }
            case 13 -> {
                toggleBooleanConfig("border-options.prevent-enderpearl", player);
                saveAndReloadBorder();
                openBorderPage(player);
            }
            case 14 -> {
                toggleBooleanConfig("border-options.prevent-chorus-fruit", player);
                saveAndReloadBorder();
                openBorderPage(player);
            }
            case 15 -> {
                toggleBooleanConfig("border-options.visualizer-enabled", player);
                saveAndReloadBorder();
                player.sendMessage(Component.text(
                        "Hinweis: Ein Server-Neustart ist erforderlich, damit die Visualizer-Aenderung wirksam wird.",
                        NamedTextColor.YELLOW));
                openBorderPage(player);
            }
            case 16 -> {
                int current = plugin.getConfig().getInt("border-options.visualizer-range", 8);
                int next = cycleIntPreset(current, VISUALIZER_RANGE_PRESETS);
                plugin.getConfig().set("border-options.visualizer-range", next);
                saveAndReloadBorder();
                player.sendMessage(Component.text()
                        .append(Component.text("Visualizer-Reichweite auf ", NamedTextColor.GREEN))
                        .append(Component.text(next + " Chunks", NamedTextColor.YELLOW))
                        .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                        .build());
                openBorderPage(player);
            }
            default -> {}
        }
    }

    private void handleWorldRegenClick(Player player, ConfigMenuHolder holder, int slot) {
        switch (slot) {
            case 0 -> openMainPage(player);
            case 8 -> player.closeInventory();
            case 10 -> {
                toggleBooleanConfig("world-regeneration.enabled", player);
                saveAndReloadAll();
                openWorldRegenPage(player);
            }
            case 12 -> {
                int current = plugin.getConfig().getInt("world-regeneration.chunk-radius", 16);
                int next = cycleIntPreset(current, CHUNK_RADIUS_PRESETS);
                plugin.getConfig().set("world-regeneration.chunk-radius", next);
                saveAndReloadAll();
                player.sendMessage(Component.text()
                        .append(Component.text("Chunk-Radius auf ", NamedTextColor.GREEN))
                        .append(Component.text(next + " Chunks", NamedTextColor.YELLOW))
                        .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                        .build());
                openWorldRegenPage(player);
            }
            case 13 -> {
                int current = plugin.getConfig().getInt("world-regeneration.chunky-quiet-ms", 500);
                int next = cycleIntPreset(current, CHUNKY_QUIET_PRESETS);
                plugin.getConfig().set("world-regeneration.chunky-quiet-ms", next);
                saveAndReloadAll();
                player.sendMessage(Component.text()
                        .append(Component.text("Chunky-Pause auf ", NamedTextColor.GREEN))
                        .append(Component.text(next + " ms", NamedTextColor.YELLOW))
                        .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                        .build());
                openWorldRegenPage(player);
            }
            case 14 -> {
                int current = plugin.getConfig().getInt("world-regeneration.default-border-size", 1000);
                int next = cycleIntPreset(current, BORDER_SIZE_PRESETS);
                plugin.getConfig().set("world-regeneration.default-border-size", next);
                saveAndReloadAll();
                player.sendMessage(Component.text()
                        .append(Component.text("Standard-Border-Groesse auf ", NamedTextColor.GREEN))
                        .append(Component.text(next + " Bloecke", NamedTextColor.YELLOW))
                        .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                        .build());
                openWorldRegenPage(player);
            }
            default -> {}
        }
    }

    private void toggleBooleanConfig(String path, Player player) {
        boolean current = plugin.getConfig().getBoolean(path, false);
        boolean newValue = !current;
        plugin.getConfig().set(path, newValue);
        plugin.saveConfig();
        player.sendMessage(Component.text()
                .append(Component.text(path, NamedTextColor.GRAY))
                .append(Component.text(" → ", NamedTextColor.GRAY))
                .append(Component.text(newValue ? "true" : "false",
                        newValue ? NamedTextColor.GREEN : NamedTextColor.RED))
                .build());
    }

    private int cycleIntPreset(int current, int[] presets) {
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == current) {
                return presets[(i + 1) % presets.length];
            }
        }
        return presets[0];
    }

    private Double cycleMaxHealthPreset(Double current) {
        double[] presets = MAX_HEALTH_PRESETS;
        if (current == null) {
            return presets[0];
        }
        for (int i = 0; i < presets.length; i++) {
            if (Math.abs(presets[i] - current) < 0.001D) {
                if (i + 1 >= presets.length) {
                    return null;
                }
                return presets[i + 1];
            }
        }
        return presets[0];
    }

    private double cycleDoublePreset(double current, double[] presets) {
        for (int i = 0; i < presets.length; i++) {
            if (Math.abs(presets[i] - current) < 0.0001D) {
                return presets[(i + 1) % presets.length];
            }
        }
        return presets[0];
    }

    private void saveAndReloadBorder() {
        plugin.saveConfig();
        borderManager.reloadConfig();
    }

    private void saveAndReloadAll() {
        plugin.saveConfig();
        timeManager.reload();
        borderManager.reloadConfig();
    }

    private Inventory createInventory(ConfigMenuHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        return inventory;
    }

    private ItemStack namedItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(buildLore(loreLines));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack booleanToggleItem(String configPath, String name, boolean enabled,
                                        String... loreLines) {
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        List<String> allLines = new ArrayList<>();
        allLines.add(enabled ? "Aktuell: Aktiviert" : "Aktuell: Deaktiviert");
        for (String line : loreLines) {
            if (!line.startsWith("Aktuell:")) {
                allLines.add(line);
            }
        }
        meta.lore(buildLore(allLines.toArray(new String[0])));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack intCycleItem(String configPath, String name, int[] presets, int current,
                                   Material material, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        List<String> allLines = new ArrayList<>();
        allLines.add("Aktuell: " + current);
        for (String line : loreLines) {
            if (!line.startsWith("Aktuell:")) {
                allLines.add(line);
            }
        }
        meta.lore(buildLore(allLines.toArray(new String[0])));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack maxHealthCycleItem(String configPath, String name, Double current,
                                         Material material, String... loreLines) {
        String display = current == null ? "aus" : String.valueOf(current);
        ItemStack item = new ItemStack(current == null ? Material.GRAY_DYE :
                (current >= 20.0D ? Material.GOLDEN_APPLE : Material.APPLE));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, current == null ?
                NamedTextColor.RED : NamedTextColor.GREEN));
        List<String> allLines = new ArrayList<>();
        allLines.add("Aktuell: " + display);
        for (String line : loreLines) {
            if (!line.startsWith("Aktuell:")) {
                allLines.add(line);
            }
        }
        meta.lore(buildLore(allLines.toArray(new String[0])));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack doubleCycleItem(String configPath, String name, double[] presets, double current,
                                      Material material, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        List<String> allLines = new ArrayList<>();
        allLines.add("Aktuell: " + current);
        for (String line : loreLines) {
            if (!line.startsWith("Aktuell:")) {
                allLines.add(line);
            }
        }
        meta.lore(buildLore(allLines.toArray(new String[0])));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack displayItem(String configPath, String name, String value,
                                  Material material, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        List<String> allLines = new ArrayList<>();
        allLines.add("Aktuell: " + value);
        for (String line : loreLines) {
            if (!line.startsWith("Aktuell:")) {
                allLines.add(line);
            }
        }
        meta.lore(buildLore(allLines.toArray(new String[0])));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack placeholderItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.lore(buildLore(loreLines));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(String... lines) {
        List<Component> lore = new ArrayList<>(lines.length);
        for (String line : lines) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        return lore;
    }
}