# Audit de parité Paper 26.3 → Fabric 1.21.1

Audit du 24 octobre 2026. Référence : les 48 classes principales du package `fr.isaac.cutcleanreborn.paper` de Paper 26.3, son `config.yml`, ses commandes et ses tests natifs. Cible inspectée : package `fr.isaac.cutcleanreborn.fabric`, mixins, ressources et vérificateurs séparés de Fabric 1.21.1. Les différences signalées aux quatre ports Fabric modernes doivent être validées séparément dans leur propre suite ; ce rapport ne leur attribue pas les résultats 1.21.1.

L'audit ne se limite pas au nombre de tests verts : les premières 91 assertions ne détectaient ni les livres enchantés ni le transfert rapide d'enclume. Les deux échecs ont été reproduits dans le serveur officiel avec le JAR remappé, puis corrigés. Aucun interrupteur sans implémentation ni fonction désactivée faute de portage n'a été retenu.

## Sources et correspondance des services

| Référence Paper | Implémentation Fabric inspectée |
| --- | --- |
| `CutCleanRebornPlugin`, `CutCleanCommands` | `CutCleanReborn`, `Settings` : cycle de vie, Brigadier, aliases, validation YAML et sauvegarde |
| `GameplayMath`, `LootSupport` | `GameplayMath`, `LootRules.stacks/addRule/validRule` : mêmes politiques de quantité, Fortune, Looting et XP bornée |
| `MobDropService` | `LootRules.beginDeath/capture/finishDeath`, `LivingLootMixin`, `EntityLootMixin` |
| `OreDropService`, `OreCatalog` | `LootRules.block/ORES`, `BlockLootMixin`, `VanillaCatalog.ore` |
| `VanillaDropCatalog` | `VanillaCatalog.mob` : tables réelles, joueur fictif non connecté, aucun spawn de créature |
| `TreeCutterService` | `Timber`, callbacks BEFORE/AFTER/CANCELED et casse réelle par `ServerPlayerGameMode.destroyBlock` |
| `PlacedBlockTracker` | `Origins` et `PlacedBlockMixin`, fichier par monde et par dimension |
| `BetterSugarCaneService` | `LootRules.block/caneQuantity`, causes distinctes et marqueurs d'origine |
| `AutoEnchantService` | `InventoryRules.auto/snapshot/restore`, composants persistants natifs |
| `CleanStackService` | `InventoryRules.clean/setCleanTarget`, `CraftPreviewMixin`, ramassage et correction d'inventaire |
| `StuffRulesService` | `InventoryRules`, `EnchantingMixin`, `AnvilRefundMixin`, hooks d'inventaire/équipement/craft/destruction, `WorldState` |

## Chaque famille de chemins de configuration

Les chemins ci-dessous restent ceux de Paper. Les noms d'objets/enchantements sont résolus dans le registre de Minecraft 1.21.1 ; un objet apparu dans une version ultérieure n'est pas artificiellement déclaré disponible.

| Chemins | Contrat vérifié dans le code cible |
| --- | --- |
| `animal-drops.cook` | Cuisson animale héritée, désactivable par créature, sept viandes/poissons transformés sans perdre les composants |
| `animal-drops.multiplier` | Quantité animale finale ×1–5 ; s'applique au vanilla et aux overrides, pas aux extras |
| `animal-drops.xp-bonus` | Défaut animal 0–50 ; addition à l'XP native, pas de remplacement de l'XP vanilla |
| `animal-drops.collection-range` | 0–32, même monde, distance au tueur ; auto-enchant, Clean personnel, inventaire puis débordement au sol |
| `animal-drops.item-overrides.<MOB>` | Ancien remplacement de nourriture principale conservé, accessible comme auparavant dans le YAML |
| `mobs.<MOB>.cooked-food` | Toute créature vivante sauf joueur ; héritage animal ou faux pour les autres. Le retour à la valeur héritée depuis l'UI retire la clé spécifique |
| `mobs.<MOB>.xp-bonus` | 0–1 000 000, héritage si absent ; retour à la valeur héritée rétablit également les changements globaux futurs |
| `mobs.<MOB>.vanilla-overrides` | Liste d'entrées `item`, `minimum`, `maximum`, `chance` ; viande crue/cuite reconnue comme même source. Une entrée invalide est ignorée AVANT suppression du vanilla |
| `mobs.<MOB>.drops` | Extras indépendants ; quantité 0–64, minimum ≤ maximum, chance finie 0–100 ; validation identique au lecteur Paper |
| `mobs.<MOB>.last-edited`, ancien `edit-count` | Date de modification conservée, ancien compteur de clics retiré lors d'une édition ; score basé sur différences actives et entrées valides |
| `mob-drops.looting-vanilla`, `.looting-custom` | Bonus additif 0..Looting sur les règles modifiées/extras selon les deux options. Les drops vanilla non modifiés conservent leur calcul Minecraft, comme Paper |
| `ore-drops.auto-smelt` | Fer/or/cuivre bruts et débris antiques, composants préservés, XP de four proportionnelle avec arrondi aléatoire identique |
| `ore-drops.fortune-vanilla`, `.fortune-custom` | Réévaluation native sans Fortune lorsque désactivée sur vanilla ; multiplicateur de règle 1..(Fortune+1), options séparées |
| `ore-drops.<bloc>.xp-bonus` | 0–1 000 000, seulement si outil adapté et en Survie, ajouté à l'XP native |
| `ore-drops.<bloc>.vanilla-overrides`, `.drops` | Même schéma d'entrée ; première règle vanilla valide, extras séparés. Silk Touch garde le bloc et n'active ni ces règles ni la fusion |
| Blocs `ore-drops.<bloc>` | 19 variantes 1.21.1 : pierre/deepslate pour charbon, fer, cuivre, or, redstone, lapis, diamant, émeraude ; quartz, or du Nether et débris antiques |
| `tree-cutter.enabled` | Arbres naturels en Survie seulement ; au moins deux bûches, même type, feuilles naturelles connectées, exclusion des constructions |
| `tree-cutter.max-logs`, `.blocks-per-tick` | Bornes 2–2048 et 1–64 ; un arbre hors limite garde la casse normale ; files réparties entre ticks |
| `tree-cutter.hand`, `.axes.<MATERIAL>` | Chaque hache distincte ; option main/autres outils. Outil capturé avant le premier bloc, arrêt à sa casse/changement |
| `tree-cutter.sneak-only` | Condition d'accroupissement pour démarrer, sans changer la casse vanilla sinon |
| `tree-cutter.break-time.enabled`, `.percent` | Effort 0–100%, `ceil(1+(logs-1)*percent/100)` cycles sur le premier bloc ; progression expire après 15 s et est retirée à la déconnexion |
| `tree-cutter.break-leaves` | Feuilles naturelles, gradient de distance vanilla, après les bûches ; respect de la casse native, protections et durabilité |
| `tree-cutter.apple-chance` | Override optionnel 0–100%, indépendant du master Timber, chêne/chêne noir, exclusion cisailles/Silk ; retrait de clé restaure vraiment le vanilla |
| `automatic-enchantments.enabled`, `.refresh-version` | Master et rafraîchissement explicite ; UUID par objet, sauvegarde des enchantements d'origine et snapshot ; modifications joueur préservées |
| `automatic-enchantments.armor.<MATERIAL>.<enchant>` | Armures disponibles, compatibilité native, conflits natifs, niveau valide 1–255 ; config invalide ne crée pas d'UUID |
| `automatic-enchantments.tools.<MATERIAL>.<enchant>` | Outils de la référence y compris masse, arc/arbalète/trident, cisailles, canne à pêche, brosse et briquet ; copie même matériau, compatibles seulement et sans écrasement, même si ancien nom d'enchantement court |
| `clean-stacks.players.<UUID>` | Préférence personnelle persistante sans permission administrateur ; activation normalise immédiatement l'inventaire |
| `clean-stacks.wood.<MATERIAL>` | Bûches, bûches écorcées, tiges Nether, planches ; pas de blocs `*_WOOD` fabriqués ; sortie arbitraire valide et point fixe explicite |
| `clean-stacks.stone.<MATERIAL>` | Dix pierres brutes de référence ; pas de pierres finies, Nether/End ; défaut pierre si cible absente/invalide |
| Cycles Clean Stacks | Canonisation déterministe, idempotence, quantité conservée ; objets nommés/enchantés/avec données personnalisées protégés |
| `game-rules.stuff-rules.enabled` | Master armures/caps/masse ; aucune règle effacée en le désactivant ; correction à la tick suivante d'un changement d'inventaire/équipement, et filet périodique 20 ticks |
| `.armor-rules[].id/enabled/target/maximum` | UUID, règle initialement désactivée, toutes armures/famille/liste exacte ; maximum 0–4 pièces équipées seulement ; surplus inventaire ou sol ; cible vide retombe sur ancien `material` |
| `.enchantment-caps[].id/enabled/target/enchantment/maximum` | Groupes ALL/ARMOR/SWORDS/TOOLS ou liste exacte, cible vide retombe sur ancien `item`, nom enchant normalisé, maximum positif ; minimum des caps superposés |
| Cible `TOOLS` Stuff | Ensemble strict Paper : épées/pioche/hache/pelle/houe/masse/arc/arbalète/trident, distinct du catalogue plus large des auto-enchantements |
| Cibles `DIAMOND` et `minecraft:diamond` | Famille pour règles d'armure, gemme exacte pour caps ; aucune confusion de catégorie dans la sélection d'objets exacts |
| `.enchantment-caps[].refund-xp/xp-per-removed-level` | Option et montant positif/nul sur entier complet, règle au plafond réellement retenu ; jamais plus que niveaux payés, pas de paiement = pas de remboursement |
| `.unique-mace.enabled/exists/id` | Une identité CCR réservée synchroniquement ; crafting normal curseur vide ; shift/touche numérique/jet refusés avant consommation ; destruction/usure/vide/despawn libèrent, retrait/ramassage/chunk unload non |

Le préfixe des trois dernières familles est toujours `game-rules.stuff-rules`. Le champ Fabric optionnel `source` dans les overrides de créatures permet de distinguer l'objet supprimé de sa sortie ; son absence conserve le contrat historique `item`.

## Menus : toutes les options, pas seulement les premiers écrans

| Menus Paper parcourus | Parcours Fabric correspondant / fonctions conservées |
| --- | --- |
| `ConfigMainMenu`, `MenuSupport`, `ConfirmationMenu`, `AnvilTextInput` | Sept catégories, en-tête, retour parent, fermeture directe, pagination indépendante, saisie sans coût, erreur explicite, annulation, confirmations, permission revérifiée au clic ; reload invalide garde l'ancien état, reload valide ferme les vues et invalide les retours en attente |
| `MobListMenu`, `MobGlobalSettingsMenu`, `MobConfigurationMenu` | Registre des créatures vivantes, recherche, tri modifiées/récentes, compte de différences, paramètres globaux, cuisson/XP par mob et reset |
| `DropListMenu`, `DropEditorMenu` | Catalogue vanilla réel + aperçu Looting III, overrides manuels, changement item/range/chance, extras, désactivation 0% séparée du rétablissement vanilla, clic droit désactiver/rétablir ; reset de drops sans effacer cuisson/XP |
| `OreListMenu`, `OreConfigurationMenu`, `OreDropListMenu`, `OreDropEditorMenu` | Toutes variantes, paramètres Fortune/fusion, plages de base/Fortune III, XP, vanilla/extras, reset et copie complète avec confirmation d'écrasement |
| `TreeCutterMenu`, `TreeAxeMenu`, `TreeBreakTimeMenu`, `TreeHarvestMenu` | Master, axes/main, sneak, effort, maximum, débit, récolte feuilles, chance pommes, restauration vanilla ; les sous-options conservées mais inactives sont marquées EN PAUSE |
| `AutoEnchantCategoryMenu`, `AutoEnchantItemListMenu`, `AutoEnchantItemMenu`, `AutoEnchantmentsListMenu` | Armures/outils, liste et recherche, édition/ajout/suppression, navigateur d'enchantements compatibles, recherche, niveau courant préchargé, clic droit retirer, copie même matériau sans écraser, refresh confirmé |
| `CleanStackMenu`, `CleanStackMaterialMenu` | Préférence personnelle, bois/pierre, conversion par source ou groupée (toutes, bûches non écorcées, écorcées, planches, pierres), clic droit reset source, quantité et métadonnées protégées |
| `BetterSugarCaneMenu`, `SugarCaneCauseMenu` | Master puis causes joueur/support indépendantes, enabled/chance/quantité finale et reset confirmé ; Échap revient cause→canne→racine |
| `StuffRulesMenu`, `ArmorRuleListMenu`, `ArmorRuleEditorMenu` | Accès direct, master, règles initialement OFF, édition maximum/cible, groupes/familles et navigateur multi-sélection d'objets exacts, copie désactivée, suppression confirmée, clic droit toggle et Maj-clic suppression confirmée |
| `EnchantmentCapListMenu`, `EnchantmentCapEditorMenu`, `EnchantmentPickerMenu`, `EnchantmentRefundMenu` | Règles initialement OFF, groupes ou objets exacts, registre d'enchantements/recherche, maximum entier positif, option/remboursement borné, duplication et suppression |
| `RuleTargetMenu`, `UniqueMaceMenu` | Préréglages distincts de la sélection exacte, saisie de listes conservée, recherche multi-sélection avec validation explicite ; état de masse persistante et explications de réservation/destruction |

Les emplacements de cases et le nombre de lignes ne sont pas identiques entre loaders : Fabric utilise un coffre vanilla de six lignes avec 36 cases de contenu. Aucun réglage ne dépend d'un paquet client personnalisé. Le niveau de retour est sémantique, pas l'historique des pages ou des recherches.

## Corrections de l'audit et preuves discriminantes

1. **Livres enchantés** : lecture native de `STORED_ENCHANTMENTS`, pas seulement `ENCHANTMENTS`. L'entrée BOOK de la table demeure la cible de règle même lorsque la sortie devient ENCHANTED_BOOK. Une vraie transaction de table payée et un indice visible de niveau >1 évitent un test probabiliste trompeur.
2. **Enclume shift-click** : plafonner dans `onTake` est trop tard, le transfert a déjà vidé la pile. Correction dans `createResult RETURN`, puis journal de transaction distinct capturé avant `onTake` et conservé pendant le recalcul des entrées. Tests normal, shift, inventaire plein, nouvelle tentative et absence de réutilisation du journal.
3. **XP** : remboursement `min(coût, niveaux avant-après, demande)`, jamais sur correction passive. Le bonus mob configuré est indépendant de `doMobLoot`, comme le `setDroppedExp` inconditionnel de Paper.
4. **Configurations anciennes** : noms sans namespace/majuscules, target vide→ancien item/material, caps invalides ignorés, auto hors1..255 ignoré sans marqueur, listes exactes invalides rejetées intégralement, distinction gemme/famille et auto-tools/Stuff-TOOLS.
5. **Mutabilité des copies** : copie récursive Map/List dans `Settings.set`, donc une modification d'un minerai copié n'édite pas le minerai source et les entrées programmatiques immuables n'explosent pas pendant une édition.
6. **Équipement** : inventaire, équipement, transfert de menu, ramassage et connexion déclenchent une correction dédupliquée à la tick suivante ; garde de réentrance et exclusion des joueurs d'aperçu sans connexion.
7. **Masse** : toutes les voies non normales refusées avant transfert/consommation, pas seulement le shift ; les hooks de destruction distinguent vraie destruction et simple retrait.
8. **Vanilla/aperçus** : les trois surcharges `dropResources` sont ciblées explicitement ; `getDrops` seul est pur et ne génère ni XP ni retrait d'origine. Créer un override depuis l'aperçu Looting utilise les valeurs de BASE pour éviter un double bonus.
9. **Héritage mob** : remettre une valeur à son défaut retire l'override ; une modification globale ultérieure continue donc de s'appliquer. Le reset limité aux drops garde la cuisson/XP.
10. **Plusieurs administrateurs** : rouge natif146 réussites/1 échec avec deux sessions distinctes, A en saisie sur règle1 et B supprimant règle0. Les éditions, cibles, copies, confirmations de suppression, toggles et retours résolvent maintenant l'ID au moment d'agir ; une règle supprimée annule explicitement l'action et retourne à la liste. IDs anciens absents/dupliqués normalisés une fois. Cas ciblés également vérifiés pour suppression de la règle en saisie, copie d'une règle disparue, suppression différée et Échap après décalage.
11. **Cycle de vie commun au solo** : rouge natif156 réussites/3 échecs après STOPPING/STOPPED/STARTED : déduplication d'un ancien UUID, correction bloquée à la tick suivante, menu ancien rouvert. Le nettoyage central des données transitoires couvre les trois hooks, sans effacer les réglages ou fichiers persistants.
12. **Cause canne SUPPORT** : `BetterSugarCaneService.onSupportBreak` de Paper exige que le bloc ne puisse plus être placé. L'équivalent Fabric est maintenant `!state.canSurvive(level,pos)` pour les drops sans joueur. Une régression physique a d'abord démontré164 réussites/1 échec : un vrai piston détruisait une canne encore soutenue avec quantité4 au lieu de1. Le second cas, retrait du sol avec destruction physique et quantité4, était déjà vert ; la garde préserve cette vraie perte de support, les exclusions placées et la casse joueur.

## Audit groupé des états conservés entre sessions

| État | Politique finale |
| --- | --- |
| Singleton `CutCleanReborn.instance` | Conservé : service Fabric enregistré une seule fois. Pointeur `server` retiré à STOPPED puis réassigné à STARTED |
| `pending`, compteur `ticks` | Queue vidée aux trois hooks ; compteur remis à zéro au démarrage |
| `InventoryRules.scheduled/correcting` | Sets vidés aux trois hooks ; un même UUID dans la prochaine session peut immédiatement être corrigé |
| `Timber.active/ready/progress/internal` | Vidés aux trois hooks et pour le joueur à sa déconnexion ; aucune référence d'ancien `ServerLevel` conservée |
| `LootRules.deaths/probe/dropping` | ThreadLocal retirés aux trois hooks, y compris le contexte de garde de drops |
| `Menus.opening/epoch` | Map vidée, génération de session changée aux trois hooks ; fermeture explicite des vues à STOPPING. Une ancienne vue ne peut pas remettre son parent en attente pour la nouvelle session |
| `VanillaCatalog.cache` | Vidé aux trois hooks : les tables/échantillons d'une sauvegarde ou d'un datapack ne sont pas réutilisés dans une autre |
| `Origins`, `WorldState` | Sauvegarde avant arrêt, références retirées, nouveaux objets chargés depuis les fichiers du monde au démarrage |
| `Settings`, `mobSnapshots` | Configuration globale de profil et empreintes correspondantes conservées intentionnellement ; ce ne sont pas des références de monde ni des états de menu |
| Registre enchantements | Résolu depuis le serveur courant à chaque accès, aucun Holder de monde dans un cache global |
| Journaux enclume/table | Champs de chaque menu vanilla, pas de singleton statique ; fermeture des vues et retrait des joueurs libèrent les instances |
| Constantes/maps statiques d'objets | Données immuables du registre natif de la version, pas d'état de partie |

Le test lifecycle est étiqueté « common lifecycle hooks » : callbacks communs rejoués dans un serveur natif et la même JVM. Il ne prétend pas instancier `IntegratedServer` ni créer deux sauvegardes solo visibles. Cette dernière preuve reste un scénario client réel distinct.

## Limites et différences à ne pas masquer

- **Permissions** : Fabric n'a pas la permission Bukkit `cutcleanreborn.config`. Contrôle opérateur niveau2 et propriétaire solo côté serveur ; préférence personnelle ouverte à tous. Ce n'est pas une compatibilité avec un gestionnaire de permissions Bukkit.
- **Protections** : les plugins Paper ne tournent pas sous Fabric. Les callbacks de protection Fabric et le pipeline Minecraft réel sont honorés ; l'interopérabilité avec un mod de protection précis demande son essai dédié.
- **Données d'un autre loader** : les marqueurs Bukkit PDC de blocs/objets ne sont pas implicitement convertis en composants et fichiers Fabric. Les chemins YAML sont compatibles ; une migration de monde existant entre loaders n'est pas revendiquée.
- **Versions du jeu** : Minecraft1.21.1 n'a pas les objets/mobs ajoutés plus tard (ex. armure cuivre). Ils ne peuvent pas être ajoutés en simple port serveur. Les registres26.x ont été signalés à l'agent moderne pour ne pas y figer les familles1.21.1.
- **Catalogue vanilla** : 256 échantillons de la table d'un mob dans un état par défaut ne décrivent pas exhaustivement chaque couleur, âge, équipement ou drop très rare. C'est déjà la méthode de la référence ; les vrais drops runtime gardent leur contexte natif et les ajouts manuels restent accessibles. Un aperçu n'est pas une preuve sur toutes les variantes.
- **État mondial** : origines par dimension et masse par sauvegarde dans des fichiers atomiques. Deux parties solo différentes ne partagent pas une réservation de masse. Une sauvegarde Paper n'est pas modifiée en place.
- **Validation visuelle** : joueur synthétique et appels serveur natifs ne prouvent ni lisibilité, ni rendu, ni serveur intégré avec Fabulously Optimized. Le contrôle réel avec client reste dans le suivi principal. Aucun résultat « visuel PASS » n'est créé ici.

## Validation reproductible

Dans le module Fabric1.21.1 : `gradlew.bat build productionSmokeTest productionPersistenceTest`. Résultat final : **15 tests unitaires, 165 contrôles natifs et22 contrôles froids, zéro échec**. La passe précédente validait146 contrôles natifs, puis152 après le correctif multi-admin ; les7 contrôles lifecycle et6 contrôles physiques canne/piston complètent la suite finale. Les JSON contiennent les labels exacts et `realPlayerVisual:false`. L'erreur YAML volontaire du scénario reload invalide apparaît dans le journal et est suivie d'une assertion de refus réussi : elle ne doit pas être confondue avec une erreur de chargement réelle.

Les vérifications à froid sont séparées dans `productionPersistenceTest` (alias `coldPersistenceTest`) : trois JVM officielles successives dans `build/smoke-server/persistence`, coffre vanilla sauvegardé dans le chunk contenant des objets natifs, comparaisons de leurs identités et d'un nonce entre processus, puis retrait et deuxième lecture. Les trois journaux de sauvegarde/arrêt sont archivés. Le résultat final de cette suite est consigné dans le journal de portage, sans réutiliser les fixtures de `run-server`.

`runServer` reste un serveur jouable sans vérificateur ni arrêt automatique. Les tâches QA n'installent aucun helper dans le JAR distribué. La validation globale25 combinaisons et les preuves de JAR sont centralisées par l'agent principal, après gel des sources.

Une branche neuve ne contient pas d'acceptation EULA : `src/qa/fixtures/eula.txt` est ignoré par Git, `eula.example` vaut faux, les tâches refusent de lancer QA sans acceptation locale explicite. Voir le README du module ; `build` ne réalise jamais cette autorisation.
