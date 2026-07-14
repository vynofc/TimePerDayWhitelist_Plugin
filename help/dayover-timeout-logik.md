# Kurzfassung: aktuelle Day-Over-/Timeout-Logik

Diese Datei fasst nur den aktuellen Ablauf zusammen.

## Day-Over

Der normale Mitternachtswechsel und `/debugtime dayover` laufen beide über denselben Reset-Pfad.

### Dabei passiert zuerst global:

1. Alle aktuell bekannten Spieler-UUIDs werden gesammelt.
2. `currentDate` wird auf das neue Datum gesetzt.
3. `playedToday` und `lastKitClaimDate` werden geleert.
4. Die Daten werden gespeichert.

### Für Online-Spieler:

1. Nicht-whitelisted Spieler ohne Bypass werden sofort über den Player-Scheduler zurückgesetzt.
2. Vor dem Leeren wird das aktuelle Inventar inklusive Rüstung, Offhand und Enderchest als Session-Fortschritt berechnet.
3. Dieser Wert wird in `totalLevel` übernommen.
4. Danach werden Inventar, Enderchest, XP, Health, Hunger und Position/Spielzustand zurückgesetzt.

### Für Offline-Spieler:

1. Ihre `sessionPoints` werden sofort verworfen.
2. Nicht-whitelisted Spieler werden in `pendingDayOverReset` eingetragen.
3. Beim nächsten Join greift ein verzögerter Reset:
   - Inventarstand wird dann noch einmal live ausgewertet
   - Fortschritt wird in `totalLevel` übernommen
   - danach wird der Spieler zurückgesetzt
   - Daily-Kit und Join-Nachricht laufen anschließend normal

### Wichtige Ausnahme:

- Whitelisted Spieler oder Spieler mit `timeperday.bypass` werden nicht hart zurückgesetzt.
- Für sie werden beim Day-Over nur verbleibende Session-Punkte entfernt.

## Timeout

Timeout bedeutet: Tageslimit erreicht.

### Normaler Ablauf:

1. Pro Tick wird `playedToday` erhöht.
2. Sobald `remaining <= 0` ist, wird der Spieler finalisiert.
3. Die Finalisierung berechnet den aktuellen Inventarwert.
4. Dieser Wert wird als `gained-level` in `totalLevel` übernommen.
5. `sessionPoints` werden dabei auf `0` gesetzt.
6. Danach wird der Spieler mit der normalen Kick-Komponente gekickt.

### Debug-Timeout

`/debugtime timeout <Spieler>` erzwingt denselben Ablauf:

1. `playedToday` wird direkt auf das Spielerlimit gesetzt.
2. Danach laufen dieselbe Finalisierung und derselbe Kick-Pfad wie beim echten Timeout.
3. Whitelisted oder Bypass-Spieler werden dabei nicht gekickt.

## Betroffene Dateien

- `src/main/java/gg/vynofc/timeperday/manager/PlayerTimeManager.java`
- `src/main/java/gg/vynofc/timeperday/listener/PlayerListener.java`
- `src/main/java/gg/vynofc/timeperday/command/DebugTimeCommand.java`
