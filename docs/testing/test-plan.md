# Vollstaendiger Testplan fuer TimePerDayWhitelist

## 1) Voraussetzungen

1. Java 21 installiert.
2. Maven installiert.
3. Paper/Folia Testserver verfuegbar.
4. OP-Rechte oder passende Permissions:
   - `timeperday.use`
   - `timeperday.admin`
   - `timeperday.debug`

## 2) Build und Deployment

1. Im Projektordner bauen:

```bash
mvn clean package
```

2. Erzeugte JAR aus `target/` in den Server-Ordner `plugins/` kopieren.
3. Server starten.
4. Sicherstellen, dass beim Start keine Plugin-Fehler in der Konsole erscheinen.

## 3) Initiale Konfigurationspruefung

1. `config.yml` oeffnen.
2. Pruefen:
   - `default-limit-minutes`
   - `warnings`
   - `messages.*`
   - `progression.items.<ITEM>.level`
   - `progression.spawn-kits.<LEVEL>`
3. Server neu starten oder `/admintime reload` ausfuehren.

Erwartung:
- Config wird ohne Fehler geladen.

## 4) Smoke-Test Ingame

1. Mit Testspieler joinen.
2. `/timeleft` ausfuehren.

Erwartung:
- Ausgabe zeigt Restzeit, Session-Punkte, Gesamtlevel, Progress-Level und Kit-Info.
- Keine Fehlermeldungen in Konsole.

## 5) Berechtigungs-Tests

1. Als Spieler ohne Admin-Rechte:
   - `/admintime ...` testen.
   - `/debugtime ...` testen.
2. Als Admin/OP erneut testen.

Erwartung:
- Ohne Rechte: sauber verweigert.
- Mit Rechten: Befehle funktionieren.

## 6) Progressions-Tests (Kernlogik)

1. In `config.yml` ein Test-Item setzen, z. B.:
   - `COAL.level = 0.03`
2. Spieler bekommt genau 35 Kohle.
3. `/timeleft` und `/admintime info <Spieler>` pruefen.

Erwartung:
- Session-Punkte und Progress-Level spiegeln den Inventarwert.
- Gesamtlevel steigt noch nicht sofort.

## 7) Timeout-Tests (echter Pfad)

1. Limit fuer Testspieler stark reduzieren:
   - `/admintime set <Spieler> 1`
2. Warten bis Limit erreicht.

Erwartung:
1. Warnungen gemaess `warnings` erscheinen.
2. Bei Limit-Ende:
   - Session wird finalisiert.
   - Gesamtlevel steigt um den finalisierten Wert.
   - Session-Punkte gehen auf 0.
   - Spieler wird gekickt.

## 8) Debug-Tests `/debugtime`

### 8.1 Warnung
1. `/debugtime warn <Spieler> 30`

Erwartung:
- Normale Warnnachricht wird angezeigt.

### 8.2 Timeout
1. Spieler mit Test-Items online.
2. `/debugtime timeout <Spieler>`

Erwartung:
- Gleicher Ablauf wie echter Timeout (Finalisierung + Kick).

### 8.3 Day-Over online
1. Spieler online mit gefuelltem Inventar.
2. `/debugtime dayover`

Erwartung:
- Tageswerte resetten.
- Spielerzustand wird korrekt auf Day-Over gesetzt.

### 8.4 Day-Over offline
1. Spieler mit Daten erstellen, dann ausloggen.
2. `/debugtime dayover` ausfuehren.
3. Spieler wieder einloggen.

Erwartung:
- Ausstehender Day-Over-Reset wird beim Join angewendet.
- Danach normale Join-Info/Kit-Logik.

## 9) Kit-Tests

1. Spieler auf verschiedene Levels setzen:
   - `/admintime setlevel <Spieler> <Level>`
2. Join neu ausloesen.

Erwartung:
- Immer nur hoechstes passendes Kit.
- Kit nur einmal pro Tag.
- Overflow-Items werden gedroppt.

## 10) Whitelist- und Bypass-Tests

1. `/admintime whitelist add <Spieler>`
2. Timeout und Day-Over erneut testen.
3. Optional Permission `timeperday.bypass` testen.

Erwartung:
- Whitelist/Bypass-Spieler folgen den Sonderregeln (kein normaler harter Timeout-/Resetpfad).

## 11) Reset-Tests

### 11.1 Spieler-Reset
1. `/admintime resetplayer <Spieler>`

Erwartung:
- Nur Zielspieler wird zurueckgesetzt.

### 11.2 Global-Reset
1. `/admintime reset`

Erwartung:
- Alle betroffenen Spieler-/Weltzustaende werden gemaess Pluginlogik zurueckgesetzt.

## 12) Reload-Tests

1. `config.yml` Nachrichten/Levelwerte aendern.
2. `/admintime reload`
3. Relevante Befehle erneut testen.

Erwartung:
- Neue Config wird aktiv ohne Neustart.

## 13) Persistenz-Tests

1. Fortschritt erzeugen (Zeit + Level + Kit-Status).
2. Server sauber stoppen.
3. Server starten.
4. Werte erneut pruefen.

Erwartung:
- Daten aus `playerdata.yml` sind konsistent geladen.

## 14) Negative und Edge-Case-Tests

1. Ungueltige Zahlen:
   - `/admintime setlevel <Spieler> abc`
2. Offline-Spieler bei Commands, die Online-Spieler brauchen.
3. Nicht konfigurierte Items im Inventar.
4. Volles Inventar bei Kit-Vergabe.

Erwartung:
- Klare Fehlermeldungen.
- Kein stilles Scheitern.
- Kein unerwarteter Serverfehler.

## 15) Abschluss-Checkliste (Release-Ready)

- Plugin startet ohne Fehler.
- Alle Permissions verhalten sich korrekt.
- `/timeleft`, `/admintime`, `/debugtime` funktionieren.
- Progression, Timeout und Day-Over sind konsistent.
- Reset- und Reload-Pfade sind stabil.
- Persistenz ueber Neustart korrekt.
- Keine kritischen Fehler in Konsole waehrend aller Tests.
