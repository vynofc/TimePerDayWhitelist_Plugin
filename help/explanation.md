# Explanation: Aktueller Stand der Umsetzung

Diese Datei beschreibt den aktuellen Implementierungsstand des Plugins so genau wie moeglich. Sie ist bewusst auf das heutige Verhalten begrenzt und nennt keine Altformate mehr, die nicht mehr unterstuetzt werden.

## Kurzueberblick

Das Plugin kombiniert weiterhin ein hartes Tageslimit mit einer einfachen Progressionslogik:

1. Spieler haben pro Tag ein konfigurierbares Zeitlimit.
2. Item-Pickups waehrend der aktiven Spielzeit erzeugen Session-Punkte.
3. Beim Ablauf der Zeit werden Session-Punkte in dauerhaftes Gesamtlevel umgewandelt.
4. Level-basierte Kits werden einmal pro Tag vergeben.

## Aktuell unterstuetzte Daten und Pfade

### Persistente Spielerdaten

Die Datei `playerdata.yml` speichert aktuell:

1. `playtime.<uuid>`
2. `limits.<uuid>`
3. `whitelist.<uuid>`
4. `session-points.<uuid>`
5. `total-level.<uuid>`
6. `last-kit-claim-date.<uuid>`

### Config-Struktur

Nur diese Progressionsform ist gueltig:

1. `progression.items.<ITEM>.level`
2. `progression.spawn-kits.<LEVEL>`

Andere Formate werden nicht mehr gelesen.

## 1) Progression und Item-Pickups

Die Progression laeuft ausschliesslich ueber Item-Pickups.

Ablauf:

1. `PlayerItemListener` reagiert auf `EntityPickupItemEvent`.
2. Das Material und die Menge werden an `PlayerTimeManager.addSessionPoints(...)` uebergeben.
3. `readItemLevel(...)` liest den Wert nur aus `progression.items.<ITEM>.level`.
4. Falls der Wert groesser als 0 ist, wird `sessionPoints` erhoeht.
Wichtige Folge:

- Items, die nur im Inventar liegen und nicht ueber Pickup erfasst werden, erzeugen keine Progression.
- Nicht konfigurierte Items erzeugen keine Punkte.
- Whitelist- und Bypass-Spieler erhalten keine Progression.

## 2) Zeitlimit und Abschluss der Session

Bei jedem Tick wird das Zeitlimit geprueft.

Wenn die Restzeit 0 oder kleiner ist:

1. `finalizeSessionProgress(...)` uebernimmt alle Session-Punkte in `totalLevel`.
2. `sessionPoints` wird auf 0 gesetzt.
3. Der Spieler wird gekickt.

Die Kick-Nachricht zeigt dabei:

- gespielte Zeit
- heutige Session-Punkte
- Gesamtlevel
- bestes erreichbares Kit

## 3) Join- und Profilanzeige

Beim normalen Join und via `/timeleft` werden die aktuellen Profilwerte angezeigt:

- bisher gespielte Zeit
- Session-Punkte
- Gesamtlevel
- Progress-Level
- bestes Kit
- verbleibende Zeit

Die Profilwerte kommen direkt aus den aktuellen Player-Daten und aus der Config, nicht aus alten Konvertierungswerten.

## 4) Kit-System

Die Kit-Logik ist Level-basiert:

1. `readSpawnKits()` liest `progression.spawn-kits`.
2. `getBestKitLevelFor(...)` sucht die hoechste passende Stufe.
3. `grantDailyKit(...)` vergibt das Kit nur einmal pro Tag.
4. Falls das Inventar voll ist, werden Overflow-Items gedroppt.

Das Stufenmodell laeuft aktuell in 10er-Schritten bis Level 150.

## 5) Admin-Funktionen

Die Admin-Funktionen decken Zeit und Fortschritt ab:

- Tageslimit setzen
- Spieler-Level setzen und addieren
- Spieler- und Global-Reset
- Whitelist-Verwaltung
- Reload
- GUI-Verwaltung

## Entfernte Altlogik

Die folgende Altlogik wurde entfernt und ist absichtlich nicht mehr Teil des Systems:

- Rueckfallpfade fuer alte Config-Formate

Damit ist die Config klar und eindeutig: jedes Item bekommt genau einen Wert ueber `level`.

## Auffaellige Risiken und moegliche Folgefehler

1. Das System ist weiterhin pickup-basiert. Wer Items nicht aufhebt, sammelt keine Punkte.
2. Ein Stack-Pickup zaehlt als `Menge × level`, was gewollt ist, aber bei grossen Mengen schnell viel Progress erzeugen kann.
3. Die Punktevergabe ist theoretisch durch Drop/Pickup-Farming manipulierbar, weil die Quelle nur der Pickup ist.
4. Alte Config-Dateien ohne das aktuelle `level`-Schema liefern keine Punkte mehr.

## Dateien im Kern

- [src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java](../src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java)
- [src/main/java/gg/vynofc/timeperday/listener/PlayerItemListener.java](../src/main/java/gg/vynofc/timeperday/listener/PlayerItemListener.java)
- [src/main/java/gg/vynofc/timeperday/listener/PlayerListener.java](../src/main/java/gg/vynofc/timeperday/listener/PlayerListener.java)
- [src/main/java/gg/vynofc/timeperday/command/TimeCommand.java](../src/main/java/gg/vynofc/timeperday/command/TimeCommand.java)
- [src/main/java/gg/vynofc/timeperday/command/AdminTimeCommand.java](../src/main/java/gg/vynofc/timeperday/command/AdminTimeCommand.java)
- [src/main/resources/config.yml](../src/main/resources/config.yml)

## Fazit

Das Plugin ist jetzt auf ein klares, aktuelles Datenmodell reduziert:

1. keine alten Progressionsschluessel mehr
2. Progression nur ueber `progression.items.<ITEM>.level`
3. Session-Abschluss ueber das vorhandene Zeitlimit
4. dauerhafte Gesamtlevel und tägliche Kits bleiben erhalten
