# CutCleanReborn — Fabric 1.21.11

Mod natif pour Minecraft **1.21.11**, Java 21, Fabric Loader 0.19.5 et Fabric API 0.141.6+1.21.11. La famille du dossier ne promet pas la compatibilité avec d'autres correctifs Minecraft.

## Installation

Installer Fabric et Fabric API pour cette version exacte, puis placer `build/libs/cutcleanreborn-0.1.0-SNAPSHOT+1.21.11.jar` dans `mods/`. Ne pas installer les JAR de sources, de développement ou `ccrverification`.

En solo, installer le mod dans le profil Fabric local : le serveur intégré applique toutes les règles. Sur serveur dédié, le client peut rester vanilla ; les menus sont des inventaires et enclumes standards. Le propriétaire du monde solo a accès à la configuration sans activer les triches. Sur serveur dédié, les opérateurs de niveau 2 peuvent administrer ; les autres joueurs gardent les commandes publiques et Clean Stacks.

`/ccr` ouvre les menus pour l'administrateur ; alias `/cutclean`, `/crr`. Commandes : `config`, `status`, `help`, `ping`, `reload`, et `/cleanstacks [on|off|status|toggle|true|false]`. Échap revient au parent, le bouton Fermer quitte. Les écrans de saisie acceptent aussi `/ccr edit <valeur>` et `/ccr cancel`.

La configuration est dans `config/cutcleanreborn/config.yml`, avec les chemins Paper conservés. L'origine des blocs placés et l'identité de la masse unique sont enregistrées dans `<monde>/data/cutcleanreborn-state.yml` : deux mondes solo ne partagent pas cet état. Sauvegarder monde et configuration avant toute migration ; aucune conversion automatique des anciens marqueurs Bukkit/PDC n'est annoncée.

## Développement et vérification

Avant le premier lancement local, lire [l'EULA Minecraft](https://www.minecraft.net/eula). Dans `platform-fabric-modern/src/qa/fixtures/`, copier `eula.example` vers `eula.txt`, puis ouvrir ce fichier et choisir `eula=true` seulement si l'EULA est acceptée. Dans le workspace, ce dossier partagé est voisin du projet (`../platform-fabric-modern/`) ; dans une branche autonome, il est intégré au projet. Faire la même préparation dans `src/manual-fixtures/` avant `runServer`. Les fichiers `eula.txt` restent locaux, ignorés par Git ; aucune acceptation n'est distribuée ni automatique.

Depuis ce dossier : `.\gradlew.bat build` compile, exécute les tests unitaires et produit le JAR. `.\gradlew.bat build productionSmokeTest productionPersistenceTest` teste ce véritable JAR sur le lanceur Fabric officiel, hors Loom. Les scénarios fonctionnels utilisent `build/smoke-server/production/` ; les trois démarrages froids utilisent `build/smoke-server/persistence/` et vérifient le coffre NBT, la configuration, les origines et le verrou de masse unique, y compris après destruction. `smokeTest` est la variante native de développement. Le vérificateur est un mod de test séparé, jamais inclus dans le JAR distribué.

`.\gradlew.bat runServer` démarre un serveur jouable normal dans `build/manual-server/`, sans fermeture automatique ni remise à zéro de la configuration. `runClient` utilise `build/qa-client/`. Les fichiers et mondes existants de `run-server/` ne sont pas touchés.

Les sources communes sont dans `platform-fabric-modern/` et le script de validation dans `build-logic/fabric-modern-production.gradle`, à côté de ce projet dans le workspace, ou intégrés localement dans une branche autonome. Aucun serveur Paper n'est requis. L'état des preuves et les limites connues sont consignés dans `porting/fabric-modern.md` du workspace ; un test de menu simulé côté serveur n'est pas une validation visuelle dans Minecraft.
