# Client Minecraft — environnement QA isolé

Ce projet est un outil de test interne, pas une variante Paper ou un JAR à distribuer. Son lanceur Gradle se nomme `qa-gradlew.bat` (`qa-gradlew` sous Unix) pour que le Dev Launcher affiche seulement les 25 véritables variantes. `./qa-gradlew.bat runServer` démarre le vrai serveur natif du module sélectionné, exclusivement dans `build/client-test-server/` du module.

`gradle.properties` sélectionne `target_project` (Paper/Purpur/Spigot/Bukkit et version). Le profil Modrinth « CCR QA » doit utiliser la même révision Minecraft. Adresse : `127.0.0.1:25591`. Arrêter le serveur avant de changer de cible. Le compte Minecraft connecté doit être opérateur dans ce monde de test uniquement.

Le petit plugin séparé `CCRClientQA.jar` prépare des scènes et consigne des observations avec un joueur réel. Il n'entre jamais dans les JAR CutCleanReborn distribués. Le serveur utilise l'authentification Minecraft normale et n'écoute que sur la boucle locale.

Le helper refuse de se charger hors d'un dossier `<Paper|Purpur|Spigot|Bukkit>_<version>/build/client-test-server`. Les opérations de préparation vérifient aussi le chemin réel du seul monde `client-qa-world` : les autres mondes, y compris les dimensions secondaires, sont refusés. Un nom de scène inconnu ne change plus rien. Ranger normalement toute masse QA dans un coffre avant `/ccrqa setup` ou `scene` : ces commandes refusent désormais d'effacer une masse présente dans l'inventaire ou sur le curseur, pour ne pas fausser la preuve de réservation/destruction.

`./qa-gradlew.bat test jar` compile le helper et vérifie ses gardes de chemins/noms sans démarrer Minecraft. Ces tests ne remplacent pas l'essai natif futur des refus de préparation.

Le fichier facultatif `player.local.json`, ignoré par Git, contient les champs `name` et `uuid` du compte réellement utilisé. Il prépare uniquement la liste blanche et les droits de ce nouveau serveur QA. Ne pas copier les identifiants d'authentification du launcher ni modifier les listes d'un serveur existant.

`/ccrqa setup` remet en place la scène et le joueur de test (créatif, invulnérable, niveau50). `/ccrqa scene timber|ore|cane|mob|stacks|equipment` fournit les blocs et outils d'un scénario en survie. Ces commandes effacent l'inventaire et la petite zone du **monde QA uniquement**. `/ccrqa observe` consigne l'état réel sans déclarer de réussite automatique. Les observations et ouvertures/fermetures des menus figurent dans `client-qa-observations.log` ; un résultat manuel exige toujours une interaction et une vérification visuelle réelles.

La scène `equipment` fournit une enclume, une table de fabrication, une épée, un livre Sharpness V, quatre pièces d'armure et les composants de deux masses, avec50niveaux. Elle ne crée pas de règles, n'équipe rien, ne fabrique rien et n'effectue aucune transaction d'enclume. Configurer les règles dans `/ccr`, puis manipuler les écrans natifs avec le joueur connecté ; observer les niveaux avant/après et les vrais objets. Le livre permet de tester un cap ciblant les épées sans plafonner son entrée avant l'opération. Haste reste une commodité des fixtures, pas une preuve du ressenti de minage ordinaire.
