# Modrinth publication

CCR **0.1.0** is published on [Modrinth](https://modrinth.com/mod/cut-clean-reborn/versions): five Minecraft revisions across Paper, Purpur, Spigot, Bukkit/CraftBukkit and Fabric, for 25 separate releases. The previous 0.0.1 Paper release is preserved.

Names follow [Custom Recipe](https://modrinth.com/mod/custom-recipes-mod): `0.1.0-26.3-(Fabric)`. Both the title and version number use that format. The 26.1.x artifact family is tagged and named **26.1.2**, the exact tested Minecraft revision. Installable JAR filenames retain `cutcleanreborn-0.1.0+<family>.jar`.

The English changelogs describe player-visible improvements and installation requirements. They omit build internals, test counters and development notes. Fabric releases require the exact tested Fabric API dependency and declare `server_only`, which includes singleplayer and servers accepting vanilla clients. Plugin releases are classified as plugins on their actual platform.

All 25 releases are listed publicly, and their anonymously downloaded bytes match the tested JARs. They use the existing successful Full, cold-restart and standalone-build evidence; no new complete player traversal is claimed. See [the release receipt](releases/0.1.0/published.json).

## Project description

The token successfully creates versions and edits their changelogs, but Modrinth rejects project-description edits with HTTP 401 (`PROJECT_WRITE` is required). The live description is therefore still the previous Paper-focused text. A complete replacement retaining the feature descriptions and adding the five platforms is ready in [project-description.md](releases/0.1.0/project-description.md).

## Maintenance commands

Run from the workspace root on Windows with Python 3. The token is read only from `MODRINTH_TOKEN` in the Windows **user** environment. It is never stored in the source, manifest, journal or command line. Future publication remains subject to the user's authorization.

```powershell
# Refresh the current source/JAR evidence before preparing a publication.
.\validation\porting\collect-artifacts.ps1
python -B validation/modrinth/release.py prepare
python -B validation/modrinth/release.py publish
python -B validation/modrinth/release.py verify

# Optional, once the token/account can edit the project page:
python -B validation/modrinth/release.py update-description
```

The script is intentionally scoped to the authorized 0.1.0 release and existing project `2GUh56dK`. Preparation performs reads only. Publication checks every JAR first and resumes by release name plus exact SHA-512; a conflicting existing file or metadata blocks the run instead of replacing it. An uncertain upload is not blindly retried. Only distribution JARs are uploaded.

Modrinth's plugin loaders support `game_versions` but do not expose an `environment` field. Their v2 environment is consequently `unknown`; the verifier explicitly checks the v3 `plugin` classification instead. Fabric supports the field and must retain `server_only`. This follows the [official loader catalog](https://api.modrinth.com/v3/tag/loader) and [environment definitions](https://modrinth.com/news/article/new-environments/).
