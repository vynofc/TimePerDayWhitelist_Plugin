# How-To: Neuen Config-Eintrag hinzufügen

Eine detaillierte Schritt-für-Schritt-Anleitung, um einen neuen Konfigurationseintrag selbstständig in das Plugin einzubauen – inklusive `/tpdconfig`-GUI-Integration.

---

## Übersicht: Welche Dateien werden geändert?

Je nach Typ des Config-Eintrags (Boolean, Zahl, String, read-only) und ob er ein GUI-Item bekommen soll, variiert die Anzahl der zu ändernden Dateien:

| Änderung | Wann nötig? |
|---|---|
| `config.yml` | **immer** |
| `PlayerTimeManager.java` | **immer** |
| `ConfigMenuService.java` | nur wenn GUI-Item gewünscht |
| `PlayerTickManager.java` | nur wenn Logik jede Sekunde laufen soll |
| `PlayerListener.java` | nur wenn Logik beim Join laufen soll |
| `PlayerResetManager.java` | nur wenn Logik beim Day-Over-Reset laufen soll |

---

## Schritt 1: `config.yml` – Neuen Key definieren

**Wo:** `src/main/resources/config.yml`

Füge den neuen Key mit einem aussagekräftigen Kommentar ein. Der Default-Wert wird verwendet, wenn der Key in der Datei noch nicht existiert.

**Beispiel – Boolean:**
```yaml
# Ob Spieler beim Join automatisch das tägliche Kit erhalten
auto-kit: true
```

**Beispiel – Zahl (double):**
```yaml
# Maximales Spielerleben pro Tag. Eine Zahl (z. B. 20.0 = 10 Herzen)
# oder "aus" zum Deaktivieren.
max-health: "aus"
```

**Beispiel – String:**
```yaml
# Begrüßungsnachricht beim Server-Join (MiniMessage-Format)
welcome-message: "<green>Willkommen auf dem Server!"
```

> **Wichtig:** Der Key muss in `camelCase` oder `kebab-case` sein. Keine Punkte im Key selbst (Punkte trennen Sektionen in YAML).

---

## Schritt 2: `PlayerTimeManager.java` – Feld + Getter + Config-Lader

**Wo:** `src/main/java/fun/vynofc/timeperday/manager/PlayerTimeManager.java`

### 2.1 Feld deklarieren

Füge ein `volatile`-Feld in der Feld-Sektion hinzu (ca. Zeile 47–51):

```java
volatile boolean autoKit = true;
```

**Warum `volatile`?** Das Feld wird vom Tick-Loop (anderer Thread bei Folia) gelesen und von `reload()`/`load()` geschrieben. `volatile` garantiert Sichtbarkeit der Änderung über Threads hinweg ohne `synchronized`.

### 2.2 Config-Lader-Methode schreiben

Füge eine neue Methode hinzu (in der Nähe von `reloadDefaultLimitFromConfig()`):

```java
void reloadAutoKitFromConfig() {
    this.autoKit = plugin.getConfig().getBoolean("auto-kit", true);
}
```

**Verfügbare Config-Methoden:**

| Config-Typ | Methode | Beispiel |
|---|---|---|
| `boolean` | `getBoolean(path, default)` | `getBoolean("auto-kit", true)` |
| `int` | `getInt(path, default)` | `getInt("max-players", 20)` |
| `long` | `getLong(path, default)` | `getLong("timeout", 300L)` |
| `double` | `getDouble(path, default)` | `getDouble("speed", 1.0D)` |
| `String` | `getString(path, default)` | `getString("prefix", "world_")` |
| `List<Integer>` | `getIntegerList(path)` | `getIntegerList("warnings")` |

### 2.3 Getter schreiben

```java
public boolean isAutoKitEnabled() {
    return autoKit;
}
```

### 2.4 Lader in `load()` und `reload()` aufrufen

In beiden Methoden muss `reloadAutoKitFromConfig()` aufgerufen werden, damit der Wert beim Serverstart UND bei `/tpdadmin reload` aktualisiert wird:

```java
// In load():
public synchronized void load() {
    this.resetZoneId = readResetZoneIdFromConfig();
    persistenceManager.load();
    tickManager.reloadWarningThresholds();
    worldRegenerationManager.load();
    reloadMaxHealthFromConfig();
    reloadAutoKitFromConfig();  // ← NEU
    dirty = false;
}

// In reload():
public void reload() {
    this.resetZoneId = readResetZoneIdFromConfig();
    persistenceManager.reload();
    tickManager.reloadWarningThresholds();
    worldRegenerationManager.reload();
    reloadMaxHealthFromConfig();
    reloadAutoKitFromConfig();  // ← NEU
}
```

---

## Schritt 3: `ConfigMenuService.java` – GUI-Item hinzufügen (optional)

**Wo:** `src/main/java/fun/vynofc/timeperday/gui/config/ConfigMenuService.java`

Nur nötig, wenn der Config-Wert über `/tpdconfig` änderbar sein soll. Es gibt drei Arten von GUI-Items:

### 3.1 Boolean-Toggle (an/aus)

**Item ins GUI einfügen** – in `openMainPage()` (oder `openBorderPage()` / `openWorldRegenPage()`):

```java
boolean autoKit = plugin.getConfig().getBoolean("auto-kit", true);
inventory.setItem(15, booleanToggleItem("auto-kit", "Auto-Kit",
        autoKit,
        "Vergibt das taegliche Kit automatisch beim Join.",
        "Klicke zum Umschalten."));
```

**Click-Handler** – in `handleMainClick()` (oder der entsprechenden Seite):

```java
case 15 -> {
    toggleBooleanConfig("auto-kit", player);
    saveAndReloadAll();
    openMainPage(player);
}
```

> **`saveAndReloadAll()`** speichert die Config und ruft `timeManager.reload()` + `borderManager.reloadConfig()` auf. Verwende `saveAndReloadBorder()` wenn nur Border-Einstellungen betroffen sind.

### 3.2 Integer/Double mit Presets (durchklickbar)

**Presets definieren** – als `static final`-Array oben in der Klasse:

```java
private static final int[] MAX_PLAYERS_PRESETS = {10, 20, 50, 100};
```

**Item ins GUI einfügen:**

```java
int maxPlayers = plugin.getConfig().getInt("max-players", 20);
inventory.setItem(17, intCycleItem("max-players", "Max. Spieler",
        MAX_PLAYERS_PRESETS, maxPlayers, Material.PLAYER_HEAD,
        "Maximale Anzahl gleichzeitiger Spieler.",
        "Aktuell: " + maxPlayers,
        "Klicke zum Durchschalten."));
```

**Click-Handler:**

```java
case 17 -> {
    int current = plugin.getConfig().getInt("max-players", 20);
    int next = cycleIntPreset(current, MAX_PLAYERS_PRESETS);
    plugin.getConfig().set("max-players", next);
    saveAndReloadAll();
    player.sendMessage(Component.text()
            .append(Component.text("Max. Spieler auf ", NamedTextColor.GREEN))
            .append(Component.text(String.valueOf(next), NamedTextColor.YELLOW))
            .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
            .build());
    openMainPage(player);
}
```

### 3.3 Read-Only (nur Anzeige, nicht änderbar)

```java
String value = plugin.getConfig().getString("server-name", "Mein Server");
inventory.setItem(18, displayItem("server-name", "Servername",
        value, Material.NAME_TAG,
        "Anzeigename des Servers.",
        "Nur in config.yml aenderbar."));
```

Kein Click-Handler nötig – das Item hat keinen `case`-Eintrag.

### 3.4 Verfügbare GUI-Hilfsmethoden

| Methode | Zweck | Item-Farbe |
|---|---|---|
| `booleanToggleItem(key, name, enabled, lore...)` | Boolean an/aus | LIME_DYE (grün) / GRAY_DYE (grau) |
| `intCycleItem(key, name, presets, current, material, lore...)` | Integer durchschalten | Material-Parameter |
| `displayItem(key, name, value, material, lore...)` | Read-Only-Anzeige | Gelb (YELLOW) |
| `namedItem(material, name, lore...)` | Navigation (z.B. "Zurück") | Gold (GOLD) |
| `placeholderItem(material, name, lore...)` | Platzhalter | Gelb (YELLOW) |

---

## Schritt 4: Logik einbauen (wo nötig)

### 4.1 Im Tick-Loop (`PlayerTickManager.java`)

Wenn die Logik **jede Sekunde** für jeden Online-Spieler ausgeführt werden soll:

```java
// In tickPlayer() – vor oder nach der bestehenden Logik:
if (manager.isAutoKitEnabled()) {
    // Deine Logik hier
}
```

> **Performance-Hinweis:** `tickPlayer()` läuft 1× pro Sekunde pro Spieler. Halte die Logik leicht – keine Datei-I/O, keine schweren Berechnungen.

### 4.2 Beim Join (`PlayerListener.java`)

Wenn die Logik **einmal beim Betreten** des Servers ausgeführt werden soll:

```java
// In onPlayerJoin() – nach den bestehenden Checks:
if (timeManager.isAutoKitEnabled()) {
    // Deine Logik hier
}
```

### 4.3 Beim Day-Over-Reset (`PlayerResetManager.java`)

Wenn die Logik **beim täglichen Reset** ausgeführt werden soll:

```java
// In resetOnlinePlayerState() oder wipeOnlinePlayerState():
if (manager.isAutoKitEnabled()) {
    // Deine Logik hier
}
```

---

## Schritt 5: Bauen und Testen

```bash
mvn clean package
```

Bei **BUILD SUCCESS** ist der neue Config-Eintrag fertig.

---

## Zusammenfassung: Minimal-Änderungen pro Typ

### Boolean-Toggle mit GUI

```
1. config.yml              → Key + Default + Kommentar
2. PlayerTimeManager.java  → volatile Feld, reload*(), Getter, load()/reload()-Aufruf
3. ConfigMenuService.java  → booleanToggleItem() + case im Click-Handler
```

### Zahl mit Presets + GUI

```
1. config.yml              → Key + Default + Kommentar
2. PlayerTimeManager.java  → volatile Feld, reload*(), Getter, load()/reload()-Aufruf
3. ConfigMenuService.java  → static final PRESETS, intCycleItem(), case + cycleIntPreset()
```

### Nur Config (kein GUI)

```
1. config.yml              → Key + Default + Kommentar
2. PlayerTimeManager.java  → volatile Feld, reload*(), Getter, load()/reload()-Aufruf
3. (optional) Logik        → PlayerTickManager / PlayerListener / PlayerResetManager
```

---

## Checkliste

- [ ] `config.yml`: Key mit Default-Wert und deutschem Kommentar
- [ ] `PlayerTimeManager.java`: `volatile`-Feld, Getter, `reload*FromConfig()`
- [ ] `PlayerTimeManager.java`: Aufruf in `load()` UND `reload()`
- [ ] `ConfigMenuService.java`: GUI-Item + Click-Handler (falls GUI)
- [ ] `PlayerTickManager.java` / `PlayerListener.java`: Logik (falls nötig)
- [ ] `mvn clean package`: Build erfolgreich
- [ ] Manuell testen: `/tpdconfig` → GUI öffnen, Wert ändern, prüfen
- [ ] Manuell testen: `/tpdadmin reload` → Wert bleibt erhalten
- [ ] Manuell testen: Server-Neustart → Wert bleibt erhalten