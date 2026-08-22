#!/usr/bin/env python3
"""
Import en masse d'un village : convertit les structures d'un mod vers notre
palette, les ecrit dans le mod, et prepare les commandes pour aller les
visiter en jeu.

Les .nbt atterrissent dans src/main/resources/data/emeraldweapons/structure/,
ce qui les rend adressables par `/place template emeraldweapons:<nom>` --
pas besoin de bidouiller le dossier de sauvegarde.

Usage :
    python tools/import_village.py                     # village taiga complet
    python tools/import_village.py --theme mountain    # autre theme CTOV
    python tools/import_village.py --only house        # sous-ensemble
    python tools/import_village.py --render            # + apercus iso
"""

import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_structure as nbtlib
import reskin

ROOT = nbtlib.ROOT
OUT_DIR = reskin.OUT_DIR
CMD_FILE = os.path.join(ROOT, "tools", "preview", "builds", "commandes.txt")


def import_theme(theme="taiga", jar="ctov", only=None, render=False, limit=None):
    jp = nbtlib.find_jar(jar)
    prefix = "/village/%s/" % theme
    with zipfile.ZipFile(jp) as z:
        names = sorted(n for n in z.namelist()
                       if n.endswith(".nbt") and prefix in n
                       and (only is None or only in n))
    if limit:
        names = names[:limit]
    os.makedirs(OUT_DIR, exist_ok=True)
    done, failed, skipped, unmapped_total = [], [], 0, {}

    for n in names:
        rel = n.split(prefix, 1)[1][:-4].replace("/", "_")
        out_name = "%s_%s" % (theme, rel)
        try:
            st = nbtlib.load(n, jar)
        except Exception as e:
            failed.append((out_name, str(e)[:60]))
            continue
        if len(st.solid_cells()) < 8:
            skipped += 1                       # marqueurs, entites montees
            continue
        root, stats = reskin.reskin_structure(st)
        with open(os.path.join(OUT_DIR, out_name + ".nbt"), "wb") as f:
            f.write(nbtlib.serialize(root))
        for k, v in reskin.unmapped(st).items():
            unmapped_total[k] = unmapped_total.get(k, 0) + v
        done.append((out_name, st.size, stats["blocs"]))
        if render:
            try:
                import build_view
                build_view.view(os.path.join(OUT_DIR, out_name + ".nbt"),
                                out_name, cut=0.0, plans=False, angles=(0,))
            except Exception:
                pass

    print("=== village '%s' importe ===" % theme)
    print("  %d structures ecrites, %d ignorees (trop petites), %d en echec"
          % (len(done), skipped, len(failed)))
    for name, err in failed[:5]:
        print("     echec %s : %s" % (name, err))
    if unmapped_total:
        tot = sum(unmapped_total.values())
        print("  blocs restes vanilla : %d (mobilier surtout), %d types"
              % (tot, len(unmapped_total)))
        for k, v in sorted(unmapped_total.items(), key=lambda kv: -kv[1])[:8]:
            print("     %-46s %5d" % (k, v))

    os.makedirs(os.path.dirname(CMD_FILE), exist_ok=True)
    with open(CMD_FILE, "w", encoding="utf-8") as f:
        f.write("# Commandes pour visiter les batiments importes.\n")
        f.write("# 1. Reconstruis le mod :  build_mod.bat\n")
        f.write("# 2. En jeu, en creatif, place-toi et lance une commande :\n\n")
        for name, size, blocks in sorted(done, key=lambda d: -d[2]):
            f.write("/place template emeraldweapons:%-40s  # %sx%sx%s, %d blocs\n"
                    % (name, size[0], size[1], size[2], blocks))
    print("  commandes : %s" % os.path.relpath(CMD_FILE, ROOT))
    if done:
        big = max(done, key=lambda d: d[2])
        print()
        print("  Pour visiter la plus grande :")
        print("     /place template emeraldweapons:%s" % big[0])
    return done


if __name__ == "__main__":
    argv = sys.argv[1:]

    def opt(flag, default=None):
        if flag in argv:
            i = argv.index(flag)
            return argv[i + 1]
        return default

    import_theme(theme=opt("--theme", "taiga"),
                 jar=opt("--jar", "ctov"),
                 only=opt("--only"),
                 render="--render" in argv,
                 limit=int(opt("--limit", 0)) or None)
