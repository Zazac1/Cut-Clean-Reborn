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

## Avancement vérifié

| Variante ajoutée | Java | Compilation/tests unitaires | Serveur officiel isolé | Vérifications natives | Tests visuels/en jeu |
| --- | --- | --- | --- | --- | --- |
| Paper 1.21.1 | 21 | Réussis, 9 tests | Chargement et arrêt réussis | 237 réussies | Pas encore réalisés |
| Paper 1.21.11 | 21 | Réussis, 9 tests | Chargement et arrêt réussis | 245 réussies | Pas encore réalisés |
| Paper 26.1.x / 26.1.2 | 25 | Réussis, 9 tests | Chargement et arrêt réussis | 245 réussies | Pas encore réalisés |
| Paper 26.2 | 25 | Réussis, 9 tests | Chargement et arrêt réussis | 246 réussies | Pas encore réalisés |
| Paper 26.3 | 25 | Réussis, 9 tests | Chargement et arrêt réussis | 246 réussies | Client QA en préparation |
| Purpur 1.21.1 | 21 | Réussis, 9 tests | Chargement et arrêt réussis | 237 réussies | Pas encore réalisés |
| Purpur 1.21.11 | 21 | Réussis, 9 tests | Chargement et arrêt réussis | 245 réussies | Pas encore réalisés |
| Purpur 26.1.x / 26.1.2 | 25 | Réussis, 9 tests | Chargement et arrêt réussis | 245 réussies | Pas encore réalisés |
| Purpur 26.2 | 25 | Réussis, 9 tests | Chargement et arrêt réussis | 246 réussies | Pas encore réalisés |
| Purpur 26.3 | 25 | Réussis, 9 tests | Chargement et arrêt réussis | 246 réussies | Pas encore réalisés |

Les nombres de vérifications varient avec les registres de créatures de chaque Minecraft. Les parcours de menus, validations d'objets, corrections de caps, sauvegarde d'origine et cycle de masse unique testés sont les mêmes. La casse réelle d'un arbre avec un joueur, les interactions visuelles et les remboursements payés en jeu restent à vérifier.

Adaptations effectuées : ouverture de l'AnvilView par le joueur sur les nouvelles variantes, API minimale correcte du plugin de vérification, compilation Java adaptée, délai maximal de la tâche native. La première tentative 1.21.1 avait un vérificateur mal étiqueté 26.2 ; après correction et arrêt du seul processus de test concerné, la suite complète a réussi.

Avertissement spécifique 1.21.1 : Paper signale l'écoute de `EntityRemoveEvent` comme obsolète et susceptible d'affecter les performances. La fonctionnalité de libération de la masse unique reste testée et fonctionnelle ; cet adaptateur devra être affiné avec les événements disponibles sur Spigot/CraftBukkit.

## Preuves et livrables

Le contrôle global `test-all.ps1` couvre maintenant les dix variantes Paper/Purpur. Il vérifie explicitement l'identité du serveur dans le journal. Les reçus sous `validation/results/evidence/` associent résultat natif, tests unitaires, empreinte du JAR et empreinte des sources ; toute modification des sources pendant ou après les tests exige une revalidation.

`porting/collect-artifacts.ps1` génère le tableau complet des 25 combinaisons et ne copie que les JAR correspondant aux preuves à jour, dans `dist/<Plateforme>/<Famille>/`. Une observation native simulée ne devient jamais un résultat joueur réel par simple copie.

Les tests visuels utilisent les profils Modrinth « CCR QA » séparés et le projet Run Launcher `validation/client-server/`. Son serveur Paper 26.3 a effectivement démarré dans un monde neuf sous `Paper_26.3/build/client-test-server/`, lié à `127.0.0.1:25591` avec authentification Minecraft normale. Aucun ancien monde n'a été lancé.

Notes détaillées des autres portages : [Spigot/CraftBukkit](spigot-bukkit.md), [Fabric moderne](fabric-modern.md) et `fabric-1.21.1.md` lorsqu'il est disponible. Ces étapes restent en cours et ne sont pas couvertes par les dix validations Paper/Purpur.
