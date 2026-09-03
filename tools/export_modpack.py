#!/usr/bin/env python3
"""
Fabrique le profil CurseForge du Mode Arcencium, pret a importer ailleurs.

Un pack CurseForge est un zip de deux choses :

  - `manifest.json`, qui NOMME les mods par leurs identifiants projet/fichier.
    CurseForge les telechargera lui-meme a l'import : le zip ne les contient
    pas, ce qui le garde petit et legal.
  - `overrides/`, une copie de l'instance qui sera deversee par-dessus : les
    reglages, les scripts, le shader, et les jars qui ne sont PAS sur
    CurseForge -- dont le notre.

Les identifiants sont lus dans le `minecraftinstance.json` de l'instance, que
CurseForge tient a jour ; on ne les invente pas, et le pack suit donc
exactement ce qui est installe le jour de l'export.

Usage :
    python tools/export_modpack.py                    # pack complet
    python tools/export_modpack.py --slim             # sans kubejs/assets (~145 Mo de moins)
    python tools/export_modpack.py --instance "<chemin>" --version 1.2.0
"""
import argparse
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_INSTANCE = Path(os.environ.get("USERPROFILE", "")) / "curseforge" / "minecraft" \
    / "Instances" / "All the Mods 10 - CUSTOM"

NAME = "Mode Arcencium"
AUTHOR = "Gjelal"
# Le shader retenu au cahier (§27) ; les autres packs de l'instance ne suivent pas.
SHADERPACK = "ComplementaryUnbound_r5.5.1 + EuphoriaPatches_1.6.4"
# Ce qu'on recopie tel quel dans overrides/.
FOLDERS = ["config", "defaultconfigs", "kubejs", "resourcepacks", "datapacks"]
FILES = ["options.txt"]
# Jamais : sauvegardes, journaux, caches, captures -- et le grenier de CurseForge.
SKIP_DIRS = {"logs", "crash-reports", "saves", "screenshots", "backups", "local",
             "downloads", "debug", ".mixin.out"}


def human(size):
    return f"{size / 1e6:.1f} Mo"


def tree_size(path):
    total = 0
    for base, dirs, files in os.walk(path):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for f in files:
            try:
                total += os.path.getsize(os.path.join(base, f))
            except OSError:
                pass
    return total


def collect(instance: Path, version: str, slim: bool, out_dir: Path):
    inst_json = instance / "minecraftinstance.json"
    if not inst_json.exists():
        sys.exit(f"Instance introuvable : {inst_json}")
    data = json.loads(inst_json.read_text(encoding="utf-8"))

    loader = (data.get("baseModLoader") or {}).get("name") or "neoforge-21.1.174"
    mc_version = (data.get("baseModLoader") or {}).get("minecraftVersion") or "1.21.1"

    # ---- les mods que CurseForge sait retrouver seul
    by_file = {}
    for addon in data.get("installedAddons", []):
        f = addon.get("installedFile") or {}
        if f.get("fileName") and addon.get("addonID") and f.get("id"):
            by_file[f["fileName"]] = (addon["addonID"], f["id"])

    mods_dir = instance / "mods"
    present = sorted(p.name for p in mods_dir.glob("*.jar"))
    files, loose = [], []
    for jar in present:
        if jar in by_file:
            project, file_id = by_file[jar]
            files.append({"projectID": project, "fileID": file_id, "required": True})
        else:
            loose.append(jar)

    # ---- le pack sur le disque
    if out_dir.exists():
        shutil.rmtree(out_dir)
    over = out_dir / "overrides"
    (over / "mods").mkdir(parents=True)

    # notre jar : celui qu'on vient de compiler, pas la copie de l'instance
    ours = sorted(ROOT.glob("build/libs/emeraldweapons-*.jar"))
    ours = [p for p in ours if "sources" not in p.name and "-dev" not in p.name]
    fresh = ours[-1].name if ours else None
    for jar in loose:
        if fresh and jar.startswith("emeraldweapons-"):
            continue                          # remplace par le jar frais, plus bas
        shutil.copy2(mods_dir / jar, over / "mods" / jar)
    if fresh:
        shutil.copy2(ours[-1], over / "mods" / fresh)
        if fresh not in loose:
            loose.append(fresh)

    # ---- les reglages, scripts et ressources
    copied = []
    for folder in FOLDERS:
        src = instance / folder
        if not src.is_dir():
            continue
        if slim and folder == "kubejs":
            dst = over / folder
            shutil.copytree(src, dst, ignore=shutil.ignore_patterns("assets"))
            copied.append(f"{folder} (sans assets)")
        else:
            shutil.copytree(src, over / folder)
            copied.append(folder)
    for name in FILES:
        if (instance / name).is_file():
            shutil.copy2(instance / name, over / name)
            copied.append(name)

    # ---- le shader retenu, et lui seul
    shaders = instance / "shaderpacks" / SHADERPACK
    if shaders.is_dir():
        shutil.copytree(shaders, over / "shaderpacks" / SHADERPACK)
        copied.append(f"shaderpacks/{SHADERPACK}")

    # ---- l'image du profil, si elle a ete peinte
    icon = ROOT / "tools" / "pack" / "icon.png"
    if icon.is_file():
        shutil.copy2(icon, out_dir / "icon.png")

    # ---- manifest et liste lisible
    manifest = {
        "minecraft": {"version": mc_version,
                      "modLoaders": [{"id": loader, "primary": True}],
                      "recommendedRam": 16384},
        "manifestType": "minecraftModpack",
        "manifestVersion": 1,
        "name": NAME,
        "version": version,
        "author": AUTHOR,
        "files": files,
        "overrides": "overrides",
    }
    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")

    rows = "\n".join(f"<li>{jar}</li>" for jar in loose)
    (out_dir / "modlist.html").write_text(
        "<html><head><meta charset='utf-8'><title>" + NAME + "</title></head><body>"
        f"<h1>{NAME} {version}</h1>"
        f"<p>{len(files)} mods installes par CurseForge, "
        f"{len(loose)} fournis dans le pack :</p><ul>{rows}</ul>"
        "</body></html>", encoding="utf-8")
    return files, loose, copied


def zip_pack(out_dir: Path, target: Path):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        target.unlink()
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for base, dirs, files in os.walk(out_dir):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for f in files:
                full = Path(base) / f
                z.write(full, full.relative_to(out_dir))
    return target.stat().st_size


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--instance", default=str(DEFAULT_INSTANCE))
    ap.add_argument("--version", default=None, help="par defaut, la version du mod")
    ap.add_argument("--slim", action="store_true", help="sans kubejs/assets")
    ap.add_argument("--out", default=str(ROOT / "dist"))
    args = ap.parse_args()

    version = args.version
    if not version:
        props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        for line in props.splitlines():
            if line.strip().startswith("mod_version"):
                version = line.split("=", 1)[1].strip()
    version = version or "1.0.0"

    instance = Path(args.instance)
    out_root = Path(args.out)
    staging = out_root / "pack"
    print(f"Instance : {instance}")
    files, loose, copied = collect(instance, version, args.slim, staging)

    print(f"  {len(files)} mods references par identifiant CurseForge")
    print(f"  {len(loose)} jars embarques :")
    for jar in sorted(loose):
        print(f"      {jar}")
    print(f"  overrides : {', '.join(copied)}")
    print(f"  poids des overrides : {human(tree_size(staging))}")

    target = out_root / f"ModeArcencium-{version}{'-slim' if args.slim else ''}.zip"
    size = zip_pack(staging, target)
    print(f"\nPack ecrit : {target}  ({human(size)})")
    print("Import : CurseForge -> Create Custom Profile -> Import -> choisir ce zip.")


if __name__ == "__main__":
    main()
