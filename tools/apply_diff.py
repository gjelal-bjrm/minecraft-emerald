#!/usr/bin/env python3
"""
Transforme le releve de la Sonde en calque Java, sans l'interpreter.

C'est l'outil qui met fin a dix allers-retours. Le joueur corrigeait le
sanctuaire a la main ; je lisais son releve, j'en tirais une REGLE, et je
reecrivais le generateur d'apres cette regle. Chaque traduction perdait
quelque chose : une orientation de marche, une bordure prise pour un passage,
un palier pris pour un trou.

Ici, aucune traduction. Chaque ligne « cx+3 y+5 cz-12 : A -> B » devient une
pose de B a cette case, exactement. Le calque s'applique en dernier, une fois
le sanctuaire bati.

On ecarte le bruit -- pieges declenches, sable tombe, coffres convertis par
Lootr -- qui n'est pas du fait du joueur et reviendrait a chaque partie.

Usage :
    python tools/apply_diff.py [chemin/du/releve]
"""

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORT = os.path.join(ROOT, "run", "arcencium_diff.txt")
TARGET = os.path.join(ROOT, "src", "main", "java", "com", "emerald", "game",
                      "SanctuaryOverlay.java")

# Ce qui bouge tout seul entre la construction et le releve.
NOISE = ("falling_trap", "lootr_chest", "lootr_trapped_chest")

# La cour est piétinée par la garnison : l'herbe qui tourne en terre n'est le
# fait de personne et reviendrait a chaque partie.
TRAMPLED = ("grass_block", "dirt", "farmland", "grass", "short_grass", "fern",
            "snow", "fire", "torch")

LINE = re.compile(r"^\s+cx([+-]\d+) y([+-]\d+) cz([+-]\d+) : (\S+) -> (\S+)$")


def read(path):
    cells = []
    for raw in io.open(path, encoding="utf-8").read().splitlines():
        m = LINE.match(raw)
        if not m:
            continue
        was, now = m.group(4), m.group(5)
        if any(n in was or n in now for n in NOISE):
            continue
        # le sable qui tombe laisse de l'air : ce n'est pas une demolition
        if was.endswith("sand") and now.endswith("air"):
            continue
        if any(t in was for t in TRAMPLED) and any(t in now for t in TRAMPLED):
            continue
        cells.append((int(m.group(1)), int(m.group(2)), int(m.group(3)), now))
    return cells


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else REPORT
    if not os.path.exists(path):
        sys.exit("Releve introuvable : %s" % path)
    cells = read(path)

    # ON CUMULE, on ne remplace pas.
    #
    # Un releve se prend contre le sanctuaire tel qu'il vient d'etre bati --
    # calque compris. Il ne contient donc que ce qui a change DEPUIS, et ecraser
    # le tableau avec lui effacerait tout le travail des tours precedents. On
    # relit donc les cases deja posees, et les nouvelles priment a position
    # egale : c'est la derniere volonte du joueur qui compte.
    src_before = io.open(TARGET, encoding="utf-8").read()
    merged = {}
    for old in re.findall(r'\{"(-?\d+)", "(-?\d+)", "(-?\d+)", "([^"]+)"\}', src_before):
        merged[(int(old[0]), int(old[1]), int(old[2]))] = old[3]
    fresh = 0
    for dx, dy, dz, state in cells:
        if merged.get((dx, dy, dz)) != state:
            fresh += 1
        merged[(dx, dy, dz)] = state
    cells = [(k[0], k[1], k[2], v) for k, v in sorted(merged.items(),
                                                     key=lambda kv: (kv[0][2], kv[0][1], kv[0][0]))]
    print("%d deja posees, %d nouvelles ou modifiees" % (len(merged) - fresh, fresh))
    # Un etat sans espace de noms ne se relit pas : c'est le signe d'un releve
    # produit avant que le format complet ne soit en place.
    naked = [c for c in cells if ":" not in c[3]]
    if naked:
        sys.exit("%d etats sans espace de noms (ex. « %s ») : refais un releve "
                 "avec la version a jour du mod." % (len(naked), naked[0][3]))
    src = io.open(TARGET, encoding="utf-8").read()
    body = "\n".join(
        '            {"%d", "%d", "%d", "%s"},' % c for c in cells)
    # La borne haute NE contient PAS le saut de ligne qui la precede : sinon
    # le groupe d'ouverture l'a deja consomme, plus rien ne correspond, et
    # re.sub rend le fichier inchange -- ce qu'il a fait deux fois en silence.
    out, count = re.subn(r"(CELLS = \{)[\s\S]*?(\n    \};)",
                         lambda m: m.group(1) + "\n" + body + m.group(2),
                         src)
    if count != 1:
        sys.exit("Tableau CELLS introuvable dans %s" % TARGET)
    io.open(TARGET, "w", encoding="utf-8", newline="\n").write(out)
    print("%d cases rejouees dans %s" % (len(cells), os.path.relpath(TARGET, ROOT)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
