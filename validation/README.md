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

Chaque suite native travaille exclusivement sous `build/` (plugins : `build/smoke-server/` ; Fabric moderne : `build/smoke-server/production/` ; Fabric1.21.1 : `build/production-server/`), restaure ses données de test et s'arrête automatiquement. Aucun vérificateur n'entre dans le JAR distribué. Les exports matérialisent les dépendances partagées nécessaires à une compilation autonome ; `validate-exports.ps1` vérifie ces branches dans des copies sans modules voisins.

Les reçus `validation/results/evidence/` lient un succès aux sources et au SHA-256 du JAR réellement testé. Ils ne prouvent pas une interaction visuelle ou un redémarrage à froid. Les reçus manuels ne doivent être ajoutés qu'après les scénarios réellement exécutés : voir [TESTS_CODEX.md](TESTS_CODEX.md). Le collecteur refuse une preuve périmée et distingue explicitement tests natifs et tests avec joueur.
