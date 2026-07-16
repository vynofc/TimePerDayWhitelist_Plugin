# 6) Zeitzone fuer Tagesreset konfigurierbar machen (Lern-Guide)

## Ziel
Tageswechsel soll in einer definierten Zeitzone passieren (nicht nur Server-Default).

---

## Warum wichtig?
Wenn dein Host in UTC laeuft, du aber deutschsprachige Spieler hast, ist "Mitternacht" sonst verschoben.

---

## Was du bauen willst

1. Neues Config-Feld: `reset-timezone`
2. `ZoneId` beim Start/Reload laden
3. `LocalDate.now(zoneId)` statt `LocalDate.now()`

---

## Betroffene Dateien

- `src/main/resources/config.yml`
- `src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java`
- `src/main/java/gg/vynofc/timeperday/manager/PlayerTickManager.java`

---

## Schritt-fuer-Schritt

## Schritt 1: Config erweitern
In `config.yml`:

```yml
reset-timezone: "Europe/Berlin"
```

## Schritt 2: ZoneId im Manager halten
In `PlayerTimeManager`:

- Feld `private volatile ZoneId resetZoneId;`
- Methode `reloadTimezoneFromConfig()`
  - String lesen
  - mit `ZoneId.of(...)` parsen
  - bei Fehler: fallback auf `ZoneId.systemDefault()` + Warnlog

## Schritt 3: Datumsberechnung umstellen
Alle Stellen mit `LocalDate.now()` auf:

- `LocalDate.now(resetZoneId)`

Wichtig:
- Initialisierung von `currentDate`
- Vergleich im Tick (`today`)
- Debug-DayOver (falls "heutiges Datum" verwendet wird)

## Schritt 4: Reload verdrahten
Bei `/admintime reload`:

- zuerst Config reload
- dann `reloadTimezoneFromConfig()`
- dann restliche Daten/Cache neu laden

---

## Testplan

1. Zeitzone auf `Europe/Berlin`, dann auf `UTC`.
2. Logge testweise `currentDate` und `today`.
3. Pruefe, ob DayOver im erwarteten Zeitfenster passiert.

---

## Haeufige Fehler

- Ungueltige Zone wirft Exception und bricht Reload.
- Eine Stelle benutzt neue Zone, andere weiterhin systemDefault.
- Zeitzone wird nur beim Start geladen, nicht beim Reload.

---

## Lernziel
Du lernst saubere Zeitlogik mit expliziter Zone statt implizitem Serververhalten.

