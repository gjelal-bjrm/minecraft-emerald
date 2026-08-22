#!/usr/bin/env python3
"""
Lecture / ecriture des fichiers .nbt de structures Minecraft.

Le format .nbt d'un structure block est du NBT gzippe, gros-boutiste :
  root {
    size:     [int, int, int]
    palette:  [ {Name: "minecraft:oak_planks", Properties: {...}}, ... ]
    blocks:   [ {pos: [x,y,z], state: <index palette>, nbt: {...}}, ... ]
    entities: [...]
    DataVersion: int
  }

Sert a DEUX choses :
  1. lire les batiments vanilla (1180 structures dans le jar client) pour
     etudier comment Mojang construit -- voir tools/study_build.py ;
  2. ECRIRE nos propres batiments, ce qu'attend le systeme jigsaw.

Usage :
    python tools/nbt_structure.py list                 # familles dispo
    python tools/nbt_structure.py info <chemin|nom>    # taille, palette
"""

import gzip
import io
import os
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLIENT_JAR = os.path.join(ROOT, "build", "moddev", "artifacts",
                          "neoforge-21.1.193-client-extra-aka-minecraft-resources.jar")

# --------------------------------------------------------------- tags NBT

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG = 0, 1, 2, 3, 4
TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING = 5, 6, 7, 8
TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 9, 10, 11, 12


class TypedList(list):
    """Liste NBT : conserve le type de ses elements pour la reecriture."""
    def __init__(self, items=(), item_type=TAG_END):
        super().__init__(items)
        self.item_type = item_type


class Reader:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def raw(self, n):
        v = self.b[self.i:self.i + n]
        self.i += n
        return v

    def u1(self):
        v = self.b[self.i]
        self.i += 1
        return v

    def i1(self):
        return struct.unpack(">b", self.raw(1))[0]

    def i2(self):
        return struct.unpack(">h", self.raw(2))[0]

    def u2(self):
        return struct.unpack(">H", self.raw(2))[0]

    def i4(self):
        return struct.unpack(">i", self.raw(4))[0]

    def i8(self):
        return struct.unpack(">q", self.raw(8))[0]

    def f4(self):
        return struct.unpack(">f", self.raw(4))[0]

    def f8(self):
        return struct.unpack(">d", self.raw(8))[0]

    def string(self):
        return self.raw(self.u2()).decode("utf-8", errors="replace")

    def payload(self, t):
        if t == TAG_BYTE:
            return self.i1()
        if t == TAG_SHORT:
            return self.i2()
        if t == TAG_INT:
            return self.i4()
        if t == TAG_LONG:
            return self.i8()
        if t == TAG_FLOAT:
            return self.f4()
        if t == TAG_DOUBLE:
            return self.f8()
        if t == TAG_BYTE_ARRAY:
            return bytearray(self.raw(self.i4()))
        if t == TAG_STRING:
            return self.string()
        if t == TAG_LIST:
            it = self.u1()
            n = self.i4()
            return TypedList([self.payload(it) for _ in range(n)], it)
        if t == TAG_COMPOUND:
            out = {}
            while True:
                ct = self.u1()
                if ct == TAG_END:
                    return out
                name = self.string()
                out[name] = self.payload(ct)
        if t == TAG_INT_ARRAY:
            n = self.i4()
            return [self.i4() for _ in range(n)]
        if t == TAG_LONG_ARRAY:
            n = self.i4()
            return [self.i8() for _ in range(n)]
        raise ValueError("tag NBT inconnu : %d" % t)


def parse(data):
    """bytes (gzippes ou non) -> (nom racine, compound racine)."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    r = Reader(data)
    t = r.u1()
    if t != TAG_COMPOUND:
        raise ValueError("racine NBT inattendue : %d" % t)
    return r.string(), r.payload(TAG_COMPOUND)


# ---------------------------------------------------------------- ecriture

class Writer:
    def __init__(self):
        self.o = io.BytesIO()

    def u1(self, v):
        self.o.write(struct.pack(">B", v))

    def string(self, s):
        e = s.encode("utf-8")
        self.o.write(struct.pack(">H", len(e)))
        self.o.write(e)

    def kind(self, v):
        """Type NBT deduit de la valeur Python."""
        if isinstance(v, bool):
            return TAG_BYTE
        if isinstance(v, int):
            return TAG_INT if -2147483648 <= v <= 2147483647 else TAG_LONG
        if isinstance(v, float):
            return TAG_DOUBLE
        if isinstance(v, str):
            return TAG_STRING
        if isinstance(v, (bytearray, bytes)):
            return TAG_BYTE_ARRAY
        if isinstance(v, TypedList):
            return TAG_LIST
        if isinstance(v, list):
            return TAG_LIST
        if isinstance(v, dict):
            return TAG_COMPOUND
        raise ValueError("valeur non serialisable : %r" % (v,))

    def payload(self, t, v):
        if t == TAG_BYTE:
            self.o.write(struct.pack(">b", int(v)))
        elif t == TAG_SHORT:
            self.o.write(struct.pack(">h", v))
        elif t == TAG_INT:
            self.o.write(struct.pack(">i", v))
        elif t == TAG_LONG:
            self.o.write(struct.pack(">q", v))
        elif t == TAG_FLOAT:
            self.o.write(struct.pack(">f", v))
        elif t == TAG_DOUBLE:
            self.o.write(struct.pack(">d", v))
        elif t == TAG_BYTE_ARRAY:
            self.o.write(struct.pack(">i", len(v)))
            self.o.write(bytes(v))
        elif t == TAG_STRING:
            self.string(v)
        elif t == TAG_LIST:
            it = getattr(v, "item_type", TAG_END)
            if it == TAG_END and len(v):
                it = self.kind(v[0])
            self.u1(it)
            self.o.write(struct.pack(">i", len(v)))
            for e in v:
                self.payload(it, e)
        elif t == TAG_COMPOUND:
            for k, e in v.items():
                et = self.kind(e)
                self.u1(et)
                self.string(k)
                self.payload(et, e)
            self.u1(TAG_END)
        elif t == TAG_INT_ARRAY:
            self.o.write(struct.pack(">i", len(v)))
            for e in v:
                self.o.write(struct.pack(">i", e))
        else:
            raise ValueError("type non gere en ecriture : %d" % t)


def serialize(root, name="", compress=True):
    w = Writer()
    w.u1(TAG_COMPOUND)
    w.string(name)
    w.payload(TAG_COMPOUND, root)
    data = w.o.getvalue()
    return gzip.compress(data) if compress else data


# ------------------------------------------------------------- structures

class Structure:
    """Vue pratique sur un .nbt : blocs indexes par (x,y,z)."""

    def __init__(self, root):
        self.root = root
        sz = root.get("size", [0, 0, 0])
        self.size = (int(sz[0]), int(sz[1]), int(sz[2]))
        pal = root.get("palette") or (root.get("palettes") or [[]])[0]
        self.palette = []
        for entry in pal:
            props = entry.get("Properties", {})
            self.palette.append((entry.get("Name", "minecraft:air"), props))
        self.blocks = {}                 # (x,y,z) -> index palette
        self.block_nbt = {}              # (x,y,z) -> compound (coffres, jigsaw)
        self.dropped = 0                 # etats hors palette ignores
        for b in root.get("blocks", []):
            # ~1% des structures CTOV referencent un index depassant la palette
            # d'exactement 1 (incoherence de leur outillage). On ignore ces
            # blocs plutot que de refuser tout le fichier.
            if int(b["state"]) >= len(self.palette):
                self.dropped += 1
                continue
            pos = b["pos"]
            key = (int(pos[0]), int(pos[1]), int(pos[2]))
            self.blocks[key] = int(b["state"])
            if "nbt" in b:
                self.block_nbt[key] = b["nbt"]

    def name_at(self, x, y, z):
        idx = self.blocks.get((x, y, z))
        return None if idx is None else self.palette[idx][0]

    def props_at(self, x, y, z):
        idx = self.blocks.get((x, y, z))
        return {} if idx is None else self.palette[idx][1]

    def counts(self):
        from collections import Counter
        c = Counter()
        for idx in self.blocks.values():
            c[self.palette[idx][0]] += 1
        return c

    def solid_cells(self):
        """Blocs reellement poses (hors air et structure_void)."""
        skip = {"minecraft:air", "minecraft:structure_void", "minecraft:cave_air"}
        return {k: self.palette[v][0] for k, v in self.blocks.items()
                if self.palette[v][0] not in skip}

    def rotated(self, turns):
        """Nouvelle Structure tournee de turns * 90 degres autour de Y."""
        turns %= 4
        if turns == 0:
            return self
        sx, sy, sz = self.size
        new = Structure.__new__(Structure)
        new.root = self.root
        new.palette = self.palette
        new.block_nbt = {}
        new.blocks = {}
        for (x, y, z), idx in self.blocks.items():
            for _ in range(turns):
                x, z = z, (sx - 1 - x)
                sx, sz = sz, sx
            new.blocks[(x, y, z)] = idx
            sx, sz = self.size[0], self.size[2]
            for _ in range(turns):
                sx, sz = sz, sx
        new.size = (sx, sy, sz)
        return new


# Dossier mods du modpack : source des batiments a etudier (CTOV, Towns &
# Towers, Repurposed Structures... des milliers de structures bien plus
# travaillees que le vanilla).
MODS_DIR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge",
                        "minecraft", "Instances", "All the Mods 10 - CUSTOM", "mods")


def find_jar(fragment):
    """Retrouve un jar du modpack par fragment de nom (ex: 'ctov')."""
    if os.path.exists(fragment):
        return fragment
    if not os.path.isdir(MODS_DIR):
        raise FileNotFoundError("dossier mods introuvable : %s" % MODS_DIR)
    frag = fragment.lower()
    hits = [f for f in os.listdir(MODS_DIR) if f.endswith(".jar") and frag in f.lower()]
    if not hits:
        raise FileNotFoundError("aucun jar ne correspond a '%s'" % fragment)
    return os.path.join(MODS_DIR, sorted(hits, key=len)[0])


def load(path_or_name, jar_path=None):
    """Charge une structure depuis :
      - un chemin disque direct ;
      - le jar client vanilla (nom court, ex "village/plains/houses/x") ;
      - un jar de mod si jar_path est donne (chemin ou fragment de nom)."""
    if jar_path is None and os.path.exists(path_or_name):
        with open(path_or_name, "rb") as f:
            return Structure(parse(f.read())[1])
    if jar_path is not None:
        jp = find_jar(jar_path)
        with zipfile.ZipFile(jp) as z:
            entry = path_or_name
            if entry not in z.namelist():
                cands = [n for n in z.namelist()
                         if n.endswith(".nbt") and entry in n]
                if not cands:
                    raise KeyError("structure introuvable dans %s : %s"
                                   % (os.path.basename(jp), entry))
                entry = sorted(cands, key=len)[0]
            return Structure(parse(z.read(entry))[1])
    entry = path_or_name
    if not entry.startswith("data/"):
        entry = "data/minecraft/structure/" + entry.lstrip("/")
    if not entry.endswith(".nbt"):
        entry += ".nbt"
    with zipfile.ZipFile(CLIENT_JAR) as z:
        return Structure(parse(z.read(entry))[1])


def list_structures(prefix="", jar_path=None):
    if jar_path is not None:
        with zipfile.ZipFile(find_jar(jar_path)) as z:
            names = [n for n in z.namelist() if n.endswith(".nbt")]
        return sorted(n for n in names if prefix in n)
    with zipfile.ZipFile(CLIENT_JAR) as z:
        names = [n[len("data/minecraft/structure/"):-4] for n in z.namelist()
                 if n.startswith("data/minecraft/structure/") and n.endswith(".nbt")]
    return sorted(n for n in names if prefix in n)


def scan_sizes(jar_path, prefix="", top=25):
    """Classe les structures d'un jar par volume : pour reperer vite les
    gros batiments interessants."""
    rows = []
    with zipfile.ZipFile(find_jar(jar_path)) as z:
        for n in z.namelist():
            if not n.endswith(".nbt") or prefix not in n:
                continue
            try:
                st = Structure(parse(z.read(n))[1])
            except Exception:
                continue
            sx, sy, sz = st.size
            rows.append((sx * sy * sz, st.size, len(st.solid_cells()), n))
    rows.sort(reverse=True)
    return rows[:top]


if __name__ == "__main__":
    args = sys.argv[1:]
    jar = None
    if "--jar" in args:
        i = args.index("--jar")
        jar = args[i + 1]
        args = args[:i] + args[i + 2:]
    if args and args[0] == "sizes":
        for vol, size, solid, name in scan_sizes(jar, args[1] if len(args) > 1 else ""):
            print("%8d  %-14s %5d blocs  %s" % (vol, "x".join(map(str, size)), solid, name))
        sys.exit(0)
    if not args or args[0] == "list":
        pref = args[1] if len(args) > 1 else ""
        names = list_structures(pref, jar)
        print("%d structures" % len(names))
        for n in names[:60]:
            print("  ", n)
        if len(names) > 60:
            print("   ... et %d autres" % (len(names) - 60))
    elif args[0] == "info":
        s = load(args[1], jar)
        print("taille :", s.size)
        print("blocs poses :", len(s.solid_cells()), "/", len(s.blocks))
        print("palette : %d entrees" % len(s.palette))
        for name, n in s.counts().most_common(25):
            print("   %-44s %4d" % (name, n))
