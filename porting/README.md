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

## Avancement vérifié — validation finale en cours

Un passage complet des vingt variantes plugins a réussi après la correction du retour Échap en créatif. Chaque variante a réussi ses neuf tests unitaires, son démarrage sur le véritable serveur annoncé et ses vérifications natives :

| Minecraft réellement testé | Java | Paper et Purpur, par variante | Spigot et CraftBukkit, par variante |
| --- | --- | --- | --- |
| 1.21.1 | 21 | 248 vérifications | 259 vérifications |
| 1.21.11 | 21 | 256 vérifications | 267 vérifications |
| 26.1.2 (famille 26.1.x) | 25 | 256 vérifications | 267 vérifications |
| 26.2 | 25 | 257 vérifications | 268 vérifications |
| 26.3 | 25 | 257 vérifications | 268 vérifications |

Ces nombres décrivent ce passage historique, pas une certification permanente des fichiers courants : le raccourcissement ultérieur d'un titre de menu exige une nouvelle preuve. La commande finale sans filtre doit revalider les 25 variantes. Les cinq variantes Fabric sont implémentées et en revalidation native après les dernières corrections de parité ; voir leurs notes détaillées ci-dessous.

Les tests froids plugins, indépendants des tests natifs en un seul processus, relancent trois vraies JVM sur le même nouveau monde. La première matrice a conservé ses échecs de garde d'intégrité et de fixture ; une nouvelle matrice homogène est en cours. Ne pas mélanger ses résultats avec ceux d'anciens essais. Fabric vérifie également trois arrêts/redémarrages sur son JAR de distribution, en dehors de Loom.

Les nombres de vérifications varient avec les registres Minecraft et les adaptateurs de plateforme. Les essais automatisés couvrent notamment les menus, caps, enchantements, origines placées, objets persistants et masse unique. Ils ne prouvent ni le ressenti de minage standard ni une interaction réalisée à la souris dans un client.

### Essais réellement effectués dans Minecraft

Sur Paper 26.3, dans un profil « CCR QA » et un monde neufs : ouverture de `/ccr`, navigation Timber, retour Échap d'un niveau en créatif, annulation de saisie et de confirmation dans Stuff Rules, fermeture explicite, conservation d'une règle après redémarrage, récolte réelle de quatre rondins avec durabilité de la hache, activation d'Auto Smelt et récolte de lingots avec Fortune. Les empreintes des JAR, conditions et limites de chaque essai figurent dans [le journal client](client-tests.md).

Le helper applique Haste uniquement dans cette scène QA ; le temps de minage normal n'est donc pas validé par cet essai. Les autres plateformes/versions, les remboursements d'XP réellement payés et le solo Fabric restent à vérifier avec un véritable client. Aucun reçu « joueur réel : PASS » global n'est déduit de ces essais partiels.

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
