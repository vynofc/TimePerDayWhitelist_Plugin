# progression-bonus-multiplier (Idee)

## Typ
`double` – Multiplikator für Session-Punkte.

## Default
`1.0`

## Konfig
```yaml
# Multiplikator für Session-Punkte. 1.0 = normal, 2.0 = doppelte Punkte.
# Nützlich für Boost-Events oder Wochenenden.
progression-bonus-multiplier: 1.0
```

## GUI
`/tpdconfig` → Hauptseite, Slot (frei wählbar). Presets: `1.0, 1.5, 2.0, 3.0`.

## Logik
In `PlayerProgressionManager.calculateInventorySessionPoints()`: Rückgabewert mit Multiplikator multiplizieren.

## Betrifft
- `PlayerProgressionManager.java` – Multiplikator anwenden
- `PlayerTimeManager.java` – Feld + Getter + Config-Lader
- `ConfigMenuService.java` – GUI-Item + Click-Handler