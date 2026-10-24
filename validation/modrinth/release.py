"""Prepare, publish and verify the explicitly authorized CCR 0.1.0 release.

The Modrinth token is read from the Windows user environment, never from a file
or command-line argument. Preparation makes no remote changes. Publication is
resumable: a name collision must match the exact file and metadata, otherwise
the script stops instead of replacing an existing release.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import winreg
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PROJECT = "2GUh56dK"
VERSION = "0.1.0"
RELEASE = Path(__file__).parent / "releases" / VERSION
RESULTS = ROOT / "validation/results/modrinth"
UA = "Zazac1/CutCleanReborn (https://github.com/Zazac1/Cut-Clean-Reborn)"


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


class Api:
    def __init__(self):
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as key:
            self.token = winreg.QueryValueEx(key, "MODRINTH_TOKEN")[0].strip()
        if not self.token:
            raise RuntimeError("The Windows user MODRINTH_TOKEN is empty")

    def request(self, path, method="GET", data=None, content_type=None, api_version="v2"):
        headers = {"Authorization": self.token, "User-Agent": UA}
        if content_type:
            headers["Content-Type"] = content_type
        if api_version not in {"v2", "v3"}:
            raise ValueError("Unexpected API version")
        req = urllib.request.Request("https://api.modrinth.com/" + api_version + path,
                                     data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=45) as response:
                payload = response.read()
            return json.loads(payload) if payload else None
        except urllib.error.HTTPError as error:
            description = error.read().decode("utf-8", errors="replace")
            description = description.replace(self.token, "<redacted>")
            raise RuntimeError(f"Modrinth HTTP {error.code} at {path}: {description}") from None
        except (OSError, TimeoutError) as error:
            raise RuntimeError(f"Modrinth transport error at {path}: {type(error).__name__}") from None

    def json(self, path, method, data):
        return self.request(path, method, json.dumps(data).encode(), "application/json")

    def upload(self, payload, path):
        boundary = "CCR-" + uuid.uuid4().hex
        file_bytes = path.read_bytes()
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"data\"\r\n"
                "Content-Type: application/json\r\n\r\n").encode()
        body += json.dumps(payload).encode()
        body += (f"\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"jar\"; "
                 f"filename=\"{path.name}\"\r\nContent-Type: application/java-archive\r\n\r\n").encode()
        body += file_bytes + f"\r\n--{boundary}--\r\n".encode()
        return self.request("/version", "POST", body, "multipart/form-data; boundary=" + boundary)


def properties(module):
    return dict(line.split("=", 1) for line in (module / "gradle.properties").read_text().splitlines()
                if "=" in line and not line.startswith(("#", "!")))


def changelog(loader, minecraft, props):
    title = f"{VERSION}-{minecraft}-({loader})"
    lines = [f"# {title}", "", f"## CutClean Reborn — Minecraft {minecraft} ({loader})", "",
             "This release brings clearer in-game configuration and the full CutClean Reborn ruleset "
             f"to {loader} on Minecraft {minecraft}.", ""]
    if loader != "Paper" or minecraft != "26.3":
        lines += ["## Added", "",
                  f"- **{loader} support** — customize mob and ore drops, Timber, automatic enchantments, "
                  "Clean Stacks, sugar cane and equipment rules on this Minecraft version.", ""]
    lines += ["## Improved", "",
              "- Timber settings are grouped into tools, leaves and apples, and breaking controls.",
              "- Stuff Rules makes armor limits, enchantment caps and the unique mace easier to find and configure.",
              "- Better Sugar Cane separates player breaks from support loss, each with its own chance and quantity.",
              "- Press Escape to return to the previous menu or cancel unfinished input.",
              "- Configuration menus use concise English labels.",
              "- Updated the CCR logo.", "",
              "## Fixed", "",
              "- Clean Stacks converts only drops from blocks you break, including Timber. Enabling it does not "
              "change stored items, command-given items, unrelated pickups or crafting results.", "",
              "## Compatibility / servers", ""]
    java = "21" if minecraft.startswith("1.21.") else "25"
    if loader == "Fabric":
        lines += [f"- Requires Java {java}, Fabric Loader {props['loader_version']} or newer, and Fabric API.",
                  "- Install in `mods/` for singleplayer or on a Fabric server. Players can join a dedicated "
                  "CCR server without installing CCR on their client.",
                  "- Server operators and singleplayer world owners can use `/ccr config`."]
    else:
        platform = "CraftBukkit" if loader == "Bukkit" else loader
        lines += [f"- Requires Java {java} and a {platform} server running Minecraft {minecraft}.",
                  "- Install in `plugins/`. Players do not need a client mod.",
                  "- Operators can use `/ccr config`; plugin permission: `cutcleanreborn.config`."]
    lines += ["", "## Before updating", "",
              "- Existing configuration paths remain compatible.",
              "- Close your world or stop your server before replacing the previous CCR JAR.", ""]
    return "\n".join(lines)


def prepare(api):
    project = api.request("/project/" + PROJECT)
    if project["slug"] != "cut-clean-reborn" or project["status"] != "approved":
        raise RuntimeError("Expected the existing public Cut Clean Reborn project")
    write(RESULTS / "project-before-publication.json", project)
    games = {item["version"] for item in api.request("/tag/game_version")}
    loaders = {item["name"] for item in api.request("/tag/loader")}
    loader_fields = {item["name"]: item["supported_fields"]
                     for item in api.request("/tag/loader", api_version="v3")}
    matrix = read(ROOT / "validation/results/artifacts/compatibility.json")["rows"]
    full = read(ROOT / "validation/results/latest.json")
    if full["Mode"] != "Full" or full["Failed"] or len(matrix) != 25:
        raise RuntimeError("A clean full validation and all 25 current artifacts are required")
    api_project = api.request("/project/fabric-api")
    if api_project["id"] != "P7dR8mSH":
        raise RuntimeError("Unexpected Fabric API project")
    rows = []
    for row in matrix:
        if row["build"] != "PASS" or row["startup"] != "PASS" or not row["cold_restart"].startswith("PASS:"):
            raise RuntimeError("Incomplete native/cold evidence: " + row["project"])
        module = ROOT / row["project"]
        props = properties(module)
        path = ROOT / row["artifact"]
        contents = path.read_bytes()
        sha256 = hashlib.sha256(contents).hexdigest()
        if sha256 != row["sha256"]:
            raise RuntimeError("JAR changed since validation: " + row["project"])
        loader, minecraft = row["platform"], row["minecraft"]
        if minecraft not in games or loader.lower() not in loaders:
            raise RuntimeError("Unrecognized Modrinth compatibility: " + row["project"])
        with zipfile.ZipFile(path) as jar:
            if "icon.png" not in jar.namelist():
                raise RuntimeError("Missing logo: " + row["project"])
        dependencies = []
        if loader == "Fabric":
            query = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([minecraft])})
            candidates = api.request("/project/P7dR8mSH/version?" + query)
            matches = [v for v in candidates if v["version_number"] == props["fabric_api_version"]
                       and v["status"] == "listed"]
            if len(matches) != 1:
                raise RuntimeError("Cannot identify the tested Fabric API version: " + row["project"])
            dependencies = [{"project_id": "P7dR8mSH", "version_id": matches[0]["id"], "dependency_type": "required"}]
        name = f"{VERSION}-{minecraft}-({loader})"
        notes = changelog(loader, minecraft, props)
        notes_path = RELEASE / (row["project"] + ".md")
        notes_path.parent.mkdir(parents=True, exist_ok=True)
        notes_path.write_text(notes, encoding="utf-8")
        payload = {"project_id": PROJECT, "name": name, "version_number": name,
                   "changelog": notes, "game_versions": [minecraft], "loaders": [loader.lower()],
                   "version_type": "release", "status": "listed", "featured": False,
                   "dependencies": dependencies, "file_parts": ["jar"], "primary_file": "jar"}
        # Modrinth's plugin loaders expose only game_versions, not environment.
        # Their v2 environment is therefore unknown; v3 project_types=plugin is
        # checked below. Fabric exposes environment and must retain server_only.
        if "environment" in loader_fields[loader.lower()]:
            payload["environment"] = "server_only" if loader == "Fabric" else "dedicated_server_only"
        rows.append({"project": row["project"], "artifact": row["artifact"], "sha256": sha256,
                     "sha512": hashlib.sha512(contents).hexdigest(), "size": len(contents),
                     "expected_environment": payload.get("environment", "unknown"), "payload": payload})
    if len({r["payload"]["version_number"] for r in rows}) != 25:
        raise RuntimeError("Duplicate release names")
    families = ["1.21.1", "1.21.11", "26.1.2", "26.2", "26.3"]
    platforms = ["Bukkit", "Spigot", "Purpur", "Paper", "Fabric"]
    rows.sort(key=lambda r: (families.index(r["payload"]["game_versions"][0]), platforms.index(r["project"].split("_")[0])))
    write(RELEASE / "manifest.json", {"project_id": PROJECT, "version": VERSION, "versions": rows})
    body = project["body"]
    old = "**CutClean Reborn** is a configurable **Paper plugin for Minecraft 26.3**, primarily designed for **UHC servers**."
    new = "**CutClean Reborn** brings configurable UHC and quality-of-life rules to **Paper, Purpur, Spigot, CraftBukkit and Fabric**. Fabric also supports **singleplayer**."
    if old not in body:
        raise RuntimeError("Review the changed project description before editing it")
    body = body.replace(old, new, 1)
    body = body.replace("No client-side mod is required.", "Dedicated servers accept normal Minecraft clients; players do not need CCR installed locally.", 1)
    body = body.replace("Players can enable automatic conversion of supported wood and stone stacks.",
                        "Players can convert supported wood and stone drops from blocks they break, including Timber. "
                        "Enabling Clean Stacks never changes items already stored in the inventory. Command-given items, unrelated pickups and crafting results are unchanged.")
    body = body.replace("`/cleanstacks <true|false>`", "`/cleanstacks on` or `/cleanstacks off` (`true` and `false` also remain supported).")
    old = "## 🖥️ Server-side only\n\nCutClean Reborn is a **Paper plugin**.\n\nPlayers do **not** need to install any client-side mod to join the server."
    new = ("## 🖥️ Installation and compatibility\n\n"
           "| Minecraft | Platforms | Java |\n| --- | --- | --- |\n"
           "| 1.21.1, 1.21.11 | Paper, Purpur, Spigot, Bukkit/CraftBukkit, Fabric | 21+ |\n"
           "| 26.1.2, 26.2, 26.3 | Paper, Purpur, Spigot, Bukkit/CraftBukkit, Fabric | 25+ |\n\n"
           "Download the file matching your exact Minecraft version and platform.\n\n"
           "- **Paper / Purpur / Spigot / CraftBukkit:** put the matching JAR in `plugins/`.\n"
           "- **Fabric:** install Fabric Loader and the required Fabric API, then put CCR in `mods/`. "
           "It works in singleplayer and on dedicated servers.\n"
           "- **Multiplayer players:** CCR is not required on clients joining a CCR server.\n\n"
           "Use `/ccr config` as an operator. On Fabric, the singleplayer world owner also has access. "
           "Escape returns to the previous menu and cancels unfinished input.")
    if old not in body:
        raise RuntimeError("Review the changed installation section before editing it")
    body = body.replace(old, new, 1).replace("- Your Paper version", "- Your Minecraft version and server platform or mod loader")
    (RELEASE / "project-description.md").write_text(body, encoding="utf-8")
    print("Prepared 25 release payloads and player-facing English changelogs. No remote changes.", flush=True)


def matches(version, row):
    wanted = row["payload"]
    for key in ("project_id", "name", "version_number", "version_type", "status", "changelog"):
        if version.get(key) != wanted[key]:
            raise RuntimeError(f"Existing version metadata differs ({key}): {wanted['name']}")
    if version.get("environment") != row["expected_environment"]:
        raise RuntimeError("Existing version environment differs: " + wanted["name"])
    for key in ("loaders", "game_versions"):
        if sorted(version[key]) != sorted(wanted[key]):
            raise RuntimeError(f"Existing version compatibility differs ({key}): {wanted['name']}")
    actual = {(d.get("project_id"), d.get("version_id"), d["dependency_type"]) for d in version["dependencies"]}
    expected = {(d.get("project_id"), d.get("version_id"), d["dependency_type"]) for d in wanted["dependencies"]}
    if actual != expected:
        raise RuntimeError("Existing version dependencies differ: " + wanted["name"])
    if len(version["files"]) != 1:
        raise RuntimeError("Expected exactly one release file: " + wanted["name"])
    file = version["files"][0]
    if (file["hashes"]["sha512"] != row["sha512"] or file["filename"] != Path(row["artifact"]).name
            or file["size"] != row["size"] or not file["primary"]):
        raise RuntimeError("Existing version file differs: " + wanted["name"])


def publish(api):
    manifest = read(RELEASE / "manifest.json")
    account = api.request("/user")
    owned = api.request("/user/" + account["id"] + "/projects")
    if not any(p["id"] == PROJECT for p in owned):
        raise RuntimeError("The token account cannot manage the CCR project")
    rows = manifest["versions"]
    if manifest["project_id"] != PROJECT or len(rows) != 25:
        raise RuntimeError("Invalid publication plan")
    # Check every artifact before the first external change.
    for row in rows:
        if hashlib.sha512((ROOT / row["artifact"]).read_bytes()).hexdigest() != row["sha512"]:
            raise RuntimeError("Artifact changed after preparation: " + row["project"])
    existing = api.request("/project/" + PROJECT + "/version")
    receipt = {"project_id": PROJECT, "started_at": datetime.now(timezone.utc).isoformat(), "versions": []}
    for index, row in enumerate(rows, 1):
        name = row["payload"]["version_number"]
        candidates = [v for v in existing if v["version_number"] == name or v["name"] == name]
        if len(candidates) > 1:
            raise RuntimeError("Ambiguous duplicate release: " + name)
        if candidates:
            version = candidates[0]
        else:
            # Do not retry an uncertain POST. A subsequent invocation recovers by
            # remote name and exact SHA-512, without publishing duplicate versions.
            version = api.upload(row["payload"], ROOT / row["artifact"])
            existing.append(version)
        matches(version, row)
        receipt["versions"].append({"project": row["project"], "id": version["id"], "name": name,
                                     "sha512": row["sha512"], "url": "https://modrinth.com/mod/cut-clean-reborn/version/" + version["id"]})
        write(RESULTS / "published.json", receipt)
        print(f"[{index}/25] Published and verified {name} ({version['id']})", flush=True)
        time.sleep(0.15)
    receipt["finished_at"] = datetime.now(timezone.utc).isoformat()
    write(RESULTS / "published.json", receipt)
    verify(api)


def verify(api):
    rows = read(RELEASE / "manifest.json")["versions"]
    current = api.request("/project/" + PROJECT + "/version")
    checked = []
    for row in rows:
        candidates = [v for v in current if v["version_number"] == row["payload"]["version_number"]]
        if len(candidates) != 1:
            raise RuntimeError("Missing or duplicate public release: " + row["project"])
        matches(candidates[0], row)
        checked.append({"project": row["project"], "id": candidates[0]["id"], "status": "PASS"})
    project = api.request("/project/" + PROJECT)
    if project["status"] != "approved" or set(project["loaders"]) != {"bukkit", "spigot", "paper", "purpur", "fabric"}:
        raise RuntimeError("Public project/platform metadata is incomplete")
    query = urllib.parse.urlencode({"ids": json.dumps([r["id"] for r in checked])})
    types = {v["id"]: v["project_types"] for v in api.request("/versions?" + query, api_version="v3")}
    for row, result in zip(rows, checked):
        expected = ["mod"] if row["payload"]["loaders"] == ["fabric"] else ["plugin"]
        if types.get(result["id"]) != expected:
            raise RuntimeError("Wrong mod/plugin classification: " + result["project"])
    write(RESULTS / "verified.json", {"verified_at": datetime.now(timezone.utc).isoformat(),
                                     "project_id": PROJECT, "project_status": project["status"], "versions": checked,
                                     "project_description_matches_plan": project["body"] ==
                                     (RELEASE / "project-description.md").read_text(encoding="utf-8")})
    print("Verified 25 public releases, exact files, dependencies, environments and mod/plugin classification.", flush=True)


def update_description(api):
    """Optional project-page edit, requiring the separate PROJECT_WRITE scope."""
    description = (RELEASE / "project-description.md").read_text(encoding="utf-8")
    current = api.request("/project/" + PROJECT)
    if current["body"] != description:
        before = read(RESULTS / "project-before-publication.json")
        if current["body"] != before["body"]:
            raise RuntimeError("Project description changed concurrently; preserve the new edit")
        api.json("/project/" + PROJECT, "PATCH", {"body": description})
    if api.request("/project/" + PROJECT)["body"] != description:
        raise RuntimeError("Project description did not persist")
    print("Project description updated and verified.", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["prepare", "publish", "verify", "update-description"])
    args = parser.parse_args()
    try:
        globals()[args.mode.replace("-", "_")](Api())
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
