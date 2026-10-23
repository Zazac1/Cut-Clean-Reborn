# Vérification de persistance à froid

Depuis la racine :

```powershell
pwsh -File validation/run-persistence-tests.ps1
```

Le mode par défaut couvre les vingt plugins Paper/Purpur/Spigot/CraftBukkit, séquentiellement. Un contrôle intermédiaire peut utiliser `-Project Bukkit_1.21.1`. Java21/25, les serveurs officiels existants et les EULA déjà acceptées sont nécessaires ; le helper compile contre l'API minimale Bukkit1.21.1. Si celle-ci n'est pas installée, l'amorçage officiel décrit dans `platform-bukkit/tools/README.md` peut construire un dépôt privé, à fournir par `-MavenRepository <chemin>`.

Le runner construit le JAR actuel, crée exclusivement un nouveau `build/persistence-server/<essai>` dans chaque module et démarre **trois processus Java distincts**, avec PID, nonce JVM et nonce d'essai contrôlés. Le même monde neuf est sauvegardé puis rechargé deux fois ; ce n'est ni un reload de plugin ni un nouveau tracker dans la même JVM. Chaque phase doit terminer avec code0 et un journal prouvant l'arrêt et la sauvegarde complets. Les empreintes sources/JAR/serveur/helper sont contrôlées entre toutes les phases ; un changement invalide le résultat. Un timeout arrête seulement le processus de test possédé et n'écrit aucun reçu réussi.

Les objets réels sont placés dans un coffre réel. La suite vérifie les identités et snapshots PDC des outils, les enchantements initiaux conservés, l'application exactement une fois, un rafraîchissement explicite et la protection d'un outil modifié. Elle vérifie les marqueurs Timber et canne dans les chunks sauvegardés, leur suppression sans résurrection et la conservation d'autres origines. Les préférences CleanStacks par UUID sont exercées via le service de production avec une identité/inventaire synthétiques vides ; elles sont stockées dans config.yml, pas dans le PDC joueur. Configurations minerais, conversions, caps/remboursement et armure sont relues par les nouveaux services après redémarrage.

La canne repose sur de la terre non gravitaire : du sable flottant serait un mauvais décor de persistance, car une mise à jour de fluide pourrait le faire tomber normalement et casser la plante. Les assertions consignent les types exacts de chaque bloc conservé/consommé.

Exception de journal explicitement bornée à Bukkit/Spigot26.3 : le source officiel BuildTools `MinecraftServer.saveAllChunks` n'émet plus la ligne « All dimensions are saved » présente en26.2. `stopServer` appelle toujours `saveAllChunks(false,true,false)` puis ferme tous les niveaux. Pour ces deux seuls modules, le runner exige donc après « Stopping server » la désactivation des deux plugins, les sauvegardes joueurs/mondes et celles des trois dimensions, en plus de la sortie0 et des relectures dans les JVM suivantes. Les autres plateformes/versions gardent leur marqueur final de sauvegarde/I/O. Toute erreur de sauvegarde, fermeture de niveau ou déverrouillage invalide la preuve ; aucun journal n'est présenté comme dépourvu des avertissements système OSHI connus.

Une réservation de masse existante est initialisée avec son identité et un vrai objet de coffre. Après le premier redémarrage, l'objet est sorti du coffre et laissé **despawn réellement** par le serveur : le listener CCR doit recevoir la destruction native et libérer la réservation. Le dernier démarrage vérifie que ni la réservation ni l'objet détruit ne reviennent. La suite ne prétend pas valider le craft réel d'un joueur ni ses transactions.

La réflexion se limite à appeler les services/package-private existants et à lire leurs trackers, pour couvrir les packages `paper` et `bukkit` sans modifier les sources de production. Le helper reste un JAR de vérification séparé, jamais dans les JAR distribués. Les reçus JSON, rapports Markdown et journaux sont sous `validation/results/persistence/` et dans les mondes de test ; ces sorties sont ignorées par Git. L'absence de joueur réseau signifie que les menus, remboursements payés, usure d'outils et visuels restent des tests en jeu séparés.
