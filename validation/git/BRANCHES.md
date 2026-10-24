# Git branches

The layout follows [CustomRecipe](https://github.com/Zazac1/CustomRecipe). Main holds the workspace; each `<loader>/<Minecraft>` branch holds one autonomous project directly at its root.

| Branch | Contents |
| --- | --- |
| `main` | The 25 version directories, validation and documentation |
| `paper/<Minecraft>`, `purpur/<Minecraft>` | The matching project's own build and sources |
| `spigot/<Minecraft>`, `bukkit/<Minecraft>` | The matching project, including its local BuildTools helpers |
| `fabric/<Minecraft>` | The matching native Fabric project and local resources |

The five families are `1.21.1`, `1.21.11`, `26.1.x` (tested revision `26.1.2`), `26.2` and `26.3`. Main remains the default branch. Existing branch ancestry, including historical Fabric commits, is retained.

## Updating version branches

1. Change the relevant version directories on main.
2. Run `.\test-all.ps1` and the relevant cold checks. Read the reports and resolve every failure.
3. Commit the intended sources and documentation.
4. Run from the workspace root:

```powershell
.\validation\git\sync-branches.ps1
.\validation\git\sync-branches.ps1 -Mode Local
.\validation\validate-exports.ps1
.\validation\git\sync-branches.ps1 -Mode Publish
```

The default mode only displays a plan. `-Project Purpur_26.3` limits the plan to an enabled project. `branch-exports.json`, beside the scripts in this directory, lists all 25 projects. Every dependency list is empty: the exported tree is exactly the version directory already present in main.

Local mode appends a commit only when that project's tree changes. Publish checks remote ancestry, then pushes main and enabled branches atomically. It does not force updates, delete branches or switch the working checkout. A divergent remote or a version branch open in another checkout blocks publication.

`validation/check-export-trees.ps1` compares every file and Git mode with the corresponding main directory. `validation/validate-exports.ps1` then extracts fresh branch archives under `validation/build/branch-validation/` and builds them without neighboring projects. Reports are `validation/results/export-trees.json` and `validation/results/exports.json`. Dependency caches may be reused; extracted sources and test outputs are fresh.

## Working on a version branch

A clone builds directly with `gradlew build`. Native tests require that project's actual server and an already accepted EULA. Workspace-wide validation belongs to main.

Report a version fix back into its directory on main, validate and synchronize again. Do not merge the differently shaped version branch directly into main. Local worlds, configuration and caches are ignored and are never exported. GitHub publication is authorized; this workflow never publishes to Modrinth.
