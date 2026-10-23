# Couche Bukkit portable

Ce dossier contient l'intégralité du plugin pour les dix variantes Spigot/CraftBukkit. Il s'agit d'une adaptation fonctionnelle des 48 classes Paper de référence, et non d'un plugin réduit ou d'un ensemble de fonctionnalités désactivées.

`src/main` fournit les commandes, menus, configurations, services et persistance. `BukkitCompat` isole seulement les trois API absentes de Bukkit : cause de fermeture d'inventaire, tick courant et envoi de la barre d'action. Les menus emploient les titres et métadonnées Bukkit ; les noms d'entités restent des traductions natives Minecraft.

`src/test` conserve les neuf tests déterministes de gameplay. `src/smoke` fournit un plugin de vérification séparé, installé uniquement dans les serveurs temporaires de test. Il utilise une vue et un joueur synthétiques pour exécuter les vrais listeners et l'ordonnanceur. Ne jamais décrire ces vérifications comme un test visuel ou un test réseau joueur.

Les modules dépendent exclusivement de l'API officielle `org.bukkit:bukkit` de leur version, installée par BuildTools. Aucun JAR serveur Bukkit/Spigot ou code serveur décompilé n'est inclus dans les sources du plugin. Consulter `../porting/spigot-bukkit.md` pour les commandes et preuves de portage.

Pour un export de branche autonome, copier `src` à la racine du projet de version et le fichier de construction commun dans `build-logic/bukkit.gradle`. Ces chemins sont détectés automatiquement par les projets.
