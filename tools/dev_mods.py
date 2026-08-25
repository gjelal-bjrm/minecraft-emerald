#!/usr/bin/env python3
"""
Installe des mods du modpack dans l'environnement de developpement.

Le runtime de dev charge ce qu'il trouve dans run/mods/. Copier un jar ne
suffit pourtant pas : chaque mod declare ses dependances dans son
neoforge.mods.toml, et refuse de demarrer si l'une manque. Ce script les
resout donc TRANSITIVEMENT depuis le dossier du modpack, puis copie tout.

Les dependances sur minecraft et neoforge sont ignorees : elles sont fournies
par l'environnement lui-meme.

Usage :
    python tools/dev_mods.py gateways apotheosis cataclysm irons_spellbooks
    python tools/dev_mods.py --list          # ce qui est deja installe
    python tools/dev_mods.py --clean         # vide run/mods
"""

import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN_MODS = os.path.join(ROOT, "run", "mods")
MODS_DIR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge", "minecraft",
                        "Instances", "All the Mods 10 - CUSTOM", "mods")

PROVIDED = {"minecraft", "neoforge", "forge", "java"}

_MODID = re.compile(r'^\s*modId\s*=\s*"([^"]+)"', re.MULTILINE)
_DEP_BLOCK = re.compile(r'\[\[dependencies\.[^\]]+\]\](.*?)(?=\[\[|\Z)', re.DOTALL)
_TYPE = re.compile(r'^\s*type\s*=\s*"([^"]+)"', re.MULTILINE)
_MANDATORY = re.compile(r'^\s*mandatory\s*=\s*(true|false)', re.MULTILINE)


def read_toml(path):
    """Rend (ids fournis, ids requis) pour un jar, ou None si ce n'est pas un mod."""
    try:
        with zipfile.ZipFile(path) as z:
            for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                if name in z.namelist():
                    return z.read(name).decode("utf-8", "replace")
    except Exception:
        pass
    return None


def parse(text):
    provides = set()
    requires = set()
    # les identifiants declares hors bloc de dependance sont ceux du mod lui-meme
    head = text.split("[[dependencies.")[0]
    provides.update(_MODID.findall(head))
    for block in _DEP_BLOCK.findall(text):
        ids = _MODID.findall(block)
        if not ids:
            continue
        kind = _TYPE.search(block)
        mandatory = _MANDATORY.search(block)
        needed = (kind.group(1) == "required") if kind else (
            mandatory.group(1) == "true" if mandatory else True)
        if needed:
            requires.update(i for i in ids if i not in PROVIDED)
    return provides, requires


def index():
    """Catalogue le dossier du modpack : identifiant de mod -> chemin du jar."""
    by_id = {}
    meta = {}
    for name in sorted(os.listdir(MODS_DIR)):
        if not name.endswith(".jar"):
            continue
        path = os.path.join(MODS_DIR, name)
        text = read_toml(path)
        if not text:
            continue
        provides, requires = parse(text)
        meta[path] = requires
        for mod_id in provides:
            by_id.setdefault(mod_id, path)
    return by_id, meta


def resolve(targets, by_id, meta):
    """Ferme transitivement l'ensemble des jars a copier."""
    chosen = {}
    missing = set()
    queue = list(targets)
    seen = set()
    while queue:
        mod_id = queue.pop()
        if mod_id in seen or mod_id in PROVIDED:
            continue
        seen.add(mod_id)
        path = by_id.get(mod_id)
        if path is None:
            missing.add(mod_id)
            continue
        chosen[path] = mod_id
        queue.extend(meta.get(path, ()))
    return chosen, missing


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    os.makedirs(RUN_MODS, exist_ok=True)

    if "--clean" in sys.argv:
        for name in os.listdir(RUN_MODS):
            if name.endswith(".jar"):
                os.remove(os.path.join(RUN_MODS, name))
        print("run/mods vide")
        return
    if "--list" in sys.argv or not args:
        jars = sorted(n for n in os.listdir(RUN_MODS) if n.endswith(".jar"))
        print("%d jar(s) dans run/mods :" % len(jars))
        for name in jars:
            print("   " + name)
        return

    if not os.path.isdir(MODS_DIR):
        sys.exit("dossier du modpack introuvable : %s" % MODS_DIR)

    print("Lecture du modpack...")
    by_id, meta = index()
    print("  %d mods catalogues" % len(by_id))

    # les cibles peuvent etre donnees par identifiant exact ou par fragment de nom
    targets = set()
    for wanted in args:
        if wanted in by_id:
            targets.add(wanted)
            continue
        hits = [m for m in by_id if wanted.lower() in m.lower()]
        if not hits:
            print("  !! aucun mod ne correspond a %r" % wanted)
            continue
        targets.update(hits)

    chosen, missing = resolve(targets, by_id, meta)
    for path, mod_id in sorted(chosen.items(), key=lambda kv: kv[1]):
        dest = os.path.join(RUN_MODS, os.path.basename(path))
        if not os.path.exists(dest):
            shutil.copy2(path, dest)
        print("  %-28s %s" % (mod_id, os.path.basename(path)))
    if missing:
        print("  !! dependances introuvables : %s" % ", ".join(sorted(missing)))
    print("%d jar(s) installes dans run/mods" % len(chosen))


if __name__ == "__main__":
    main()
