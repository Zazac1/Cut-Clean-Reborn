# CutCleanReborn

## Organisation

- Un projet autonome par dossier `<Loader>_<Minecraft>`, comme dans CustomRecipies.
- Références fonctionnelles : `Paper_26.2` et `Paper_26.3`. Le portage demandé couvre cinq versions/familles et les plateformes Paper, Purpur, Spigot, Bukkit/CraftBukkit et Fabric ; voir `porting/README.md`.
- L'utilisateur autorise désormais du code partagé et des modules/adaptateurs séparés quand cela préserve les fonctionnalités et simplifie les portages. Ne pas convertir cette exigence en fonctionnalités désactivées ou en compatibilités seulement déclaratives.
- L'utilisateur a explicitement autorisé les sous-agents, cinq profils Modrinth « CCR QA » isolés avec Fabric/Fabulously Optimized et la publication des sources/branches validées sur GitHub. Aucune publication Modrinth ni aucun service payant. Préserver les fichiers, instances, mondes et configurations existants.
- Conserver les fonctionnalités et les chemins de configuration existants. Ne pas commencer de nouveau portage sans demande de l'utilisateur.
- Le dépôt Git reste à la racine. Ne pas créer ou supprimer de dépôts imbriqués pour une simple réorganisation.
- Les branches `<loader>/<Minecraft>` contiennent directement un projet de version compilable ; `main` garde le workspace complet. Voir `BRANCHES.md` et `sync-branches.ps1`. Le portage Fabric actuel peut faire évoluer sa branche historique en conservant son ascendance ; la permission de supprimer l'ancien historique n'oblige pas à le détruire.
- Les mondes, configurations locales et serveurs dans `run-server/` appartiennent à l'utilisateur. Les tests fonctionnels utilisent exclusivement `build/smoke-server/`, les triplets froids plugins `build/persistence-server/<essai>/`, et les clients le nouveau monde `build/client-test-server/`. Ne jamais réutiliser un monde utilisateur pour ces vérifications.
- Conserver le nommage `cutcleanreborn-<version>+<Minecraft>.jar`. Une modification du nom de fichier ne constitue pas une nouvelle version du plugin.

## Validation

Pour une validation finale des variantes actuelles :

1. Exécuter `.\test-all.ps1` depuis la racine, sans limiter les versions et en mode `Full` par défaut.
2. Lire `validation/results/latest.md` et traiter tout `FAIL` comme bloquant.
3. Le contrôle couvre les 25 projets des cinq plateformes. La parité stricte porte sur Paper ; Purpur partage ses sources par version, Spigot/Bukkit ont leur adaptateur commun, Fabric ses implémentations natives. Chaque plateforme doit tourner sur son vrai serveur, Fabric avec son JAR de distribution hors Loom. Appliquer les correctifs équivalents aux références ; documenter toute divergence nécessaire et adapter explicitement les contrôles quand les API l'exigent.
4. Si les tests automatiques passent, sélectionner dans `validation/TESTS_CODEX.md` uniquement les scénarios manuels pertinents.
5. Ne jamais présenter une compilation ou des interactions simulées comme une validation visuelle dans Minecraft.

`-Mode Quick` et `-Mode Compile` sont destinés aux contrôles intermédiaires. Les reçus de validation doivent correspondre aux sources courantes et au JAR réellement testé. Ne pas confondre une reconstruction de tracker dans le même processus avec un arrêt/redémarrage à froid, ni les tests natifs avec des interactions réalisées par un joueur.
