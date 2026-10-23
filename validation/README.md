# Validation de CutCleanReborn

Comme dans CustomRecipies, une commande depuis la racine contrôle les variantes présentes. Pour l'instant, elle couvre Paper 26.2 et Paper 26.3 ; les futurs portages auront leurs propres contrôles lorsqu'ils seront ajoutés.

```powershell
.\test-all.ps1
# Équivalent : .\validation\run-all-tests.ps1
```

Le mode `Full`, utilisé par défaut, vérifie :

1. la structure autonome de chaque projet et la cohérence des versions ;
2. la parité des sources, configurations, tests et règles de compilation entre les variantes Paper, hors métadonnées propres à Minecraft ;
3. la compilation, les tests unitaires et les tests sur un vrai serveur Paper isolé ;
4. le nom et le contenu des JAR, leurs métadonnées et l'absence du plugin de test dans le JAR à distribuer.

Les sources doivent rester identiques entre les deux variantes Paper actuelles. Toute adaptation future spécifique à une version devra être documentée et accompagnée d'un ajustement explicite du contrôle de parité.

Les rapports sont disponibles dans `validation/results/latest.md` et `latest.json`, et les journaux par projet dans le même dossier. Le code de sortie est `0` si tous les contrôles obligatoires passent, `1` sinon. Les résultats générés ne sont pas suivis par Git.

## Autres modes

```powershell
.\test-all.ps1 -Mode Quick                  # Structure et parité, sans compilation
.\test-all.ps1 -Mode Compile                # Structure, parité et compilation des sources/tests
.\test-all.ps1 -Mode Full -RerunTasks        # Reconstruction et nouvelle exécution forcées
.\test-all.ps1 -Mode Full -Version Paper_26.3
```

## Prérequis

Java 25 est nécessaire. Gradle utilise son cache habituel et peut demander un accès réseau pour les dépendances.

Les tests natifs utilisent le JAR `run-server/paper-<Minecraft>.jar` et le fichier `run-server/eula.txt` déjà présents dans chaque projet. Les deux installations existantes ont été conservées lors de la réorganisation. Pour une nouvelle installation :

```powershell
cd Paper_26.3
.\gradlew.bat downloadPaperServer
```

Lire et accepter soi-même l'EULA Minecraft dans `run-server/eula.txt` avant d'exécuter la suite native. La validation ne télécharge pas de nouveau serveur et ne modifie ni les mondes ni la configuration de `run-server/`.

Chaque suite native travaille exclusivement dans `Paper_<version>/build/smoke-server/`, restaure sa configuration de départ à chaque lancement, s'arrête automatiquement et n'inclut jamais son plugin de vérification dans le JAR distribué. Une compilation ou des tests simulés ne remplacent pas une vérification en jeu : voir [TESTS_CODEX.md](TESTS_CODEX.md).
