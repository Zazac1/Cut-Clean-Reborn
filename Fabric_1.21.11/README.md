# CutCleanReborn

Mod Fabric pour Minecraft 1.21.11, orienté server-side, fonctionnel en solo (serveur integre) et en multijoueur.

## Stack

- Minecraft: 1.21.11
- Fabric Loader: 0.19.3
- Java: 21
- Loom: 1.17.13
- Fabric API: 0.141.4+1.21.11 (requis)

Les règles de gameplay sont appliquées par le serveur. La configuration est lue au démarrage depuis `config/cutcleanreborn.json`; sur un serveur dédié, modifiez ce fichier côté serveur.

`legacyPre118Mining` est désactivé par défaut. Quand il est désactivé, il n'enregistre aucun worldgen et ne modifie aucun bloc. La limite diamant est également désactivée par défaut; activez `diamondLimit.enabled` pour appliquer la limite UHC (17 par défaut), avec `convert_to_xp` ou `drop_excess`.

## Demarrage rapide

1. Installer Java 21.
1. Lancer la compilation:

```powershell
.\gradlew.bat build
```

1. Lancer un client de dev:

```powershell
.\gradlew.bat runClient
```

1. Lancer un serveur de dev:

```powershell
.\gradlew.bat runServer
```

## Sortie

Le JAR est genere dans `build/libs/`.

## License

CC0-1.0
