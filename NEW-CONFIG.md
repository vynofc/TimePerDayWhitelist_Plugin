# NEW-CONFIG.md – Vorlage für neue Config-Einträge

Sag mir kurz, was du willst, und ich erledige den Rest.
Fülle die Felder unten aus – leere/optionale Felder einfach weglassen.

---

## Neuer Config-Eintrag

```
config-key:              max-health
Typ:                     Zahl (double) / "aus"
Default:                 "aus"
Beschreibung:            Maximales Spielerleben pro Tag. Eine Zahl (z.B. 20.0 = 10 Herzen)
                         oder "aus" zum Deaktivieren.
```

### GUI (optional)

```
GUI-Seite:               MAIN
GUI-Name:                Max. Leben
GUI-Slot:                16
GUI-Material:            APPLE
GUI-Presets:             20.0, 10.0, 6.0, 2.0  (durchklickbar, letzter = "aus")
                         (weglassen = nur in config.yml änderbar)
```

### Logik (optional)

```
Wo greift es?            Tick-Loop (jede Sekunde) + PlayerJoin
Was passiert?            MAX_HEALTH-Attribut auf Wert setzen, Leben kappen wenn > max
Betrifft Whitelist?      ja
```

---

## Beispiele

### Beispiel 1: Einfacher Boolean-Toggle

```
config-key:              allow-flight
Typ:                     boolean
Default:                 false
Beschreibung:            Erlaubt Spielern das Fliegen.

GUI-Seite:               MAIN
GUI-Name:                Flugmodus
GUI-Slot:                15
GUI-Material:            FEATHER
```

### Beispiel 2: Integer mit Presets

```
config-key:              max-players
Typ:                     int
Default:                 20
Beschreibung:            Maximale Spieleranzahl auf dem Server.

GUI-Seite:               MAIN
GUI-Name:                Max. Spieler
GUI-Slot:                17
GUI-Material:            PLAYER_HEAD
GUI-Presets:             10, 20, 50, 100
```

### Beispiel 3: Nur in config.yml (kein GUI)

```
config-key:              backup-interval
Typ:                     int (Minuten)
Default:                 60
Beschreibung:            Intervall für automatische Backups in Minuten.
```

### Beispiel 4: String (nur in config.yml änderbar, GUI zeigt read-only)

```
config-key:              server-name
Typ:                     String
Default:                 "Mein Server"
Beschreibung:            Der Anzeigename des Servers.

GUI-Seite:               MAIN
GUI-Name:                Servername
GUI-Slot:                18
GUI-Material:            NAME_TAG
```

---

## Was ich dann automatisch mache

1. `config.yml` – neuen Key mit Default-Wert + Kommentar einfügen
2. `PlayerTimeManager.java` – Feld + Getter + `reload*FromConfig()`-Methode
3. Logik einbauen (Tick-Loop, Join-Event, etc.) – je nach `Wo greift es?`
4. `ConfigMenuService.java` – GUI-Item + Click-Handler (falls GUI gewünscht)
5. `mvn clean package` – Build + Test