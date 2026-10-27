# Better Controller

Minecraft-Mod (NeoForge 21.1.265, Minecraft 1.21.1, Java 21, Mod-ID `bettercontroller`).

Verbindliche Anforderungen und Arbeitsweise stehen im Projektbriefing:

@PROJEKTBRIEFING.md

Überblick über Bedienung, Architektur und aktuellen Stand: `README.md`.

## Hinweise für Claude Code

- `logic/` und `signal/` sind reines Java (nur Gson) und dürfen keine Minecraft-, NeoForge- oder GLFW-Klassen importieren. Dort gehört alles Testbare hin.
- Client-Klassen (`client/`) nur aus Client-Code referenzieren. S2C-Handler werden in `GraphNetwork` über Lambdas registriert, damit der Dedicated Server sie nie lädt.
- Ohne Zugriff auf maven.neoforged.net kann man die Logik-Tests so ausführen: `javac` mit Gson und JUnit-Console-Standalone auf dem Classpath über `src/main/java/com/bettercontroller/{logic,signal}` und `src/test`.
