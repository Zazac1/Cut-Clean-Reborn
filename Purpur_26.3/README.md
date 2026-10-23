# CutCleanReborn — Purpur 26.3

Module Purpur pour Minecraft 26.3, Java 25. Le code de gameplay, la configuration et les tests sont partagés avec `../Paper_26.3/` ; la compilation et le démarrage Purpur sont définis dans `../build-logic/purpur.gradle`.

```powershell
.\gradlew.bat build
.\gradlew.bat downloadServer
.\gradlew.bat smokeTest
```

Le serveur de test est réellement Purpur, pas Paper. La suite native doit réussir avant de déclarer le chargement vérifié. Les tests en jeu avec un joueur restent une étape distincte. Ce module n'est pas encore exporté comme branche autonome ; le suivi global enregistre les étapes restantes.
