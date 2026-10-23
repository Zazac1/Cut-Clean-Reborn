# Essais avec un vrai client — suivi

Ces observations ne remplacent ni la matrice complète ni les tests de toutes les plateformes. Un test natif automatique ne doit jamais être présenté comme un essai visuel.

## Paper26.3 — 24octobre2026, en cours

Client Minecraft26.3 avec Fabulously Optimized, nouveau profil « CCR QA », serveur Paper26.3-218 authentifié dans un nouveau monde `build/client-test-server`. JAR réellement installé : SHA-256 `d0694dbbcb3a6bd51c2e380572df466afb6478bb820cb5cfe183d71077224bfb`.

- `/ccr` ouvre réellement le menu de configuration ; Timber et « Tools & Activation » s'affichent correctement.
- Régression constatée avant correction : en créatif, Échap depuis les outils fermait toute l'interface. La vue personnelle est de type `CREATIVE` dans CraftBukkit, et non `CRAFTING` comme en survie. Les tests initiaux ne reproduisaient pas ce cas.
- Après correction et redémarrage : Échap depuis « Tools & Activation » revient à Timber ; un second Échap revient au menu principal. Vérification visuelle et événements serveur concordants (`PLAYER`, ouvertures successives des bons parents, mode `CREATIVE`).
- Les nouvelles régressions automatiques couvrent aussi la fermeture explicite, les interfaces étrangères et la saisie à enclume. Elles ne constituent pas à elles seules un test client de ces autres scénarios.
- Stuff Rules → Armor Limits → nouvelle règle désactivée : ouverture réelle de l'éditeur, saisie à enclume de la limite4 puis Échap sans modification, confirmation de suppression puis Échap sans suppression, et bouton Fermer quittant toute la configuration : vérifiés visuellement. Le titre de saisie de limite d'armure déborde du cadre ; à raccourcir avant validation finale.
- Premier essai Timber non concluant : la scène QA utilisait des feuilles sans distance naturelle initialisée et un effet Haste insuffisant pour une casse par clic bref. Le helper de test est corrigé ; aucun succès de gameplay n'est déduit de cet essai.

Reste à réaliser : autres menus, annulation de saisie/confirmation, gameplay en survie, persistance après arrêt/redémarrage ; puis autres versions et plateformes, dont le solo Fabric.
