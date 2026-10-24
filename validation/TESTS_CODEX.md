# Vérifications manuelles ciblées

Après une validation automatique `Full` réussie, choisir uniquement les scénarios concernés par les changements. Ne pas présenter ces scénarios comme réalisés sans les avoir exécutés dans Minecraft.

Pour la première validation complète d'un portage, couvrir toutes les familles du [reçu client](client-evidence.md), y compris les enchantements automatiques et Clean Stacks. Le choix ciblé ci-dessus concerne ensuite les revalidations ; un seul scénario ne transforme jamais un portage incomplet en parcours joueur complet.

- Menus : ouvrir `/ccr`, naviguer dans Timber, Stuff Rules et Better Sugar Cane ; Échap remonte d'un niveau, conserve la page et annule les saisies/confirmations. Fermer quitte toute la configuration.
- Timber : arbre naturel contre construction en rondins, outils autorisés, accroupissement, progression de casse, protection d'un autre plugin, changement/casse de l'outil, feuilles et pommes.
- Butin : morts d'animaux, minage avec/sans outil adapté, Fortune, Silk Touch, expérience et enchantements préexistants.
- Stuff Rules : limites d'armure équipée, plafonds d'enchantements et remboursement limité aux niveaux réellement payés ; copie et suppression de règles.
- Enchantements automatiques : application sur un vrai objet, enchantement préexistant plus fort, plafond actif et rafraîchissement.
- Clean Stacks : préférence personnelle, vraie casse de blocs avec butin converti sans perte de quantité et objet avec métadonnées exclu. Vérifier aussi que l'activation, /give, les objets déjà stockés, les ramassages hors casse et les planches fabriquées restent inchangés. Un test /give seul ne valide jamais les butins de blocs.
- Masse unique : fabriquer, ramasser, décharger/recharger le chunk puis détruire la masse. Seule sa disparition réelle libère une nouvelle fabrication.
- Canne à sucre : casse directe et perte de support, quantité finale et chance, canne placée par un joueur après redémarrage.
- Sauvegarde : modifier un réglage, redémarrer le serveur et vérifier les préférences et règles existantes.
- Fabric solo : quitter et recharger un nouveau monde QA, puis changer de monde dans le même client sans fuite d'état propre au monde. Les tests mécaniques de cycle de vie ne remplacent pas cet essai intégré.
