#!/usr/bin/env python3
"""
Ancre une structure monolithique par son CENTRE au lieu de son coin.

POURQUOI. Minecraft ne cherche les structures que dans un rayon de 8 chunks
(128 blocs) autour de chaque chunk genere : c'est en dur dans
ChunkGenerator.createReferences. Une piece de 163 blocs de cote posee depuis
son coin depasse ce rayon de 35 blocs -- les chunks au-dela n'apprennent
jamais l'existence de la structure et ne posent rien. D'ou une cathedrale
tranchee net, avec 31% de ses blocs manquants.

COMMENT. On cree une piece d'ancrage minuscule (1x1x1, un simple bloc jigsaw
qui disparait a la generation) qui devient le point de depart, et on greffe
la grande piece dessus par un jigsaw place en son MILIEU. Le batiment
s'etend alors de +/-81 blocs autour du chunk de depart, soit 5,1 chunks :
tout est dans les clous.

Usage :
    python tools/center_anchor.py cathedral_main_0 cathedral
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_structure as nbtlib

ROOT = nbtlib.ROOT
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons")
STRUCT_DIR = os.path.join(DATA, "structure")
NS = "emeraldweapons"


def make_jigsaw_nbt(name, target, pool, final_state="minecraft:air"):
    return {"id": "minecraft:jigsaw", "joint": "rollable", "name": name,
            "target": target, "pool": pool, "final_state": final_state}


def build(piece_name, family):
    """Ajoute un jigsaw central a la piece, et ecrit la piece d'ancrage."""
    path = os.path.join(STRUCT_DIR, piece_name + ".nbt")
    st = nbtlib.load(path)
    sx, sy, sz = st.size
    # Centre sur les TROIS axes. Le test max_distance_from_center applique
    # aux pieces enfants est une boite CUBIQUE : ancree en bas, une piece de
    # 250 de haut sort de la boite par le haut et est rejetee en entier.
    cx, cy, cz = sx // 2, sy // 2, sz // 2

    anchor_name = "%s:%s_anchor" % (NS, family)
    body_name = "%s:%s_body" % (NS, family)
    body_pool = "%s:%s/body" % (NS, family)

    # --- 1. le jigsaw central de la grande piece, tourne vers le bas
    palette = [{"Name": n, **({"Properties": dict(p)} if p else {})}
               for n, p in st.palette]
    jig_state = {"Name": "minecraft:jigsaw",
                 "Properties": {"orientation": "down_south"}}
    palette.append(jig_state)
    jig_index = len(palette) - 1

    blocks = nbtlib.TypedList([], nbtlib.TAG_COMPOUND)
    replaced = False
    for pos, idx in st.blocks.items():
        entry = {"pos": nbtlib.TypedList(list(pos), nbtlib.TAG_INT),
                 "state": idx}
        if pos == (cx, cy, cz):
            entry["state"] = jig_index
            entry["nbt"] = make_jigsaw_nbt(
                body_name, anchor_name, "minecraft:empty",
                # le bloc d'origine reprend sa place apres generation
                st.palette[idx][0])
            replaced = True
        elif pos in st.block_nbt:
            entry["nbt"] = st.block_nbt[pos]
        blocks.append(entry)
    if not replaced:
        blocks.append({"pos": nbtlib.TypedList([cx, cy, cz], nbtlib.TAG_INT),
                       "state": jig_index,
                       "nbt": make_jigsaw_nbt(body_name, anchor_name,
                                              "minecraft:empty",
                                              "minecraft:air")})

    root = {
        "size": nbtlib.TypedList([sx, sy, sz], nbtlib.TAG_INT),
        "palette": nbtlib.TypedList(palette, nbtlib.TAG_COMPOUND),
        "blocks": blocks,
        "entities": st.root.get("entities",
                                nbtlib.TypedList([], nbtlib.TAG_COMPOUND)),
        "DataVersion": int(st.root.get("DataVersion", 3955)),
    }
    with open(path, "wb") as f:
        f.write(nbtlib.serialize(root))

    # --- 2. la piece d'ancrage : un seul jigsaw, tourne vers le haut
    anchor = {
        "size": nbtlib.TypedList([1, 1, 1], nbtlib.TAG_INT),
        "palette": nbtlib.TypedList([
            {"Name": "minecraft:jigsaw",
             "Properties": {"orientation": "up_south"}}], nbtlib.TAG_COMPOUND),
        "blocks": nbtlib.TypedList([{
            "pos": nbtlib.TypedList([0, 0, 0], nbtlib.TAG_INT),
            "state": 0,
            "nbt": make_jigsaw_nbt(anchor_name, body_name, body_pool,
                                   "minecraft:air"),
        }], nbtlib.TAG_COMPOUND),
        "entities": nbtlib.TypedList([], nbtlib.TAG_COMPOUND),
        "DataVersion": int(st.root.get("DataVersion", 3955)),
    }
    apath = os.path.join(STRUCT_DIR, "%s_anchor.nbt" % family)
    with open(apath, "wb") as f:
        f.write(nbtlib.serialize(anchor))

    print("piece   : %s  (%dx%dx%d), jigsaw central en (%d, %d, %d)"
          % (piece_name, sx, sy, sz, cx, cy, cz))
    print("ancrage : %s_anchor.nbt" % family)
    print("portee  : +/-%d blocs horizontalement (%.1f chunks, limite 8)"
          % (max(cx, cz), max(cx, cz) / 16))
    print("          +/-%d blocs verticalement (limite max_distance 128)" % cy)
    print("start_height a mettre : %d  -> base de la piece a y=%d"
          % (54 + cy, 54 + cy + 1 - cy))
    return cx, cy, cz


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "cathedral_main_0",
          sys.argv[2] if len(sys.argv) > 2 else "cathedral")
