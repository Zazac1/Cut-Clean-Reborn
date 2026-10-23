# Organisation des branches Git

Le modèle suit [CustomRecipe](https://github.com/Zazac1/CustomRecipe) : une branche `<loader>/<Minecraft>` contient directement un projet compilable à sa racine, sans dossier parent supplémentaire.

| Branche | Rôle | Contenu à la racine |
| --- | --- | --- |
| `main` | Vue globale et développement coordonné | `Paper_26.2/`, `Paper_26.3/`, validation et documentation |
| `paper/26.2` | Variante Paper 26.2 active | Contenu autonome de `Paper_26.2/` |
| `paper/26.3` | Variante Paper 26.3 active | Contenu autonome de `Paper_26.3/` |
| `fabric/1.21.11` | Historique Fabric, en pause | Ancienne implémentation conservée, non synchronisée |

`main` reste la branche par défaut. La branche Fabric n'est ni supprimée ni présentée comme un portage actuel. Aucun nouveau loader n'est créé tant que son portage n'est pas demandé.

## Mettre à jour les branches de version

1. Modifier les projets dans `main` et valider avec `.\test-all.ps1`.
2. Enregistrer dans Git les changements à publier. Les branches sont générées depuis le dernier commit de `main`, jamais depuis des fichiers non enregistrés.
3. Vérifier le plan, puis synchroniser :

```powershell
.\sync-branches.ps1                  # Affiche le plan sans modifier les branches
.\sync-branches.ps1 -Mode Local      # Met à jour les branches locales uniquement
.\sync-branches.ps1 -Mode Publish    # Vérifie GitHub, puis publie main et les branches Paper ensemble
```

Le script conserve l'historique de chaque branche existante et ajoute un commit si son contenu change. Il refuse un historique divergent ou une branche de version ouverte dans un autre checkout. La publication utilise une mise à jour atomique normale : aucun force-push, aucune suppression de branche et aucun changement de branche dans le dossier de travail.

Pour une nouvelle variante sans historique, son premier commit contient uniquement son projet. Les variantes existantes conservent leurs propres commits antérieurs. La branche Fabric historique n'est jamais modifiée par ce script.

## Travailler depuis une branche de version

Un clone de `paper/26.2` ou `paper/26.3` se compile directement avec `gradlew build`, comme les branches Fabric/NeoForge/Forge de CustomRecipe. Le contrôle global `test-all.ps1` appartient à `main` ; les tests natifs propres à une variante restent disponibles via `gradlew smokeTest`.

Comme leurs arborescences diffèrent, ne pas fusionner directement une branche de version dans `main`. Reporter le correctif dans le dossier correspondant de `main`, valider, puis resynchroniser. Les fichiers locaux non suivis, les caches et les mondes ne sont jamais inclus dans les branches générées.
