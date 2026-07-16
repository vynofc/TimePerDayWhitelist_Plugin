# 3) Tageswechsel-Persistenz robust machen (Lern-Guide)

## Ziel
Beim DayOver keine inkonsistenten Daten in `playerdata.yml`.

---

## Problem
Im DayOver-Flow wird frueh gespeichert, danach werden aber nochmal Daten veraendert (z. B. Session-Daten fuer offline Spieler entfernt).

Wenn der Server zwischen diesen Schritten crasht, kann die Datei alten Zustand enthalten.

---

## Was du bauen willst
Ein klares Muster:

1. Alle DayOver-Mutationen im Speicher
2. genau ein finales Save nach Abschluss der Mutationen
3. danach erst Folia-Tasks fuer Online-Spieler

---

## Betroffene Datei

- `src/main/java/gg/vynofc/timeperday/manager/PlayerResetManager.java`

---

## Schritt-fuer-Schritt

## Schritt 1: Mutationsblock klar gruppieren
In `triggerDayOver(...)`:

- `currentDate` setzen
- Maps aendern (playedToday clear, lastKitClaimDate clear, sessionPoints remove fuer offline etc.)
- pendingReset setzen

Alles zuerst vollstaendig ausfuehren.

## Schritt 2: Erst danach speichern
Genau nach dem Abschluss des Datenblocks:

- `save()` bzw. neues `forceSave()` aufrufen

Damit ist persistierter Zustand gleich In-Memory-Zustand.

## Schritt 3: Runtime-Aktionen danach
Player-Reset/Kit-Vergabe fuer Online-Spieler kann danach weiterhin scheduler-basiert laufen.

Wichtig:
- Diese Laufzeitaktionen muessen nicht zwingend dieselbe Datenstruktur nochmal veraendern.
- Wenn doch, wieder dirty markieren.

---

## Edge Cases, die du testen solltest

1. DayOver bei 0 Online-Spielern.
2. DayOver bei gemischten Online/Offline-Spielern.
3. Neustart direkt nach DayOver.
4. Whitelist + bypass Spieler.

---

## Testplan

1. Vor DayOver absichtlich Session-Daten erzeugen.
2. DayOver triggern.
3. `playerdata.yml` direkt pruefen.
4. Server neu starten und sicherstellen, dass Zustand korrekt geladen wird.

---

## Haeufige Fehler

- Zu frueh speichern.
- Nach finalem Save nochmal still Daten veraendern.
- Offline/Online-Spieler unterschiedlich behandeln, aber nur einen Pfad persistieren.

