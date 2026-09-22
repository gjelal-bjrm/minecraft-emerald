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

LE SERVEUR DES BANCS (run-server/) tourne SANS les mods du modpack, et c'est
voulu (build.gradle) : les bancs mesurent la ville avec le seul mod. Le banc de
la faune de Haven a pourtant besoin des animaux d'Alex's Mobs, d'Aquaculture et
de Living Things. --server les pose dans run-server/mods le temps de ce banc,
et --server --clean les retire ensuite :

    python tools/dev_mods.py --server alexsmobs aquaculture livingthings
    python tools/dev_mods.py --server --clean
"""

import io as _io
import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN_MODS = os.path.join(ROOT, "run", "mods")
SERVER_MODS = os.path.join(ROOT, "run-server", "mods")
# LE PROFIL DU MODE, PAS LA COPIE D'ATM10 : le joueur ne joue plus sur
# « All the Mods 10 - CUSTOM », et le dev ne doit pas lire ailleurs que la ou
# il joue. Les deux dossiers ont les memes jars a quatre pres, mais le principe
# compte : une seule source de verite.
MODS_DIR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge", "minecraft",
                        "Instances", "Mode Arcencium", "mods")

PROVIDED = {"minecraft", "neoforge", "forge", "java"}

_MODID = re.compile(r'^\s*modId\s*=\s*"([^"]+)"', re.MULTILINE)
_MODS_BLOCK = re.compile(r'\[\[mods\]\](.*?)(?=\[\[|\Z)', re.DOTALL)
_DEP_BLOCK = re.compile(r'\[\[dependencies\.[^\]]+\]\](.*?)(?=\[\[|\Z)', re.DOTALL)
_TYPE = re.compile(r'^\s*type\s*=\s*"([^"]+)"', re.MULTILINE)
_MANDATORY = re.compile(r'^\s*mandatory\s*=\s*(true|false)', re.MULTILINE)


def read_toml(path):
    """Rend le mods.toml d'un jar, ou None si ce n'est pas un mod."""
    try:
        with zipfile.ZipFile(path) as z:
            for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                if name in z.namelist():
                    return z.read(name).decode("utf-8", "replace")
    except Exception:
        pass
    return None


def nested_ids(path):
    """Identifiants fournis par les jars EMBARQUES dans celui-ci.

    NeoForge extrait tout seul les jar-in-jar de META-INF/jarjar : une
    dependance qui s'y trouve est donc deja satisfaite, et la chercher dans le
    modpack la ferait passer pour manquante a tort.
    """
    found = set()
    try:
        with zipfile.ZipFile(path) as z:
            for inner in z.namelist():
                if not (inner.startswith("META-INF/jarjar/") and inner.endswith(".jar")):
                    continue
                with z.open(inner) as fh:
                    data = _io.BytesIO(fh.read())
                try:
                    with zipfile.ZipFile(data) as iz:
                        for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                            if name in iz.namelist():
                                text = iz.read(name).decode("utf-8", "replace")
                                found.update(_MODID.findall(text.split("[[dependencies.")[0]))
                except Exception:
                    continue
    except Exception:
        pass
    return found


def parse(text):
    provides = set()
    requires = set()
    # les identifiants des blocs [[mods]] sont ceux du mod lui-meme. Pas « tout ce qui
    # precede la premiere dependance » : Alex's Mobs declare ses dependances AVANT son
    # bloc [[mods]], et son jar passait pour celui de CodxLib (22 sept.)
    for block in _MODS_BLOCK.findall(text):
        provides.update(_MODID.findall(block))
    if not provides:
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
        provides |= nested_ids(path)
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
    global RUN_MODS
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--server" in sys.argv:
        RUN_MODS = SERVER_MODS
    where = os.path.relpath(RUN_MODS, ROOT).replace(os.sep, "/")
    os.makedirs(RUN_MODS, exist_ok=True)

    if "--clean" in sys.argv:
        for name in os.listdir(RUN_MODS):
            if name.endswith(".jar"):
                os.remove(os.path.join(RUN_MODS, name))
        print("%s vide" % where)
        return
    if "--list" in sys.argv or not args:
        jars = sorted(n for n in os.listdir(RUN_MODS) if n.endswith(".jar"))
        print("%d jar(s) dans %s :" % (len(jars), where))
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
    print("%d jar(s) installes dans %s" % (len(chosen), where))


if __name__ == "__main__":
    main()
