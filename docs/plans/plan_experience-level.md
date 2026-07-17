# Plan: Experience-Level soll in die Progression zählen

## Ziel
Du willst zusätzlich zu den Item-Punkten auch die Minecraft-Experience-Level (grüne Zahl über der XP-Leiste) in die Session-Punkte einrechnen.

Regel:
- 1 Experience-Level = 0.2 Level-Punkte (Standardwert aus der Config)
- Der Wert soll konfigurierbar sein

## Warum das sinnvoll ist
Aktuell kommen Session-Punkte nur aus Inventar + Enderchest. Dadurch wird Fortschritt über XP-Level nicht berücksichtigt.

Wenn XP-Level mitzählen:
- Spieler bekommen auch für längeres Spielen/Erleben Fortschritt
- Das System wird fairer, weil nicht nur Item-Horten zählt
- Du kannst den Anteil mit einer Zahl feinjustieren

## Ist-Stand im Code
Die Berechnung der Session-Punkte liegt in:
- src/main/java/fun/vynofc/timeperday/manager/PlayerProgressionManager.java

Wichtige Methode:
- calculateInventorySessionPoints(Player player)

Dort werden aktuell nur Item-Punkte summiert.

## Implementierungsplan (einfach und lernorientiert)

### Schritt 1: Config erweitern
In src/main/resources/config.yml unter progression einen neuen Bereich ergänzen.

Vorschlag:

```yml
progression:
  experience:
    level-per-level: 0.2
```

Bedeutung:
- level-per-level = Wie viele Plugin-Level pro 1 Minecraft-Experience-Level angerechnet werden

Warum so benannt?
- Der Pfad ist eindeutig und kollidiert nicht mit progression.items.*.level

### Schritt 2: Wert in der Progression lesen
In PlayerProgressionManager eine kleine Hilfsmethode hinzufügen, z. B.:

```java
private double readExperienceLevelFactor() {
    return Math.max(0.0D, manager.plugin.getConfig()
            .getDouble("progression.experience.level-per-level", 0.2D));
}
```

Warum Math.max(0.0D, ...)?
- Negative Werte würden sonst Punkte abziehen
- Mit Clamp auf >= 0 bleibt das Verhalten stabil

### Schritt 3: Experience-Level in die Session-Punkte einrechnen
In calculateInventorySessionPoints(Player player) die bisherige Item-Summe behalten und Experience ergänzen.

Gedanke:

```java
double itemPoints = ... // bisherige Summe
double experiencePoints = player.getLevel() * readExperienceLevelFactor();
return itemPoints + experiencePoints;
```

Wichtig:
- player.getLevel() liefert den integer XP-Level des Spielers
- Du rechnest nur den Level-Stand ein (nicht den XP-Fortschrittsbalken zwischen 2 Leveln)

### Schritt 4: Verhalten in bestehenden Flows prüfen
Du musst nichts an Tick/Kick/Finalize ändern, weil dort bereits calculateInventorySessionPoints(...) indirekt verwendet wird.

Das heißt:
- /tpd Anzeige nutzt automatisch den neuen Wert
- Kick bei Zeitablauf finalisiert automatisch Item + XP-Level
- Day-Over-Finalisierung nimmt ebenfalls die neue Summe

### Schritt 5: Dokumentation im README ergänzen
In README.md bei Progressionslogik kurz ergänzen:
- Session-Punkte bestehen aus Item-Punkten + Experience-Level-Anteil
- Formel: session = itemPoints + (xpLevel * level-per-level)

## Mini-Checkliste nach dem Coden

1. config.yml enthält progression.experience.level-per-level
2. Server startet ohne Fehler
3. /tpd zeigt höhere Session-Punkte, wenn Spieler XP-Level haben
4. Bei Timeout ist gained-level größer, wenn XP-Level > 0
5. Mit level-per-level: 0.0 gibt es wieder nur Item-basierte Punkte

## Konkrete Testfälle

### Test A: Nur Experience-Level
- Spielerinventar leer
- Enderchest leer
- Spieler hat Level 10
- Config: level-per-level = 0.2

Erwartung:
- Session-Punkte = 2.0

### Test B: Items + Experience-Level
- Item-Punkte (aus Inventar/Enderchest) = z. B. 1.35
- Spieler hat Level 5
- Config: 0.2

Erwartung:
- Session-Punkte = 1.35 + 1.0 = 2.35

### Test C: Config deaktiviert Anteil
- level-per-level = 0.0

Erwartung:
- XP-Level hat keinen Einfluss

### Test D: Schutz gegen negative Werte
- level-per-level = -5

Erwartung:
- Effektiv 0.0 (durch Math.max)

## Häufige Fehler (damit du schneller lernst)

- Falscher Config-Pfad (Tippfehler): progression.experience.level-per-level genau prüfen
- Aus Versehen player.getExp() statt player.getLevel() verwenden
  - player.getExp() ist nur Fortschritt innerhalb eines Levels (0.0 bis 1.0)
- Wert nicht mit Default laden
  - Dann bricht Verhalten auf alten Configs

## Optionaler Bonus (später)
Wenn du es noch genauer willst, kannst du zusätzlich den XP-Balken anteilig rechnen:
- points = (player.getLevel() + player.getExp()) * factor

Für den ersten sauberen Schritt ist aber nur player.getLevel() absolut richtig und einfacher.

## Kurzfassung für die Umsetzung
- Config um progression.experience.level-per-level erweitern (Default 0.2)
- In PlayerProgressionManager Faktor aus Config lesen
- In calculateInventorySessionPoints(...) itemPoints + (player.getLevel() * factor) zurückgeben
- Mit den 4 Testfällen verifizieren
