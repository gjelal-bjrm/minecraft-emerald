#!/usr/bin/env python3
"""
Copie le releve d'une salle de la ville dans le mod, apres controle.

C'est la suite d'apply_diff.py, pour la ville de Jak. La Sonde (clic droit
dans le vide, debout dans la salle) ou « /arcencium haven salle <n> capture »
ecrivent run/arcencium_jak/<salle>.nbt : ce que le joueur a change dans la
salle par rapport au volume du port, etat complet, NBT des coffres et des
panneaux, cadres, tableaux et porte-armures. Ce script le copie dans

    src/main/resources/data/emeraldweapons/jak/zones/ctyport/<salle>.nbt

ou JakOverlay le rejoue apres chaque pose de la ville. Il n'interprete rien :
il copie, ou il refuse, ou il ecarte ce qui n'est jamais un amenagement.

CE QU'IL REFUSE, et pourquoi :
  - un releve pris sur une AUTRE VERSION DU PORT (sha1 du volume different de
    celui du depot) : ses cellules ne designent plus les memes blocs, et le jeu
    le refuserait de toute facon ;
  - un releve pris a une autre origine de pose que Haven.ORIGIN ;
  - un etat SANS ESPACE DE NOMS : il ne se relit pas, c'est le signe d'un
    fichier fabrique ou retouche a la main.

CE QU'IL ECARTE, par identifiant EXACT et jamais par sous-chaine -- le filtre
d'apply_diff.py jetait les feux de camp parce que « campfire » contient
« fire » :
  - l'eau et la lave qui COULENT (minecraft:water ou minecraft:lava, level
    different de 0) : le releve des sanctuaires en contenait soixante-six ;
  - toute entite qui n'est pas un decor (cadre, cadre lumineux, tableau,
    porte-armure).

CE QU'IL SIGNALE sans rien retirer : les blocs d'autres mods absents de
run/mods -- le dev n'a qu'une partie du modpack, et un bloc absent est saute a
la pose, avec une ligne au journal seulement.

Le NBT est relu et reecrit EN GARDANT SES TYPES : nbt_structure.Writer devine
le type d'apres la valeur Python, si bien qu'un flottant y redevient un double
et un tableau d'entiers une liste -- la rotation d'un porte-armure ne se
relirait plus. Quand rien n'est ecarte, le fichier est copie octet pour octet.

Usage :
    python tools/jak_zone_apply.py <salle> [--from DOSSIER] [--dest DOSSIER] [--dry-run]

    <salle>   1, 2, 3, l'identifiant (appartement_1), ou le chemin d'un .nbt
    --from    dossier des releves (defaut : run/arcencium_jak)
    --dest    dossier de sortie (defaut : src/main/resources/.../jak/zones/<volume>)
    --dry-run controles et resume, sans rien ecrire
"""

import argparse
import collections
import gzip
import json
import os
import re
import shutil
import struct
import sys
import zipfile
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_structure as nbt  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN_JAK = os.path.join(ROOT, "run", "arcencium_jak")
DATA_JAK = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons", "jak")
ROOMS = os.path.join(DATA_JAK, "haven_rooms.json")
MODS = os.path.join(ROOT, "run", "mods")
HAVEN_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "emerald", "haven", "Haven.java")

DECOR = {"minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting", "minecraft:armor_stand"}
FLUIDS = {"minecraft:water": "eau", "minecraft:lava": "lave"}
# le jeu et le mod lui-meme sont toujours la
PRESENT = {"minecraft", "emeraldweapons"}
FORMAT = 1


# ------------------------------------------------------------ NBT type

class Tag(object):
    """Une valeur NBT et son type, pour reecrire le fichier sans rien changer d'autre."""
    __slots__ = ("type", "value")

    def __init__(self, kind, value):
        self.type = kind
        self.value = value

    def __eq__(self, other):
        return isinstance(other, Tag) and self.type == other.type and self.value == other.value

    def __repr__(self):
        return "Tag(%d, %r)" % (self.type, self.value)


class TypedReader(nbt.Reader):
    """Le lecteur de nbt_structure, qui garde le type de chaque valeur."""

    def typed(self, kind):
        if kind == nbt.TAG_LIST:
            item = self.u1()
            count = self.i4()
            return Tag(kind, (item, [self.typed(item) for _ in range(count)]))
        if kind == nbt.TAG_COMPOUND:
            out = collections.OrderedDict()
            while True:
                child = self.u1()
                if child == nbt.TAG_END:
                    return Tag(kind, out)
                name = self.string()
                out[name] = self.typed(child)
        return Tag(kind, self.payload(kind))


class TypedWriter(nbt.Writer):
    """L'ecrivain de nbt_structure, qui ecrit le type lu et non un type devine."""

    def typed(self, tag):
        kind, value = tag.type, tag.value
        if kind == nbt.TAG_LIST:
            item, items = value
            self.u1(item)
            self.o.write(struct.pack(">i", len(items)))
            for entry in items:
                self.typed(entry)
        elif kind == nbt.TAG_COMPOUND:
            for name, entry in value.items():
                self.u1(entry.type)
                self.string(name)
                self.typed(entry)
            self.u1(nbt.TAG_END)
        elif kind == nbt.TAG_LONG_ARRAY:
            self.o.write(struct.pack(">i", len(value)))
            for entry in value:
                self.o.write(struct.pack(">q", entry))
        else:
            self.payload(kind, value)


def read_nbt(path):
    with open(path, "rb") as handle:
        data = handle.read()
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    reader = TypedReader(data)
    if reader.u1() != nbt.TAG_COMPOUND:
        raise ValueError("racine NBT inattendue")
    name = reader.string()
    return name, reader.typed(nbt.TAG_COMPOUND)


def write_nbt(path, name, root):
    writer = TypedWriter()
    writer.u1(nbt.TAG_COMPOUND)
    writer.string(name)
    writer.typed(root)
    with open(path, "wb") as handle:
        handle.write(gzip.compress(writer.o.getvalue()))


def field(compound, key, default=None):
    tag = compound.value.get(key)
    return default if tag is None else tag.value


def items(compound, key):
    tag = compound.value.get(key)
    return [] if tag is None or tag.type != nbt.TAG_LIST else tag.value[1]


def ints(compound, key):
    tag = compound.value.get(key)
    if tag is None:
        return None
    if tag.type == nbt.TAG_INT_ARRAY:
        return list(tag.value)
    if tag.type == nbt.TAG_LIST:
        return [entry.value for entry in tag.value[1]]
    return None


# ------------------------------------------------------------ le depot

def volume_sha1(name):
    """Le sha1 des donnees du volume, lu dans son en-tete v2 comme JakVolume.dataSha1."""
    path = os.path.join(DATA_JAK, name + ".jakv")
    if not os.path.exists(path):
        return None
    inflater = zlib.decompressobj()
    head = b""
    with open(path, "rb") as handle:
        while len(head) < 69:
            chunk = handle.read(4096)
            if not chunk:
                break
            head += inflater.decompress(chunk)
    # magie, version, trois tailles, origine et cellule (4 doubles), puis le sha1
    if head[:4] != b"JAKV" or len(head) < 69 or head[4] != 2:
        return None
    return head[49:69].hex()


def haven_origin():
    with open(HAVEN_JAVA, encoding="utf-8") as handle:
        found = re.search(r"ORIGIN\s*=\s*new BlockPos\((-?\d+),\s*(-?\d+),\s*(-?\d+)\)", handle.read())
    return [int(v) for v in found.groups()] if found else None


def rooms():
    with open(ROOMS, encoding="utf-8") as handle:
        return json.load(handle)


def mod_blocks():
    """Les blocs declares par les jars de run/mods, par espace de noms."""
    out = collections.defaultdict(set)
    if not os.path.isdir(MODS):
        return out
    pattern = re.compile(r"^assets/([^/]+)/blockstates/(.+)\.json$")
    for jar in sorted(os.listdir(MODS)):
        if not jar.endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(MODS, jar)) as archive:
                for entry in archive.namelist():
                    match = pattern.match(entry)
                    if match:
                        out[match.group(1)].add(match.group(2))
        except (zipfile.BadZipFile, OSError) as error:
            print("  (jar illisible ignore : %s, %s)" % (jar, error))
    return out


def split_state(state):
    """« ns:bloc[a=1,b=2] » -> (ns:bloc, {a: 1, b: 2})."""
    if "[" not in state:
        return state, {}
    block, rest = state.split("[", 1)
    props = {}
    for pair in rest.rstrip("]").split(","):
        if "=" in pair:
            key, value = pair.split("=", 1)
            props[key.strip()] = value.strip()
    return block, props


# ------------------------------------------------------------ le travail

def resolve_source(arg, source_dir):
    if arg.endswith(".nbt") and os.path.exists(arg):
        return arg
    room = arg
    if arg.isdigit():
        listed = rooms().get("rooms", [])
        number = int(arg)
        if not 1 <= number <= len(listed):
            sys.exit("Salle %s inconnue : la ville en compte %d." % (arg, len(listed)))
        room = listed[number - 1]["id"]
    return os.path.join(source_dir, room + ".nbt")


def main():
    # sous Windows, la sortie d'un tube est en cp1252 : les guillemets « »
    # et les accents des etats arrivaient illisibles
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="Copie le releve d'une salle dans le mod, apres controle.")
    parser.add_argument("salle")
    parser.add_argument("--from", dest="source", default=RUN_JAK)
    parser.add_argument("--dest", default=None)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    path = resolve_source(args.salle, args.source)
    if not os.path.exists(path):
        sys.exit("Releve introuvable : %s\nReleve la salle en jeu (Sonde, ou /arcencium haven salle <n> capture)."
                 % path)
    root_name, root = read_nbt(path)
    if field(root, "format") != FORMAT or field(root, "kind") != "salle":
        sys.exit("%s n'est pas un releve de salle au format %d." % (path, FORMAT))
    room = field(root, "room", "")
    volume = field(root, "volume", "ctyport")
    refusals = []
    alerts = []

    # --- le sha1 et l'origine : sinon les cellules ne designent plus les memes blocs
    repo_sha1 = volume_sha1(volume)
    sha1 = field(root, "sha1", "")
    if repo_sha1 is None:
        refusals.append("volume %s.jakv absent ou pas en version 2 dans le depot" % volume)
    elif sha1 != repo_sha1:
        refusals.append("releve sur le volume %s, le depot a %s : refais le releve sur la ville reposee"
                        % (sha1, repo_sha1))
    origin = ints(root, "origin")
    expected_origin = haven_origin()
    if expected_origin is None:
        alerts.append("Haven.ORIGIN introuvable dans Haven.java : origine non controlee")
    elif origin != expected_origin:
        refusals.append("releve a l'origine %s, Haven.ORIGIN est %s" % (origin, expected_origin))
    listed = rooms()
    if listed.get("sha1") != repo_sha1:
        alerts.append("haven_rooms.json porte le sha1 %s, le volume %s" % (listed.get("sha1"), repo_sha1))
    box_min, box_max = ints(root, "box_min"), ints(root, "box_max")
    for entry in listed.get("rooms", []):
        if entry["id"] == room:
            envelope = ([v - 1 for v in entry["box"]["min"]], [v + 1 for v in entry["box"]["max"]])
            if (box_min, box_max) != envelope:
                alerts.append("la boite du releve %s -> %s n'est plus la coque de %s dans haven_rooms.json (%s -> %s)"
                              % (box_min, box_max, room, envelope[0], envelope[1]))
            break
    else:
        alerts.append("salle %s absente de haven_rooms.json" % room)

    # --- la palette
    palette = [tag.value for tag in items(root, "palette")]
    naked = sorted({state for state in palette if ":" not in split_state(state)[0]})
    if naked:
        refusals.append("%d etat(s) sans espace de noms (ex. « %s »)" % (len(naked), naked[0]))

    if refusals:
        print("REFUS de %s :" % path)
        for reason in refusals:
            print("  - " + reason)
        return 1

    # --- les cellules
    cells = items(root, "cells")
    kept_cells = []
    dropped = collections.Counter()
    kept_blocks = collections.Counter()
    removed = 0
    block_entities = collections.Counter()
    namespaces = collections.defaultdict(set)
    for cell in cells:
        index = field(cell, "state", -1)
        if not 0 <= index < len(palette):
            dropped["index de palette invalide"] += 1
            continue
        block, props = split_state(palette[index])
        if block in FLUIDS and props.get("level", "0") != "0":
            dropped["%s qui coule" % FLUIDS[block]] += 1
            continue
        kept_cells.append(cell)
        if block in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
            removed += 1
        else:
            kept_blocks[block] += 1
        ns, _, name = block.partition(":")
        if ns not in PRESENT:
            namespaces[ns].add(name)
        data = cell.value.get("nbt")
        if data is not None:
            block_entities[field(data, "id", "?")] += 1

    # --- les decors
    entities = items(root, "entities")
    kept_entities = []
    kinds = collections.Counter()
    for entry in entities:
        data = entry.value.get("nbt")
        kind = field(data, "id", "?") if data is not None else "?"
        if kind not in DECOR:
            dropped["entite %s (pas un decor)" % kind] += 1
            continue
        kept_entities.append(entry)
        kinds[kind] += 1

    # --- les mods absents du dev
    if namespaces:
        declared = mod_blocks()
        for ns in sorted(namespaces):
            missing = sorted(name for name in namespaces[ns] if name not in declared.get(ns, set()))
            if missing:
                alerts.append("bloc(s) de %s absent(s) de run/mods, sautes a la pose en dev : %s"
                              % (ns, ", ".join(missing[:8]) + (" ..." if len(missing) > 8 else "")))

    # --- le resume
    dest_dir = args.dest or os.path.join(DATA_JAK, "zones", volume)
    dest = os.path.join(dest_dir, room + ".nbt")
    print("Releve   : %s" % path)
    print("Salle    : %s (salle %s), releve le %s par %s"
          % (room, field(root, "number", "?"), field(root, "captured", "?"), field(root, "author", "?")))
    print("Volume   : %s, sha1 %s (identique au depot)" % (volume, sha1))
    print("Origine  : %s ; coque %s -> %s" % (origin, box_min, box_max))
    print("Cellules : %d gardee(s) sur %d, dont %d retrait(s) (air)" % (len(kept_cells), len(cells), removed))
    for block, count in kept_blocks.most_common(12):
        print("    %-52s %4d" % (block, count))
    if len(kept_blocks) > 12:
        print("    ... et %d autre(s) bloc(s)" % (len(kept_blocks) - 12))
    print("NBT      : %d entite(s) de bloc%s" % (sum(block_entities.values()),
          "" if not block_entities else " (" + ", ".join("%s %d" % kv for kv in block_entities.most_common()) + ")"))
    print("Decors   : %d garde(s) sur %d%s" % (len(kept_entities), len(entities),
          "" if not kinds else " (" + ", ".join("%s %d" % kv for kv in kinds.most_common()) + ")"))
    for reason, count in sorted(dropped.items()):
        print("ECARTE   : %d %s" % (count, reason))
    for alert in alerts:
        print("ALERTE   : %s" % alert)

    if args.dry_run:
        print("Essai a blanc : rien d'ecrit (destination %s)" % dest)
        return 0
    os.makedirs(dest_dir, exist_ok=True)
    if not dropped:
        shutil.copyfile(path, dest)
        how = "copie octet pour octet"
    else:
        root.value["cells"] = Tag(nbt.TAG_LIST, (nbt.TAG_COMPOUND, kept_cells))
        root.value["entities"] = Tag(nbt.TAG_LIST, (nbt.TAG_COMPOUND, kept_entities))
        write_nbt(dest, root_name, root)
        how = "reecrit sans les cellules et entites ecartees"
    _, check = read_nbt(dest)
    if len(items(check, "cells")) != len(kept_cells) or len(items(check, "entities")) != len(kept_entities):
        sys.exit("Relecture de %s incoherente : fichier a jeter." % dest)
    print("Ecrit    : %s (%s)" % (os.path.relpath(dest, ROOT) if dest.startswith(ROOT) else dest, how))
    print("Rejoue a la prochaine pose de la ville (/arcencium haven rebuild), une fois les ressources"
          " rechargees (relance du jeu, ou /reload).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
