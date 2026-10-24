# Validation de CutCleanReborn

Comme dans CustomRecipies, une commande depuis la racine contrôle les 25 variantes : Paper, Purpur, Spigot, Bukkit/CraftBukkit et Fabric sur 1.21.1, 1.21.11, 26.1.x (révision 26.1.2), 26.2 et 26.3. Un contrôle enregistré n'est pas une promesse de réussite : consulter le rapport généré après l'exécution complète.

```powershell
.\test-all.ps1
# Équivalent : .\validation\run-all-tests.ps1
```

Le mode `Full`, utilisé par défaut, vérifie :

1. la structure de chaque projet, ses sources locales ou explicitement partagées et la cohérence des versions ;
2. la parité des sources, configurations, tests et règles de compilation entre les variantes Paper, hors métadonnées propres à Minecraft ;
3. la compilation, les tests unitaires et les tests sur un vrai serveur isolé de la plateforme demandée, dont l'identité est vérifiée dans le journal ;
4. le nom et le contenu des JAR, leurs métadonnées et l'absence du plugin de test dans le JAR à distribuer.

Pour Fabric, le mode complet lance le véritable JAR de distribution dans le serveur officiel Fabric, hors des classes de développement Loom. Il contrôle les dépendances embarquées, l'identité Minecraft/Loader, les tests natifs et l'égalité du JAR installé avec celui à distribuer. Les sources partagées et adaptateurs de version entrent dans l'empreinte de validation ; toute modification pendant les tests invalide le résultat.

Les sources doivent rester identiques entre les variantes Paper en dehors des adaptations documentées. Le contrôle exclut les métadonnées `plugin.yml` (plugin et vérificateur), les paramètres de compilation Java/API et normalise uniquement `view.open()` en son équivalent ancien `player.openInventory(view)` dans le fichier de saisie à enclume. Toutes les autres lignes de ce fichier restent contrôlées. La famille `26.1.x` porte son nom de dossier/JAR mais déclare sa révision concrète `26.1.2` dans les métadonnées.

Les rapports sont disponibles dans `validation/results/latest.md` et `latest.json`, et les journaux par projet dans le même dossier. Le code de sortie est `0` si tous les contrôles obligatoires passent, `1` sinon. Les résultats générés ne sont pas suivis par Git.

## Autres modes

```powershell
.\test-all.ps1 -Mode Quick                  # Structure et parité, sans compilation
.\test-all.ps1 -Mode Compile                # Structure, parité et compilation des sources/tests
.\test-all.ps1 -Mode Full -RerunTasks        # Reconstruction et nouvelle exécution forcées
.\test-all.ps1 -Mode Full -Version Paper_26.3
.\test-all.ps1 -Mode Full -Platform Purpur  # Les cinq vrais serveurs Purpur
.\test-all.ps1 -Mode Full -Platform Fabric  # Les cinq serveurs officiels Fabric
```

## Prérequis

Java 21 est nécessaire pour 1.21.1/1.21.11 et Java 25 pour 26.x. Gradle sélectionne les toolchains déclarées par chaque projet, utilise son cache habituel et peut demander un accès réseau pour les dépendances.

Les tests plugins utilisent le JAR `run-server/<paper|purpur|spigot|bukkit>-<Minecraft>.jar` et l'EULA déjà acceptée du projet. Les installations existantes sont conservées. Pour préparer un nouveau serveur Paper/Purpur :

```powershell
cd Paper_26.3
.\gradlew.bat downloadPaperServer
# Dans un module Purpur : .\gradlew.bat downloadServer
```

Lire et accepter soi-même l'EULA Minecraft dans `run-server/eula.txt` avant d'exécuter la suite native. La validation ne télécharge pas de nouveau serveur et ne modifie ni les mondes ni la configuration de `run-server/`.

Spigot/CraftBukkit et leur API se construisent depuis les sources officielles avec [l'amorçage BuildTools](../platform-bukkit/tools/README.md), sans API précompilée redistribuée. Fabric prépare son serveur officiel avec `productionSmokeTest`.

Chaque suite native travaille exclusivement sous `build/` (plugins : `build/smoke-server/` ; tous les Fabric : `build/smoke-server/production/`), restaure ses données de test et s'arrête automatiquement. Aucun vérificateur n'entre dans le JAR distribué. Les exports matérialisent les dépendances partagées nécessaires à une compilation autonome ; `validate-exports.ps1` vérifie ces branches dans des copies sans modules voisins.

## Arrêts et redémarrages à froid

Pour les 20 plugins, `./validation/run-persistence-tests.ps1` lance trois vrais processus Java successifs par projet dans un nouvel essai `build/persistence-server/`. Le helper séparé vérifie coffre/objets avec données persistantes, marqueurs de chunks, préférences UUID et réservation/destruction de la masse. Les reçus conservent PID distincts, nonce, empreintes et preuve d'arrêt/sauvegarde propre ; les essais interrompus restent des échecs, sans faux résultat vert.

Pour les cinq Fabric, `productionPersistenceTest` réalise le même principe en trois étapes dans `build/smoke-server/persistence/`, avec un nonce conservé dans le coffre natif et des journaux par étape. Le mode global `Full` inclut cette tâche. Les 20 plugins ont leur commande froide distincte pour conserver leurs reçus détaillés. Ces contrôles ne prouvent ni le rendu client, ni le paiement réel d'une transaction effectuée par un joueur connecté.

Les reçus `validation/results/evidence/` lient un succès aux sources et au SHA-256 du JAR réellement testé. Le résultat natif seul ne prouve pas une interaction visuelle ou un redémarrage à froid : Fabric ajoute un champ `cold_restart` contrôlé séparément, tandis que les plugins ont les reçus du triplet indépendant. Les reçus manuels ne doivent être ajoutés qu'après les scénarios réellement exécutés : voir [TESTS_CODEX.md](TESTS_CODEX.md). Le collecteur refuse une preuve périmée et distingue explicitement tests natifs, persistance et tests avec joueur.

Les [reçus client de schéma 2](client-evidence.md) distinguent les observations partielles des dix familles obligatoires (onze pour Fabric avec le solo). Un ancien `result: PASS` accompagné d'un seul nom de scénario ne valide plus un parcours complet. `.\validation\test-client-evidence.ps1` vérifie ce contrôle avec des données synthétiques, sans jeu ni serveur.
