# Reçus des essais avec un vrai joueur

Le collecteur lit `validation/manual/<Projet>.json`, conservé localement et exclu de Git. Un reçu décrit uniquement des interactions réellement effectuées dans le client indiqué. Le vérificateur contrôle la cohérence et la couverture du récit ; il ne peut pas prouver à lui seul qu'une personne a joué. Ne jamais générer de succès à partir des tests natifs ou d'un écran principal.

Le schéma 2 remplace l'ancien booléen global : une seule observation ne valide plus tout le portage. Les empreintes doivent correspondre au JAR installé **pendant** les essais et aux sources réellement testées. Après une modification, refaire les scénarios concernés ; ne pas actualiser simplement les empreintes d'un ancien reçu.

## Couverture

Chaque famille a un statut `PASS`, `PARTIAL` ou `FAIL` et une observation concrète. Une famille absente reste à faire. `PASS` signifie que toute la portée indiquée ci-dessous a été vérifiée, pas seulement une de ses interactions. Les détails ciblés sont dans [TESTS_CODEX.md](TESTS_CODEX.md).

| Identifiant | Portée à vérifier dans le client |
| --- | --- |
| `menus_commands` | Commandes principales, menus Timber/Stuff Rules/canne, retour Échap d'un niveau, annulation des saisies et confirmations, fermeture explicite |
| `timber` | Arbre naturel/construction, activation et outil, durabilité, progression de casse standard et arrêt/changement d'outil ; ne pas déduire le ressenti d'un clic accéléré par Haste |
| `mob_loot` | Vraie mort d'animal, cuisson, quantités et expérience ; vérifier les interactions Looting pertinentes |
| `ore_loot` | Vrai minage, outil adapté/inadapté, fusion, Fortune, Silk Touch et expérience |
| `auto_enchants` | Application réelle sur l'objet, conservation des enchantements existants, plafonds et rafraîchissement |
| `clean_stacks` | Commande personnelle, conversion réelle et quantités, exclusion d'objet avec métadonnées |
| `stuff_rules` | Limite d'armure équipée, plafonds d'enchantements, transaction payante et remboursement borné, copie/suppression d'une règle |
| `unique_mace` | Fabrication, ramassage et chunk rechargé sans libérer la réservation ; vraie destruction permettant une nouvelle fabrication |
| `sugar_cane` | Casse joueur et perte de support, chance/quantité, origine placée conservée après redémarrage |
| `persistence` | Réglage modifié dans le client, arrêt propre puis nouveau démarrage, retour du joueur et vérification des règles/préférences |
| `solo_world_lifecycle` | Fabric uniquement : serveur intégré réel, sauvegarder/quitter un nouveau monde QA, en ouvrir un second dans le même client sans fuite de menus/tâches/réglages propres au monde, puis recharger le premier |

Les dix premières familles sont requises pour chaque variante. Fabric demande en plus le cycle de vie solo, non remplaçable par une simulation de callbacks ou un serveur dédié. Une vérification d'interopérabilité avec un autre plugin, lorsqu'elle est pertinente, reste explicitement décrite avec ses conditions ; ne pas la déduire d'un test sans ce plugin.

## Format initial (ne valide rien)

```json
{
  "schema": 2,
  "project": "Paper_26.3",
  "platform": "Paper",
  "minecraft": "26.3",
  "artifact_sha256": "empreinte-du-JAR-effectivement-installe-pendant-les-essais",
  "source_fingerprint": "empreinte-des-sources-effectivement-testees",
  "observed_at": null,
  "client": {
    "minecraft": "26.3",
    "profile": "identifiant-du-profil-QA-effectivement-utilise",
    "mode": "dedicated"
  },
  "scenarios": []
}
```

Renseigner une date ISO réelle dans `observed_at`, puis des objets `{ "id": "timber", "status": "PARTIAL", "observation": "description précise du test et de ses limites" }` uniquement après les interactions correspondantes. `mode` vaut `dedicated` pour un plugin, `singleplayer` pour les essais Fabric solo. Les fichiers `validation/results/evidence/<Projet>.json` permettent d'identifier les empreintes natives, mais ne constituent jamais une preuve joueur.

Le collecteur affiche les observations partielles dans `dist/compatibility.json` et `dist/README.md`. Il ne passe à « Validé avec joueur » que si toutes les familles requises sont `PASS`, dans un contexte compatible et avec les bonnes empreintes. Un JSON manuel illisible ne retire pas un JAR déjà validé nativement.

## Vérifier le contrôle sans lancer Minecraft

```powershell
.\validation\test-client-evidence.ps1
.\porting\collect-artifacts.ps1
.\porting\collect-artifacts.ps1 -RequireComplete
```

La dernière commande échoue tant qu'une des 25 variantes manque de parcours joueur complet ou de preuve froide. Les cas du premier script sont des données synthétiques, jamais des reçus de gameplay ; aucun n'est écrit sous `validation/manual/`.
