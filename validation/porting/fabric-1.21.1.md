# Fabric 1.21.1 — journal de portage

## Architecture

Module autonome `Fabric_1.21.1`, Java21, mappings officiels Mojang, Loom1.17.13/Gradle9.5.1, Loader0.20.1, API0.116.17+1.21.1. YAML sûr (SnakeYAML2.5 inclus), sauvegardes atomiques, chemins de configuration Paper conservés. Les classes de vérification sont dans `src/qa`, jamais dans le JAR principal.

Services natifs : `InventoryRules`, `LootRules`, `Timber`, `Origins`, `WorldState`, `Menus`. Adaptation par callbacks Fabric et mixins ciblés, sans installer une couche Bukkit. Les opérations serveur sont autoritaires et communes au serveur intégré et dédié.

## Commandes de validation exécutées

```powershell
.\gradlew.bat genSources
.\gradlew.bat compileJava
.\gradlew.bat build qaClasses
.\gradlew.bat smokeTest
.\gradlew.bat build productionSmokeTest
.\gradlew.bat productionPersistenceTest
```

Le serveur officiel autonome utilise le lanceur `https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.20.1/1.1.2/server/jar`. Les dépendances téléchargées proviennent des dépôts officiels de Mojang/Fabric/Maven Central.

## Preuves actuelles

- Compilation + 15 tests unitaires : réussite (résultat XML dans `Fabric_1.21.1/build/test-results/test/`).
- Serveur natif de développement : démarrage, chargement sans erreur de mixin, 146 contrôles réussis sur la passe précédant les correctifs multi-admin/lifecycle ; la preuve finale ci-dessous utilise le vrai JAR remappé indépendant, pas seulement Loom.
- JAR remappé sur serveur Fabric officiel indépendant : **165 contrôles réussis, zéro échec**, dont fabrication normale/rapide/jet/touche numérique de masse, transactions d'enclume normales/rapides/inventaire plein, livres enchantés stockés, table payée, destructions de masse, catalogue vanilla, saisies/menu, deux sessions administrateurs, hooks lifecycle communs, canne physiquement détruite par piston ou vraie perte de support. Relances répétées du même monde QA réussies. Rapport final sous `build/smoke-server/production/ccr-qa-result.json`.
- Persistance à froid : **22 contrôles réussis, zéro échec** sur trois JVM distinctes23288/24116/24496, étapes write3/read11/reread8. Même nonce natif `27930c30-774d-4c2f-a3ac-b1e2e6f8592a` stocké dans le coffre ; chaque journal contient sauvegarde des chunks et arrêt propre, `cleanShutdown:true`. Agrégat sous `build/smoke-server/persistence/ccr-persistence-result.json` ; les preuves antérieures sont conservées dans `build/smoke-server/persistence-history/`.
- Tests visuels avec client connecté/solo : centralisés par l'agent principal, pas revendiqués ici.

Couverture native explicite : commandes/alias, catalogue d'outils, Clean Stacks (quantité, objets nommés, cycles, point fixe, prévisualisation de craft et quantité transférée), auto-enchantements (UUID, exactement une fois, refresh, originaux, modification joueur, conflits), caps, remboursement borné aux niveaux payés et transactions réelles d'enclume/enchantement, armure équipée et inventaire de secours, fusion et XP réels, Silk Touch, canne joueur/support et origine placée, mort de créature vanilla remplacée/extras, catalogue vanilla/Looting sans apparition de boss, fabrication/identité/doublon/shift-click de masse, casse de durabilité/destruction/disparition naturelle contre simple retrait, sauvegarde d'origine rechargée, Timber par `ServerPlayerGameMode.destroyBlock`, durabilité, callback de protection, exclusion des constructions, temps proportionnel, arrêt au bris d'outil, menus coffre, Échap parent/racine, annulation de saisie enclume, rejet de saisie invalide, sauvegarde valide, fermeture directe, pagination sans historique, reset par clic droit, conversion groupée écorcée, recherche Bow et copie d'outil autonome.

## Correctifs issus des essais

1. Loom1.19.3 exige Gradle9.8 ; choix cohérent1.17.13 pour le wrapper9.5.1 existant.
2. Le vérificateur séparé doit être le sourceSet du lancement serveur ; compiler les classes seules ne suffit pas à les charger.
3. Les trois surcharges `Block.dropResources` sont ciblées avec leurs descripteurs complets. Le sélecteur de nom seul n'accrochait que la première et laissait les drops joueur vanilla. La garde empêche toute mutation/XP lors d'un simple aperçu `getDrops`.
4. Le Timber capture l'outil **avant** la première casse : si la hache casse sur le premier bloc, la récolte ne continue pas à main nue.
5. La récolte conserve le joueur serveur et vérifie son retrait/changement de monde ; les tests synthétiques passent par le même pipeline sans devoir insérer une fausse connexion dans la liste des vrais joueurs.
6. Les transformations viande/fusion préservent les composants de l'objet avec `transmuteCopy`. Les conflits d'enchantements sont vérifiés contre l'ensemble en cours d'édition, pas seulement le snapshot initial.
7. Les origines et l'identité de masse sont propres au monde, ce qui évite la contamination entre sauvegardes solo.
8. Les fixtures de craft doivent avoir un accès au vrai monde : `ContainerLevelAccess.NULL` ne calculait pas de recette. La correction concerne le test, pas une recette artificielle ajoutée au mod.
9. `runServer` reste le serveur jouable sourceSet principal, sans QA ni arrêt automatique. `runSmokeServer` est séparé et utilisé par `smokeTest`. Seuls les dossiers isolés sous `build/` reçoivent les fixtures ; jamais les fichiers du serveur utilisateur.
10. Le confort des menus conserve recherche des créatures/objets, tri des créatures modifiées, aperçus natifs vanilla/Looting/Fortune, conversions groupées (bûches, écorcées, planches, pierre), clic droit de réinitialisation et suppression, ainsi que copie pour les outils sans préfixe de matériau. Les sources sont formatées automatiquement pour leur maintenance.
11. L'audit intégral de parité a détecté des bugs hors des premières91 assertions : livres `STORED_ENCHANTMENTS`, contexte BOOK de table, caps d'enclume appliqués trop tard après shift, ledger XP réutilisable/recalculé. Les résultats sont maintenant plafonnés AVANT transfert et l'XP réelle est seule remboursable.
12. Imports YAML anciens, cibles d'armure vs gemme exacte, `TOOLS` distincts des auto-tools, lignes invalides, copies de sections indépendantes, corrections à la tick suivante et héritage mob rétabli sont vérifiés par régressions dédiées.
13. Les menus conservent maintenant aussi les navigateurs d'enchantements compatibles, niveaux courants et clic droit retirer, cibles presets/exactes multiples, états EN PAUSE, toggle/suppression rapide de règles, reset des drops seuls, désactivation/rétablissement vanilla et confirmation d'écrasement minerai.
14. Les trois environnements de vérification sont tous sous `build/smoke-server` (`development`, `production`, `persistence`), jamais dans le `run-server` utilisateur. `productionPersistenceTest` lance trois processus officiels, vérifie trois PID distincts, le nonce natif d'un coffre et les journaux de sauvegarde/arrêt. Le helper `ColdPersistenceChecks` reste dans `src/qa`, séparé du JAR distribué.
15. Le cas multi-admin a d'abord été reproduit rouge146/1, puis corrigé par UUID stable pour toutes les lectures/éditions/copies/suppressions/retours. Une règle disparue annule explicitement l'action, sans toucher sa voisine ; les IDs anciens absents/dupliqués sont normalisés une fois.
16. Le cycle commun STOPPING/STOPPED/STARTED a d'abord été rouge156/3 : déduplication d'ancien UUID et menu ancien conservés. Le nettoyage central des files, sessions et caches est maintenant vérifié ; config, préférences et marqueurs persistants sont conservés. Ce test rejoue les hooks communs dans une JVM native, pas deux IntegratedServer réels avec client.
17. L'EULA des fixtures est locale, ignorée par Git, jamais automatiquement acceptée ni committée. `eula.example` vaut faux ; les tâches QA refusent un accord local absent/non accepté. Le README explique le préalable de lecture/acceptation par l'utilisateur et la séparation du serveur jouable.
18. La cause canne SUPPORT exige une vraie perte de support (`!state.canSurvive`). Un piston détruisant une canne encore soutenue conserve le butin vanilla ; le bonus de perte de support et l'exclusion des cannes placées restent inchangés. Régression physique d'abord rouge164/1 : piston réellement alimenté/étendu et quatre cannes au lieu d'une, tandis que le cas vraie perte de support passait déjà. Le JSON et le journal rouges sont archivés sous `persistence-history/pre-cane-support-20261024/`.

Empreinte SHA-256 du JAR final contrôlé : `649a1d6ed22f1d79239d2e5c714d09997f9a749673b5d2a6b322a5f9da671497`. Le JAR inclut sa licence et SnakeYAML2.5, sans vérificateur, fixture ou fichier EULA. `runServer --dry-run` ne dépend d'aucune tâche QA et conserve le lancement jouable normal.

L'audit détaillé de chaque famille de chemins et de chacun des menus de référence est dans `fabric-parity-audit.md`. Les points imposés par l'écosystème/les versions, ainsi que l'absence de preuve visuelle Fabric, y sont distingués des fonctionnalités effectivement implémentées.

## Points de différence documentés

Les permissions sont opérateur2/propriétaire solo, pas Bukkit. Les protections doivent être leurs équivalents Fabric. Interfaces vanilla, pas d'écran client custom ni de dépendance Mod Menu obligatoire. Les états/objets existants d'un autre loader ne sont pas convertis implicitement ; copier les chemins YAML est possible, mais les marqueurs Bukkit PDC ne sont pas les composants Fabric d'un monde séparé. Le champ optionnel `source` corrige l'identification du butin vanilla quand le matériau de sortie est différent.

Les répertoires de test sont exclus de Git. Aucun fichier, monde ou profil existant de l'utilisateur n'a été supprimé ou modifié. Aucun commit, branche ou push n'a été effectué par ce sous-agent ; l'agent principal centralise Git et les publications autorisées.
