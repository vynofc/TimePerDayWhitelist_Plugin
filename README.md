# TimePerDayWhitelist

Paper/Folia Plugin mit hartem Tageslimit plus Progressionssystem.

## Features

- 1 Stunde Spielzeit pro Tag (konfigurierbar)
- Auto-Kick bei Tageslimit
- Tagesreset über Datumswechsel
- Item-basierte Session-Punkte mit genau einem `level`-Wert pro Item
- Session-Punkte werden aus Inventar/Enderchest berechnet und erst beim Session-Ende in Gesamtlevel umgerechnet
- Gesamtlevel wird persistent gespeichert
- Level-basierte Spawn-Kits in 10er-Stufen bis Level 150
- Join-/Kick-/Time-Anzeige als Profiluebersicht inkl. `/timeleft`-Hinweis
- Admin-Funktionen für Zeit und Level
- Ingame-Admin-Kisten-UI für Spielerverwaltung, Limits, Level, Whitelist, Reload und Global-Reset
- Konfigurierte MiniMessage-Nachrichten werden tatsächlich als formatierte Components gerendert

## Code-Struktur (Manager-Aufteilung)

Die Logik ist in mehrere spezialisierte Manager getrennt, damit Wartung und Erweiterungen einfacher sind:

- `PlayerTimeManager`: Zentrale Fassade und öffentliche API (wird von Commands/Listenern genutzt)
- `PlayerPersistenceManager`: Laden, Speichern und Reload von `playerdata.yml` und Spawn-Kits
- `PlayerProgressionManager`: Session-Punkte, Gesamtlevel-Finalisierung und tägliche Kit-Vergabe
- `PlayerResetManager`: Day-Over-Ablauf, Spieler-/Welt-Reset und Pending-Reset-Verwaltung
- `PlayerTickManager`: 1-Sekunden-Tick, Warnungen und Timeout-Handling
- `PlayerMessageManager`: Join/Kick/Warn-Nachrichten inkl. Placeholder-Auflösung
- `PlayerTimeSnapshot`: Datenstruktur für Zeit-/Progressions-Snapshots

Vorteile der Aufteilung:

- Kleinere, klar abgegrenzte Klassen statt einer großen Sammelklasse
- Bessere Lesbarkeit und weniger Seiteneffekte pro Änderung
- Einfacheres Testen einzelner Verantwortlichkeiten

## Installation

1. JAR in den `plugins/`-Ordner legen.
2. Server starten.
3. `config.yml` anpassen.
4. `/admintime reload` ausführen oder Server neu starten.

### Build

Voraussetzungen: JDK 21+, Maven

```bash
mvn clean package
```

## Progressionslogik

1. Spieler sammelt waehrend der Tagesstunde konfigurierte Items im Inventar oder in der Enderchest.
2. Jedes konfigurierte Item hat genau einen `level`-Wert, der direkt als Progressionsgewinn zaehlt.
3. Session-Punkte in Anzeigen entsprechen dem aktuellen Inventarwert und werden nicht mehr beim Pickup gespeichert.
4. `total-level` wird erst bei Zeitablauf oder Tagesreset erhöht; der Live-Fortschritt ist vorher über `Progress-Level` sichtbar.
5. Bei Zeitablauf oder Tagesreset wird vor dem Leeren/Finalisieren das komplette Inventar berechnet.
6. Bei Zeitablauf wird der Spieler gekickt.
7. Vor dem Kick gilt:
   - `gained-level = session-points`
   - `total-level += gained-level`
8. Session-Punkte werden auf 0 gesetzt.

## Kit-Logik

- Kits kommen aus `progression.spawn-kits` in der Config.
- Es wird nur das höchste passende Kit vergeben.
- Pro Spieler pro Tag wird das Kit nur einmal vergeben.
- Standardstaffel läuft in 10er-Schritten von Level 10 bis 150.

Frühe Stufen:

- 10: Holz-Tools + 5 Brot
- 20: Stein-Tools
- 30: Eisen-Tools + 8 Brot
- 40: Eisen-Tools + Iron Boots + Iron Leggings

## Befehle

| Befehl | Beschreibung | Permission |
|---|---|---|
| `/timeleft` | Zeigt Zeit, Session-Punkte, Gesamtlevel | `timeperday.use` |
| `/admintime info [Spieler]` | Zeit + Progressionswerte anzeigen | `timeperday.admin` |
| `/admintime set <Spieler> <Minuten>` | Tageslimit setzen | `timeperday.admin` |
| `/admintime reset` | GLOBAL: setzt aktive Weltzustände und alle Spieler-/Leveldaten zurück | `timeperday.admin` |
| `/admintime resetplayer <Spieler>` | Setzt nur den angegebenen Spieler zurück | `timeperday.admin` |
| `/admintime setdefault <Minuten>` | Standardlimit setzen | `timeperday.admin` |
| `/admintime setlevel <Spieler> <Level>` | Gesamtlevel setzen | `timeperday.admin` |
| `/admintime addlevel <Spieler> <Level>` | Gesamtlevel addieren | `timeperday.admin` |
| `/admintime whitelist <add\|remove> <Spieler>` | Zeitlimit-Bypass verwalten | `timeperday.admin` |
| `/admintime gui` | Öffnet die Ingame-Adminoberfläche | `timeperday.admin` |
| `/admintime reload` | Config und Daten neu laden | `timeperday.admin` |
| `/debugtime <dayover\|warn\|timeout>` | Manuelle Debug-Events auslösen (Tag vorbei, Warnung, Timeout) | `timeperday.debug` |

## Wichtige Config-Bereiche

- `default-limit-minutes`
- `warnings`
- `messages.*`
- `progression.items.*.level`
- `progression.spawn-kits.*`

## Admin-GUI

- `/admintime gui` öffnet eine Kistenoberfläche für Admins.
- Dort können Spieler ausgewählt und Limits, heutige Zeit, Gesamtlevel und Whitelist verwaltet werden.
- Reload und Global-Reset sind ebenfalls über die GUI erreichbar.

## Hinweis

Die Punktevergabe basiert jetzt auf dem aktuellen Inventarstand vor Session-Ende oder Reset, nicht mehr auf Item-Pickups.

## Lizenz

Siehe [LICENSE](LICENSE).
