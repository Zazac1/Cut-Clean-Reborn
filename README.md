# CutCleanReborn — Fabric 1.21.1

Port natif Fabric du gameplay actuel de CutCleanReborn, en solo (serveur intégré) et sur serveur dédié. Aucun adaptateur Bukkit n'est utilisé. Les interfaces utilisent les écrans vanilla de coffre et d'enclume : les clients n'ont pas besoin d'un protocole ou d'un écran personnalisé.

## Installation

- Minecraft **1.21.1**, Java **21**, Fabric Loader **0.20.1** (minimum déclaré) et Fabric API **0.116.17+1.21.1** (révision utilisée pour les tests).
- Installer `cutcleanreborn-0.1.0+1.21.1.jar` dans `mods/`. En solo, installer dans le profil client. Sur serveur dédié, installer côté serveur ; un client Fabric/FO ou vanilla peut utiliser les interfaces serveur.
- SnakeYAML est inclus dans le JAR : aucun téléchargement manuel supplémentaire n'est nécessaire pour la configuration.
- Ne pas installer le JAR `ccrverification`, destiné uniquement aux tests de développement.

## Configuration et commandes

`/ccr` ou `/ccr config` ouvre les menus. `/cutclean` et `/crr` sont des alias. Autres sous-commandes : `status`, `reload`, `help`, `ping`.

`/cleanstacks on|off|status|toggle` est une préférence personnelle persistante, accessible sans être opérateur. `true` et `false` sont également acceptés.

Les réglages administrateur demandent le niveau opérateur 2 ; le propriétaire d'un monde solo peut aussi les ouvrir, sans activer les triches. Les permissions Bukkit ne sont pas transposables littéralement à Fabric ; l'autorité repose sur le serveur Minecraft, pas sur le client.

La configuration YAML est dans `config/cutcleanreborn/config.yml`. Les chemins Paper sont conservés. Les changements sont validés et enregistrés immédiatement. Une saisie invalide reste ouverte ; Échap l'annule. Dans les listes et réglages, Échap revient au **parent logique**, pas à la page de pagination précédente. À la racine, Échap ferme. Le bouton Fermer ferme directement toute la navigation.

Fonctionnalités : butin par créature (cuisson, XP, collecte, vanilla/extras et Looting), minerais (variantes, XP, Fortune, Silk Touch, fusion et copie), Timber naturel (outils, accroupissement, temps proportionnel, débit, limites, feuilles/pommes), enchantements automatiques persistants, Clean Stacks, canne à sucre par cause, limites d'armure équipée, caps d'enchantement, remboursements d'XP payé et masse unique.

Les origines placées sont sauvegardées dans `<monde>/data/cutcleanreborn-origins.json`. L'existence/identité de la masse est également **locale au monde**, dans `cutcleanreborn-state.json` : une nouvelle partie solo n'hérite pas de la masse d'une autre sauvegarde. Ne pas supprimer ces fichiers pour nettoyer un serveur en production.

Les objets enchantés par CCR portent des composants persistants `ccr:auto_enchant_*`. Le rafraîchissement préserve l'identité et les enchantements d'origine et n'écrase pas les changements ultérieurs du joueur. Une masse porte `ccr:unique_mace_id` ; ramassage, coffre et déchargement ne la rendent pas disponible. Destruction, vide, disparition naturelle et casse de durabilité la libèrent.

Un override de butin de créature peut préciser `source` en plus de `item` : le premier désigne l'objet vanilla supprimé, le second l'objet de remplacement. Les anciennes entrées sans `source` conservent leur interprétation historique.

## Compilation et vérification

Depuis ce dossier, avec Java installé et accès aux dépôts officiels :

`build` compile sans accepter l'EULA Minecraft. Pour les tâches qui démarrent un serveur QA, lire d'abord [l'EULA officielle](https://www.minecraft.net/eula). Seulement si vous l'acceptez, créer localement `src/qa/fixtures/eula.txt` avec votre acceptation (`eula=true`) ; `eula.example` contient un exemple **non accepté**. Cette fixture est volontairement ignorée par Git : une branche fraîche ne contient pas votre acceptation. Les tâches la copient dans les trois environnements QA et refusent de démarrer si elle manque ou reste non acceptée. Ne pas la committer. Le serveur jouable `runServer` demande séparément son propre `run-server/eula.txt` ; aucune tâche ne l'accepte pour vous.

```powershell
.\gradlew.bat build
.\gradlew.bat smokeTest
.\gradlew.bat productionSmokeTest
.\gradlew.bat productionPersistenceTest
```

Sous Linux/macOS, utiliser `./gradlew`.

- `build` : compilation, tests unitaires, remappage et JAR autonome dans `build/libs/`.
- `smokeTest` : serveur Minecraft/Fabric réel de développement, monde isolé `build/smoke-server/development/`, vérificateur séparé du JAR distribué, arrêt automatique et rapport `ccr-qa-result.json`.
- `runServer` : véritable serveur jouable dans `run-server/`, sans vérificateur, sans arrêt automatique et sans écrasement de fichiers. Il faut accepter l'EULA et configurer ce nouveau serveur normalement. `runSmokeServer` est le lancement distinct réservé au test de développement.
- `productionSmokeTest` : télécharge le lanceur officiel Fabric et lance le **JAR remappé de distribution**, Fabric API et le vérificateur de test dans `build/smoke-server/production/`. Ce test ne repose pas sur les noms déobfusqués ou le classpath de Loom.
- `productionPersistenceTest` (alias `coldPersistenceTest`) : trois JVM officielles successives, écriture puis deux redémarrages à froid dans `build/smoke-server/persistence/`. Objets réellement enregistrés dans un coffre/chunk vanilla, origines placées, préférences UUID, config, snapshots et masse unique ; résultat `ccr-persistence-result.json`, trois journaux `phase-*.log` avec sauvegarde/arrêt propre contrôlés.

Les deux serveurs smoke sont limités à `127.0.0.1:25594`, le contrôle froid à `127.0.0.1:25595`, avec authentification désactivée uniquement dans ces environnements locaux isolés. Ils réutilisent leurs mondes QA ; le smoke restaure les réglages et le contrôle froid conserve volontairement ses modifications entre ses trois phases. Ils ne touchent jamais `run-server/` ni une sauvegarde existante. Exécuter les tâches serveur séquentiellement pour limiter la mémoire.

## État des vérifications

Les rapports exacts sont dans `build/test-results/test/`, `build/smoke-server/production/ccr-qa-result.json` et `build/smoke-server/persistence/ccr-persistence-result.json`. Les vérifications natives utilisent de vraies classes et transactions Minecraft, avec un joueur synthétique : elles couvrent notamment l'arbre par la casse normale, les protections Fabric, la durabilité, les transactions d'enclume normales/rapides et livres enchantés, la fabrication de masse, les butins et les parcours de menu. Le contrôle à froid vérifie des fichiers natifs relus par d'autres processus. Ce ne sont **pas** des tests visuels avec un joueur connecté. La validation visuelle/solo via les profils QA fait l'objet du suivi central de portage.

Les contrôles lifecycle rejouent les hooks serveur communs dans une même JVM : ils vérifient la remise à zéro des caches, files d'attente et sessions de menu, en conservant les données persistantes. Ce contrôle mécanistique n'est pas un lancement effectif de deux mondes solo avec un client intégré.

## Différences techniques

- Timber respecte les callbacks `PlayerBlockBreakEvents` et la casse/durabilité Minecraft. Les plugins de protection Paper ne sont pas chargés dans Fabric ; leurs équivalents Fabric doivent utiliser ces callbacks.
- Les plafonds corrigent les objets dans l'inventaire et les pièces équipées à la tick suivant leur modification, sans fouiller les coffres. Les remboursements ne concernent que les niveaux réellement payés lors d'une opération d'enchantement ou d'enclume ; une correction à la connexion ne crée jamais d'XP. Les livres stockés sont pris en charge et le clic rapide d'enclume ne contourne pas le plafond.
- La fabrication rapide de masse unique est bloquée ; utiliser un clic normal avec curseur vide, comme dans la version Paper.
- Aucun monde, minerai généré, bloc enregistré ou identifiant vanilla n'est remplacé. Le port historique Fabric en pause n'a pas été fusionné avec des fonctions anciennes sans rapport avec la référence Paper actuelle.

Sources officielles : [documentation Fabric 1.21.1](https://docs.fabricmc.net/1.21.1/develop/), [métadonnées Fabric](https://meta.fabricmc.net/), [API Fabric](https://github.com/FabricMC/fabric).
