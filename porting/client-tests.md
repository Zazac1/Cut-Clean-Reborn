# Essais avec un vrai client — suivi

Ces observations ne remplacent ni la matrice complète ni les tests de toutes les plateformes. Un test natif automatique ne doit jamais être présenté comme un essai visuel.

## Profils de test séparés

Cinq nouveaux profils Modrinth sont réservés aux essais : `CCR QA 26.3 - FO`, puis `CCR QA 26.2 - préparation`, `CCR QA 26.1.2 - préparation`, `CCR QA 1.21.11 - préparation` et `CCR QA 1.21.1 - préparation`. Leur nom n'est jamais une preuve de la version installée. L'installation et le lancement des quatre profils supplémentaires ont été explicitement confirmés par l'utilisateur.

| Profil ciblé | Version FO sélectionnée et Minecraft annoncé | Observation réelle |
| --- | --- | --- |
| 26.3 | 15.0.0-beta.2 / 26.3 | Client déjà utilisé pour les essais Paper décrits ci-dessous |
| 26.2 | 14.1.1 / 26.2 | Installation terminée, bouton Play et 49 mods ; pas encore lancé |
| 26.1.2 | 13.6.0 / 26.1.2 | Installation terminée, bouton Play et 48 mods ; pas encore lancé |
| 1.21.11 | 12.3.0 / 1.21.11 | Installation déclenchée sur le profil dédié ; fin et lancement à revérifier |
| 1.21.1 | 6.5.0 / 1.21.1 | Client réellement lancé, accueil terminé et écran principal atteint : Minecraft 1.21.1/Fabric, FO 6.5.0 visibles |

Le premier lancement 1.21.1 vérifie seulement le profil de base : aucun CCR n'y était encore installé, aucun monde n'a été ouvert et aucun essai gameplay 1.21.1 n'est déduit de cet écran principal. Les lancements et essais restants se feront séquentiellement pour limiter la mémoire.

La duplication Modrinth conservait la synchronisation globale de certains fichiers. Les quatre options par instance `Unsync game settings`, `Unsync multiplayer servers`, `Unsync command history` et `Unsync saved creative hotbars` ont été activées et vérifiées visuellement dans les cinq profils QA avant les prochains lancements. Les réglages globaux et les options des anciennes instances n'ont pas été modifiés par cette séparation. Les packs de ressources restent gérés par le modpack ; leur interrupteur est désactivé dans cette interface, et aucun réglage global de packs n'a été changé.

Le nom affiché d'une instance peut différer de son dossier physique : vérifier le profil et son dossier avant toute copie de JAR. Les mondes solo de test doivent être créés neufs dans le bon profil QA ; ne jamais reprendre une sauvegarde existante.

## Paper26.3 — 24octobre2026, en cours

Client Minecraft26.3 avec Fabulously Optimized, nouveau profil « CCR QA », serveur Paper26.3-218 authentifié dans un nouveau monde `build/client-test-server`. JAR réellement installé : SHA-256 `d0694dbbcb3a6bd51c2e380572df466afb6478bb820cb5cfe183d71077224bfb`.

- `/ccr` ouvre réellement le menu de configuration ; Timber et « Tools & Activation » s'affichent correctement.
- Régression constatée avant correction : en créatif, Échap depuis les outils fermait toute l'interface. La vue personnelle est de type `CREATIVE` dans CraftBukkit, et non `CRAFTING` comme en survie. Les tests initiaux ne reproduisaient pas ce cas.
- Après correction et redémarrage : Échap depuis « Tools & Activation » revient à Timber ; un second Échap revient au menu principal. Vérification visuelle et événements serveur concordants (`PLAYER`, ouvertures successives des bons parents, mode `CREATIVE`).
- Les nouvelles régressions automatiques couvrent aussi la fermeture explicite, les interfaces étrangères et la saisie à enclume. Elles ne constituent pas à elles seules un test client de ces autres scénarios.
- Stuff Rules → Armor Limits → nouvelle règle désactivée : ouverture réelle de l'éditeur, saisie à enclume de la limite4 puis Échap sans modification, confirmation de suppression puis Échap sans suppression, et bouton Fermer quittant toute la configuration : vérifiés visuellement. Le titre de saisie de limite d'armure déborde du cadre ; à raccourcir avant validation finale.
- Premier essai Timber non concluant : la scène QA utilisait des feuilles sans distance naturelle initialisée et un effet Haste insuffisant pour une casse par clic bref. Le helper de test est corrigé ; aucun succès de gameplay n'est déduit de cet essai.

Reste à réaliser : autres menus, annulation de saisie/confirmation, gameplay en survie, persistance après arrêt/redémarrage ; puis autres versions et plateformes, dont le solo Fabric.

### Deuxième démarrage QA

JAR après raccourcissement du titre : SHA-256 `18abaa746be28531cb71d202cbd40b8f7e14b08339aa1f75aef1ff3df1f3988d`. Le précédent serveur a sauvegardé et quitté proprement ; la règle d'armure désactivée créée dans l'interface est toujours présente dans la configuration rechargée.

Timber en survie : vrai clic de minage du joueur sur le rondin initial, puis les quatre rondins récoltés par le pipeline normal du serveur. Vérification visuelle des objets au sol et observation serveur `logs=0`, hache en diamant endommagée de4points. Le helper utilise Haste64 uniquement pour rendre ce clic bref instantané : cet essai ne valide donc pas encore le ressenti du temps de minage standard, ni les protections d'un autre plugin.

Minerai : activation d'Auto Smelt depuis `/ccr` → Ore Drops, puis casse réelle d'un bloc de fer avec une pioche FortuneIII. Résultat effectivement ramassé dans l'inventaire : 4lingots de fer, pioche endommagée de1point, deux blocs de diamant laissés intacts. La téléportation QA a seulement placé le joueur près du butin pour le ramassage natif. Cette observation confirme la fusion et le résultat de Fortune ; elle ne mesure pas précisément l'XP et ne valide pas encore le cas Silk Touch en jeu.
