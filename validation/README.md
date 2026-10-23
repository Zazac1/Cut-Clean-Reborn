# Validation de CutCleanReborn

Comme dans CustomRecipies, une commande depuis la racine contrôle les variantes présentes. Elle couvre actuellement les cinq projets Paper et les cinq projets Purpur : 1.21.1, 1.21.11, 26.1.x (révision 26.1.2), 26.2 et 26.3. Les autres plateformes auront leurs propres contrôles lorsqu'elles seront ajoutées.

```powershell
.\test-all.ps1
# Équivalent : .\validation\run-all-tests.ps1
```

Le mode `Full`, utilisé par défaut, vérifie :

1. la structure de chaque projet, ses sources locales ou explicitement partagées et la cohérence des versions ;
2. la parité des sources, configurations, tests et règles de compilation entre les variantes Paper, hors métadonnées propres à Minecraft ;
3. la compilation, les tests unitaires et les tests sur un vrai serveur isolé de la plateforme demandée, dont l'identité est vérifiée dans le journal ;
4. le nom et le contenu des JAR, leurs métadonnées et l'absence du plugin de test dans le JAR à distribuer.

Les sources doivent rester identiques entre les variantes Paper en dehors des adaptations documentées. Le contrôle exclut les métadonnées `plugin.yml` (plugin et vérificateur), les paramètres de compilation Java/API et normalise uniquement `view.open()` en son équivalent ancien `player.openInventory(view)` dans le fichier de saisie à enclume. Toutes les autres lignes de ce fichier restent contrôlées. La famille `26.1.x` porte son nom de dossier/JAR mais déclare sa révision concrète `26.1.2` dans les métadonnées.

Les rapports sont disponibles dans `validation/results/latest.md` et `latest.json`, et les journaux par projet dans le même dossier. Le code de sortie est `0` si tous les contrôles obligatoires passent, `1` sinon. Les résultats générés ne sont pas suivis par Git.

## Autres modes

```powershell
.\test-all.ps1 -Mode Quick                  # Structure et parité, sans compilation
.\test-all.ps1 -Mode Compile                # Structure, parité et compilation des sources/tests
.\test-all.ps1 -Mode Full -RerunTasks        # Reconstruction et nouvelle exécution forcées
.\test-all.ps1 -Mode Full -Version Paper_26.3
.\test-all.ps1 -Mode Full -Platform Purpur  # Les cinq vrais serveurs Purpur
```

## Prérequis

Java 21 est nécessaire pour 1.21.1/1.21.11 et Java 25 pour 26.x. Gradle sélectionne les toolchains déclarées par chaque projet, utilise son cache habituel et peut demander un accès réseau pour les dépendances.

Les tests natifs utilisent le JAR `run-server/paper-<Minecraft>.jar` ou `run-server/purpur-<Minecraft>.jar`, ainsi que le fichier `run-server/eula.txt` déjà présents dans chaque projet. Les deux installations existantes ont été conservées lors de la réorganisation. Pour une nouvelle installation :

```powershell
cd Paper_26.3
.\gradlew.bat downloadPaperServer
# Dans un module Purpur : .\gradlew.bat downloadServer
```

Lire et accepter soi-même l'EULA Minecraft dans `run-server/eula.txt` avant d'exécuter la suite native. La validation ne télécharge pas de nouveau serveur et ne modifie ni les mondes ni la configuration de `run-server/`.

Chaque suite native travaille exclusivement dans `<Plateforme>_<version>/build/smoke-server/`, restaure sa configuration de départ à chaque lancement, s'arrête automatiquement et n'inclut jamais son plugin de vérification dans le JAR distribué. Dans `main`, Purpur partage les sources du module Paper de la même version ; son export de branche matérialise ces sources afin de rester compilable seul. Une compilation ou des tests simulés ne remplacent pas une vérification en jeu : voir [TESTS_CODEX.md](TESTS_CODEX.md).
