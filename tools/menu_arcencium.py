"""Repose le menu principal du mode par-dessus celui d'ATM10.

POURQUOI CE SCRIPT EXISTE.

Le menu vivait UNIQUEMENT dans le dossier de l'instance CurseForge
(`packmenu/resources/`), pose a la main une fois. Le depot n'en gardait qu'une
copie morte, dans `tools/pack/packmenu/`, que rien ne reinstallait.

Il a donc suffi que CurseForge repare l'instance -- ou qu'on en recree une --
pour que le fond « Please Change Me » et le bouton Akliz d'ATM10 reviennent
sans que personne ne touche a quoi que ce soit. Mesure du 4 septembre : les
deux instances portaient l'image d'ATM10 du 8 juin, et l'export l'emportait
telle quelle dans le zip -- le profil importe ouvrait donc sur le menu d'ATM10.

Le depot fait desormais foi. Ce script recopie `tools/pack/packmenu/assets`
par-dessus le `packmenu/resources/assets` de chaque instance, et
`export_modpack.py` applique le meme calque au zip : une instance reparee
redevient juste au prochain passage, et le zip ne peut plus mentir.

    python tools/menu_arcencium.py            # les instances connues
    python tools/menu_arcencium.py "<chemin>" # une instance precise
"""

import hashlib
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "tools", "pack", "packmenu", "assets")

INSTANCES = os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Instances")
DEFAULTS = ["All the Mods 10 - CUSTOM", "Mode Arcencium"]


def digest(path):
    if not os.path.isfile(path):
        return "(absent)"
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()[:12]


def apply(assets_dir):
    """Pose le calque sur un dossier `assets` de PackMenu. Rend le nombre de fichiers changes."""
    changed = 0
    for folder, _, files in os.walk(SOURCE):
        rel = os.path.relpath(folder, SOURCE)
        dest_dir = os.path.join(assets_dir, rel) if rel != "." else assets_dir
        os.makedirs(dest_dir, exist_ok=True)
        for name in files:
            src = os.path.join(folder, name)
            dst = os.path.join(dest_dir, name)
            before = digest(dst)
            after = digest(src)
            if before == after:
                continue
            shutil.copy2(src, dst)
            changed += 1
            print("    %-46s %s -> %s" % (os.path.join(rel, name).replace("\\", "/"),
                                          before, after))
    return changed


def main():
    targets = sys.argv[1:] or [os.path.join(INSTANCES, name) for name in DEFAULTS]
    if not os.path.isdir(SOURCE):
        raise SystemExit("Aucun calque de menu dans %s" % SOURCE)
    for instance in targets:
        assets = os.path.join(instance, "packmenu", "resources", "assets")
        label = os.path.basename(instance.rstrip("\\/"))
        if not os.path.isdir(os.path.dirname(assets)):
            print("  %-28s pas de dossier packmenu, ignoree" % label)
            continue
        print("  %s" % label)
        changed = apply(assets)
        print("    %d fichier(s) repose(s)" % changed)


if __name__ == "__main__":
    main()
