# CutCleanReborn — variante Paper

Ce dossier est un projet autonome. Sur sa branche `paper/<Minecraft>`, son contenu se trouve directement à la racine du dépôt.

- Préserver les fonctionnalités, les réglages et les données existantes. Ne pas démarrer un portage sans demande.
- Ne pas réintroduire de dépendance vers un dossier source voisin.
- Les mondes et réglages de `run-server/` appartiennent à l'utilisateur. Les tests fonctionnels utilisent `build/smoke-server/`, les triplets froids `build/persistence-server/<essai>/` et les clients QA `build/client-test-server/` : jamais un monde utilisateur.
- Garder le nommage `cutcleanreborn-<version>+<Minecraft>.jar` et ne pas incrémenter la version pour un simple renommage.
- Dans le workspace global de `main`, suivre le guide AGENTS de la racine et valider toutes les variantes via `test-all.ps1`.
- Dans un clone de cette seule branche, valider avec `gradlew build`, puis `gradlew smokeTest` si le JAR Paper et l'EULA sont disponibles dans `run-server/`. Signaler clairement les tests non exécutés.
- Une compilation ou des interactions simulées ne prouvent pas le rendu visuel dans Minecraft.
- Reporter les correctifs dans le dossier correspondant de `main` avant de resynchroniser les branches ; ne pas fusionner directement les arborescences globale et autonome.
