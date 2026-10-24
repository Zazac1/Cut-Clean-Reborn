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

**Menu-layout follow-up in progress:** after the 17:48 blocks-only validation below, the user requested better button/settings placement. Compact centered Fabric pages, grouped dashboards, separated footer utilities and matching Paper/Bukkit presentation are now implemented. The earlier results below are historical for these changed sources until the new unfiltered Full run and cold/standalone receipts finish. Targeted Fabric checks passed 186 native assertions on 1.21.1 and 162 on 26.3 before the final short-help copy adjustment; actual-client checks are in progress on JAR `cdfe6859…`. No complete player traversal is inferred.

Clean Stacks now affects only drops from blocks the player breaks, including Timber. Activation, inventory correction, unrelated pickup, mob collection and plank crafting never normalize those items. All 25 native runs have been refreshed for this change; current plugin cold receipts and standalone exports are still in progress. Client observations from the preceding release remain historical and are not relabelled.

The latest unfiltered `.\test-all.ps1` run, in default `Full` mode, finished at **17:48:19 Paris time on 24 October 2026**, with **zero failures across all 25 variants**: 259 unit tests and 6,160 native checks. Every variant started on its actual advertised runtime; Fabric uses its release JAR outside Loom. Each cell below gives **unit tests / native checks per variant**:

| Minecraft réellement testé | Java | Paper et Purpur, chacun | Spigot et CraftBukkit, chacun | Fabric |
| --- | --- | --- | --- | --- |
| 1.21.1 | 21 | 9 / 255 | 9 / 266 | 15 / 182 |
| 1.21.11 | 21 | 9 / 263 | 9 / 274 | 16 / 158 |
| 26.1.2 (famille 26.1.x) | 25 | 9 / 263 | 9 / 274 | 16 / 158 |
| 26.2 | 25 | 9 / 264 | 9 / 275 | 16 / 158 |
| 26.3 | 25 | 9 / 264 | 9 / 275 | 16 / 158 |

These receipts match the current sources and JARs, including the blocks-only scope, concise English in-game copy, Fabric input title/cost corrections and list-page return preservation. All 25 source fingerprints were rechecked after the run without drift. A later covered-source or JAR change requires new evidence; this table is not a permanent certification.

The latest Fabric run passed 22 cold checks for 1.21.1 and 26 for each other version, or 126 assertions across 15 JVM launches. A new 20-plugin cold run is in progress for the changed plugin sources/JARs. The earlier `20261024-130030-0dd226f8` success is historical, not evidence for this revision. Earlier failed attempts remain archived separately; they are not erased or counted as successful runs.

All **25 current native-verified JARs** are built. The collector and 25 standalone exports must be refreshed after committing the blocks-only change. The preceding standalone attempt `20261024-161746-32abe95c` remains historical. **0/25 complete player traversals** are claimed. Logs remain under `build/branch-validation/` and receipts under `validation/results/exports/`; these checks do not replace client tests.

Les nombres de vérifications varient avec les registres Minecraft et les adaptateurs de plateforme. Les essais automatisés couvrent notamment les menus, caps, enchantements, origines placées, objets persistants et masse unique. Ils ne prouvent ni le ressenti de minage standard ni une interaction réalisée à la souris dans un client.

### Essais réellement effectués dans Minecraft

Current Fabric 1.21.1 release `40050ed6…` was installed only in its isolated QA profile, preserving all 48 other mod files. Actual-client checks confirm unchanged activation/given inventory, ordinary thrown-and-picked-up Birch Logs, and four crafted Birch Planks. Actual survival breaks separately produced one Oak Log from Birch and one Stone from Granite, confirmed before pickup. Haste 64 enabled brief click input, not a standard-mining-feel claim. Its Clean Stacks receipt remains PARTIAL; the preceding release's menu and solo lifecycle PASS are not inherited. World B saves normally at 17:48:53 and the whole client closes at 17:49:10; cold-client verification is in progress.

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

Les essais visuels utilisent uniquement les profils Modrinth « CCR QA » séparés et le projet Run Launcher `validation/client-server/`. Le serveur client QA écoute sur `127.0.0.1:25591`, avec authentification Minecraft normale. Les mondes et configurations d'origine ne sont jamais réutilisés.

Notes détaillées : [Spigot/CraftBukkit](spigot-bukkit.md), [Fabric moderne](fabric-modern.md), [Fabric 1.21.1](fabric-1.21.1.md) et [audit de parité Fabric](fabric-parity-audit.md). Les rapports conservés attestent un état précis ; seul le tableau associé aux sources et JAR courants fait foi pour une livraison.
