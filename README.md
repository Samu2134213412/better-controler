# Better Controller

Minecraft-Mod (Java Edition 1.21.1, NeoForge 21.1.265, Java 21): Mit einem Controller baut man in einem grafischen Node-Editor Logikschaltungen aus Tastatureingaben und Logikgattern. Die Ergebnisse werden auf Funkfrequenzen gesendet.

## Bauen und Starten

```
gradlew.bat test        # Unit-Tests der Logik
gradlew.bat build       # Mod-JAR in build/libs/
gradlew.bat runClient   # Testclient starten
gradlew.bat runServer   # Dedicated Server starten (eula in run/ bestätigen)
```

In VS Code: Ordner öffnen, die Java-Erweiterungen importieren das Gradle-Projekt automatisch.

## Bedienung

| Aktion | Steuerung |
|---|---|
| Editor öffnen | Controller in Haupt- oder Nebenhand halten, **Shift+C** (in den Steuerungsoptionen änderbar) |
| Node-Bibliothek | **Shift+Space** oder Button „+ Node" |
| Node verschieben | Linksklick auf Node und ziehen |
| Verbinden | Vom Ausgang (rechts) auf einen Eingang (links) ziehen |
| Verbindung lösen/umhängen | Eingang anklicken und ziehen bzw. loslassen |
| Node löschen | Rechtsklick auf Node oder Entf |
| Zoomen | Mausrad (50–200 %, Zoom auf den Mauszeiger) |
| Arbeitsfläche verschieben | Mittlere Maustaste oder Linksklick auf leere Fläche und ziehen |
| Ansicht zurücksetzen | Button „Ansicht" |
| Taste eines Keyboard-Nodes ändern | Node anklicken, „Taste ändern", gewünschte Taste drücken (ESC bricht ab) |
| Frequenz eines Ausgangs | Node anklicken, Zahl 0–999999 eingeben |
| Speichern | „Speichern": speichert in der aktuellen Welt (serverseitig) |
| Export | „Export": schreibt `.minecraft/bettercontroller/graphs/<Name>.bcgraph` |
| Import / Öffnen / Löschen | „Graphen": Liste der Welt-Graphen, Umschalten auf Dateien zum Import |

Der Controller wird mit Redstone, Quarz und Eisenbarren gecraftet (Rezeptbuch) und liegt im Kreativ-Tab „Werkzeuge".

Die Nodes im Editor zeigen eine Live-Vorschau: Grüner Kopf bzw. rote Leitung bedeutet „an", berechnet mit den gerade gedrückten Tasten.

## Funktionsweise

- **Nodes:** Keyboard Input (Standardtaste B), AND, OR, NOT, NAND, NOR, XOR (je 2 Eingänge, NOT 1) und Redstone Output.
- **Verbindungsregeln:** Ein Ausgang kann beliebig viele Eingänge speisen, ein Eingang hat höchstens eine Verbindung (eine neue ersetzt die alte). Schleifen werden abgelehnt, die Auswertung ist dadurch immer eindeutig. Unverbundene Eingänge gelten als „aus".
- **Ausführung:** Gespeicherte Graphen laufen auf dem Server dauerhaft. Keyboard-Nodes lesen die Tasten des Graph-Besitzers, aber nur, solange er online ist und einen Controller hält. Der Client meldet nur Tasten, die in seinen Graphen vorkommen, und nur bei Änderungen. Der Server verwirft ungültige oder nicht belegte Tasten.
- **Frequenzen:** Jeder Redstone-Output sendet auf seiner Frequenz (0–999999). Regel bei gleicher Frequenz: Die Frequenz ist **an, solange mindestens ein Ausgang auf ihr an ist** (ODER). Das ist unabhängig von der Reihenfolge. Signale eines gelöschten Graphen oder entfernter Nodes verschwinden sofort. Der Zustand gilt pro Welt und wird beim Serverstart aus den gespeicherten Graphen neu berechnet.
- **Abfrage:** `/bettercontroller signal <frequenz>` und `/bettercontroller signals` zeigen den Signalzustand. Bis es einen Empfängerblock gibt, ist das der Weg, Schaltungen im Spiel zu prüfen.
- **Rechte:** Jeder Spieler sieht und bearbeitet nur seine eigenen Graphen (maximal 32 pro Welt). Operatoren (Level 2) sehen und bearbeiten alle.
- **Speicherung:** `<welt>/data/bettercontroller_graphs.dat` (SavedData der Overworld), also getrennt pro Welt. Einträge, die beim Laden ungültig sind, werden unverändert behalten und nicht gelöscht.
- **Dateiformat `.bcgraph`:** versioniertes JSON (`"format": "bettercontroller.graph", "version": 1`). Unbekannte Felder werden ignoriert, fehlende Einstellungen bekommen Standardwerte. Beim Import werden geprüft: Größe (48 KB), Verschachtelungstiefe, Version, IDs (1–99999), Node-Anzahl (128), Verbindungen (256), Ports, Tasten, Frequenzen, Koordinaten und Schleifenfreiheit. Ein fehlerhafter Import ändert nichts. Importierte Graphen bekommen eine neue ID und werden beim Speichern vom Server erneut geprüft. Zugriff nur auf einfache Dateinamen in `bettercontroller/graphs`.

## Projektstruktur

```
src/main/java/com/bettercontroller/
  logic/    reines Java ohne Minecraft: LogicGraph, NodeType, GraphEvaluator, GraphJsonCodec, GraphFileStore, KeyCodes, GraphLimits
  signal/   FrequencyRegistry (Frequenzzustände, reines Java)
  server/   WorldGraphData (SavedData), ServerGraphService (Pakete, Rechte, Auswertung), ModCommands
  network/  GraphPayloads, GraphNetwork (NeoForge-Payload-API)
  item/     ModItems (Controller)
  client/   BetterControllerClient (Tastenkürzel), ClientGraphState, ClientPayloadHandler
  client/screen/  LogicEditorScreen, BlockLibraryScreen, GraphManagerScreen
```

## Stand

| Bereich | Status |
|---|---|
| Logikgatter, Graphregeln, Auswertung, JSON-Codec, Import/Export-Validierung, Dateizugriff, Frequenzregeln | implementiert, **25 Unit-Tests grün** |
| Controller-Item, Shift+C, Editor, Bibliothek, Graph-Verwaltung, Speicherung, Netzwerk, Rechte, Befehle | implementiert, **noch nie kompiliert oder im Spiel getestet** (siehe unten) |
| Empfängerblock mit echter Redstone-Ausgabe | **nicht implementiert** (bewusst nicht Teil dieses Auftrags) |
| Create / Create: Aeronautics | **nicht implementiert** |

Der Minecraft-abhängige Teil wurde in einer Umgebung ohne Zugriff auf die NeoForge- und Mojang-Server geschrieben. Er wurde gegen die APIs von 1.21.1/NeoForge 21.1 geschrieben, aber noch nicht gebaut. Der erste Schritt ist `gradlew.bat build`. Kompilierfehler bitte so wie sie sind zurückmelden.

## Manuelle Tests (offen)

- [ ] `gradlew.bat build` erfolgreich
- [ ] Controller im Kreativmenü und per Rezept, bleibt nach Neustart erhalten
- [ ] Shift+C öffnet den Editor nur mit Controller in der Hand, nicht doppelt
- [ ] Nodes platzieren, verschieben, verbinden, löschen; Zoom und Verschieben; Text bleibt scharf
- [ ] Bibliothek scrollt (kleines Fenster bzw. hohe GUI-Skalierung) und Klicks treffen den sichtbaren Eintrag
- [ ] Taste ändern (inkl. F-Tasten, Pfeile, Leertaste), ESC bricht ab
- [ ] Speichern, Welt neu laden: Graph und Einstellungen sind noch da; andere Welt: Graph nicht sichtbar
- [ ] Export, Datei verändern bzw. kaputt machen, Import: Fehlermeldung, nichts verändert
- [ ] Taste mit Controller in der Hand halten: `/bettercontroller signal <f>` zeigt ON; Controller weglegen: OFF
- [ ] Zwei Ausgänge auf derselben Frequenz: ON, solange einer an ist; Graph löschen: Signal weg
- [ ] Dedicated Server mit zwei Spielern: fremde Graphen nicht sichtbar; Operator sieht alle
