# CutCleanReborn — Bukkit 1.21.11

Minecraft concret : 1.21.11. Java 21. Build : `./gradlew build`.
Les sources Bukkit portables sont dans `src/`, les outils BuildTools dans `tools/` et toute la compilation dans `build.gradle`. Ce projet se compile seul, dans le workspace comme sur sa branche de version.
L'API officielle `org.bukkit:bukkit:1.21.11-R0.2-SNAPSHOT` est installée par BuildTools dans Maven local ; elle est requise pour compiler sans dépendre de Paper.

Serveur de test officiel à construire avec [Spigot BuildTools](https://www.spigotmc.org/wiki/buildtools/) (`java -jar BuildTools.jar --rev 1.21.11 --compile SPIGOT,CRAFTBUKKIT`) puis à déposer dans `run-server/bukkit-1.21.11.jar`, avec un `eula.txt` accepté après lecture de l'EULA Minecraft.
La commande `./gradlew smokeTest` utilise exclusivement `build/smoke-server`, jamais les mondes utilisateur. Pour les tests joueur, `./gradlew runServer` ouvre un serveur local authentifié sur `127.0.0.1:25595` dans `build/manual-server`, sans vérificateur automatique. Les configurations de ce test manuel sont conservées entre lancements. Ne lancer qu'une de ces variantes à la fois sur ce port.
Première compilation sans dépôt Maven préexistant : depuis ce dossier, lancer `pwsh -File tools/bootstrap-bukkit.ps1 -BuildPlugin`. Le script construit l'API officielle dans `build/official-maven`, redirige Gradle vers ce dépôt privé, conserve ses journaux et n'accepte aucune EULA. Consulter `tools/README.md` pour les prérequis et les commandes suivantes.

L'existence de ce projet ne prouve pas encore son état : consulter le suivi de portage et les vrais rapports de test.

