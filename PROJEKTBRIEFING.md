# Projektbriefing: Better Controller – Minecraft-Mod

## 1. Projektziel

Entwickle und erweitere den Minecraft-Mod „Better Controller“. Der Mod ermöglicht es Spielern, über einen eigenen Controller grafische Logikschaltungen in einem Node-Editor zu erstellen und damit Eingaben und später echte Redstone-Ausgänge zu steuern.

Das Konzept orientiert sich an visuellen Programmiersystemen wie Unreal Engine Blueprints. Spieler sollen Logikschaltungen ohne Java-Code erstellen können.

Der Mod soll langfristig mit Create und gegebenenfalls Create: Aeronautics zusammenarbeiten können. Eine solche Integration darf aber erst als implementiert gelten, wenn sie technisch tatsächlich umgesetzt und getestet wurde.

## 2. Entwicklungsumgebung

* Minecraft Java Edition 1.21.1
* NeoForge 21.1.265
* Java 21
* Entwicklungsumgebung: Visual Studio Code
* Mod-ID: `bettercontroller`
* Graph-Dateiendung: `.bcgraph`
* Modsprache: Java

Vorhandene Projektdateien müssen zunächst untersucht werden. Bestehende Implementierungen sollen erweitert und nicht unnötig ersetzt werden.

Wichtige vorhandene Klassen sind unter anderem:

* `LogicGraph.java`
* `LogicEditorScreen.java`
* `BlockLibraryScreen.java`
* `GraphManagerScreen.java`
* `WorldGraphData.java`
* `GraphNetwork.java`
* `GraphJsonCodec.java`
* `GraphFileStore.java`

Die genauen Pfade und Klassennamen sind vor Änderungen im Repository zu überprüfen.

## 3. Controller-Item

Der Mod besitzt ein eigenes Controller-Item.

Funktion:

* Der Spieler muss den Controller in der Haupt- oder Nebenhand halten, um den Logic Editor öffnen zu können.
* Die Tastenkombination `Shift+C` öffnet den Editor, sofern die Voraussetzung erfüllt ist.
* Ohne Controller in der Hand darf der Editor nicht geöffnet werden.
* Doppelte Screen-Öffnungen sollen verhindert werden.
* Das Item muss korrekt über NeoForge registriert sein und nach einem Neustart weiterhin verfügbar sein.

Ein eigenes Crafting-Rezept und eine individuelle Itemtextur können ergänzt werden, falls sie noch nicht existieren.

## 4. Grafischer Node-Editor

Der Logic Editor ist eine visuelle Arbeitsfläche mit einem Raster und miteinander verbindbaren Nodes.

### Arbeitsfläche

* Nodes frei platzieren und verschieben.
* Nodes durch Ports verbinden.
* Verbindungen zwischen Nodes visuell darstellen.
* Nodes per Rechtsklick löschen.
* Zoom mit dem Mausrad.
* Arbeitsfläche verschieben, beispielsweise mit gedrückter mittlerer Maustaste.
* Mauskoordinaten korrekt zwischen Bildschirm und Canvas umrechnen.
* Node-Positionen bleiben beim Zoomen und Verschieben unverändert gespeichert.
* Zoom und Canvas-Verschiebung dürfen die GUI nicht unscharf machen.
* GUI-Buttons und Dialoge müssen unabhängig vom Canvas-Zoom bedienbar bleiben.

### Bedienung

* `Shift+C`: Editor öffnen, wenn der Controller gehalten wird.
* `Shift+Space`: Node-Bibliothek öffnen, sofern dies der aktuellen Implementierung entspricht.
* Eine Ansicht-zurücksetzen-Funktion kann angeboten werden.
* Die vorhandene Tastenauswahl und die bisherigen Editorfunktionen müssen erhalten bleiben.

### Rendering

* Scharfe Texte und Symbole.
* Keine unnötigen niedrig aufgelösten Zwischentexturen.
* Keine versehentliche Skalierung des gesamten Minecraft-Menüs.
* Korrekte Render- und Maus-Event-APIs für Minecraft 1.21.1 und NeoForge 21.1.265.

Die Unschärfe des Editors wurde zuvor behoben. Änderungen am Rendering müssen diese Korrektur erhalten.

## 5. Node-Bibliothek

Die Block Library ist eine scrollbare Liste verfügbarer Node-Typen.

Funktionen:

* Node-Typen auswählen.
* Mit dem Mausrad vertikal scrollen.
* Auch bei langen Listen alle Einträge erreichen.
* Scrollen an Anfang und Ende begrenzen.
* Klicks nach dem Scrollen müssen den tatsächlich angezeigten Eintrag auswählen.
* Buttons außerhalb der Liste müssen weiterhin funktionieren.

Das Scrollen der Block Library wurde bereits korrigiert und soll erhalten bleiben.

## 6. Logik-Nodes

Das bestehende Graphmodell enthält folgende Node-Typen:

### Keyboard Input

* Erfasst den Zustand einer konfigurierten Tastatureingabe.
* Jeder Keyboard-Input-Node kann eine eigene Taste erhalten.
* Beim Erstellen ist B die Standardtaste, sofern keine andere Standardbelegung festgelegt wurde.
* Die Taste wird über einen Eingabemodus geändert: Der Spieler wählt „Taste ändern“ und drückt anschließend die gewünschte Taste.
* ESC bricht die Auswahl ab.
* Geeignete Tasten sind Buchstaben, Zahlen, Funktionstasten, Pfeiltasten, Leertaste und weitere von Minecraft unterstützte Tastaturtasten.
* Serverseitige Modellklassen dürfen keine Client-only- oder GLFW-Abhängigkeiten besitzen.
* Tastenzuweisungen müssen gespeichert, geladen, exportiert und importiert werden können.

Die tatsächliche Erkennung von Tastendrücken ist clientseitig umzusetzen. Der Server darf den resultierenden Zustand nicht blind aus unvalidierten Clientdaten übernehmen.

### AND

Der Ausgang ist aktiv, wenn alle Eingänge aktiv sind.

### OR

Der Ausgang ist aktiv, wenn mindestens ein Eingang aktiv ist.

### NOT

Kehrt den Eingangszustand um.

### NAND

Kehrt das Ergebnis eines AND-Gatters um.

### NOR

Kehrt das Ergebnis eines OR-Gatters um.

### XOR

Ist bei zwei unterschiedlichen Eingangszuständen aktiv.

### Redstone Output

* Repräsentiert einen Logikausgang.
* Soll den berechneten Zustand einer Schaltung bereitstellen.
* Die tatsächliche Verbindung zu Minecraft-Redstone ist noch zu überprüfen beziehungsweise umzusetzen.
* Ein nur in der GUI dargestellter AN/AUS-Zustand gilt nicht als echte Redstone-Integration.

Die Auswertung muss bei gültigen Verbindungen reproduzierbar und konsistent sein. Ungültige Verbindungen, unbekannte Node-Typen und fehlerhafte Eingabedaten müssen sicher behandelt werden.

## 7. Verbindungen und Graphmodell

Ein Graph besteht aus:

* einer Graph-ID,
* Node-IDs,
* Node-Typen,
* Node-Positionen,
* Node-Einstellungen,
* Verbindungen mit expliziten Quell- und Zielports.

Anforderungen:

* Node-IDs müssen eindeutig und innerhalb definierter Grenzen liegen.
* Verbindungen müssen auf existierende Nodes und gültige Ports verweisen.
* Ungültige oder doppelte Verbindungen müssen entsprechend der festgelegten Regeln behandelt werden.
* Node-Positionen und Einstellungen dürfen beim Speichern oder Laden nicht verloren gehen.
* Alle Änderungen müssen mit der bestehenden Graphverwaltung kompatibel bleiben.

## 8. Speichern, Laden, Import und Export

Graphen sollen dauerhaft gespeichert und weltbezogen verwaltet werden.

### Speicherung

* Serverseitige Speicherung über Minecraft `SavedData`.
* Speicherung im DataStorage der jeweiligen Serverwelt, derzeit über die Overworld-Verwaltung.
* Änderungen müssen als geändert markiert und zuverlässig persistiert werden.
* Nach einem Weltneustart müssen Graphen und ihre Einstellungen wieder verfügbar sein.
* Daten verschiedener Welten dürfen nicht ungewollt vermischt werden.

### Export

* Export eines Graphen in eine `.bcgraph`-Datei.
* Standardverzeichnis: `.minecraft/bettercontroller/graphs`.
* Export enthält Graph-Version, IDs, Node-Typen, Positionen, Einstellungen und Verbindungen.
* Das Format soll versioniert und erweiterbar sein.

### Import

* Import einer `.bcgraph`-Datei.
* Vollständige Validierung vor der Übernahme.
* Prüfung von Formatversion, Dateigröße, JSON-Tiefe, IDs, Node-Anzahl, Verbindungen, Ports und Einstellungswerten.
* Dateizugriff auf den vorgesehenen Ordner beschränken.
* Keine Java-native Deserialisierung oder Ausführung importierter Inhalte.
* Ein fehlgeschlagener Import darf den vorhandenen Graphen nicht teilweise verändern.
* Importierte Daten müssen serverseitig erneut validiert werden.

Graphdaten sollen nach Möglichkeit rückwärtskompatibel bleiben. Neue Einstellungen müssen für ältere Graphdateien sinnvolle Standardwerte erhalten.

## 9. Multiplayer und Netzwerk

Der Server ist maßgeblich für:

* Graphdaten,
* Berechtigungen,
* gespeicherte Einstellungen,
* spätere Frequenzzuordnungen,
* tatsächliche Redstone-Zustände.

Anforderungen:

* Verwendung der korrekten NeoForge-Payload- und Netzwerk-APIs für Version 1.21.1.
* Clientdaten serverseitig validieren.
* Besitzerrechte und Operator-Berechtigungen beachten.
* Graphänderungen nur an berechtigte Spieler synchronisieren.
* Keine unkontrollierten Netzwerkupdates oder unnötigen Updates pro Frame.
* Client-only-Klassen nicht in serverseitig geladenen Modellklassen verwenden.
* Veraltete Graphzustände dürfen nach Löschen oder Entfernen eines Graphen nicht weiterwirken.

Weltneustart, Weltisolation, Berechtigungen und Dedicated-Multiplayer müssen zusätzlich im Spiel getestet werden.

## 10. Zukünftiges Frequenzsystem

Geplant ist ein eigenes drahtloses Frequenzsystem. Dieses ist getrennt von der bisherigen Graphverwaltung zu entwickeln.

### Frequenzen pro Ausgangs-Node

* Jeder Redstone-Output-Node erhält seine eigene einstellbare Frequenz.
* Vorgesehener Frequenzbereich: 0 bis 999999.
* Die Frequenz wird in den Node-Einstellungen im Editor geändert.
* Unterschiedliche Ausgangs-Nodes können unterschiedliche Frequenzen verwenden.
* Mehrere Nodes dürfen dieselbe Frequenz verwenden, sofern eine deterministische Regel definiert wird.
* Frequenzen müssen in Graphdaten, Speicherung, Export und Import erhalten bleiben.

### Signalverwaltung

* Serverseitige Verwaltung der aktiven Ausgangszustände.
* Zuordnung über Graph-ID und Ausgangs-ID.
* Zustandsänderungen sollen zuverlässig weitergegeben werden.
* Veraltete Signale nach Löschen oder Entfernen eines Graphen müssen entfernt werden.
* Die Verwaltung muss weltbezogen bleiben.
* Das Verhalten bei gleichen Frequenzen muss eindeutig dokumentiert sein.

### Abgrenzung

Ein eigener platzierbarer Empfängerblock mit konfigurierbarer Frequenz und echter Redstone-Ausgabe ist ausdrücklich nicht Teil des aktuellen Auftrags. Er darf nicht als bereits implementiert vorausgesetzt werden.

Auch eine echte Create- oder Create: Aeronautics-Integration ist noch nicht umgesetzt.

## 11. Tests und Qualität

Nach Änderungen sollen, soweit passend, folgende Befehle ausgeführt werden:

`.\gradlew.bat test`

`.\gradlew.bat build`

Bestehende automatisierte Tests dürfen nicht unnötig entfernt oder abgeschwächt werden.

Sinnvolle Testbereiche:

* Logikgatter und Eingaben.
* Tastenzuweisungen und ungültige Tastencodes.
* Graphverbindungen und Portvalidierung.
* Graph-JSON-Codec.
* Import- und Exportvalidierung.
* Node-IDs und Grenzwerte.
* Speicherung und Laden.
* Frequenzvalidierung und Zuordnung, sobald implementiert.
* Netzwerkvalidierung und Berechtigungen, soweit automatisierbar.

Ein erfolgreicher Build beweist nicht, dass das Verhalten im Spiel korrekt ist. Manuelle Tests müssen separat dokumentiert werden.

## 12. Aktueller Projektstand

Bei der letzten bekannten Prüfung:

* `.\gradlew.bat test` war erfolgreich.
* `.\gradlew.bat build` war erfolgreich.
* Sieben automatisierte Tests wurden als erfolgreich gemeldet.
* Die IDE-Diagnose meldete keine Probleme.
* Der Editor war nicht mehr unscharf.
* Die Node-Bibliothek konnte scrollen.
* Die freie Tastenauswahl funktionierte.
* Das Controller-Item war implementiert und das Öffnen des Editors an das Halten des Items gekoppelt.
* Graphspeicherung, Import und Export waren implementiert.

Diese Angaben beschreiben den zuletzt gemeldeten Stand. Claude Code soll das Repository selbst untersuchen und prüfen, ob die Implementierungen tatsächlich vorhanden sind.

## 13. Arbeitsweise für Claude Code

1. Repository und vorhandene Implementierungen zuerst untersuchen.
2. Keine Funktionen neu schreiben, bevor die bestehende Implementierung verstanden wurde.
3. Vorhandene Architektur erweitern, statt konkurrierende Systeme einzuführen.
4. Änderungen in kleinen, nachvollziehbaren Schritten durchführen.
5. Korrekte APIs für Minecraft 1.21.1 und NeoForge 21.1.265 verwenden.
6. Nach jeder sinnvollen Änderung Tests und Build ausführen.
7. Geänderte Dateien und technische Entscheidungen dokumentieren.
8. Zwischen bereits implementierten, automatisiert getesteten und manuell getesteten Funktionen unterscheiden.
9. Keine Create-Integration oder funktionierende Redstone-Ausgabe behaupten, solange sie nicht tatsächlich umgesetzt und geprüft wurde.
10. Vorhandene GUI-, Speicher-, Import-/Export- und Netzwerkfunktionen bei allen Änderungen erhalten.

Das Ziel ist ein stabiler, erweiterbarer Minecraft-Mod, bei dem Spieler über einen Controller visuelle Logikschaltungen erstellen und diese langfristig mit echten Minecraft-Funktionen verbinden können.
