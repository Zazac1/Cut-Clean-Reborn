# Organisation des branches Git

Le modèle suit [CustomRecipe](https://github.com/Zazac1/CustomRecipe) : une branche `<loader>/<Minecraft>` contient directement un projet compilable à sa racine, sans dossier parent supplémentaire.

| Branche | Rôle | Contenu à la racine |
| --- | --- | --- |
| `main` | Vue globale et développement coordonné | 25 modules ciblés, code partagé, validation et documentation |
| `paper/<Minecraft>` | Cinq variantes Paper vérifiées automatiquement | Contenu autonome du module Paper |
| `purpur/<Minecraft>` | Cinq variantes Purpur vérifiées automatiquement | Module + copie exacte des sources Paper correspondantes + compilation partagée matérialisée |
| `spigot/<Minecraft>`, `bukkit/<Minecraft>` | Portages en cours | Activation uniquement après validation ; code partagé matérialisé à l'export |
| `fabric/1.21.1` | Port natif validé automatiquement, essais client/solo restant à réaliser | Projet Fabric autonome, avec ses tests séparés |
| Autres `fabric/<Minecraft>` | Revalidation finale des portages en cours | Activation uniquement après validation |

`main` reste la branche par défaut. Les familles sont `26.3`, `26.2`, `26.1.x` (révision concrète testée : `26.1.2`), `1.21.11` et `1.21.1`. `branch-exports.json` définit les noms, dépendances à embarquer et activations. Un dossier présent mais non validé ne justifie pas une branche annoncée comme supportée. L'ancien historique Fabric est conservé jusqu'à la synchronisation de son portage actuel.

Une branche activée atteste un export de sources ayant passé les contrôles indiqués, pas une preuve de tous les essais visuels. Le tableau de compatibilité et le journal client distinguent ces étapes ; une branche locale n'est pas encore une publication GitHub.

## Mettre à jour les branches de version

1. Modifier les projets dans `main` et valider avec `.\test-all.ps1`.
2. Enregistrer dans Git les changements à publier. Les branches sont générées depuis le dernier commit de `main`, jamais depuis des fichiers non enregistrés.
3. Vérifier le plan, puis synchroniser :

```powershell
.\sync-branches.ps1                  # Affiche le plan sans modifier les branches
.\sync-branches.ps1 -Mode Local      # Met à jour les branches locales uniquement
.\sync-branches.ps1 -Mode Publish    # Vérifie GitHub, puis publie main et les branches activées ensemble
.\sync-branches.ps1 -Project Purpur_26.3 # Plan limité à une variante déjà activée
```

Le script conserve l'historique de chaque branche existante et ajoute un commit si son contenu change. Il refuse un historique divergent ou une branche de version ouverte dans un autre checkout. La publication utilise une mise à jour atomique normale : aucun force-push, aucune suppression de branche et aucun changement de branche dans le dossier de travail.

Pour une nouvelle variante sans historique, son premier commit contient uniquement son projet. Les variantes existantes conservent leurs propres commits antérieurs. Une branche Fabric existante ne sera mise à jour que lorsque son portage actuel sera activé explicitement.

`branch-export.ps1` compose les arbres depuis le commit de `main` et les dépendances déclarées. Dans `main`, les sources communes restent uniques ; dans une branche Purpur/Spigot/Bukkit, elles sont réellement présentes sous `src/`, avec la logique de compilation sous `build-logic/`. Le mode Plan n'écrit même pas d'objets Git. Les exports doivent être recompilés séparément avant publication pour prouver leur autonomie.

## Travailler depuis une branche de version

Un clone d'une branche activée se compile directement avec `gradlew build`, comme les branches Fabric/NeoForge/Forge de CustomRecipe. Le contrôle global `test-all.ps1` appartient à `main` ; les tests natifs propres à une variante restent disponibles via `gradlew smokeTest` quand le vrai serveur et l'EULA sont préparés.

Comme leurs arborescences diffèrent, ne pas fusionner directement une branche de version dans `main`. Reporter le correctif dans le dossier correspondant de `main`, valider, puis resynchroniser. Les fichiers locaux non suivis, les caches et les mondes ne sont jamais inclus dans les branches générées.
