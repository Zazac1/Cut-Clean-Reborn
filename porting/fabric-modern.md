# Fabric modernes — journal de portage

Cibles : 26.3, 26.2, 26.1.2 (famille 26.1.x) et 1.21.11. Java 25 pour 26.x, Java 21 pour 1.21.11. Loom officiel 1.17.21 ; mappings Mojang sur la dernière version obfusquée, sans remapping pour 26.x.

Les sources communes sont dans `platform-fabric-modern/`. L'agent principal centralise les branches autonomes, la validation visuelle et les sorties finales. Les dossiers générés ne signifient pas un portage validé ; compilation, démarrage, features et tests réels sont suivis séparément.

Référence fonctionnelle : Paper_26.3, tous chemins YAML conservés. Le mod fonctionne côté serveur et serveur intégré ; les menus utilisent les paquets d'inventaire vanilla pour être utilisables sans écran client spécifique.

## Architecture et installation

Chaque projet contient son wrapper Gradle, ses versions exactes, sa licence et un petit adaptateur `NativeAccess`. Le workspace utilise les sources communes voisines ; les branches autonomes peuvent embarquer ces mêmes sources sous `platform-fabric-modern/` et le script sous `build-logic/`. Le build préfère ces copies locales lorsqu'elles existent. Aucun serveur ou plugin Paper n'est requis à l'exécution.

| Projet | Minecraft exact | Java | Fabric API | Loom |
| --- | --- | --- | --- | --- |
| Fabric_26.3 | 26.3 | 25 | 0.165.0+26.3 | 1.17.21, non obfusqué |
| Fabric_26.2 | 26.2 | 25 | 0.161.0+26.2 | 1.17.21, non obfusqué |
| Fabric_26.1.x | 26.1.2 | 25 | 0.155.3+26.1.2 | 1.17.21, non obfusqué |
| Fabric_1.21.11 | 1.21.11 | 21 | 0.141.6+1.21.11 | 1.17.21, mappings Mojang puis remapping |

Fabric Loader 0.19.5 et Fabric API sont nécessaires. SnakeYAML 2.5 est inclus dans le JAR. Le nom de sortie reste `cutcleanreborn-0.1.0+<famille>.jar` ; seul le suffixe de développement a été retiré, sans incrémenter la version de base. Les métadonnées déclarent la version Minecraft exacte, pas une compatibilité non testée avec tous les correctifs 26.1.

Serveur dédié : installer le JAR et Fabric API dans `mods/` ; les clients peuvent rester vanilla. Solo : installer le mod dans le profil Fabric local. L'administration est réservée aux opérateurs natifs de niveau 2 et au propriétaire du monde solo, même sans triches. Les permissions Bukkit/Paper d'un gestionnaire externe ne sont pas importées automatiquement.

## Fonctionnalités portées

- Commandes `/ccr`, `/cutclean`, `/crr`, configuration, statut complet, rechargement YAML sécurisé et préférence `/cleanstacks` par UUID.
- Menus sémantiques à inventaires vanilla, pagination sans empiler l'historique, Échap vers le parent, annulation de saisie et fermeture explicite. Sélecteurs d'enchantements compatibles et de cibles exactes/groupées, recherche, copie de matériau, confirmations et réinitialisation du butin distincte de cuisson/XP.
- Créatures : vrais drops de mort, cuisson, multiplicateur, extras/remplacements, Looting indépendant, XP et collecte près du tueur. Catalogue vanilla échantillonné sans faire apparaître de créature ni modifier l'équipement du joueur. L'aperçu Looting ne sert jamais de base déjà augmentée au remplacement.
- Minerais : Fortune, Silk Touch préservé, fusion et XP, variantes et copie indépendante des sections. Un aperçu de loot ne crée ni objets ni XP. Une ligne de configuration invalide ne retire pas le vanilla.
- Timber : arbres naturels, origine des blocs placés, outils, main/sneak, temps proportionnel, budget par tick, feuilles/pommes et durabilité. Chaque bloc supplémentaire passe par la vraie destruction native et les callbacks Fabric de protection. Les modifications de casse joueur sont réservées au mode Survie.
- Auto-enchant : UUID NBT, état d'origine, rafraîchissement versionné, conflits et modifications du joueur conservés. Un marqueur partiel n'autorise pas une réécriture ; un niveau importé hors de 1 à 255 est ignoré.
- Clean Stacks : préférence individuelle, catégories bois/pierre, préréglages, copies et cycles déterministes. Quantité préservée et objets personnalisés protégés. Les conversions vers un objet valide hors catégorie restent possibles.
- Canne à sucre : causes joueur/perte de support indépendantes et origine placée persistante. Un piston qui détruit une canne encore soutenue conserve la quantité vanilla ; le bonus support n'est applicable que si la canne ne peut réellement plus survivre à sa position.
- Stuff Rules : limites d'armures équipées avec restitution sans perte, plafonds par enchantement/cible, corrections dès le tick suivant et scan de sécurité. Livres stockés pris en charge. Enclume plafonnée avant tout transfert normal/rapide ; remboursement lié à la transaction, plafonné au coût réellement payé, sans création gratuite d'XP.
- Administration simultanée : les éditeurs Stuff suivent l'identité de règle, jamais sa position dans la liste. Une suppression/repositionnement par un autre administrateur ne peut pas déplacer une saisie, une confirmation de suppression, une copie ou un retour Échap sur la règle voisine. Une règle supprimée pendant la saisie annule celle-ci explicitement. Les anciens ID manquants/dupliqués sont normalisés une seule fois, sans changer les paramètres de jeu.
- Masse unique : identité native persistante, refus avant consommation des ingrédients des clics non sûrs/duplications, libération après vraie destruction, durabilité, despawn ou vide. Ramassage et déchargement ne libèrent pas le verrou.

Les chemins Paper de `config.yml` sont conservés, y compris les anciennes variantes `material`/`item` et enchantements sans namespace. Le fichier est `config/cutcleanreborn/config.yml`. L'origine des blocs et l'état de la masse sont sauvegardés séparément dans `<monde>/data/cutcleanreborn-state.yml` afin de ne pas contaminer deux mondes solo. Une migration automatique des anciens objets Bukkit/PDC n'est pas annoncée : sauvegarder les mondes avant de changer de plateforme.

## Vérification reproductible

Depuis chacun des quatre projets :

Préparation locale préalable : lire l'EULA Minecraft, copier `platform-fabric-modern/src/qa/fixtures/eula.example` vers `eula.txt` dans ce même dossier, puis éditer ce fichier en `eula=true` uniquement si l'EULA est acceptée. Le dossier commun est voisin du projet dans le workspace et intégré au projet dans la branche autonome. La même préparation dans `src/manual-fixtures/` est nécessaire pour `runServer`. Les acceptations `eula.txt` sont locales et ignorées par Git ; seul le modèle `eula.example` avec `false` est distribué. Aucun test ni build ne doit accepter l'EULA à la place de l'utilisateur.

```powershell
.\gradlew.bat build productionSmokeTest productionPersistenceTest
```

`build` inclut 16 tests unitaires (configuration/copies atomiques, identités concurrentes stables, entrées invalides, calculs et conversions). `productionSmokeTest` installe le véritable JAR release — remappé sur 1.21.11 — et un vérificateur séparé sur le lanceur Fabric officiel. Le vérificateur n'est pas inclus dans le JAR distribué. Les tests sont exclusivement sous `build/smoke-server/`, jamais dans les mondes `run-server/` de l'utilisateur.

Le scénario fonctionnel comporte 141 contrôles natifs : commandes, menus/Échap/saisies/sélecteurs, vrais drops/XP, arbre cassé via la vraie chaîne de protection/durabilité, vrai craft/recette, vraie table d'enchantement de livres, vraie enclume avec paiement et shift, retrait d'armure dès le tick suivant, destruction de masse et invalidation d'un retour différé après rechargement. Les scénarios multi-administrateurs vérifient saisies, suppression, copie et retour Échap après changement concurrent des règles. Six contrôles mécanistiques invoquent les callbacks CCR enregistrés pour le cycle de vie : nouvelle instance de runtime, même UUID à nouveau corrigé, ancien menu abandonné, préférences/origines/masse conservées. Ils ne rejouent pas les événements globaux Fabric, dont certaines initialisations ne sont pas réentrantes. Cela ne simule pas un véritable client IntegratedServer. Six contrôles physiques distinguent une vraie extension de piston détruisant une canne soutenue et une vraie perte de support, avec quantités discriminantes 1/4. Le monde QA réutilisé est débarrassé uniquement de ses anciens objets/XP dans la zone de test, pour qu'ils ne puissent pas produire de faux positifs.

`productionPersistenceTest` utilise un autre monde et trois JVM distinctes : écriture, lecture puis destruction/retrait, relecture. Un vrai coffre de chunk sauvegardé contient la pioche auto-enchantée et la masse : UUID/enchantements sont relus par le codec NBT Minecraft, sans sérialisation de remplacement. Les trois phases vérifient aussi YAML, préférence UUID, origine bois/canne et non-résurrection d'un verrou détruit ou d'une origine retirée. Les rapports incluent les PID des processus.

Résultats : `build/smoke-server/production/ccr-qa-result.json` et `build/smoke-server/persistence/ccr-persistence-result.json`. Les reçus SHA256/empreinte source sont centralisés par `validation/evidence.ps1`.

`runServer` reste un lancement normal jouable dans `build/manual-server/`, sans QA ni réinitialisation de sa configuration ; `runSmokeServer`/`smokeTest` sont les lancements de développement avec vérificateur et arrêt automatique. `runClient` reste isolé dans `build/qa-client/`.

Ces preuves automatiques ne constituent pas une validation visuelle réelle ni une session client solo complète ; ces scénarios sont à vérifier séparément par l'agent principal. Les avertissements Windows OSHI/Perflib observés aussi sur les autres serveurs proviennent des compteurs système : aucun changement du registre n'a été effectué. Le catalogue affiche des chances observées sur 256 tirages, pas une probabilité exacte de tous les loots rares/conditionnels.

## Passage figé du 24 octobre 2026

Après les corrections Stuff multi-administrateurs, cycle de vie et canne/piston, la commande complète ci-dessus a réussi séparément et séquentiellement dans les quatre projets. Les JAR release ont les mêmes SHA-256 que les copies réellement installées dans les deux runtimes de chaque projet. Chaque triplet possède un nonce unique conservé dans le coffre NBT, trois PID distincts et trois journaux d'arrêt/sauvegarde propres.

| Minecraft | Tests unitaires | Contrôles natifs hors Loom | Contrôles froids, trois JVM | PID écriture / lecture / relecture |
| --- | --- | --- | --- | --- |
| 26.3 | 16 / 16 | 141 / 141 | 26 / 26 | 9832 / 11084 / 25432 |
| 26.2 | 16 / 16 | 141 / 141 | 26 / 26 | 20776 / 23512 / 30296 |
| 26.1.2 | 16 / 16 | 141 / 141 | 26 / 26 | 15740 / 17324 / 27672 |
| 1.21.11 | 16 / 16 | 141 / 141 | 26 / 26 | 2308 / 16392 / 8128 |

Le lancement normal `runServer` 26.3 a également été démarré réellement : monde neuf `build/manual-server/ccr-manual`, écoute locale authentifiée, aucun `ccrverification` chargé et aucun rapport QA créé. La commande console `ccr status` a répondu après le démarrage ; le serveur est resté ouvert jusqu'à la commande `stop`, avec sauvegarde propre et sortie 0. Ce diagnostic ne prouve pas une connexion ou interaction de joueur.

Ce passage ne remplace pas le contrôle global obligatoire des 25 projets ni ses reçus d'empreintes centralisés. Tous les processus serveur Fabric de cette passe sont arrêtés ; aucun fichier `run-server/`, profil ou monde existant n'a été utilisé.
