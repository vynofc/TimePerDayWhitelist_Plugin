# Explanation: Vollstaendige Aenderungen

Diese Datei erklaert im Detail, was implementiert wurde, warum es so gebaut wurde und wie die einzelnen Teile zusammenspielen.

## Ziel der Umsetzung

Das Plugin wurde von reinem Tageszeit-Limit auf ein kombiniertes System erweitert:

1. Spieler haben weiterhin ein hartes Tageslimit (1h/Tag ueber Config).
2. Innerhalb dieser Zeit sammeln Spieler Progression ueber Item-Pickups.
3. Session-Punkte werden bei Zeitablauf in persistenten Gesamtlevel ueberfuehrt.
4. Kits werden levelabhaengig vergeben (bis Level 150).
5. Informationen zu Zeit, Session und Level werden in Commands/Nachrichten angezeigt.

## Geaenderte Dateien (Ueberblick)

1. src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java
2. src/main/java/gg/vynofc/timeperday/listener/PlayerItemListener.java (neu)
3. src/main/java/gg/vynofc/timeperday/listener/PlayerListener.java
4. src/main/java/gg/vynofc/timeperday/TimePerDayPlugin.java
5. src/main/java/gg/vynofc/timeperday/command/TimeCommand.java
6. src/main/java/gg/vynofc/timeperday/command/AdminTimeCommand.java
7. src/main/resources/config.yml
8. README.md
9. help/Anleitung.md

## 1) PlayerTimeManager: Kernlogik erweitert

In dieser Klasse wurden die wichtigsten neuen Systeme eingebaut.

### Neue persistente Daten

Zusatzlich zu playtime/limits/whitelist:

1. session-points.<uuid>
2. total-level.<uuid>
3. last-kit-claim-date.<uuid>

Diese Werte werden beim Laden gelesen und beim Speichern in playerdata.yml geschrieben.

### Tick-Logik bei Zeitablauf

Bei remaining <= 0 passiert jetzt:

1. Session abschliessen (sessionPoints -> totalLevel)
2. Session-Punkte auf 0 setzen
3. Danach Spieler kicken

Damit bleibt die alte Zeitlogik erhalten, aber Progression wird korrekt verbucht.

### Tageswechsel

Bei Datumswechsel:

1. playedToday wird zurueckgesetzt
2. sessionPoints wird zurueckgesetzt
3. totalLevel bleibt erhalten

Das entspricht dem gewuenschten Verhalten: daily fresh run, aber dauerhafter Account-Fortschritt.

### Kit-System

Neue Logik:

1. readSpawnKits() liest progression.spawn-kits aus config.yml
2. getBestKitLevelFor(level) ermittelt das hoechste erreichbare Kit
3. grantDailyKit(player) vergibt Kit genau 1x pro Tag
4. Ueberlauf-Items werden bei vollem Inventar gedroppt

### Placeholder fuer Nachrichten

Es wurde ein Platzhalter-System hinzugefuegt, damit Nachrichten Werte wie Level zeigen koennen:

1. gained-level
2. total-level
3. session-points

## 2) Neuer Listener: PlayerItemListener

Datei: src/main/java/gg/vynofc/timeperday/listener/PlayerItemListener.java

Aufgabe:

1. Reagiert auf EntityPickupItemEvent
2. Wenn Entity ein Player ist, werden Material + Menge an den Manager gegeben
3. Dort werden Punkte laut progression.items.<ITEM>.level gerechnet

Hinweis:

Aktuell Pickup-basiert = schnell und einfach, aber potentiell exploitable (Drop/Pickup-Farming).

## 3) PlayerListener: Join-Verhalten erweitert

Datei: src/main/java/gg/vynofc/timeperday/listener/PlayerListener.java

### Bei bereits verbrauchter Tageszeit

Der Join-Kick nutzt jetzt eine Profil-Uebersicht mit Zeit-, Session-, Level- und Kit-Infos.

### Bei normalem Join

Zusatzlogik:

1. Daily-Kit-Vergabe versuchen
2. Join-Message mit:
   - remaining
   - total-level
   - session-points
   - kit-level
   - /time-Hinweis

## 4) Plugin Bootstrap erweitert

Datei: src/main/java/gg/vynofc/timeperday/TimePerDayPlugin.java

Neu:

1. Registrierung von PlayerItemListener

Damit ist Item->Punkte aktiv.

## 5) TimeCommand erweitert

Datei: src/main/java/gg/vynofc/timeperday/command/TimeCommand.java

Der Befehl /time zeigt jetzt zusaetzlich:

1. Session-Punkte
2. Gesamtlevel
3. Progress-Level
4. Bestes Kit

Neben den bisherigen Zeitwerten.

## 6) AdminTimeCommand erweitert

Datei: src/main/java/gg/vynofc/timeperday/command/AdminTimeCommand.java

### Neue Subcommands

1. /admintime setlevel <Spieler> <Level>
2. /admintime addlevel <Spieler> <Level>
3. /admintime reset (GLOBAL)
4. /admintime resetplayer <Spieler>

### Info-Ausgabe erweitert

/admintime info zeigt jetzt zusaetzlich:

1. Session-Punkte
2. Gesamtlevel

### TabComplete + Hilfe erweitert

Neue Subcommands sind in Auto-Complete und Help-Text enthalten.

### Global-Reset Verhalten

`/admintime reset` fuehrt jetzt einen globalen Reset aus:

1. alle Spielzeit-/Progressionsdaten werden geleert
2. alle Spieler-Levels werden auf 0 gesetzt
3. Online-Spieler werden auf Spawn gesetzt und Inventare/XP/Status werden resetet
4. Welt-Laufzeitstatus wird resetet (Zeit/Wetter/Non-Player-Entities)

Hinweis:

Terrain/Chunk-Neugenerierung ist nicht Teil dieses Befehls. Der Befehl resetet den aktiven Laufzeit-Zustand und alle Plugin-Daten.

## 7) config.yml stark erweitert

Datei: src/main/resources/config.yml

### Nachrichten

Erweitert mit Platzhaltern fuer Progression und Profilansicht:

1. Kick-Nachrichten bilden jetzt eine Profiluebersicht
2. Join-Nachrichten enthalten remaining, total-level, session-points, kit-level und /time-Hinweis

### progression.items

Mehrere Startwerte fuer Material -> Level wurden hinzugefuegt.
Jedes Item nutzt genau ein Feld: `level`.

### progression.spawn-kits

Komplette Staffel in 10er-Schritten bis 150 hinterlegt.

Wichtige gewuenschte Anpassung umgesetzt:

1. Kein fruehes Diamond bei Level 40
2. Level 40 = Eisen-Tools + Iron Boots + Iron Leggings

## 8) README aktualisiert

Datei: README.md

Das README beschreibt jetzt den echten Funktionsstand:

1. 1h/Tag + Progression
2. Item-Level pro Material
3. Level-Kits bis 150
4. Neue Admin-Level-Befehle
5. Global-Reset mit `/admintime reset`

## 9) help/Anleitung.md aktualisiert

Datei: help/Anleitung.md

Die Anleitung wurde von Plan-Text auf reale Implementierungsdokumentation umgestellt.

## Offene technische Punkte

1. Maven-Build laeuft erfolgreich durch.
2. Pickup-basierte Punktevergabe ist weiterhin exploitable und sollte bei Bedarf anti-exploit gehaertet werden.

## Empfohlener Testablauf

1. Testserver starten
2. Mit kleiner Tageszeit testen (z. B. 2-5 Minuten)
3. Items sammeln und /time pruefen
4. Zeit ablaufen lassen und Kick-Text pruefen
5. Rejoin vor und nach Tageswechsel testen
6. Kit-Vergabe je Level pruefen
7. Server neu starten und Persistenz pruefen

## Kurzfazit

Die angeforderte Kernidee ist jetzt integriert:

1. Tageslimit bleibt hart
2. Progression laeuft innerhalb der Tageszeit
3. Level bleiben dauerhaft
4. Kit-System geht bis 150
5. Doku und README sind auf den aktuellen Stand gebracht
