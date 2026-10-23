# CutCleanReborn — Paper 26.2

Plugin Paper 26.2, Java 25. Toutes les fonctionnalités se configurent avec `/ccr`.

Branche Git : [paper/26.2](https://github.com/Zazac1/Cut-Clean-Reborn/tree/paper/26.2). Le projet est directement à la racine de cette branche, comme dans CustomRecipe.

Projet autonome, organisé comme les variantes de CustomRecipies. Ce dossier contient ses propres sources, configuration, tests, licence et compilation ; aucun dossier source voisin n'est nécessaire.

- `src/main/java/` : fonctionnalités et menus du plugin.
- `src/main/resources/` : `plugin.yml` et configuration par défaut.
- `src/test/` : tests unitaires.
- `src/smoke/` : vérifications sur un serveur Paper isolé.
- `run-server/` : serveur de développement et mondes locaux conservés, non suivis par Git.
- `build/libs/` : JAR généré.
- `CHANGELOG.md` : changements de cette variante.

```powershell
.\gradlew.bat build
.\gradlew.bat smokeTest
.\gradlew.bat runServer
```

Le JAR est généré dans `build/libs/` au format `cutcleanreborn-<version>+26.2.jar`, par exemple `cutcleanreborn-0.1.0-SNAPSHOT+26.2.jar`.
Les anciens JAR de ce dossier sont conservés dans `build/artifact-backups/`.
Se connecter avec un client Minecraft 26.2 normal pour jouer.

`smokeTest` utilise le JAR Paper et l'EULA existants dans `run-server/`, mais exécute ses vérifications dans `build/smoke-server/`, sans toucher aux mondes de développement.
Dans la branche globale `main`, `.\test-all.ps1` valide les deux projets Paper et vérifie leur parité. Ce script global n'est pas requis pour compiler cette branche autonome. Les futurs portages seront ajoutés séparément, sur demande.

Voir le [README global](https://github.com/Zazac1/Cut-Clean-Reborn/blob/main/README.md) pour les commandes, les règles de gameplay et les tests, et le [guide des branches](https://github.com/Zazac1/Cut-Clean-Reborn/blob/main/BRANCHES.md) pour les synchroniser.
