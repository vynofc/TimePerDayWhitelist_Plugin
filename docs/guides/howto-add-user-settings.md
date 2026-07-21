# Wie ich die User-Settings per `/tpd settings` hinzugefügt habe

Eine Schritt-für-Schritt-Erklärung, wie ein neues Feature (Command + GUI) in dieses Plugin eingebaut wurde.

---

## 1. Analyse: Was existiert schon?

Bevor ich Code schreibe, lese ich, was schon da ist, um den Stil und die Architektur zu verstehen.

**Bestehendes GUI-System (Admin-Menü):**

| Datei | Zweck |
|---|---|
| `gui/MenuType.java` | Enum für die verschiedenen Menü-Typen (MAIN, PLAYER_LIST, …) |
| `gui/AdminMenuHolder.java` | Implementiert `InventoryHolder` – speichert Metadaten zum Inventar (Typ, Ziel-UUID, Seite) |
| `gui/AdminMenuService.java` | Baut die Inventare (`openMainMenu`, `openPlayerSelection`, …) und behandelt Klicks (`handleClick`) |
| `gui/AdminMenuListener.java` | Listener, der `InventoryClickEvent`/`InventoryDragEvent` abfängt und an den Service delegiert |

**Bestehendes Command-System:**

| Datei | Zweck |
|---|---|
| `command/TimeCommand.java` | `/tpd time` – zeigt Spielzeit-Info im Chat |
| `command/AdminTimeCommand.java` | `/tpdadmin …` – Admin-Befehle + GUI-Öffnung |
| `command/DebugTimeCommand.java` | `/tpddebug …` – Debug-Events |

**Verdrahtung (Wiring):**

Die Hauptklasse `TimePerDayPlugin.java` erstellt in `onEnable()` alle Services und registriert Commands + Listener.

**Erkenntnis:** Das Admin-GUI ist das Vorbild. Ich brauche ein ähnliches, aber einfacheres GUI-System für User-Settings.

---

## 2. Plan: Was muss neu erstellt/geändert werden?

```
NEU:  gui/UserSettingsHolder.java        → InventoryHolder (wie AdminMenuHolder)
NEU:  gui/UserSettingsMenuService.java    → GUI bauen + Klicks behandeln
NEU:  gui/UserSettingsMenuListener.java   → Events abfangen
EDIT: command/TimeCommand.java            → Neuen Subcommand "settings" hinzufügen
EDIT: TimePerDayPlugin.java               → Neue Services erstellen + Listener registrieren
EDIT: plugin.yml                          → Beschreibung aktualisieren
```

---

## 3. Umsetzung

### 3.1 `UserSettingsHolder.java` – Der Datenhalter

```java
public class UserSettingsHolder implements InventoryHolder {
    private final UUID ownerUuid;    // Wem gehört dieses Menü?
    private Inventory inventory;     // Das Bukkit-Inventory-Objekt

    public UserSettingsHolder(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }
    // … getter/setter
}
```

**Warum?** `InventoryHolder` ist Bukkits Mechanismus, um ein Inventar mit einem Java-Objekt zu verknüpfen. Wenn später ein Klick-Event kommt, kann ich prüfen: `event.getInventory().getHolder() instanceof UserSettingsHolder` – so weiß ich, dass es *mein* GUI ist.

Im Admin-Pendant (`AdminMenuHolder`) gibt es zusätzlich `MenuType` und `page`. Für User-Settings brauche ich das nicht – es gibt nur ein einziges Menü.

---

### 3.2 `UserSettingsMenuService.java` – Das GUI bauen

```java
public void openSettings(Player player) {
    // 1. Holder erstellen
    UserSettingsHolder holder = new UserSettingsHolder(player.getUniqueId());

    // 2. 27-Slot-Inventar mit Titel "Einstellungen"
    Inventory inventory = Bukkit.createInventory(holder, 27, "Einstellungen");
    holder.setInventory(inventory);

    // 3. Items platzieren
    inventory.setItem(11, placeholderItem(Material.NOTE_BLOCK, "Einstellung 1", …));
    inventory.setItem(13, placeholderItem(Material.BELL,      "Einstellung 2", …));
    inventory.setItem(15, placeholderItem(Material.JUKEBOX,   "Einstellung 3", …));
    inventory.setItem(22, namedItem(Material.BARRIER, "Schliessen", …));

    // 4. Spieler öffnet das Inventar
    player.openInventory(inventory);
}
```

**Wichtige Konzepte:**

- **`Bukkit.createInventory(holder, größe, titel)`** – Erstellt ein Chest-Inventar. Größe muss ein Vielfaches von 9 sein (9, 18, 27, 54).
- **Slots** sind 0-basiert: Slot 11 ist Mitte-links, 13 Mitte, 15 Mitte-rechts, 22 unten-Mitte.
- **`ItemStack` + `ItemMeta`** – Jedes Item hat einen Typ (`Material`) und Metadaten (Name, Lore-Zeilen). `displayName()` setzt den sichtbaren Namen, `lore()` die Beschriftungszeilen darunter.
- **`player.openInventory(inventory)`** – Zeigt dem Spieler das Inventar clientseitig an.

**Hilfsmethoden** (`namedItem`, `placeholderItem`, `buildLore`) sind aus `AdminMenuService` kopiert und leicht angepasst – DRY-Prinzip (Don't Repeat Yourself) wird hier bewusst gebrochen, weil die beiden Services unabhängig bleiben sollen.

---

### 3.3 `UserSettingsMenuListener.java` – Events abfangen

```java
public class UserSettingsMenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 1. Nur von Spielern
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 2. Nur für UNSERE Inventare
        if (!(event.getInventory().getHolder() instanceof UserSettingsHolder holder)) return;

        // 3. Klick abbrechen (kein Item-Movement!)
        event.setCancelled(true);

        // 4. Nur innerhalb des Inventars (nicht im Player-Inventory darunter)
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;

        // 5. An den Service delegieren
        userSettingsMenuService.handleClick(player, holder, event.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Drag-and-Drop komplett blockieren
        if (event.getInventory().getHolder() instanceof UserSettingsHolder) {
            event.setCancelled(true);
        }
    }
}
```

**Warum zwei Events?**

- `InventoryClickEvent` – Einzelner Klick auf ein Item.
- `InventoryDragEvent` – Ziehen über mehrere Slots. Wird **komplett** geblockt, weil Spieler keine Items im GUI verschieben sollen.

**`event.setCancelled(true)`** – Verhindert, dass der Server die Standard-Aktion ausführt (Item aufnehmen, verschieben, etc.).

**`getRawSlot()` vs `getSlot()`** – `getRawSlot()` ist der Slot im gesamten Fenster (inkl. Spieler-Inventar). `getSlot()` ist nur innerhalb des oberen Inventars. Wir wollen nur Klicks *im GUI* behandeln, nicht im Spieler-Inventar darunter.

---

### 3.4 `TimeCommand.java` – Command erweitern

**Vorher:**
```java
if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("time"))) {
    // Fehler: nur /tpd time ist erlaubt
}
// … zeige Zeitinfo
```

**Nachher:**
```java
// Kein Argument → zeige Zeitinfo (neues Default-Verhalten)
if (args.length == 0) {
    showTimeInfo(player);
    return true;
}

// /tpd settings → öffne GUI
if (args.length == 1 && args[0].equalsIgnoreCase("settings")) {
    userSettingsMenuService.openSettings(player);
    return true;
}

// /tpd time → zeige Zeitinfo
if (args.length == 1 && args[0].equalsIgnoreCase("time")) {
    showTimeInfo(player);
    return true;
}

// Alles andere → Fehler
player.sendMessage(Component.text("Verwendung: /tpd [time|settings]", NamedTextColor.RED));
```

**Wichtige Änderung:** Die Zeitinfo-Logik wurde in eine separate Methode `showTimeInfo(Player)` ausgelagert, weil sie jetzt von zwei Pfaden aufgerufen wird (`/tpd` ohne Args und `/tpd time`).

**Konstruktor** wurde erweitert, um `UserSettingsMenuService` entgegenzunehmen – das ist **Dependency Injection**: Die Abhängigkeit wird von außen hereingereicht, statt selbst erstellt. Das macht den Code testbarer und flexibler.

---

### 3.5 `TimePerDayPlugin.java` – Alles zusammenstecken

```java
// Neues Feld
private UserSettingsMenuService userSettingsMenuService;

// In onEnable():
userSettingsMenuService = new UserSettingsMenuService(timeManager);

// Listener registrieren
getServer().getPluginManager().registerEvents(
    new UserSettingsMenuListener(userSettingsMenuService), this);

// TimeCommand bekommt den neuen Service
TimeCommand timeCmd = new TimeCommand(timeManager, userSettingsMenuService);
```

**Pattern:** `TimePerDayPlugin` ist der **Composition Root** – der Ort, wo alle Objekte erstellt und miteinander verdrahtet werden. Neue Features werden hier "eingesteckt" (registriert).

---

### 3.6 `plugin.yml` – Metadaten aktualisieren

```yaml
tpd:
    description: Zeigt die verbleibende Spielzeit und Einstellungen an
    usage: /tpd [time|settings]
    permission: timeperday.use
```

Das `usage`-Feld ist nur Dokumentation für `/help` – es beeinflusst nicht das tatsächliche Verhalten.

---

## 4. Zusammenfassung: Das allgemeine Muster

Jedes neue GUI-Feature in diesem Plugin folgt diesem Schema:

```
1. Holder-Klasse       → speichert Zustand (welcher Spieler, welcher Menü-Typ)
2. Service-Klasse      → baut Inventar, behandelt Klick-Logik
3. Listener-Klasse     → fängt Bukkit-Events ab, delegiert an Service
4. Command erweitern   → neuer Subcommand ruft Service auf
5. Plugin#onEnable()   → Service erstellen, Listener registrieren, Command verdrahten
6. plugin.yml          → Beschreibung/Usage aktualisieren
```

Die Platzhalter-Items (Note Block, Bell, Jukebox) sind bewusst ohne Funktion – sie zeigen nur "Diese Einstellung ist noch nicht verfügbar" an. Später werden echte Einstellungen (z.B. Toggle-Buttons, Slider) diese Slots ersetzen. Dafür muss dann nur `UserSettingsMenuService` geändert werden – die Architektur (Holder, Listener, Command) bleibt gleich.