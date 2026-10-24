# Essais avec un vrai client — suivi

Ces observations ne remplacent ni la matrice complète ni les tests de toutes les plateformes. Un test natif automatique ne doit jamais être présenté comme un essai visuel.

## Profils de test séparés

Cinq nouveaux profils Modrinth sont réservés aux essais : `CCR QA 26.3 - FO`, puis `CCR QA 26.2 - préparation`, `CCR QA 26.1.2 - préparation`, `CCR QA 1.21.11 - préparation` et `CCR QA 1.21.1 - préparation`. Leur nom n'est jamais une preuve de la version installée. L'installation et le lancement des quatre profils supplémentaires ont été explicitement confirmés par l'utilisateur.

| Profil ciblé | Version FO sélectionnée et Minecraft annoncé | Observation réelle |
| --- | --- | --- |
| 26.3 | 15.0.0-beta.2 / 26.3 | Client déjà utilisé pour les essais Paper décrits ci-dessous |
| 26.2 | 14.1.1 / 26.2 | Installation terminée, bouton Play et 49 mods ; pas encore lancé |
| 26.1.2 | 13.6.0 / 26.1.2 | Installation terminée, bouton Play et 48 mods ; pas encore lancé |
| 1.21.11 | 12.3.0 / 1.21.11 | Vrai client lancé, écran principal atteint puis connexion authentifiée au nouveau serveur Paper QA ; premiers essais décrits ci-dessous |
| 1.21.1 | 6.5.0 / 1.21.1 | Client réellement lancé, accueil terminé et écran principal atteint : Minecraft 1.21.1/Fabric, FO 6.5.0 visibles |

Le premier lancement 1.21.1 vérifie seulement le profil de base : aucun CCR n'y était encore installé, aucun monde n'a été ouvert et aucun essai gameplay 1.21.1 n'est déduit de cet écran principal. Les lancements et essais restants se feront séquentiellement pour limiter la mémoire.

La duplication Modrinth conservait la synchronisation globale de certains fichiers. Les quatre options par instance `Unsync game settings`, `Unsync multiplayer servers`, `Unsync command history` et `Unsync saved creative hotbars` ont été activées et vérifiées visuellement dans les cinq profils QA avant les prochains lancements. Les réglages globaux et les options des anciennes instances n'ont pas été modifiés par cette séparation. Les packs de ressources restent gérés par le modpack ; leur interrupteur est désactivé dans cette interface, et aucun réglage global de packs n'a été changé.

Le nom affiché d'une instance peut différer de son dossier physique : vérifier le profil et son dossier avant toute copie de JAR. Les mondes solo de test doivent être créés neufs dans le bon profil QA ; ne jamais reprendre une sauvegarde existante.

Le profil QA 1.21.1 utilisait Fabric Loader 0.19.3 ; le port CCR requiert au moins 0.20.1. Après confirmation explicite de l'utilisateur, ce seul profil a été détaché du suivi du pack puis mis à jour vers 0.20.1 dans Modrinth. Les 48 JAR du pack ont été comparés par nom et SHA-256 avant/après : aucun supprimé ou modifié. L'API Fabric 0.116.12 déjà présente est conservée. Le JAR CCR validé nativement a ensuite été ajouté sans remplacement : SHA-256 `649a1d6ed22f1d79239d2e5c714d09997f9a749673b5d2a6b322a5f9da671497`. Le lancement avec CCR atteint réellement l'écran principal, 45 mods affichés. Les essais intégrés sont décrits séparément ci-dessous ; cet écran ne les remplace pas.

Le dossier de ce profil a été identifié en ouvrant son dossier depuis Modrinth : `CCR QA 26.2 - préparation (1)`, malgré son nom affiché 1.21.1. Ne pas déduire les autres correspondances de cet exemple. Les quatre autres profils QA ne sont pas encore détachés à ce stade ; les anciennes instances restent intactes.

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

## Paper 1.21.11 le 24 octobre 2026

Client réel Minecraft 1.21.11/Fabric, FO 12.3.0 (écran principal : 43 mods), profil QA dédié `63b66ed9-4caf-4c40-82a2-742b8c4697c8`. Connexion authentifiée à Paper 1.21.11-132 dans le nouveau monde propre à `Paper_1.21.11/build/client-test-server/`. Le JAR installé et le livrable ont le même SHA-256 `5e73dff0594ad4e4b80e6d23912ce9ebb2dbac0247caf0439f623f9196e0bf9a`.

En créatif, `/ccr` ouvre la configuration ; Tools & Activation → Échap → Timber → Échap → CCR Config fonctionne visuellement. Dans Better Sugar Cane, la page Player Break indique correctement PAUSED lorsque son réglage est ON mais le master OFF. Une saisie de pourcentage modifiée invalide est annulée par Échap : retour à Player Break et valeur enregistrée toujours à 100.0. Les retours successifs depuis les pages cibles/règle/remboursements fonctionnent aussi. Échap à la racine ferme CCR ; fermer ensuite une vraie enclume n'ouvre pas un ancien menu CCR.

### Transaction réellement payée à l’enclume

Une règle neuve ciblant SWORDS/Sharpness avec maximum III a été activée dans les menus. Le remboursement a été activé à un niveau payé par niveau d'enchantement retiré, puis le master Stuff Rules a été activé. La scène `equipment` a seulement fourni l'enclume, les objets et 50 niveaux en survie ; elle n'a effectué aucune transaction.

Le joueur a ouvert le bloc d'enclume par clic droit, déposé une épée vierge puis un livre Sharpness V dont l'infobulle est restée V. Le coût natif affiché était de cinq niveaux. Après un vrai clic prenant le résultat, CCR a annoncé deux niveaux remboursés ; le joueur terminait à **47 niveaux avec Sharpness III**, visible dans l'infobulle et confirmé par `/ccrqa observe` à 12:21:50 (journal : `minecraft:sharpness=3`, `repair-cost=1`, livre consommé). Une téléportation QA a seulement orienté le regard vers l'enclume, sans ouvrir son interface ni agir sur les objets.

Cet essai valide ce cas de paiement/remboursement, pas encore les autres coûts ni les cas enchanteur/armure/copie/suppression. Le reçu client reste donc PARTIAL ; aucune des dix familles complètes n'est déduite de cette seule transaction.

### Sauvegarde avec reconnexion du vrai joueur

La préférence personnelle a été passée à OFF avec `/cleanstacks off`. Le bouton Stop Server du launcher a ensuite arrêté proprement la JVM native 9492 le 24 octobre à 12:26:33 ; les joueurs et les trois dimensions ont été sauvegardés et le processus avait quitté avant le nouveau départ. Le même monde a redémarré dans la JVM native 31108, créée à 12:27:33. Les PID affichés par le launcher désignent son processus d'enveloppe, pas cette JVM Minecraft.

Après reconnexion authentifiée, le joueur a retrouvé son épée Sharpness III et ses **47 niveaux**, sans nouveau setup ni remise à 50. `/cleanstacks status` annonçait toujours OFF. Les menus rouverts montraient la même règle active All Swords/Sharpness ≤3, remboursements ON et valeur de saisie conservée à 1. Le JAR installé n'avait pas changé. La famille `persistence` est ainsi validée pour cette variante ; les autres familles restent partielles ou à faire. Les journaux avant/après sont conservés localement sous `validation/results/client/Paper_1.21.11/`.

Échap depuis la saisie de lecture retrouve sa page de remboursements en survie. Le bouton Close quitte directement cette sous-page, sans remonter les parents ; le journal confirme la vue personnelle `CRAFTING` à 12:33:44.

### Masse unique : cycle réel complet

Activation depuis Stuff Rules → Unique Mace. À l'établi vanilla, les deux noyaux lourds et les deux bâtons de Breeze de la scène ont été déposés par clics normaux. La première prise du résultat fabrique une masse ; une seconde prise est refusée avec « The unique mace already exists », sans consommer le noyau et le bâton restants. La fermeture de l'établi restitue ces ingrédients.

La masse a été jetée depuis l'inventaire, ramassée naturellement en rapprochant le joueur, puis déposée dans un chunk éloigné, vers (2000, -60, 2002). Après retour à l'établi, `/execute unless loaded 2000 -60 2002` annonce « Test passed » : le chunk est effectivement déchargé, pas seulement hors écran. Une nouvelle tentative de craft est encore refusée sans perte d'ingrédients. Au retour, la même masse est visible au sol après rechargement.

Un bloc de lave a été placé uniquement à cet endroit dans le monde QA ; aucune suppression d'entité ni aucun événement CCR n'a été invoqué artificiellement. La lave détruit réellement l'objet, et le serveur annonce à 12:50:47 la libération du craft (`exists: false`). Le noyau et le bâton conservés permettent ensuite un nouveau craft réel dans la grille personnelle 2×2. `/ccrqa observe` à 12:52:59 confirme une seule nouvelle masse dans l'inventaire, aucun ingrédient restant et un identifiant CCR différent (`5a506b9c-416a-4972-845e-bb5c4d18a58a`, contre `d10076d1-fdcf-43b2-be8e-3c802d46310d` avant destruction). La famille `unique_mace` est validée pour Paper 1.21.11 ; aucune autre variante n'hérite de ce résultat.

### Armure et copie de règle

Création dans les menus d'une règle Diamond Armor, maximum 1, puis activation. Le titre raccourci « Armor • Max Equipped » est maintenant vérifié visuellement : il tient dans l'enclume de saisie. Une copie vers Netherite conserve le maximum 1 mais est désactivée, tandis que l'original reste actif. Ouvrir sa confirmation de suppression puis presser Échap retrouve l'éditeur sans supprimer la copie (journal de 12:58:35 et configuration concordants). Après confirmation explicite de l'utilisateur pour les seules règles factices QA, cette copie est rouverte puis supprimée par le bouton de confirmation à 13:13:58. Retour à la liste avec la règle Diamond d'origine toujours active ; la configuration contient seulement cette dernière. La copie peut être recréée depuis l'original.

Dans l'inventaire de survie normal, le casque diamant est équipé par clics ; ajouter le plastron à son emplacement d'armure le renvoie immédiatement dans l'inventaire. Un seul casque reste équipé, sans perte ni duplication du plastron. Le relevé de 13:05:08 confirme casque porté, plastron, jambières et bottes conservés.

### Clean Stacks : conversion et objet nommé

Avec la préférence personnelle encore OFF, trois lots de test sont fournis par les commandes vanilla, sans appeler le service CCR : 17 Birch Logs, 23 Cobblestone et 3 Birch Logs nommés « CCR QA keep ». Ils restent distincts avant activation. Le vrai `/cleanstacks on` annonce ON et transforme les deux lots ordinaires en **17 Oak Logs et 23 Stone**. Les **3 Birch Logs nommés restent inchangés**, nom et quantité vérifiés dans l'infobulle puis dans le relevé serveur à 13:05:08. Aucun total n'est perdu ; l'épée Sharpness III, la masse unique et les armures restent intactes. La famille `clean_stacks` est validée pour cette variante.

### Commandes et fin de cette session Paper

`/ccr help`, `/ccr reload`, `/ccr status` et l'alias `/cutclean ping` sont réellement exécutés entre 13:08 et 13:10. Aide lisible, confirmation du rechargement, états effectifs cohérents et réponse Pong vérifiés dans le client. La masse reste réservée après le rechargement à chaud, visible dans Stuff Rules. Avec les parcours Timber/Stuff Rules/canne, les annulations de saisie et confirmation, ainsi que Close depuis une sous-page, la famille `menus_commands` est validée. Cela ne prétend pas couvrir toutes les pages de catalogue ou un test sans permission administrateur.

Déconnexion normale du joueur à 13:14:49, puis fermeture de ce seul client QA. Le launcher arrête proprement le serveur à 13:16:49 : joueurs, mondes et trois dimensions sauvegardés ; JVM native 31108 absente ensuite. La cible temporaire de l'adaptateur QA est remise à sa valeur par défaut Paper 26.3 seulement après cet arrêt. Les réglages et le nouveau monde Paper 1.21.11 sont conservés pour poursuivre les familles restantes ; les tests Paper ne sont pas présentés comme un parcours complet des dix familles.

## Fabric 1.21.1 en solo le 24 octobre 2026

Vrai client FO 6.5.0, Fabric Loader 0.20.1 et API 0.116.12+1.21.1, profil `bc261acf-162d-47c6-bff9-b39b741090f5`. JAR installé identique au livrable : SHA-256 `649a1d6ed22f1d79239d2e5c714d09997f9a749673b5d2a6b322a5f9da671497`, empreinte des sources `188b8cafdcaa4c3f6618a001cb061317aa9c1b74985e16de636c5f2bc638e3c1`. Aucun serveur dédié ni événement synthétique n'est utilisé dans ces essais.

### Deux mondes dans le même client

Création réelle de deux mondes plats neufs, structures désactivées : `CCR QA Fabric 1.21.1 A`, puis `CCR QA Fabric 1.21.1 B`. Le premier serveur intégré démarre à 13:31:36 ; CCR annonce son chargement solo. Le master Stuff Rules puis Masse unique sont activés dans `/ccr`. Les commandes vanilla fournissent seulement deux noyaux et deux tiges ; le joueur passe en survie et dépose lui-même les ingrédients dans la grille personnelle 2×2. La première prise fabrique la masse à 13:42:01 ; une seconde prise ne produit rien et conserve les ingrédients, restitués à la fermeture.

Sauvegarde et sortie normale de A à 13:42:32, fin de sauvegarde des trois dimensions à 13:42:33. Sans fermer le client, création de B : son serveur intégré démarre à 13:44:34. Les réglages généraux restent actifs, comme prévu pour une configuration de profil, mais la réservation de A ne bloque pas B. Le joueur fabrique réellement une première masse dans B à 13:47:58 ; une seconde reste refusée, ingrédients intacts. `/data get entity @s SelectedItem` confirme l'identifiant B `13152e7b-769a-4756-9d1c-7dcbda1b176f` à 13:49:05.

Après sauvegarde et sortie de B, A est rouvert dans le même client à 13:49:57. Le joueur retrouve sa masse d'origine et ses ingrédients ; la commande de lecture du seul objet tenu confirme l'identifiant A `7d942b8d-eaf8-4a63-afce-c9a242d47059` à 13:50:35, différent de B. Une nouvelle tentative de fabrication est toujours refusée dans A sans consommation. Les fichiers d'état propres aux deux mondes concordent avec ces objets. La famille `solo_world_lifecycle` est validée pour ce parcours A → B → A ; elle ne signifie pas que les dix familles gameplay sont terminées, ni qu'une coupe Timber a été interrompue au changement de monde.

Dans B, `/ccr` ouvre une nouvelle configuration, sans réouverture d'un ancien écran : Masse unique → Échap → Stuff Rules → Échap → Configuration → Échap → jeu. Le bouton Fermer a aussi quitté directement la sous-page Masse unique dans A. Les saisies et confirmations restent à parcourir pour valider toute la famille menus. Une infobulle de pagination reste affichée au-dessus des pages dans ce profil FO ; son origine reste à isoler et la lisibilité ne doit pas être déclarée entièrement validée.

### Préparation du redémarrage à froid

La préférence `/cleanstacks off` est explicitement enregistrée à 13:54:03. A est sauvegardé et quitté à 13:54:24 ; toutes les dimensions sont sauvegardées à 13:54:25. Le bouton Quit Game ferme normalement le client à 13:55:07 et les deux processus Java de cette session ne sont plus présents. Le journal complet de cette session QA est conservé localement dans `validation/results/client/Fabric_1.21.1/20261024-135535-solo-A-B-A.log`. Un redémarrage complet du même profil est lancé pour contrôler la persistance ; le résultat après redémarrage n'est pas encore déduit de la sauvegarde seule.

### Résultat après arrêt complet du client

Le même profil est relancé depuis Modrinth, avec de nouveaux processus Java créés à 13:57:02 et 13:57:07 ; les processus de la session précédente avaient disparu. A est sélectionné dans la liste des sauvegardes, sans setup ni objet ajouté : nouveau serveur intégré à 13:59:22, CCR prêt à 13:59:23. `/cleanstacks status` confirme toujours OFF à 14:00:33. `/ccr status` annonce StuffRules=true à 14:01:08, et la lecture de l'objet tenu retrouve exactement la masse A et son identifiant d'origine à 14:01:51. Le noyau et la tige conservés permettent de refaire la recette, mais un vrai clic de prise du résultat reste refusé ; les ingrédients sont restitués sans perte en fermant la grille. Le JAR installé conserve son SHA-256. La famille `persistence` est validée pour ce réglage, cette préférence et cette réservation ; les listes de règles d'armure et de plafonds, encore vides dans ce profil, ne sont pas présentées comme des règles joueur testées.

Le journal après redémarrage est conservé localement sous `validation/results/client/Fabric_1.21.1/20261024-140346-after-cold-restart.log`. Les messages d'erreur Steam Deck/OpenGL et les avertissements de mods du pack sont séparés des résultats CCR : aucun échec de chargement CCR n'a été observé, mais ils ne sont pas effacés du journal.

### Clean Stacks en solo

Avec la préférence encore OFF, les commandes vanilla fournissent 17 Birch Logs, 23 Cobblestone et 3 Birch Logs nommés « CCR QA keep ». Les trois lots restent distincts dans la barre rapide. `/cleanstacks on`, exécuté à 14:09:28, convertit réellement les lots ordinaires en 17 Oak Logs et 23 Stone ; les 3 rondins nommés restent en Birch. La lecture du seul inventaire QA à 14:10:30 confirme les trois quantités, le nom intact, la masse A avec son identifiant initial et les deux ingrédients conservés. La famille `clean_stacks` est validée pour Fabric 1.21.1 ; le journal est conservé localement sous `validation/results/client/Fabric_1.21.1/20261024-141112-cleanstacks.log`.

L'utilisateur a ensuite demandé de laisser les fenêtres tranquilles pendant qu'il utilise le PC. Les interactions Minecraft et Modrinth sont interrompues ; aucune désactivation de mod, modification de réglage graphique ni nouvelle manipulation de fenêtre n'est effectuée pendant cette pause des essais visuels. Le monde A et le client QA restent ouverts dans leur état courant. Le défaut d'infobulle reste à isoler, sans l'attribuer prématurément à CCR ou à un mod du pack.

### English UI cleanup requested during QA

The user resumed QA, then requested English-only in-game text and removal of unnecessary developer-facing explanations. All production menu labels and remaining gameplay messages have been translated to English. Repeated navigation/saving explanations, the pagination/return-level sentence and the displayed mace UUID have been removed. Back retains a short Esc hint; real pagination still works, but single-page Fabric menus no longer display a redundant page counter. Configuration keys, saved data and gameplay rules are unchanged.

Before this source change, a cold client comparison with ImmediatelyFast disabled still showed the pagination tooltip defect in Fabric 1.21.1. ImmediatelyFast was restored. OptiGUI was briefly disabled and the client reached the title screen, but no CCR menu was tested in that configuration: no conclusion about OptiGUI is drawn. The client was closed normally and OptiGUI restored. Neither mod was deleted or updated; both original JAR files remain enabled. The dev launcher is not operated or modified while another Codex is working on it.

The English Fabric 1.21.1 JAR (`2b141c2601d0c51d4f97943b1cc287c68c137e6ae585a473c67c34d9cde75f76`) was installed only in that QA profile. All 48 original mod filenames and SHA-256 hashes match the pre-test inventory. The client was launched, but its log records a normal stop at 14:53:02 before the new CCR menus could be observed. Overlapping desktop control was detected; the client was not repeatedly relaunched and the other task's launcher was not operated. Post-cleanup visual verification remains pending.

The gameplay observations above identify the previous JAR hashes and must not be treated as visual validation of the new English UI. A fresh full 25-variant validation was started after the cleanup; its result is not inferred from the previous successful run. `validation/test-ui-copy.ps1` checks 338 production source files for residual French copy and internal navigation explanations, and verifies that Fabric page counters are conditional on multiple pages. It is a static check, not a player test.

Separately, the client QA helper now refuses preparation outside its isolated server/world, rejects unknown scenes before changing anything and refuses to clear an inventory/cursor containing a mace. Its four new path/name unit tests pass. These guards have compiled but are not yet claimed as tested on a running server.
