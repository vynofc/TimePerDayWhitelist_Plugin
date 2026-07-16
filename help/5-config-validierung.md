# 5) Config-Validierung verbessern (Lern-Guide)

## Ziel
Fehlkonfigurationen frueh und klar erkennen, statt spaeter schwer nachvollziehbare Bugs zu haben.

---

## Was du bauen willst
Beim Start/Reload eine strukturierte Config-Pruefung:

1. Pflichtfelder checken
2. Wertebereiche checken
3. ungueltige Eintraege loggen und ignorieren
4. Option: harte Fehler fuer kritische Felder

---

## Betroffene Dateien

- `src/main/resources/config.yml`
- `src/main/java/gg/vynofc/timeperday/manager/PlayerPersistenceManager.java` (Spawn-Kits)
- ggf. neue Klasse wie `ConfigValidator`

---

## Schritt-fuer-Schritt

## Schritt 1: Validierungsregeln definieren
Beispielregeln:

- `default-limit-minutes >= 1`
- `warnings` nur nicht-negative Integer
- `progression.items.*.level > 0`
- `spawn-kits` Key muss Integer > 0 sein
- Kit-Mengen >= 1

Schreibe diese Regeln zuerst als Liste (Design vor Code).

## Schritt 2: Validator-Klasse bauen
Eine zentrale Methode, z. B.:

- `ValidationResult validate(FileConfiguration config)`

`ValidationResult` kann enthalten:
- `List<String> warnings`
- `List<String> errors`

## Schritt 3: Beim Reload anwenden
In Reload-Flow:

- Validator ausfuehren
- Warnungen loggen
- bei harten Errors: Reload abbrechen oder fallback nutzen (deine Entscheidung)

## Schritt 4: Spawn-Kit-Lader robuster machen
In `readSpawnKits()`:

- alle ungueltigen Materialien/Werte mit vollem Config-Pfad loggen
- ungueltige Eintraege ueberspringen, nicht crashen

---

## Logging-Qualitaet (wichtig)
Schlecht:
- "Ungueltiger Wert"

Gut:
- "config.yml -> progression.spawn-kits.50.DIAMON_SWORD: ungueltiges Material"

So findet man Fehler sofort.

---

## Testplan

1. Absichtlich Tippfehler in Material einbauen.
2. Negatives Item-Level setzen.
3. `warnings` mit ungueltigem Wert testen.
4. Reload ausfuehren und Logs pruefen.

---

## Haeufige Fehler

- Regeln sind im Code verstreut statt zentral.
- Fehler werden geschluckt (silent failure).
- Nach invalidem Reload bleibt unklar, welche Werte aktiv sind.

