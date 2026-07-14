# Fix: `/debugtime dayover` – Inventar und Position werden jetzt zurückgesetzt

Diese Datei erklärt, was geändert wurde, warum es notwendig war und wie der Code intern funktioniert.

## Das Problem (Issue #4)

Wenn man `/debugtime dayover` ausführte, hat die Console zwar gemeldet:

```
[TimePerDayWhitelist] Debug: Tag vorbei ausgelöst (Tageswerte zurückgesetzt).
Debug-Event ausgelöst: Tag vorbei (Spielzeiten/Session reset).
```

Aber beim anschließenden Reloggen hatte der Spieler noch:
- das alte Inventar
- die alte Position

Das war falsch, weil ein echter Tageswechsel bedeutet: der Spieler startet neu (leeres Inventar, Spawn-Position).

---

## Was fehlte – und warum

`debugTriggerDayOver()` hat bis jetzt nur die Datenmaps geleert:

```java
playedToday.clear();
sessionPoints.clear();
lastKitClaimDate.clear();
save();
```

Was aber gefehlt hat:

| Aktion                         | vorher | nachher |
|--------------------------------|--------|---------|
| Spielzeiten zurücksetzen       | ✅     | ✅      |
| Session-Punkte zurücksetzen    | ✅     | ✅      |
| Kit-Claim-Datum zurücksetzen   | ✅     | ✅      |
| Inventar online Spieler leeren | ❌     | ✅      |
| Position online Spieler reset  | ❌     | ✅      |
| Inventar + Position beim Login | ❌     | ✅      |

---

## Geänderte Dateien

1. `src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java`
2. `src/main/java/gg/vynofc/timeperday/listener/PlayerListener.java`

---

## Änderung 1 – `PlayerTimeManager.java`

### Neues Feld: `pendingDayOverReset`

```java
private final Set<UUID> pendingDayOverReset = ConcurrentHashMap.newKeySet();
```

**Warum?**
Wenn `/debugtime dayover` ausgeführt wird, können einige Spieler gerade offline sein.
Für diese können wir das Inventar nicht sofort leeren – sie sind nicht auf dem Server.
Deshalb speichern wir ihre UUID hier ab.
Wenn sie sich das nächste Mal einloggen, werden sie automatisch zurückgesetzt.

`ConcurrentHashMap.newKeySet()` erzeugt ein thread-sicheres Set – wichtig, weil
`debugTriggerDayOver()` aus einem Scheduler-Thread aufgerufen werden kann.

---

### Geänderte Methode: `debugTriggerDayOver()`

**Alt:**
```java
public synchronized void debugTriggerDayOver() {
    currentDate = LocalDate.now().format(DATE_FORMAT);
    playedToday.clear();
    sessionPoints.clear();
    lastKitClaimDate.clear();
    save();
}
```

**Neu (aktuell):**
```java
public synchronized void debugTriggerDayOver() {
    // Alle bekannten UUIDs vor dem Leeren sammeln (auch Offline-Spieler)
    Set<UUID> allTrackedUuids = new HashSet<>();
    allTrackedUuids.addAll(playedToday.keySet());
    allTrackedUuids.addAll(sessionPoints.keySet());
    allTrackedUuids.addAll(lastKitClaimDate.keySet());
    allTrackedUuids.addAll(totalLevel.keySet());
    allTrackedUuids.addAll(playerLimits.keySet());

    currentDate = LocalDate.now().format(DATE_FORMAT);
    playedToday.clear();
    // sessionPoints wird NICHT hier geleert – Online-Spieler werden via
    // resetOnlinePlayerState (Inventar zählen + finalisieren) behandelt;
    // Offline-Spieler weiter unten im Scheduler finalisiert.
    lastKitClaimDate.clear();
    save();

    plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
        Set<UUID> currentlyOnline = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            currentlyOnline.add(player.getUniqueId());
            if (!isWhitelisted(player.getUniqueId()) && !player.hasPermission("timeperday.bypass")) {
                // resetOnlinePlayerState zählt Inventar und finalisiert Session-Punkte
                player.getScheduler().run(plugin,
                        scheduledTask -> resetOnlinePlayerState(player), null);
            } else {
                // Whitelisted/Bypass: Session-Punkte einfach verwerfen
                sessionPoints.remove(player.getUniqueId());
            }
        }
        for (UUID uuid : allTrackedUuids) {
            if (!currentlyOnline.contains(uuid)) {
                if (!isWhitelisted(uuid)) {
                    // Offline-Spieler: Session-Punkte ohne Inventar finalisieren
                    finalizeSessionProgress(uuid);
                    pendingDayOverReset.add(uuid);
                } else {
                    sessionPoints.remove(uuid);
                }
            }
        }
    });
}
```

**Was passiert hier Schritt für Schritt:**

1. **UUIDs sammeln (vor dem Clear):** Alle Spieler-UUIDs aus allen Maps werden gesammelt,
   *bevor* die Maps geleert werden. Das ist wichtig – danach wäre die Information weg.

2. **Maps leeren + speichern:** `playedToday` und `lastKitClaimDate` werden geleert.
   `sessionPoints` wird bewusst *nicht* hier geleert – die Finalisierung erfolgt
   per Spieler im Scheduler-Block darunter.

3. **GlobalRegionScheduler:** Spieler-Aktionen (Inventar, Teleport) müssen im richtigen
   Bukkit/Folia-Thread laufen. `getGlobalRegionScheduler().run(...)` stellt das sicher.

4. **Online-Spieler sofort zurücksetzen:** Für jeden gerade eingeloggten Spieler (ohne
   Whitelist/Bypass) wird `resetOnlinePlayerState(player)` über den Spieler-eigenen
   Scheduler aufgerufen. Diese Methode zählt zuerst alle Inventar-Items (via
   `addInventoryToSessionPoints`), überträgt die Session-Punkte in den Gesamtlevel
   (`finalizeSessionProgress`) und leert dann Inventar, XP, Health, Position.

5. **Offline-Spieler finalisieren + in `pendingDayOverReset` eintragen:** Für alle
   bekannten UUIDs, die *nicht* gerade online sind, werden die Session-Punkte via
   `finalizeSessionProgress` in den Gesamtlevel übertragen (ohne Inventar, da kein
   Zugriff möglich). Danach werden sie ins Pending-Reset-Set eingetragen.
   Beim nächsten Login übernimmt `PlayerListener` den Rest.

---

### Neue Methode: `consumePendingDayOverReset(UUID uuid)`

```java
public boolean consumePendingDayOverReset(UUID uuid) {
    return pendingDayOverReset.remove(uuid);
}
```

**Warum?**
`PlayerListener` muss prüfen, ob für einen einloggenden Spieler ein Reset aussteht.
`remove()` tut beides auf einmal: es entfernt den Eintrag *und* gibt `true` zurück,
wenn er vorhanden war. So wird der Reset genau einmal angewendet.

---

### Neue Methode: `applyDayOverReset(Player player)`

```java
public void applyDayOverReset(Player player) {
    resetOnlinePlayerState(player);
}
```

**Warum?**
`resetOnlinePlayerState()` ist `private`. `PlayerListener` ist eine andere Klasse und
kann sie nicht direkt aufrufen. Diese öffentliche Methode ist ein sogenannter
*Wrapper* – sie delegiert einfach an die private Methode weiter.

Der Vorteil: Die eigentliche Reset-Logik bleibt an einem Ort (`resetOnlinePlayerState`),
und wir vermeiden Code-Duplikate.

---

## Änderung 2 – `PlayerListener.java`

### Geänderte Methode: `onPlayerJoin`

**Neu hinzugefügter Block:**
```java
if (timeManager.consumePendingDayOverReset(uuid)) {
    player.getScheduler().runDelayed(plugin, task -> {
        timeManager.applyDayOverReset(player);
        boolean kitGiven = timeManager.grantDailyKit(player);
        if (plugin.getConfig().getBoolean("show-on-join", true)) {
            player.sendMessage(timeManager.buildJoinInfoComponent(player, kitGiven));
        }
    }, null, 1L);
    return;
}
```

**Was passiert hier:**

1. `consumePendingDayOverReset(uuid)` prüft, ob für diesen Spieler ein Day-Over-Reset
   aussteht. Wenn ja, wird er aus dem Set entfernt (einmalig).

2. `runDelayed(..., 1L)` wartet genau 1 Tick. Das ist nötig, weil der `PlayerJoinEvent`
   erst vollständig abgeschlossen sein muss, bevor wir Inventar und Position ändern –
   genau wie beim Kick-Code darunter.

3. Innerhalb des Delays passiert in der richtigen Reihenfolge:
   - **Reset:** Inventar leeren, zu Spawn teleportieren, XP/Health zurücksetzen
   - **Kit vergeben:** Tages-Kit geben (jetzt wieder möglich, da `lastKitClaimDate` geleert wurde)
   - **Join-Nachricht:** Die normale Willkommensnachricht mit Profil-Info anzeigen

4. `return` nach dem If-Block: Wir überspringen den normalen Join-Flow, der Kit
   und Nachricht separat behandeln würde. Sonst würde beides zweimal ausgeführt.

---

## Der Ablauf im Überblick (nach dem Fix)

### Szenario A – Spieler ist online wenn `/debugtime dayover` ausgeführt wird:

```
/debugtime dayover
  → Maps geleert, gespeichert
  → GlobalScheduler: resetOnlinePlayerState(player)
     → Inventar geleert
     → Zu Spawn teleportiert
     → XP/Health/Food zurückgesetzt
```

### Szenario B – Spieler war offline und loggt sich danach ein:

```
/debugtime dayover
  → UUID in pendingDayOverReset eingetragen

Spieler loggt sich ein:
  → onPlayerJoin: consumePendingDayOverReset(uuid) → true
  → 1 Tick Delay
  → applyDayOverReset(player):
     → Inventar geleert
     → Zu Spawn teleportiert
  → grantDailyKit(player) → Kit vergeben
  → buildJoinInfoComponent → Willkommensnachricht
```

---

## Was du daraus lernen kannst

### 1. Zustand erst sammeln, dann löschen
Wenn du Maps leerst, verlierst du die Daten. Wenn du danach noch mit diesen Daten
arbeiten willst (z.B. offline Spieler identifizieren), musst du sie vorher kopieren:
```java
Set<UUID> allTrackedUuids = new HashSet<>(someMap.keySet());
someMap.clear(); // jetzt erst leeren
```

### 2. Deferred State (ausstehende Aktionen)
Manchmal kann man eine Aktion nicht sofort ausführen, weil der Zielzustand noch nicht
bereit ist (Spieler offline). Das Muster ist: eine Set/Map als „To-Do-Liste" führen
und die Aktion nachholen, sobald die Voraussetzungen erfüllt sind.

### 3. Thread-sichere Collections für geteilten Zustand
`ConcurrentHashMap.newKeySet()` statt `new HashSet<>()`, weil das Set von mehreren
Threads gleichzeitig gelesen und beschrieben werden kann (Scheduler-Thread beim Einfügen,
Main-Thread beim Login-Event).

### 4. Private Methoden über public Wrapper zugänglich machen
Wenn eine Methode intern korrekt ist, aber externe Klassen sie aufrufen sollen, erstelle
einen schlanken `public` Wrapper statt die Methode direkt zu veröffentlichen. So kannst
du die Implementierung später ändern ohne die API zu brechen.

### 5. Reihenfolge bei Join-Events
Beim `PlayerJoinEvent` ist der Spieler noch nicht vollständig initialisiert. Deshalb
werden Inventar-Änderungen und Teleports erst nach 1 Tick (`runDelayed(..., 1L)`)
ausgeführt. Das ist ein bewährtes Muster in diesem Projekt.
