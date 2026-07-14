# Anleitung: Wie `/debugtime` implementiert wurde

Diese Anleitung erklaert Schritt fuer Schritt, wie der neue Debug-Befehl eingebaut wurde, damit du das Muster spaeter selbst erweitern kannst.

## Ziel

Mit `/debugtime` soll man Zeit-Events manuell ausloesen koennen, ohne auf echte Mitternacht oder echtes Limit-Ende zu warten.

Aktuell unterstuetzte Subcommands:

1. `dayover` -> simuliert Tageswechsel
2. `warn <Spieler> <Sekunden>` -> sendet manuelle Warnung
3. `timeout <Spieler>` -> simuliert Zeitablauf + Kick

## Welche Dateien geaendert wurden

1. [src/main/java/gg/vynofc/timeperday/command/DebugTimeCommand.java](../src/main/java/gg/vynofc/timeperday/command/DebugTimeCommand.java)
2. [src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java](../src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java)
3. [src/main/java/gg/vynofc/timeperday/TimePerDayPlugin.java](../src/main/java/gg/vynofc/timeperday/TimePerDayPlugin.java)
4. [src/main/resources/plugin.yml](../src/main/resources/plugin.yml)
5. [README.md](../README.md)

## Schritt 1: Eigene Command-Klasse erstellt

In [src/main/java/gg/vynofc/timeperday/command/DebugTimeCommand.java](../src/main/java/gg/vynofc/timeperday/command/DebugTimeCommand.java) wurde eine neue Klasse mit `CommandExecutor` und `TabCompleter` angelegt.

Warum so?

1. Die bestehende Struktur nutzt pro Befehl eine eigene Klasse.
2. Tab-Completion ist wichtig fuer schnelles Testen ingame.
3. Die Subcommands bleiben sauber getrennt (`handleDayOver`, `handleWarn`, `handleTimeout`).

Wichtige Designpunkte:

1. Eigene Permission: `timeperday.debug`
2. Frueher Return bei falscher Verwendung
3. Klare Fehlermeldungen fuer Offline-Spieler oder ungueltige Zahlen

## Schritt 2: Debug-API im Manager ergaenzt

In [src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java](../src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java) wurden Methoden hinzugefuegt, damit die Command-Klasse keine interne Logik dupliziert.

Neue Methoden:

1. `debugTriggerDayOver()`
2. `debugTriggerWarning(Player player, long remainingSeconds)`
3. `debugTriggerTimeout(Player player)`
4. `getPlugin()`

### Warum diese Trennung gut ist

1. Commands bleiben duenn (Input + Routing).
2. Die eigentliche Fachlogik lebt im Manager.
3. Bestehende Funktionen wie `sendWarning(...)`, `finalizeSessionProgress(...)`, `kickPlayer(...)` werden wiederverwendet.

### Detail: `dayover`

`debugTriggerDayOver()` macht bewusst:

1. `playedToday.clear()`
2. `sessionPoints.clear()`
3. `lastKitClaimDate.clear()`
4. `save()`

Damit entspricht das Ergebnis dem erwarteten "neuen Tag" fuer Tests.

### Detail: `timeout`

`debugTriggerTimeout(Player player)` macht:

1. Check auf Whitelist/Bypass
2. Setzt `playedToday` auf das Limit
3. Finalisiert Session-Fortschritt
4. Kickt den Spieler mit normaler Kick-Component

Damit testest du exakt den echten Ablaufpfad statt einer Fake-Nachricht.

## Schritt 3: Registrierung in der Hauptklasse

In [src/main/java/gg/vynofc/timeperday/TimePerDayPlugin.java](../src/main/java/gg/vynofc/timeperday/TimePerDayPlugin.java) wurde der Befehl wie die anderen Commands registriert:

1. Instanz erstellen
2. `getCommand("debugtime")` holen
3. Executor und TabCompleter setzen

Warum wichtig?

Ohne Registrierung wird der Befehl in Bukkit/Paper nicht aktiv.

## Schritt 4: Command und Permission in `plugin.yml`

In [src/main/resources/plugin.yml](../src/main/resources/plugin.yml) wurde ergaenzt:

1. Command-Block `debugtime`
2. Permission `timeperday.debug` (default `op`)

Warum wichtig?

1. Der Command muss in `plugin.yml` bekannt sein.
2. Permissions sollen getrennt von `timeperday.admin` steuerbar bleiben.

## Schritt 5: Dokumentation aktualisiert

In [README.md](../README.md) wurde der Befehl in der Command-Tabelle ergaenzt.

Warum wichtig?

1. Server-Team sieht sofort, dass ein Test-Befehl existiert.
2. Verhindert "versteckte" Features ohne Doku.

## Folia/Paper Aspekt (wichtig zum Lernen)

Bei Folia darf man Spieler-bezogene Aktionen nicht beliebig von global aus ausfuehren.

Darum wird fuer `warn` und `timeout` der Spieler-Scheduler genutzt:

1. `target.getScheduler().run(...)`
2. Erst darin wird die Spielerlogik ausgefuehrt

Das ist konsistent mit dem bestehenden Tick-Design in deinem Projekt.

## So testest du `/debugtime` sauber

1. Server starten mit neuer JAR
2. Als OP einloggen
3. `/debugtime dayover`
4. `/debugtime warn <deinName> 30`
5. `/debugtime timeout <deinName>`

Erwartung:

1. `dayover` setzt Tageswerte zurueck
2. `warn` zeigt die normale Warning-Nachricht
3. `timeout` finalisiert Session-Punkte und kickt

## Typische Fehler, die vermieden wurden

1. Keine Logik-Duplikate in Command-Klasse
2. Keine unsichere Thread-Nutzung bei Spielerzugriff
3. Keine stillen Rechteeskalationen (eigene Debug-Permission)
4. Keine undokumentierten Befehle

## Wie du es erweitern kannst

Wenn du neue Debug-Events willst, nutze dieses Muster:

1. Neues Subcommand in `DebugTimeCommand`
2. Neue Manager-Methode in `PlayerTimeManager`
3. Optional Tab-Completion erweitern
4. Doku aktualisieren

Beispiel-Ideen:

1. `/debugtime advance <Spieler> <Sekunden>`
2. `/debugtime setplayed <Spieler> <Sekunden>`
3. `/debugtime setsession <Spieler> <Level>`

Dann kannst du Edge-Cases reproduzierbar testen, ohne Live-Spieler lange warten zu lassen.
