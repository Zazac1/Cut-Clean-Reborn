# CutCleanReborn — Purpur 26.2

Module Purpur pour Minecraft 26.2, Java 25. Les sources, la configuration, les tests et la compilation sont tous dans ce dossier. Aucun projet voisin n'est nécessaire.

```powershell
.\gradlew.bat build
.\gradlew.bat downloadServer
.\gradlew.bat smokeTest
```

Le serveur de test est réellement Purpur, pas Paper. La suite native doit réussir avant de déclarer le chargement vérifié. Les tests en jeu avec un joueur restent une étape distincte. Les preuves automatiques et les observations client restent distinctes dans le suivi global.
