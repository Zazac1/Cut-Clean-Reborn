# CutCleanReborn — Bukkit 1.21.1

Minecraft concret : 1.21.1. Java 21. Build : `./gradlew build`.
Les sources Bukkit portables sont dans `src/` en branche autonome, ou partagées dans `../platform-bukkit/src/` dans le workspace complet. La construction utilise `build-logic/bukkit.gradle` local lorsqu'il existe, sinon le répertoire commun parent.
L'API officielle `org.bukkit:bukkit:1.21.1-R0.1-SNAPSHOT` est installée par BuildTools dans Maven local ; elle est requise pour compiler sans dépendre de Paper.

Serveur de test officiel à construire avec [Spigot BuildTools](https://www.spigotmc.org/wiki/buildtools/) (`java -jar BuildTools.jar --rev 1.21.1 --compile SPIGOT,CRAFTBUKKIT`) puis à déposer dans `run-server/bukkit-1.21.1.jar`, avec un `eula.txt` accepté après lecture de l'EULA Minecraft.
La commande `./gradlew smokeTest` utilise exclusivement `build/smoke-server`, jamais les mondes utilisateur. Pour les tests joueur, `./gradlew runServer` ouvre un serveur local authentifié sur `127.0.0.1:25595` dans `build/manual-server`, sans vérificateur automatique. Les configurations de ce test manuel sont conservées entre lancements. Ne lancer qu'une de ces variantes à la fois sur ce port.
Première compilation sans dépôt Maven préexistant : dans une branche autonome, lancer `pwsh -File tools/bootstrap-bukkit.ps1 -BuildPlugin`. Dans le workspace complet depuis ce module, utiliser `pwsh -File ../platform-bukkit/tools/bootstrap-bukkit.ps1 -ProjectDirectory . -BuildPlugin`. Le script construit l'API officielle dans `build/official-maven`, redirige Gradle vers ce dépôt privé, conserve ses journaux et n'accepte aucune EULA. Consulter le README de cet outil pour les prérequis et les commandes suivantes.

L'existence de ce projet ne prouve pas encore son état : consulter le suivi de portage et les vrais rapports de test.

