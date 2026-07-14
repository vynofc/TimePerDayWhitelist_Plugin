# Anleitung: Progression auf 1h/Tag

Diese Anleitung beschreibt das aktuell implementierte System im Plugin.

## Kernregeln

1. Jeder Spieler hat pro Tag genau 1 Stunde Spielzeit (konfigurierbar über `default-limit-minutes`).
2. Nach Ablauf wird der Spieler gekickt.
3. Alle Item-Punkte aus Inventar und Enderchest werden beim Ablauf oder Reset in Gesamtlevel umgewandelt.
4. Session-Punkte werden zurückgesetzt, Gesamtlevel bleibt dauerhaft erhalten.
5. Join-Kit wird täglich einmal vergeben, abhängig vom höchsten erreichten Level-Kit.

## Wie Progression funktioniert

1. Item-Werte stehen in `progression.items` in `config.yml`.
2. Jedes Item hat genau einen `level`-Wert, der direkt als Gewinn pro Item zaehlt.
3. Die Session-Punkte ergeben sich aus allen konfigurierten Items im Inventar, in der Rüstung, in der Offhand und in der Enderchest.
4. Vor dem Leeren oder Finalisieren wird `Materialwert × Menge` über alle passenden Items berechnet.
5. Beim Tageslimit-Ende gilt: `gainedLevel = sessionPoints`, danach `totalLevel += gainedLevel`.

Beispiel:

- 1x `COAL` = 0.03 Level
- 1x `DIAMOND` = 0.2 Level

## Kit-System (bis Level 150)

1. Kits sind in `progression.spawn-kits` definiert.
2. Es wird immer nur das höchste erreichbare Kit vergeben.
3. Pro Spieler und Tag wird nur ein Kit ausgegeben.
4. Bei vollem Inventar werden Overflow-Items am Spieler gedroppt.

Frühe Stufen:

- Level 10: Holz-Tools + Brot
- Level 20: Stein-Tools
- Level 30: Eisen-Tools + Brot
- Level 40: Eisen-Tools + Iron Boots + Iron Leggings

Das vollständige Stufenmodell läuft in 10er-Schritten bis Level 150.

## Nachrichten

Verfügbare Platzhalter in `messages`:

- `{remaining}`
- `{level}`
- `{session-points}`
- `{kit-level}`
- `{kit-given}`
- `{gained-level}`
- `{total-level}`
- `{player}`
- `{played}`
- `{limit}`

## Befehle

- `/timeleft` zeigt Zeit, Session-Punkte, Gesamtlevel, Progress-Level und bestes Kit.
- `/admintime info [Spieler]` zeigt zusätzlich Progressionswerte.
- `/admintime setlevel <Spieler> <Level>` setzt Gesamtlevel.
- `/admintime addlevel <Spieler> <Level>` addiert Gesamtlevel.

## Technische Dateien

- `src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java`
- `src/main/java/gg/vynofc/timeperday/listener/PlayerListener.java`
- `src/main/java/gg/vynofc/timeperday/command/TimeCommand.java`
- `src/main/java/gg/vynofc/timeperday/command/AdminTimeCommand.java`
- `src/main/resources/config.yml`

## Hinweis zu Exploits

Aktuell zählt das System Item-Punkte über den Inventarstand am Session-Ende oder Reset.
