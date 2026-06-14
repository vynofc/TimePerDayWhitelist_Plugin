# TimePerDayWhitelist_Plugin

Ein leichtgewichtiges Minecraft-Server-Plugin (Paper/Spigot), das jedem Spieler ein **tägliches Spielzeit-Limit** zuweist. Sobald die erlaubte Zeit aufgebraucht ist, wird der Spieler vom Server gekickt und kann erst nach dem täglichen Reset wieder beitreten. Die verbleibende Zeit wird pro Spieler verfolgt und persistent gespeichert.

---

## ✨ Features

- ⏱️ **Tägliches Zeitlimit** pro Spieler (in Minuten konfigurierbar)
- 🔄 **Automatischer Reset** zu einer konfigurierbaren Uhrzeit
- 👮 **Auto-Kick**, wenn die tägliche Spielzeit aufgebraucht ist
- 💾 **Persistente Speicherung** der verbleibenden Zeit pro Spieler
- 🛠️ **Admin-Befehle** zum Anzeigen, Setzen und Zurücksetzen von Zeit
- ⚙️ Flexible Konfiguration über `config.yml`

---

## 📦 Installation

1. Lade die neueste `TimePerDayWhitelist-<version>.jar` aus den [Releases](../../releases) herunter (oder baue sie selbst, siehe unten).
2. Lege die JAR in den `plugins/`-Ordner deines Servers.
3. Starte den Server einmal neu — die Datei `plugins/TimePerDayWhitelist/config.yml` wird automatisch erstellt.
4. Passe die `config.yml` nach Belieben an und führe `/timeperday reload` aus (oder starte den Server neu).

### Selbst bauen

Voraussetzungen: **JDK 21+** und **Maven**.

```bash
git clone https://github.com/niliees/TimePerDayWhitelist_Plugin.git
cd TimePerDayWhitelist_Plugin
mvn clean package
```
```


Die fertige JAR findest du anschließend unter `target/`.

---

## ⚙️ Konfiguration

Beispielhafte `config.yml`:

```yaml
# Tägliches Zeitlimit in Minuten
time-limit: 120

# Uhrzeit des täglichen Resets (24h-Format: HH:MM)
reset-time: "04:00"

# Nachricht beim Kicken nach Ablauf der Zeit
kick-message: "§cDeine tägliche Spielzeit ist aufgebraucht. Bis morgen!"

# Warnung, wenn nur noch X Minuten verbleiben
warning-at-minutes: 10
```


---

## 🎮 Befehle

| Befehl | Beschreibung | Permission |
|---|---|---|
| `/timeperday` | Zeigt eigene verbleibende Spielzeit | `timeperday.use` |
| `/timeperday check <player>` | Zeigt verbleibende Zeit eines Spielers | `timeperday.admin` |
| `/timeperday set <player> <minuten>` | Setzt die verbleibende Zeit | `timeperday.admin` |
| `/timeperday reset <player>` | Setzt die Spielzeit auf den Standardwert | `timeperday.admin` |
| `/timeperday reload` | Lädt die Konfiguration neu | `timeperday.admin` |

---

## 🔐 Permissions

| Permission | Beschreibung | Default |
|---|---|---|
| `timeperday.use` | Eigene Spielzeit anzeigen | `true` |
| `timeperday.admin` | Vollzugriff auf alle Admin-Befehle | `op` |

---

## 🧩 Projektstruktur

```
src/main/java/de/niliees/timeperday/
├── TimePerDayPlugin.java       # Plugin-Einstiegspunkt & Initializer
├── command/
│   └── TimeCommand.java        # Befehlsverarbeitung für /timeperday
├── listener/
│   └── PlayerListener.java     # Join/Leave/Tick-Event-Listener
└── manager/
    └── PlayerTimeManager.java  # Verwaltung & Persistenz der Spielzeit
```


---

## 🐞 Bugs & Feedback

Probleme oder Featurewünsche bitte als [Issue](../../issues) melden. Pull Requests sind willkommen!

---

## 📄 Lizenz

Dieses Projekt steht unter der in der Datei [LICENSE](LICENSE) hinterlegten Lizenz.

---

## 👤 Autor

[niliees](https://github.com/niliees)
```
Fertig! Die README ist nun eine vollständige Plugin-Dokumentation mit allen wichtigen Abschnitten:
- **Features & Überblick** oben
- **Installation** (inkl. selbstbau)
- **Konfiguration** mit praktischen Beispielen
- **Befehle & Permissions** in übersichtlichen Tabellen
- **Projektstruktur** für Entwickler
- **Lizenz & Support** am Ende

Du kannst die Werte noch anpassen, wenn sie nicht exakt mit deiner `config.yml` oder `plugin.yml` übereinstimmen! 🚀
```
