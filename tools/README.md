# Amorçage autonome Bukkit / Spigot

Prérequis : PowerShell7 (`pwsh`), JDK21 pour Minecraft1.21.x ou JDK25 pour26.x, accès HTTPS aux services officiels et Git (BuildTools peut télécharger son Git portable sous Windows). Éviter les chemins contenant espaces, guillemets ou caractères spéciaux : BuildTools n'y est pas fiable.

Une branche ne distribue ni serveur Minecraft, ni CraftBukkit/Spigot, ni API précompilée. Le script télécharge **BuildTools officiel201**, vérifie son SHA-256 fixé, puis construit les dépendances à partir des entrées officielles sur la machine de l'utilisateur. Les sorties amont restent dans un cache temporaire privé `ccr-bukkit-<identité du checkout>` ; les API/journaux restent sous `build/`, sans publication ni envoi distant. Le cache court évite la limite MAX_PATH de l'extracteur PortableGit lancé par BuildTools sous Windows. TEMP ne doit pas contenir d'espace.

Depuis une branche autonome :

```powershell
pwsh -File tools/bootstrap-bukkit.ps1 -BuildPlugin
```

Depuis le workspace complet, par exemple :

```powershell
pwsh -File Bukkit_1.21.1/tools/bootstrap-bukkit.ps1 -BuildPlugin
```

Pour sélectionner explicitement Java : ajouter `-JavaExecutable 'C:/path/to/jdk/bin/java.exe'`. Sans ce paramètre, le script vérifie le JDK adéquat dans JAVA_HOME, PATH et les installations Adoptium Windows connues. Il ne modifie pas JAVA_HOME ni la configuration Git/Maven globale.

Le dépôt Maven est **privé au checkout** (`build/official-maven`), et le cache Gradle aussi (`build/official-gradle`). `MAVEN_OPTS` n'est changé que pour le processus courant, puis restauré, même en cas d'échec. Le paramètre officiel `-Dmaven.repo.local` redirige BuildTools/Maven puis `mavenLocal()` côté Gradle. Aucune dépendance à un `~/.m2` préexistant n'est nécessaire.

Le premier amorçage peut prendre10-20minutes et plusieurs gigaoctets de stockage. Les journaux sont dans `build/official-buildtools/buildtools-output.log` et `plugin-build.log`, la preuve machine dans `bootstrap-result.json`. Un résultat n'est écrit qu'après présence effective des JAR/POM Bukkit attendus et, avec `-BuildPlugin`, réussite de la compilation, des tests unitaires et du contrôle `verifyPrivateBukkitRepository` confirmant le chemin exact de l'API réellement résolue par Gradle.

Pour les commandes Gradle suivantes, conserver la redirection du dépôt, par exemple :

```powershell
./gradlew "-Dmaven.repo.local=$((Get-Location).Path)/build/official-maven" build
```

`-PrepareServer` copie le serveur construit dans le nouveau `run-server/<bukkit|spigot>-<Minecraft>.jar`, sans écraser un fichier différent. Ce mode **ne lance pas le serveur** et **n'accepte pas l'EULA**. Après lecture de l'[EULA Minecraft](https://www.minecraft.net/eula), l'utilisateur doit fournir son `run-server/eula.txt` accepté avant `smokeTest` ou `runServer`. Les tests automatiques n'utilisent jamais les mondes `run-server`.

En CI, appeler le même script avec `-BuildPlugin` dans un checkout neuf, avec le JDK correspondant. Ne pas publier/cache public les sorties BuildTools, l'API ou les serveurs ; un artefact public doit contenir uniquement le JAR du plugin sous `build/libs`. Le script n'active aucun workflow ni service payant.

Références officielles : [BuildTools](https://www.spigotmc.org/wiki/buildtools/), [priorité du dépôt local Gradle](https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.artifacts.dsl/-repository-handler/maven-local.html).
