# 1) Save-Strategie optimieren (Lern-Guide)

## Ziel
Weniger Festplattenzugriffe und trotzdem sichere Daten.

Aktuell wird oft direkt gespeichert (viele `save()`-Aufrufe). Das ist einfach, aber bei vielen Spielern teuer.

---

## Was du bauen willst
Ein kleines Save-System mit:

1. `dirty`-Flag (es gab Aenderungen)
2. periodischem Flush (z. B. alle 30 Sekunden)
3. "force save" bei kritischen Punkten (Disable, Reload, evtl. DayOver Ende)

---

## Betroffene Dateien

- `src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java`
- `src/main/java/gg/vynofc/timeperday/manager/PlayerPersistenceManager.java`
- alle Klassen mit vielen `save()`-Aufrufen (`PlayerProgressionManager`, `PlayerResetManager`, `AdminTimeCommand`, `AdminMenuService`)

---

## Schritt-fuer-Schritt

## Schritt 1: Dirty-Status einfuehren
In `PlayerTimeManager`:

- Feld `private volatile boolean dirty;`
- Methode `markDirty()` -> `dirty = true;`
- Methode `flushIfDirty()` -> wenn `dirty`, dann `persistenceManager.save()` und `dirty = false`
- Methode `forceSave()` -> immer speichern, danach `dirty = false`

Wichtig: Halte die Methoden `synchronized` oder nutze ein klares Lock, damit keine Race-Conditions entstehen.

## Schritt 2: Save-Aufrufe ersetzen
Suche alle direkten `save()`-Aufrufe und entscheide:

- normale Datenaenderung -> `markDirty()`
- kritischer Punkt -> `forceSave()`

Faustregel:
- `setLimit`, `setWhitelisted`, `setTotalLevel` -> meistens nur `markDirty()`
- `onDisable`, `reload`, Abschluss DayOver -> `forceSave()`

## Schritt 3: Periodischen Flush bauen
Du hast bereits einen Tick-Loop. Nutze ihn:

- Alle X Ticks (z. B. 600 = 30s) `flushIfDirty()`
- Nicht jede Sekunde schreiben

## Schritt 4: Reload/Shutdown absichern
In Plugin-Lifecycle:

- `onDisable()` -> `forceSave()`
- Reload-Flow -> vor dem Leeren/Neuladen ebenfalls `forceSave()`

---

## Testplan (manuell)

1. Server starten, 2-3 Daten aendern (Limit, Whitelist, Level).
2. Sofort Datei pruefen: sollte evtl. noch **nicht** jedes Mal geschrieben sein.
3. 30-60 Sekunden warten: Datei sollte aktualisiert sein.
4. Server stoppen: Daten muessen sicher in `playerdata.yml` stehen.

---

## Haeufige Fehler

- Dirty nie zuruecksetzen -> unnoetige Dauerschreibungen.
- Nur dirty setzen, aber nie flushen -> Datenverlust bei Crash.
- Nebenlaeufigkeit ignorieren -> inkonsistente Saves.

---

## Lernziel
Du lernst Write-Behind-Caching + Datenpersistenz mit "eventual flush + forced checkpoint".

