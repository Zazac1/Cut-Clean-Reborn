# Portage multiplateforme — suivi de travail

## Périmètre demandé

- Conserver Paper 26.3 et Paper 26.2 fonctionnels ; ajouter Paper 26.1.x, 1.21.11 et 1.21.1.
- Adapter ensuite les cinq variantes au serveur Purpur, puis à Spigot et à CraftBukkit (implémentation serveur de Bukkit).
- Fournir Fabric pour 26.3, 26.2, 26.1.x, 1.21.11 et 1.21.1, avec fonctionnement en solo et sur serveur.
- Cible de suivi : 25 couples plateforme/famille de versions. Ne pas déclarer compatibles des révisions Minecraft non testées ; 26.1.2 est la première révision concrète pour la famille 26.1.x.
- Préserver toutes les fonctionnalités ; documenter toute différence imposée par une API.
- Fournir les JAR dans un dossier de sortie par plateforme/version, avec un tableau final compilation/démarrage/fonctionnalités testées et erreurs restantes.
- Préserver les fichiers, profils, mondes et configurations existants. L'utilisateur a confirmé les cinq profils « CCR QA », le travail par sous-agents et la publication des sources/branches sur GitHub ; aucune publication Modrinth ni service payant.

## Référence de départ

Commit local `8af4aaf` sur `main`. Les deux variantes Paper actuelles ont chacune 48 fichiers Java principaux, 9 tests unitaires et une suite native de 246 vérifications simulant les interactions des menus. Ces tests ne constituent pas une validation visuelle dans Minecraft.

Fonctionnalités à conserver et vérifier :

1. Commandes `/ccr`, `/cutclean`, `/crr`, `/cleanstacks`, permissions administrateur et préférence personnelle Clean Stacks.
2. Menus de configuration, saisies validées, confirmations, pagination et retour Échap d'un niveau.
3. Butin des créatures : cuisson, quantité, XP, collecte, overrides vanilla, extras et Looting.
4. Minerais : variantes de blocs, drops, Fortune, Silk Touch, fusion automatique avec XP et copies de paramètres.
5. Timber naturel : outils, main, accroupissement, progression, limites, vitesse, durabilité, protections, feuilles et pommes.
6. Enchantements automatiques : identité persistante, préservation des modifications du joueur, rafraîchissement explicite, conflits et plafonds.
7. Clean Stacks : conversions, cycles stables, quantités et objets personnalisés protégés.
8. Canne à sucre : causes indépendantes, chance, quantité et origine placée persistante.
9. Stuff Rules : armure équipée, caps, remboursement d'XP réellement payé et masse unique persistante.
10. Sauvegardes, redémarrages, corrections d'inventaire, arrêt propre et compatibilité des anciens chemins de configuration.

## Architecture examinée

La logique actuelle dépend fortement de Bukkit/Paper : types Material/ItemStack/Player, événements, YAML, persistent data containers, Adventure et ordonnanceur. `GameplayMath` est le seul fichier principal déjà indépendant de ces API.

Points de compatibilité à traiter : ancienne classe InventoryView contre interfaces récentes, API des menus/enclumes, causes de fermeture, équipements/événements ajoutés récemment, Adventure 4 contre 5, Java 21 contre 25 et changements de noms de matériaux/sons.

Ordre de travail :

1. Établir la compatibilité Paper depuis 1.21.1, puis 1.21.11 et 26.1.x ; revalider les deux références.
2. Extraire uniquement les politiques et services réellement partageables ; isoler les opérations spécifiques dans des adaptateurs, sans remplacer des fonctionnalités par des interrupteurs sans effet.
3. Tester sur les véritables serveurs Purpur/Spigot/CraftBukkit, pas seulement sur Paper.
4. Réaliser les adaptations Fabric natives, y compris serveur intégré, menus et synchronisation client/serveur.
5. Créer des profils isolés et effectuer les scénarios réels avec les launchers de l'utilisateur. Les tests natifs simulés seront identifiés séparément des tests visuels/en jeu.
6. Générer les JAR, rapports et branches locales de version. L'historique existant n'a pas besoin d'être détruit pour avancer.

## Ressources officielles consultées

- Paper : https://fill.papermc.io/v3/projects/paper/versions/26.1.2/builds/latest et versions correspondantes 1.21.11/1.21.1.
- Purpur : https://api.purpurmc.org/v2/purpur.
- Spigot/CraftBukkit : https://www.spigotmc.org/wiki/buildtools/ ; compiler les serveurs officiellement avec BuildTools.
- Fabric : https://fabricmc.net/develop/ et https://docs.fabricmc.net/develop/porting/.

## Téléchargements Paper sélectionnés

| Minecraft | Build | SHA-256 du serveur |
| --- | --- | --- |
| 26.1.2 | 74 stable | 1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7 |
| 1.21.11 | 132 stable | 5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba |
| 1.21.1 | 133 stable | 39bd8c00b9e18de91dcabd3cc3dcfa5328685a53b7187a2f63280c22e2d287b9 |

Les commandes, journaux de compilation et résultats restent dans les dossiers `build/` des modules et `validation/results/`. Ce document sera enrichi au fur et à mesure ; il ne constitue pas une déclaration de portage terminé.

## Résultats automatiques — 24 octobre 2026

**Packaging follow-up:** the user accepted the previous objective, then requested clearer naming and launcher discovery. All 25 variants now declare base version `0.1.0` without the development suffix. Source/verification JARs are separated from installable artifacts, old generated CCR copies are archived, and the internal QA wrapper no longer creates a sixth Paper entry. The actual launcher scanner reports 25 variants, five per platform, all `0.1.0`. A fresh unfiltered Full run, plugin cold run and standalone export rebuild are required for these changed build inputs. The results below describe the preceding layout build until those checks finish; no old client receipt is relabelled with a new hash.

**Menu-layout follow-up validated automatically:** compact centered Fabric pages, grouped dashboards, separated footer utilities and matching Paper/Bukkit presentation are implemented. Actual Fabric 1.21.1 checks on JAR `cdfe6859…` now pass the menus/commands and cold-client persistence families. Other gameplay families remain partial or untested on this exact JAR; no complete player traversal is inferred.

Clean Stacks affects only drops from blocks the player breaks, including Timber. Activation, inventory correction, unrelated pickup, mob collection and plank crafting never normalize those items. All 25 native runs, matching cold receipts and standalone exports have been refreshed for these sources. Client observations from preceding releases remain historical and are not relabelled.

The latest unfiltered `.\test-all.ps1` run, in default `Full` mode, finished at **20:07:02 Paris time on 24 October 2026**, with **zero failures across all 25 variants**: 289 unit tests and 6,280 native checks. Every variant started on its actual advertised runtime; Fabric uses its release JAR outside Loom. Each cell below gives **unit tests / native checks per variant**:

| Minecraft réellement testé | Java | Paper et Purpur, chacun | Spigot et CraftBukkit, chacun | Fabric |
| --- | --- | --- | --- | --- |
| 1.21.1 | 21 | 9 / 260 | 9 / 271 | 21 / 186 |
| 1.21.11 | 21 | 9 / 268 | 9 / 279 | 22 / 162 |
| 26.1.2 (famille 26.1.x) | 25 | 9 / 268 | 9 / 279 | 22 / 162 |
| 26.2 | 25 | 9 / 269 | 9 / 280 | 22 / 162 |
| 26.3 | 25 | 9 / 269 | 9 / 280 | 22 / 162 |

These receipts match the current sources and JARs, including the blocks-only scope, concise English in-game copy, Fabric input title/cost corrections and list-page return preservation. All 25 source fingerprints were rechecked after the run without drift. A later covered-source or JAR change requires new evidence; this table is not a permanent certification.

The latest Fabric run passed 22 cold checks for 1.21.1 and 26 for each other version, or 126 assertions across 15 JVM launches. Plugin cold attempt `20261024-174352-e4c3917d` finished at **20:12:46 Paris time**, passing all 20 variants with 107 storage assertions each, three distinct JVMs and orderly shutdowns. Its hashes and source fingerprints match the native receipts. Combined, these are 2,266 storage checks across 75 JVM launches and 50 actual restarts. Earlier interrupted/failed attempts remain archived separately and are not counted as successes.

All **25 current native-verified JARs** are built. Fresh standalone attempt `20261024-194750-6ca3aeff` passed all 25 isolated builds and 289 unit tests at **19:58:16 Paris time**, from source revision `88e95f1635f095698875496a4abbffe129654706`. Later root-documentation updates do not alter these tested export trees. **0/25 complete player traversals** are claimed. Logs remain under `build/branch-validation/` and receipts under `validation/results/exports/`; these checks do not replace client tests. The collector gathers matching current JARs under `dist/` and preserves previous binaries in its archive.

Les nombres de vérifications varient avec les registres Minecraft et les adaptateurs de plateforme. Les essais automatisés couvrent notamment les menus, caps, enchantements, origines placées, objets persistants et masse unique. Ils ne prouvent ni le ressenti de minage standard ni une interaction réalisée à la souris dans un client.

### Essais réellement effectués dans Minecraft

Current Fabric 1.21.1 release `cdfe6859…` was installed only in its isolated QA profile, preserving all 48 other mod files. Actual checks cover the compact Timber/Stuff Rules/cane screens, one-parent Escape, input rejection/cancellation, explicit Close, commands and returning from a page-2 mob detail to page 2. A chance edited to 80 in the client survives a full shutdown and new client process; the Clean Stacks preference and existing unique-mace reservation also persist. The value is then restored to 100. Only `menus_commands` and `persistence` pass, or 2/11 families. The same-client B → A → B inventory/mace separation is observed, but active Timber-task transitions remain untested. Clean Stacks block drops, pickups and plank crafting from the preceding `40050ed6…` release remain historical observations. World B saves at 20:14:08 and the client closes at 20:14:16.

Historical Paper 1.21.11 checks covered menus/commands, the unique-mace lifecycle, Clean Stacks and a cold restart; historical Fabric 1.21.1 checks covered two-world solo lifecycle, Clean Stacks and persistence. Paper 26.3 also has partial mining/menu observations, with Haste used only in its QA scene; standard mining feel was not proven by that accelerated test. Their exact JAR hashes, conditions and limitations remain in [the client log](client-tests.md) and local receipt history. None is relabelled with a new release hash or promoted into a complete player traversal. Current receipts and the generated `dist` matrix remain authoritative.

Adaptations principales : ouverture des enclumes selon l'API de la version, API Bukkit minimale, ponts d'événements Spigot/CraftBukkit, menus et services Fabric natifs, Java 21 ou 25, et correction du retour Échap lorsque l'inventaire personnel est de type `CREATIVE` plutôt que `CRAFTING`.

Avertissement spécifique 1.21.1 : Paper signale l'écoute de `EntityRemoveEvent` comme obsolète et susceptible d'affecter les performances. La libération de la masse unique reste testée ; l'adaptateur Spigot/CraftBukkit emploie les événements réellement disponibles. Les avertissements Windows OSHI/Perflib connus ne justifient pas de modifier le registre du PC.

## Reproduire les contrôles et retrouver les preuves

Depuis la racine :

```powershell
.\test-all.ps1                        # Full : 25 variantes, vrais runtimes, tests et JAR
.\validation\run-persistence-tests.ps1 # Triplets froids des 20 variantes plugins
.\porting\collect-artifacts.ps1       # Tableau et JAR dont les preuves sont à jour
.\sync-branches.ps1                   # Plan des branches autonomes, sans écriture
.\validation\validate-exports.ps1     # Recompilation isolée des branches activées
```

Le contrôle global vérifie l'identité du serveur dans le journal. Les reçus sous `validation/results/evidence/` associent résultat natif, tests unitaires, empreinte du JAR et empreinte des sources ; toute modification de fichiers couverts exige une revalidation. Les preuves froides restent distinctes, avec trois identifiants de processus, un nonce de monde et des journaux d'arrêt.

`collect-artifacts.ps1` génère le tableau complet des 25 combinaisons et ne copie que les JAR correspondant aux preuves à jour, dans `dist/<Plateforme>/<Famille>/`. Le tableau distingue compilation, démarrage, vérifications natives, redémarrages froids et joueur réel. `-RequireComplete` refuse une livraison déclarée complète si des essais client ou des preuves froides manquent.

Les essais visuels utilisent uniquement les profils Modrinth « CCR QA » séparés et l'outil interne `validation/client-server/`, démarré avec `qa-gradlew.bat runServer`. Il n'apparaît plus comme une sixième variante Paper dans le Dev Launcher. Le serveur client QA écoute sur `127.0.0.1:25591`, avec authentification Minecraft normale. Les mondes et configurations d'origine ne sont jamais réutilisés.

Notes détaillées : [Spigot/CraftBukkit](spigot-bukkit.md), [Fabric moderne](fabric-modern.md), [Fabric 1.21.1](fabric-1.21.1.md) et [audit de parité Fabric](fabric-parity-audit.md). Les rapports conservés attestent un état précis ; seul le tableau associé aux sources et JAR courants fait foi pour une livraison.
