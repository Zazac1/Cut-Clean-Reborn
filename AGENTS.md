# CutCleanReborn

## Organisation

- Un projet autonome par dossier `<Loader>_<Minecraft>`, comme dans CustomRecipies.
- Références fonctionnelles : `Paper_26.2` et `Paper_26.3`. Le portage demandé couvre cinq versions/familles et les plateformes Paper, Purpur, Spigot, Bukkit/CraftBukkit et Fabric ; voir `validation/porting/README.md`.
- Chaque dossier de version contient réellement ses sources, ressources, tests, outils et compilation, comme CustomRecipies. La nouvelle demande de l'utilisateur remplace l'organisation avec des sources communes à la racine : aucune compilation ne doit dépendre d'un dossier voisin. Les rapports et outils transversaux restent dans `validation/`. Les JAR restent dans `<Loader>_<Minecraft>/build/libs/`, sans dossier `dist` central.
- L'utilisateur a explicitement autorisé les sous-agents, cinq profils Modrinth « CCR QA » isolés avec Fabric/Fabulously Optimized et la publication des sources/branches validées sur GitHub. Sa nouvelle demande du 24 octobre 2026 autorise aussi les 25 publications Modrinth de CCR 0.1.0 avec le jeton MODRINTH_TOKEN de l'environnement utilisateur Windows ; elle remplace l'interdiction précédente. Respecter les noms et changelogs de Custom Recipe, limiter les compatibilités aux révisions vérifiées et ne jamais enregistrer ni afficher le jeton. Aucun service payant. Préserver les fichiers, instances, mondes et configurations existants.
- Conserver les fonctionnalités et les chemins de configuration existants. Ne pas commencer de nouveau portage sans demande de l'utilisateur.
- Tous les textes visibles en jeu sont en anglais. Garder des libellés courts et utiles au joueur ; ne pas afficher de détails internes de développement, de sémantique de navigation ou d'aides répétitives sur chaque page.
- Clean Stacks convertit uniquement les butins des blocs cassés par le joueur (Timber compris). Ne jamais convertir l'inventaire à l'activation ou lors d'une correction, ni les objets donnés, ramassés hors casse, issus d'un mob ou fabriqués.
- Le dépôt Git reste à la racine. Ne pas créer ou supprimer de dépôts imbriqués pour une simple réorganisation.
- Les branches `<loader>/<Minecraft>` contiennent directement le même projet de version autonome que dans `main`. Voir `validation/git/BRANCHES.md` et `validation/git/sync-branches.ps1`. Conserver les ascendances existantes ; la permission de supprimer l'ancien historique n'oblige pas à le détruire.
- Les mondes, configurations locales et serveurs dans `run-server/` appartiennent à l'utilisateur. Les tests fonctionnels utilisent exclusivement `build/smoke-server/`, les triplets froids plugins `build/persistence-server/<essai>/`, et les clients le nouveau monde `build/client-test-server/`. Ne jamais réutiliser un monde utilisateur pour ces vérifications.
- Conserver le nommage `cutcleanreborn-<version>+<Minecraft>.jar`. Une modification du nom de fichier ne constitue pas une nouvelle version du plugin.

## Validation

Pour une validation finale des variantes actuelles :

1. Exécuter `.\test-all.ps1` depuis la racine, sans limiter les versions et en mode `Full` par défaut.
2. Lire `validation/results/latest.md` et traiter tout `FAIL` comme bloquant.
3. Le contrôle couvre les 25 projets autonomes. Contrôler la parité Paper, les copies locales Purpur par rapport au Paper correspondant et les copies locales Bukkit/Spigot. Les notices de copyright propres aux modules ne constituent pas une divergence de gameplay. Chaque plateforme doit tourner sur son vrai serveur, Fabric avec son JAR de distribution hors Loom. Appliquer les correctifs équivalents aux références ; documenter toute divergence nécessaire et adapter explicitement les contrôles quand les API l'exigent.
4. Si les tests automatiques passent, sélectionner dans `validation/TESTS_CODEX.md` uniquement les scénarios manuels pertinents.
5. Ne jamais présenter une compilation ou des interactions simulées comme une validation visuelle dans Minecraft.

`-Mode Quick` et `-Mode Compile` sont destinés aux contrôles intermédiaires. Les reçus de validation doivent correspondre aux sources courantes et au JAR réellement testé. Ne pas confondre une reconstruction de tracker dans le même processus avec un arrêt/redémarrage à froid, ni les tests natifs avec des interactions réalisées par un joueur.
