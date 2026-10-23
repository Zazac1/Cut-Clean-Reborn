# CutCleanReborn

## Organisation

- Un projet autonome par dossier `<Loader>_<Minecraft>`, comme dans CustomRecipies.
- Les variantes actuelles sont `Paper_26.2` et `Paper_26.3`. Chacune contient ses sources, ressources, compilation, licence et tests.
- Conserver les fonctionnalités et les chemins de configuration existants. Ne pas commencer de nouveau portage sans demande de l'utilisateur.
- Le dépôt Git reste à la racine. Ne pas créer ou supprimer de dépôts imbriqués pour une simple réorganisation.
- Les branches `paper/<Minecraft>` contiennent directement le projet de version ; `main` garde le workspace complet. Voir `BRANCHES.md` et `sync-branches.ps1`. Préserver `fabric/1.21.11` comme historique en pause, sans force-push ni suppression.
- Les mondes, configurations locales et serveurs dans `run-server/` appartiennent à l'utilisateur ; les tests automatiques utilisent exclusivement `build/smoke-server/`.
- Conserver le nommage `cutcleanreborn-<version>+<Minecraft>.jar`. Une modification du nom de fichier ne constitue pas une nouvelle version du plugin.

## Validation

Pour une validation finale des variantes actuelles :

1. Exécuter `.\test-all.ps1` depuis la racine, sans limiter les versions et en mode `Full` par défaut.
2. Lire `validation/results/latest.md` et traiter tout `FAIL` comme bloquant.
3. Le contrôle de parité couvre les deux variantes Paper actuelles. Appliquer les correctifs équivalents aux deux ; documenter toute divergence nécessaire et adapter explicitement ce contrôle si les API l'exigent.
4. Si les tests automatiques passent, sélectionner dans `validation/TESTS_CODEX.md` uniquement les scénarios manuels pertinents.
5. Ne jamais présenter une compilation ou des interactions simulées comme une validation visuelle dans Minecraft.

`-Mode Quick` et `-Mode Compile` sont destinés aux contrôles intermédiaires. Étendre la validation aux futurs loaders seulement lors de leur portage, selon leurs propres outils et métadonnées.
