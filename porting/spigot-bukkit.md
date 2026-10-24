# Portage Spigot / CraftBukkit

## Architecture et limites des preuves

Les dix modules `<Spigot|Bukkit>_<Minecraft>` utilisent `platform-bukkit/src` et `build-logic/bukkit.gradle`. Les 48 classes de référence Paper ont été reprises intégralement, puis adaptées sans dépendance à Paper, Adventure ou Spigot. La compilation contre **org.bukkit:bukkit** garantit le périmètre minimal ; les démarrages sur Spigot et sur CraftBukkit sont vérifiés séparément.

Pour les branches autonomes, matérialiser `platform-bukkit/src` en `src` et `build-logic/bukkit.gradle` en `build-logic/bukkit.gradle`. Les fichiers de construction détectent ces chemins locaux. Les tests natifs ne sont jamais empaquetés dans le JAR final.

Adaptations spécifiques :

- Métadonnées/version du plugin via JavaPlugin#getDescription.
- Inventaires, titres, descriptions et enclume via API Bukkit ; les noms de créatures gardent une composante traduisible grâce à la sérialisation native ItemMeta.
- Action bar native CraftBukkit sans Adventure : résolution contrôlée du constructeur de paquet et du canal joueur au chargement. L'absence d'une API attendue bloque visiblement l'activation plutôt que supprimer les indications de progression.
- Compteur de ticks local pour l'anti-double-clic.
- Règle vanilla des drops : Bukkit1.21.11 remplace `DO_TILE_DROPS` par le registre `block_drops`. Le résolveur choisit la bonne règle réelle selon la version, et trois assertions natives vérifient l'enregistrement ainsi que les valeurs vrai/faux. La canne tombant après suppression de son support respecte cette règle.
- Bukkit ne fournit pas les causes de fermeture des inventaires. Les fermetures CCR explicites (Close, reload, arrêt, validation) sont marquées, tandis que la fermeture utilisateur revient d'un niveau. Un changement de vue, une déconnexion, un décès ou une permission retirée empêchent la réouverture différée. Les fermetures externes forcées ne peuvent pas être distinguées absolument d'Échap avec l'API Bukkit ; aucune dépendance Paper n'est ajoutée pour masquer cette limite.
- Masse unique : mêmes causes EntityRemoveEvent et même identité persistante ; ni pickup ni déchargement de chunk ne libère la masse.

## Serveurs officiels

BuildTools officiel : https://www.spigotmc.org/wiki/buildtools/ ; outil téléchargé depuis https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar .

SHA-256 outil : `81ed06f5ce5fb705b558bf9b3cc2c5c53cdd9742406c5338c452faa13a1e1c24`.

Les sources, outils et serveurs générés sont isolés dans `tools/buildtools/<Minecraft>/` (ignoré par Git). Les artefacts de serveur ne sont pas redistribués dans le dépôt. BuildTools installe son API Bukkit dans le dépôt Maven local utilisé par Gradle.

Commande officielle : `java -Xms256M -Xmx2G -jar ../BuildTools.jar --rev <Minecraft> --compile SPIGOT,CRAFTBUKKIT` ; Java21 pour 1.21.x, Java25 pour 26.x. Les binaires vont dans le **nouveau** `run-server/<bukkit|spigot>-<Minecraft>.jar`, sans remplacer les configurations ou mondes utilisateur.

## Vérification native

The unfiltered Full25 run finished on 24 October 2026 at 20:07 Paris time with no failures, including the blocks-only Clean Stacks scope and menu layout changes. The ten variants below also passed matching three-JVM cold checks (107 storage assertions each) in attempt `20261024-174352-e4c3917d`. These are native checks; actual player traversals remain pending.

| Minecraft | Bukkit compilation/unitaires | Bukkit natif | Spigot compilation/unitaires | Spigot natif | Joueur réel |
| --- | --- | --- | --- | --- | --- |
| 1.21.1 | Réussis, 9 tests | 271 contrôles réussis, arrêt propre | Réussis, 9 tests | 271 contrôles réussis, arrêt propre | Pas encore |
| 1.21.11 | Réussis, 9 tests | 279 contrôles réussis, arrêt propre | Réussis, 9 tests | 279 contrôles réussis, arrêt propre | Pas encore |
| 26.1.2 (famille26.1.x) | Réussis, 9 tests | 279 contrôles réussis, arrêt propre | Réussis, 9 tests | 279 contrôles réussis, arrêt propre | Pas encore |
| 26.2 | Réussis, 9 tests | 280 contrôles réussis, arrêt propre | Réussis, 9 tests | 280 contrôles réussis, arrêt propre | Pas encore |
| 26.3 | Réussis, 9 tests | 280 contrôles réussis, arrêt propre | Réussis, 9 tests | 280 contrôles réussis, arrêt propre | Pas encore |

Les contrôles natifs utilisent vrais registres/objets/mondes/listeners/ordonnanceur, mais un joueur synthétique pour la navigation. Ils ne prouvent pas une validation visuelle ou un parcours joueur réseau. Les journaux restent dans `<module>/build/smoke-server/logs/latest.log`, le verdict dans `smoke-result.txt` et les tests unitaires dans `build/test-results/test`.

Première erreur corrigée : les serveurs modernes BuildTools sont des bootstraps dont la classe d'entrée est `org.bukkit.craftbukkit.bootstrap.Main`, et non `org.bukkit.craftbukkit.Main` directement.

Révision API1.21.11 officielle : `1.21.11-R0.2-SNAPSHOT` (et non R0.1). Les projets utilisent la révision réellement construite, sans forcer une compatibilité seulement déclarative.

Empreintes locales des serveurs construits officiellement, pas des JAR du plugin :

| Minecraft | CraftBukkit SHA-256 | Spigot SHA-256 |
| --- | --- | --- |
| 1.21.1 | a79a8a472adc07bcae8bb1e4014c4d759faf53b355646419d7952ac30f224208 | b1423e5b10f6f222d4cba429a62e8ae2a22c259cfb0c12b7de06810fa096f9d1 |
| 1.21.11 | 7735c18b8c3329b652ccb21a924242bdce207eda5173d36cf80dd451e438e122 | da8b3f6e150d2da80e8a25a230c0344494a36001aa6ed2e8e527dfae3c58db5f |
| 26.1.2 | 2be7f4ae6ea17bbb13c3aedb28a47297c5cdd579fac812c3df656bcf7c2b9cd5 | 67ad88a82570f51516faa7b0d19b24079aca8ae85da488a408ea464a26767fed |
| 26.2 | 89fcd4c58b167ba3f8a2b23db688df91c461eb0e9d1a0512e2b89130dc43da9f | a880aea1313cc2c29d67a448cba37bb7314195d3c602519374ada35efc0ff4bb |
| 26.3 | c7a1ddb40608f22425aa0722c300c029b92532ec2445751b49e4784470f5ce1f | e0b82659eab415f49a40de3444b1f0e12fa919bdebb3171d985d4db101ddb089 |

## Amorçage d'une machine sans Maven préexistant

Le script `platform-bukkit/tools/bootstrap-bukkit.ps1` est à exporter en `tools/bootstrap-bukkit.ps1` avec son README. Il télécharge BuildTools201 officiel avec vérification SHA-256, reconstruit les dépendances et utilise `build/official-maven` au lieu du `~/.m2` utilisateur. Avec `-BuildPlugin`, Gradle utilise ce dépôt privé et un cache neuf sous `build/official-gradle`. Le script ne redistribue rien, ne lance aucun serveur et n'accepte aucune EULA.

Une copie autonome Bukkit1.21.1 a passé l'amorçage officiel complet sous `build/standalone-bootstrap-bukkit-1.21.1-20261024` ; le dépôt Maven privé et le cache Gradle étaient tous deux absents avant l'exécution réussie initiale. BuildTools a reconstruit API et serveurs, puis le projet autonome a compilé et passé ses neuf tests unitaires. Le contrôle Gradle `verifyPrivateBukkitRepository` a confirmé le chemin exact du JAR Bukkit résolu dans ce dépôt privé. Une seconde exécution intégrée du script final a terminé code0 le 23 octobre2026 à22:41:21UTC ; son reçu est `build/official-buildtools/bootstrap-result.json` dans cette copie. Aucun serveur n'a été lancé, aucune EULA acceptée, aucune dépendance au Maven utilisateur n'a été nécessaire.

La première tentative d'amorçage s'est arrêtée avant compilation parce que Windows ne pouvait pas exécuter l'extracteur PortableGit dans un chemin trop long. Le script utilise maintenant un cache source temporaire court, propre au checkout, tout en gardant API/cache Gradle/logs sous `build/`. Avant la reprise, les deux dépôts privés contenaient encore zéro fichier. Aucun paramètre système ou registre n'a été modifié pour contourner cette limite.

Les dix variantes natives passent les mêmes vérifications de commandes, menus, PDC, enchantements, conversions et masse unique que la référence Paper, avec quatre assertions supplémentaires (traduction du nom d'entité et compatibilité des règles de drops). Cela reste distinct des tests joueur réseau, de la durabilité réellement consommée, des remboursements de transactions payées et d'un vrai redémarrage à froid. Le serveur peut signaler OSHI/Perflib Windows sur26.x : aucune modification du registre utilisateur n'a été effectuée pour ce diagnostic système.

## Audit de parité et vérifications encore nécessaires

Comparaison réalisée avec les sources actuelles Paper1.21.1, après normalisation du package et des fins de ligne : **41 classes principales sur48 sont identiques**. Les sept adaptations sont `AnvilTextInput`, `BetterSugarCaneService`, `CutCleanCommands`, `CutCleanRebornPlugin`, `MenuSupport`, `StuffRulesService` et `TreeCutterService`. `BukkitCompat` est la seule classe supplémentaire. Les algorithmes de butin, Fortune, Looting, enchantements automatiques, conversion et suivi de blocs placés n'ont pas été réécrits pour la plateforme.

Dans StuffRules, les changements fonctionnels sont limités à l'édition explicite ItemMeta, l'envoi de la barre d'action et la diffusion Bukkit ; les calculs de caps/remboursements, règles d'armure et réservations de masse restent ceux de référence. Dans Timber, seule la présentation de progression change d'API. Toutes les fermetures programmatiques de menus avec un parent utilisent le garde Bukkit ; la sortie directe du menu racine n'a pas de parent à rouvrir.

La suite native n'établit pas à elle seule ces scénarios, même si la logique source est conservée :

- Saisie enclume avec un vrai client : entrée invalide puis valide, bouton de validation, coût effectif nul, fermeture après validation, Échap annulant sans sauvegarde. Le test actuel attache une vue synthétique et vérifie surtout son annulation.
- Enchantement/enclume réellement payé : remboursement plafonné au coût retiré, pas de remboursement en créatif/annulation, et shift-click du résultat soumis au cap.
- Craft réel de masse : réservation concurrente de deux joueurs, transfert de l'identité de résultat et rejet du shift-click.
- Arbre réellement cassé : usure de l'outil, protection annulant un bloc intermédiaire, arrêt en quittant le monde/déconnexion ; les indications d'action-bar doivent également être observées en client.
- Drops de canne/minerais en monde, causes de support/joueur et Silk Touch/Fortune/XP ; les règles de drops sont résolues et contrôlées nativement mais ce n'est pas une reproduction de toute la physique joueur.
- La suite native seule ne constitue pas un arrêt/redémarrage à froid des origines PDC, préférences et masse unique. Le vérificateur indépendant ci-dessous apporte désormais cette preuve de stockage, distincte d'une reconnexion ou d'une interaction joueur réseau.

Ces lacunes sont des limites de couverture, pas une suppression de fonctionnalité ni une déclaration de tests joueur réussis.

## Vérificateur indépendant de redémarrage à froid

`validation/run-persistence-tests.ps1` et `validation/persistence/` ajoutent une suite de stockage distincte, compilée contre Bukkit1.21.1 et compatible avec les packages Paper et Bukkit. Aucune source de production n'a été modifiée pour rendre les tests possibles. Le premier contrôle ciblé Bukkit1.21.1 a passé103 assertions sur trois vraies JVM (15+46+42), avec deux arrêts/redémarrages complets ; reçu `validation/results/persistence/20261023-224430-f42c3067/summary.md`.

La suite utilise un vrai coffre contenant les outils enchantés et la masse, de vrais ItemStack/PDC, les vrais trackers des services pour les marqueurs de chunks et les caches de configuration reconstruits au démarrage. Elle vérifie la conservation des identités, snapshots d'enchantements, modifications du joueur, générations de rafraîchissement, conversions, caps, armure et préférences CleanStacks. Ces préférences sont dans config.yml par UUID, pas dans le PDC joueur ; une identité synthétique suffit à appeler leur setter sans prétendre être un joueur réseau.

La destruction de masse n'est pas simulée : le deuxième processus charge l'objet depuis le coffre puis le laisse despawn nativement, avec observation de son EntityRemoveEvent DESPAWN et libération par le listener de production. Le troisième vérifie que ni l'objet ni la réservation ne reviennent. La réservation initiale est toutefois préparée par configuration ; ce scénario ne valide pas un craft joueur réel. Les consommations de marqueurs utilisent les méthodes des trackers, pas un faux parcours de casse réseau.

Les mondes sont nouveaux sous `<module>/build/persistence-server/<essai>` ; PID, nonce JVM, identité d'essai, UUID du monde, arrêt/sauvegarde complets et empreintes sources/JAR/serveur/vérificateur sont contrôlés. Ces preuves restent distinctes des tests visuels, remboursements payés ou durabilité consommée par un vrai joueur.

L'essai historique `20261023-224646-428f81fc` a terminé15PASS et5FAIL, conservés sans réécriture : deux mutations temporaires d'evidence.ps1 correctement détectées, deux cannes perdues à cause du sable flottant du décor de test et un garde attendant une ligne de journal supprimée par l'amont26.3. La lecture NBT du monde Paper26.3 arrêté a confirmé que les troncs et marqueurs PDC étaient sauvegardés, mais que la canne s'était normalement cassée après la chute du sable. Aucun correctif de production n'a été déduit de ce comportement physique normal.

Le fixture utilise désormais DIRT et des assertions détaillant le type de chacun des blocs. Pour Bukkit/Spigot26.3 seulement, le garde de journal suit le source officiel `MinecraftServer.saveAllChunks`/`stopServer` : désactivation des deux plugins, sauvegardes joueurs/mondes/trois dimensions après « Stopping server », sortie0 et relectures dans les nouvelles JVM ; cette version n'émet plus « All dimensions are saved ». Les autres plateformes gardent leur marqueur final, et les erreurs de sauvegarde/fermeture/déverrouillage invalident toutes les versions.

La reprise homogène complète20 `20261023-231156-96499ca2` a terminé code0 : **20PASS, zéroFAIL, 107assertions par module, 2140au total, 60vraies JVM et40redémarrages à froid**. Les rapports courants sont `validation/results/persistence/latest.json` et `latest.md`, avec les reçus et journaux exacts conservés par essai. Le contrôle final du 23 octobre2026 à23:34:46UTC a revérifié les vingt empreintes de sources/JAR/serveurs et les soixante empreintes de journaux/résultats : toutes correspondent encore aux fichiers présents. Le JAR du helper est homogène (`c31f16318e6622eebce4014fbf22d009b923f90cf0aecce799f7ab93763cf596`), ainsi que ses sources/runner (`2e97ce348711a467db5a984c5d63c3398de62aeba2afa7424cf653b3241f1750`).

Aucun monde ou fichier de configuration existant n'a servi à ces essais, aucun serveur de test ne reste lancé. Les cinq échecs historiques ne sont pas effacés ; ils sont résolus par la nouvelle matrice complète, sans correctif de production pour le décor de test. Cette validation ne remplace toujours pas les scénarios joueur réseau de craft, paiements d'XP, usure d'outil et présentation visuelle.

## Correctif découvert par le test joueur créatif

`CraftInventoryView#getType()` renvoie explicitement CREATIVE quand son inventaire sous-jacent est CRAFTING et le joueur en créatif (source officielle CraftBukkit26.3, ligne53 ; comportement aussi présent en1.21.1). Le garde initial n'acceptait que CRAFTING et empêchait donc de rouvrir le parent après Échap en créatif.

Le nouveau garde accepte CRAFTING et CREATIVE, uniquement si le détenteur de l'inventaire supérieur est ce joueur, et ne remplace pas une vue externe. Les scénarios natifs incluent maintenant le retour Tools→Timber→racine en créatif, l'annulation de saisie, Close et fermeture programmée, ainsi que l'absence de remplacement d'un coffre externe ou d'une vue CRAFTING étrangère. Les dix couples Bukkit/Spigot ont repassé ces vérifications dans le Full20 sans échec.
