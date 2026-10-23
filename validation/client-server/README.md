# Client Minecraft — environnement QA isolé

Ce projet n'est pas un portage ou un JAR à distribuer. Run Launcher le détecte comme « CCR QA » ; son bouton Start server exécute `runServer` et démarre le vrai serveur natif du module sélectionné, exclusivement dans `build/client-test-server/` du module.

`gradle.properties` sélectionne `target_project` (Paper/Purpur/Spigot/Bukkit et version). Le profil Modrinth « CCR QA » doit utiliser la même révision Minecraft. Adresse : `127.0.0.1:25591`. Arrêter le serveur avant de changer de cible. Le compte Minecraft connecté doit être opérateur dans ce monde de test uniquement.

Le petit plugin séparé `CCRClientQA.jar` prépare des scènes et consigne des observations avec un joueur réel. Il n'entre jamais dans les JAR CutCleanReborn distribués. Le serveur utilise l'authentification Minecraft normale et n'écoute que sur la boucle locale.
