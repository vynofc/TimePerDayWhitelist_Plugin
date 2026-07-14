# TimePerDayWhitelist

Paper/Folia Plugin mit hartem Tageslimit plus Progressionssystem.

## Features

- 1 Stunde Spielzeit pro Tag (konfigurierbar)
- Auto-Kick bei Tageslimit
- Tagesreset über Datumswechsel
- Item-basierte Session-Punkte mit genau einem `level`-Wert pro Item
- Fortschrittsrelevante Item-Pickups zeigen kurz eine Action-Bar mit dem verdienten Level
- Session-Punkte werden beim Tageslimit in Gesamtlevel umgerechnet
- Gesamtlevel wird persistent gespeichert
- Level-basierte Spawn-Kits in 10er-Stufen bis Level 150
- Join-/Kick-/Time-Anzeige als Profiluebersicht inkl. `/timeleft`-Hinweis
- Admin-Funktionen für Zeit und Level
- Ingame-Admin-Kisten-UI für Spielerverwaltung, Limits, Level, Whitelist, Reload und Global-Reset
- Konfigurierte MiniMessage-Nachrichten werden tatsächlich als formatierte Components gerendert

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

1. Spieler sammelt waehrend der Tagesstunde Session-Punkte ueber konfigurierte Item-Pickups.
2. Jedes konfigurierte Item hat genau einen `level`-Wert, der direkt als Progressionsgewinn zaehlt.
3. Bei Zeitablauf wird der Spieler gekickt.
4. Vor dem Kick gilt:
   - `gained-level = session-points`
   - `total-level += gained-level`
5. Session-Punkte werden auf 0 gesetzt.

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

Aktuell basiert die Punktevergabe auf Item-Pickup. Dadurch ist Drop/Pickup-Farming möglich.
Wenn du das verhindern willst, erweitere die Vergabe später auf kontrollierte Quellen (z. B. BlockBreak/Craft/MobDrop).

## Lizenz

Siehe [LICENSE](LICENSE).
