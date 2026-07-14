# Issue #11: `+ Level doesnt work`

Diese Notiz dokumentiert die Arbeit an `vynofc/TimePerDayWhitelist_Plugin#11`.

## Beobachtung

Beispiel aus dem Issue:

- 35x `COAL`
- `35 * 0.03 = 1.05`

Im aktuellen System wird dieser Wert nicht sofort in `total-level` geschrieben, sondern zuerst als laufender Fortschritt berechnet und erst bei Timeout/Reset finalisiert.

## Ursache

Die eigentliche Progression war bereits vorhanden:

1. `getSessionPoints(Player)` berechnet den aktuellen Inventarwert live.
2. `getProgressLevel(...)` addiert `total-level + session-points`.
3. `total-level` steigt erst bei `finalizeSessionProgress(...)`.

Das Debug-/Admin-Problem war, dass die Admin-Anzeigen nur `Session-Punkte` und `Gesamtlevel` zeigten, aber nicht den laufenden `Progress-Level`.

Dadurch sah es so aus, als ob z. B. `1.05` Level "nicht angekommen" waeren, obwohl sie als Live-Fortschritt bereits vorhanden waren.

## Aenderungen

Geaendert wurden nur die Debug-/Admin-Anzeigen:

1. `src/main/java/gg/vynofc/timeperday/command/AdminTimeCommand.java`
   - `Progress-Level` in `/admintime info` ergaenzt
2. `src/main/java/gg/vynofc/timeperday/gui/AdminMenuService.java`
   - `Progress-Level` in Admin-Infoausgabe ergaenzt
   - `Progress-Level` in der Spieler-Zusammenfassung im GUI ergaenzt

## Ergebnis

Admins koennen jetzt direkt sehen:

- `Session-Punkte` = aktueller Inventarwert
- `Gesamtlevel` = bereits finalisierter Wert
- `Progress-Level` = `Gesamtlevel + Session-Punkte`

Damit ist bei Debugging sofort sichtbar, dass z. B. 35 Kohle aktuell `1.05` Fortschritt ergeben, auch bevor ein Timeout oder Reset den Wert in `total-level` uebernimmt.

## Validierung

Geprueft wurde:

1. Bestehende Progressionspfade in `PlayerTimeManager`
2. Anzeige-Pfade in `TimeCommand`, `AdminTimeCommand` und `AdminMenuService`
3. Maven-Build/Test versucht

Hinweis:

- `mvn test` konnte in der Sandbox nicht erfolgreich laufen, weil die Paper-API aus `https://repo.papermc.io/` hier nicht aufgeloest werden konnte.
