# CutCleanReborn — Paper 26.3

Base Paper pour Minecraft 26.3 et Java 25. Le plugin ne contient pour le moment que `/cutclean ping`, qui répond `Pong!` lorsque le JAR est correctement chargé.

## Développement

```powershell
.\gradlew.bat build
.\gradlew.bat runServer
```

`runServer` télécharge la dernière build Paper 26.3 une seule fois dans `dev-server/`, accepte l’EULA locale et copie automatiquement le JAR du plugin dans `dev-server/plugins/`.

Paper est uniquement un serveur : connectez un client Minecraft 26.3 normal à `localhost` pour tester.
