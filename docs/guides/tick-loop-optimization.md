# 2) Tick-Loop optimieren (Lern-Guide)

## Ziel
Die 1-Sekunden-Schleife soll pro Tick moeglichst wenig Arbeit machen.

---

## Problem
In `PlayerTickManager` werden Warnschwellen aktuell als `List<Long>` gelesen und mit `contains()` geprueft.

Das ist:
- pro Tick Konfig-Zugriff
- lineare Suche pro Spieler

---

## Was du bauen willst

1. Warnwerte einmalig beim Laden cachen
2. als `Set<Long>` speichern
3. nur bei Reload neu aufbauen

---

## Betroffene Dateien

- `src/main/java/gg/vynofc/timeperday/manager/PlayerTickManager.java`
- `src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java` (Reload-Hooks)

---

## Schritt-fuer-Schritt

## Schritt 1: Cache-Feld anlegen
In `PlayerTickManager`:

- `private Set<Long> warningThresholds = Set.of();`

## Schritt 2: Reload-Methode bauen
Neue Methode, z. B. `reloadWarningThresholds()`:

- `plugin.getConfig().getLongList("warnings")` lesen
- negative Werte filtern
- in `HashSet<Long>` umwandeln
- Ergebnis in Feld speichern

Optional:
- sortierte Debug-Ausgabe ins Log

## Schritt 3: Tick-Loop umstellen
In `tickOnlinePlayers()`:

- keine Config-Liste mehr pro Tick lesen
- direkt `warningThresholds.contains(remaining)`

## Schritt 4: Reload verdrahten
Beim Plugin-Start + bei `/admintime reload`:

- `reloadWarningThresholds()` aufrufen

---

## Bonus-Ideen

- Fuer sehr grosse Server: `Bukkit.getOnlinePlayers()` nicht mehrfach traversieren.
- Je Tick nur notwendige Daten berechnen (z. B. UUID einmal ziehen).

---

## Testplan

1. `warnings` auf `[10,30,60]` setzen.
2. Reload ausfuehren.
3. Pruefen, ob Warnungen weiterhin korrekt kommen.
4. `warnings` aendern, reload, erneut pruefen.

---

## Haeufige Fehler

- Cache wird bei Reload nicht aktualisiert.
- Filterung entfernt aus Versehen `0` (wenn du 0 als legitimen Warnwert willst, gesondert behandeln).
- Thread-Sichtbarkeit vergessen (`volatile` oder synchronized), falls parallel gelesen/geschrieben.

