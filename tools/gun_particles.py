#!/usr/bin/env python3
"""
Le tir du Morph Gun : les textures des particules et des entites, et la planche de controle.

DIX-SEPT PARTICULES NEUVES, une par usage, qu'aucun autre systeme du mod n'emploie
(ni le sceptre, ni l'arc, ni les meteos, ni les plantes), et deux textures
d'entite :

  gun_scatter_pellet  plomb du Scatter Gun (point doux a coeur chaud)
  gun_scatter_flash   eclair de bouche du Scatter Gun (etoile a huit branches)
  gun_blaster_bolt    trait du Blaster (point lumineux)
  gun_blaster_spark   eclat d'impact du Blaster (etincelle a quatre branches)
  gun_vulcan_tracer   trainee de la Vulcan Fury (point net)
  gun_vulcan_spark    eclat de la Vulcan Fury (trait oblique)
  gun_peace_mote      mote de la boule du Peace Maker (croissant)
  gun_peace_blast     anneau de l'explosion du Peace Maker
  gun_eco_glint       etincelle d'une munition d'eco (losange etire)
  gun_wave_charge     braise de la charge du Wave Concussor (point doux)
  gun_wave_dust       braise du front de son onde (trait vertical)
  gun_plasmite_trail  trainee et eclats de la grenade du Plasmite RPG (point large)
  gun_plasmite_blast  boule de feu de son explosion (anneau plein)
  gun_reflexor_bolt   trait du Beam Reflexor (point fin)
  gun_reflexor_spark  eclat de ses rebonds (etincelle a six branches)
  gun_gyro_tracer     trait des tirs de la soucoupe du Gyro Burster (point net)
  gun_gyro_spark      leurs eclats, et les feux de la soucoupe (trait oblique)
  entity/gun/eco_halo.png    halo d'une munition d'eco
  entity/gun/peace_orb.png   boule du Peace Maker (spirale)

Les textures sont BLANCHES : la couleur vient du code (jak/gun/GunParticles et
GunRenderers). La planche les reprend avec LES MEMES COULEURS, TAILLES ET DUREES
que le Java, simule tique par tique, sur le ciel de Haven (midi fige) et sur un
mur, puis le HUD (jak/gun/GunHud.draw) dessine pixel pour pixel avec la police
bitmap de Minecraft lue dans le jar client, au-dessus de la barre d'objets.

Usage :
    python tools/gun_particles.py            textures, definitions et planche
    python tools/gun_particles.py --planche  la planche seule
    python tools/gun_particles.py --planche-b  la planche du jalon B seule (ameliorations rouges et jaunes)
"""

import json
import math
import os
import random
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons")
TEX_DIR = os.path.join(ASSETS, "textures", "particle")
ENT_DIR = os.path.join(ASSETS, "textures", "entity", "gun")
DEF_DIR = os.path.join(ASSETS, "particles")
BUILD = os.path.join(ROOT, "build", "jak", "gun")
CLIENT_JAR = os.path.join(ROOT, "build", "moddev", "artifacts",
                          "neoforge-21.1.193-client-extra-aka-minecraft-resources.jar")
LANG = os.path.join(ASSETS, "lang", "fr_fr.json")

# ============================================================== textures


def canvas(size):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def paint(size, alpha_of):
    img = canvas(size)
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            a = max(0.0, min(1.0, alpha_of((x - c) / c, (y - c) / c)))
            px[x, y] = (255, 255, 255, int(round(a * 255)))
    return img


def glow(size, core=0.3, power=1.8):
    def f(u, v):
        d = math.hypot(u, v)
        return 1.0 if d < core else max(0.0, 1.0 - (d - core) / (1.0 - core)) ** power
    return paint(size, f)


def star(size, rays, width=0.12, halo=0.35):
    def f(u, v):
        d = math.hypot(u, v)
        if d > 1.0:
            return 0.0
        ang = math.atan2(v, u)
        best = 0.0
        for k in range(rays):
            a = k * math.pi * 2.0 / rays
            across = abs(math.sin(ang - a)) * d
            along = math.cos(ang - a) * d
            if along > 0:
                best = max(best, max(0.0, 1.0 - across / width) * (1.0 - d) ** 0.7)
        core = max(0.0, 1.0 - d / halo) ** 1.5
        return max(best, core)
    return paint(size, f)


def slash(size, angle_deg, length=0.85, width=0.18):
    ang = math.radians(angle_deg)
    ux, uy = math.cos(ang), math.sin(ang)

    def f(u, v):
        along = u * ux + v * uy
        across = -u * uy + v * ux
        return max(0.0, 1.0 - (abs(along) / length) ** 3) * max(0.0, 1.0 - (abs(across) / width) ** 2)
    return paint(size, f)


def crescent(size, radius=0.55, thickness=0.28, start=-40.0, sweep=250.0):
    def f(u, v):
        d = math.hypot(u, v)
        ang = (math.degrees(math.atan2(v, u)) - start) % 360.0
        if ang > sweep:
            return 0.0
        taper = math.sin(math.pi * ang / sweep)
        return max(0.0, 1.0 - abs(d - radius) / (thickness * (0.35 + 0.65 * taper))) ** 1.2 * (0.4 + 0.6 * taper)
    return paint(size, f)


def ring(size, radius=0.74, thickness=0.2, fill=0.18):
    def f(u, v):
        d = math.hypot(u, v)
        edge = max(0.0, 1.0 - abs(d - radius) / thickness) ** 1.4
        inner = fill * max(0.0, 1.0 - d / radius) if d < radius else 0.0
        return max(edge, inner)
    return paint(size, f)


def diamond(size, tall=0.95, wide=0.35):
    def f(u, v):
        n = abs(u) / wide + abs(v) / tall
        cross = max(0.0, 1.0 - abs(u) / 0.08) * max(0.0, 1.0 - abs(v) / 1.0) * 0.6
        return max(max(0.0, 1.0 - n) ** 0.8, cross)
    return paint(size, f)


def halo(size):
    def f(u, v):
        d = math.hypot(u, v)
        base = max(0.0, 1.0 - d) ** 2.2
        rim = 0.35 * max(0.0, 1.0 - abs(d - 0.62) / 0.12) ** 2
        return base + rim
    return paint(size, f)


def orb(size):
    def f(u, v):
        d = math.hypot(u, v)
        if d > 1.0:
            return 0.0
        core = max(0.0, 1.0 - d / 0.45) ** 1.3
        ang = math.atan2(v, u)
        arm = 0.0
        for k in range(3):
            phase = ang - (k * 2.0 * math.pi / 3.0) - d * 4.2
            arm = max(arm, max(0.0, math.cos(phase)) ** 8)
        return max(core, arm * (1.0 - d) ** 0.8 * 0.9, 0.25 * (1.0 - d))
    return paint(size, f)


PARTICLES = {
    "gun_scatter_pellet": lambda: glow(16, 0.28, 1.6),
    "gun_scatter_flash": lambda: star(32, 8, 0.10, 0.4),
    "gun_blaster_bolt": lambda: glow(16, 0.42, 2.2),
    "gun_blaster_spark": lambda: star(16, 4, 0.16, 0.25),
    "gun_vulcan_tracer": lambda: glow(16, 0.5, 3.0),
    "gun_vulcan_spark": lambda: slash(16, 45.0),
    "gun_peace_mote": lambda: crescent(16),
    "gun_peace_blast": lambda: ring(32),
    "gun_eco_glint": lambda: diamond(16),
    # les ameliorations rouges et jaunes (jalon B)
    "gun_wave_charge": lambda: glow(16, 0.2, 1.4),
    "gun_wave_dust": lambda: slash(16, 90.0, 0.8, 0.24),
    "gun_plasmite_trail": lambda: glow(16, 0.35, 1.2),
    "gun_plasmite_blast": lambda: ring(32, 0.7, 0.28, 0.4),
    "gun_reflexor_bolt": lambda: glow(16, 0.3, 2.6),
    "gun_reflexor_spark": lambda: star(16, 6, 0.12, 0.3),
    "gun_gyro_tracer": lambda: glow(16, 0.45, 2.8),
    "gun_gyro_spark": lambda: slash(16, -45.0),
}
ENTITIES = {"eco_halo": lambda: halo(64), "peace_orb": lambda: orb(64)}


def write_textures():
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(ENT_DIR, exist_ok=True)
    os.makedirs(DEF_DIR, exist_ok=True)
    out = {}
    for name, make in PARTICLES.items():
        img = make()
        img.save(os.path.join(TEX_DIR, name + ".png"))
        with open(os.path.join(DEF_DIR, name + ".json"), "w", encoding="utf-8") as handle:
            json.dump({"textures": ["emeraldweapons:" + name]}, handle, indent=2)
            handle.write("\n")
        out[name] = img
    for name, make in ENTITIES.items():
        img = make()
        img.save(os.path.join(ENT_DIR, name + ".png"))
        out[name] = img
    print("textures ecrites :", ", ".join(sorted(out)))
    return out


def read_textures():
    out = {}
    for name in PARTICLES:
        out[name] = Image.open(os.path.join(TEX_DIR, name + ".png")).convert("RGBA")
    for name in ENTITIES:
        out[name] = Image.open(os.path.join(ENT_DIR, name + ".png")).convert("RGBA")
    return out


# ============================================================== comportements (miroir de GunParticles)

FAMILY_TEXT = {0: (0xFF, 0x6A, 0x5A), 1: (0xFF, 0xD8, 0x4A), 2: (0x5C, 0xC8, 0xFF), 3: (0xB4, 0x8C, 0xFF)}
FAMILY_REFLECT = {0: 0xDC0C0C, 1: 0xFFD220, 2: 0x25AAFF, 3: 0x6C1FD8}


class P:
    """Une particule : Particle.tick (age++ puis mouvement) suivi de la retouche du type."""

    def __init__(self, kind, pos, vel=(0.0, 0.0, 0.0), rng=None):
        rng = rng or random.Random(1)
        self.kind, self.pos, self.vel, self.age = kind, list(pos), list(vel), 0
        self.friction, self.gravity = 1.0, 0.0
        moving = sum(v * v for v in vel) > 1e-8
        self.alpha, self.roll = 1.0, rng.random() * math.tau
        if kind == "gun_scatter_pellet":
            self.life, self.size0, self.rgb = (3 if moving else 2), (0.18 if moving else 0.09), (1.0, 0.55, 0.18)
        elif kind == "gun_scatter_flash":
            self.life, self.size0, self.rgb = 3, 0.4, (1.0, 0.6, 0.25)
        elif kind == "gun_blaster_bolt":
            self.life, self.size0, self.rgb = 2, 0.22, (1.0, 0.93, 0.45)
        elif kind == "gun_blaster_spark":
            self.life, self.size0, self.rgb = 5 + rng.randrange(4), 0.09 + rng.random() * 0.04, (1.0, 0.85, 0.3)
            self.friction, self.gravity = 0.82, 0.3
        elif kind == "gun_vulcan_tracer":
            self.life, self.size0, self.rgb = 2, (0.15 if moving else 0.11), (0.2, 0.75, 1.0)
        elif kind == "gun_vulcan_spark":
            self.life, self.size0, self.rgb = 5 + rng.randrange(4), 0.09 + rng.random() * 0.04, (0.55, 0.9, 1.0)
            self.friction, self.gravity = 0.82, 0.3
        elif kind == "gun_peace_mote":
            self.life, self.size0, self.rgb = 10 + rng.randrange(5), 0.18, (0.62, 0.32, 1.0)
            self.friction = 0.88
        elif kind == "gun_peace_blast":
            self.life, self.size0, self.rgb = 8, 0.5, (0.75, 0.5, 1.0)
        elif kind in ("gun_wave_charge", "gun_wave_dust", "gun_plasmite_trail"):
            # GunParticles.Ember : duree, taille, chute
            life, size, falls = {"gun_wave_charge": (4, 0.2, False), "gun_wave_dust": (9, 0.16, True),
                                 "gun_plasmite_trail": (7, 0.26, False)}[kind]
            self.life, self.size0, self.rgb = life + rng.randrange(3), size * (0.8 + rng.random() * 0.4), (1.0, 0.45, 0.12)
            self.friction, self.gravity = 0.9, (0.35 if falls else 0.0)
        elif kind == "gun_plasmite_blast":
            self.life, self.size0, self.rgb = 10, 1.0, (1.0, 0.4, 0.1)
        elif kind == "gun_reflexor_bolt":
            self.life, self.size0, self.rgb = 4, 0.22, (1.0, 0.98, 0.72)
        elif kind == "gun_reflexor_spark":
            self.life, self.size0, self.rgb = 5 + rng.randrange(4), 0.09 + rng.random() * 0.04, (1.0, 0.97, 0.7)
            self.friction, self.gravity = 0.82, 0.3
        elif kind == "gun_gyro_tracer":
            self.life, self.size0, self.rgb = 3, (0.24 if moving else 0.16), (1.0, 0.72, 0.18)
        elif kind == "gun_gyro_spark":
            self.life, self.size0, self.rgb = 5 + rng.randrange(4), 0.09 + rng.random() * 0.04, (1.0, 0.75, 0.2)
            self.friction, self.gravity = 0.82, 0.3
        elif kind.startswith("gun_eco_glint"):
            fam = int(kind[-1])
            self.kind = "gun_eco_glint"
            self.life, self.size0 = 14 + rng.randrange(8), 0.07
            self.rgb = tuple(c / 255.0 for c in FAMILY_TEXT[fam])
            self.friction, self.twinkle = 0.92, rng.random() * 6.0
        self.size = self.size0
        self.alive = True

    def tick(self):
        if self.age >= self.life:
            self.alive = False
            return
        self.age += 1
        self.vel[1] -= 0.04 * self.gravity
        for k in range(3):
            self.pos[k] += self.vel[k]
            self.vel[k] *= self.friction
        t = min(1.0, self.age / self.life)
        k = self.kind
        if k == "gun_scatter_pellet":
            self.rgb = (1.0, 0.55 - 0.3 * t, 0.18 - 0.1 * t)
            self.alpha, self.size = 1.0 - 0.7 * t, self.size0 * (1.0 - 0.4 * t)
        elif k == "gun_scatter_flash":
            self.size, self.alpha = self.size0 * (1.0 + 0.8 * t), 1.0 - t
        elif k == "gun_blaster_bolt":
            self.size, self.alpha, self.rgb = self.size0 * (1.0 - 0.6 * t), 1.0 - 0.5 * t, (1.0, 0.93 - 0.25 * t, 0.45 - 0.3 * t)
        elif k in ("gun_blaster_spark", "gun_vulcan_spark"):
            self.alpha = 1.0 - t
        elif k == "gun_vulcan_tracer":
            self.alpha = 1.0 - 0.6 * t
        elif k == "gun_peace_mote":
            self.roll += 0.25
            self.size = self.size0 * (1.0 - t)
            self.rgb = (0.62 - 0.35 * t, 0.32 - 0.25 * t, 1.0 - 0.45 * t)
        elif k == "gun_peace_blast":
            self.size, self.alpha = 0.5 + 4.5 * t, 1.0 - t * t
        elif k == "gun_eco_glint":
            self.alpha = (1.0 - t) * (0.6 + 0.4 * math.sin(self.age * 0.9 + self.twinkle))
            self.size = self.size0 * (1.0 - 0.5 * t)
        elif k in ("gun_wave_charge", "gun_wave_dust", "gun_plasmite_trail"):
            self.rgb = (1.0 - 0.3 * t, 0.45 - 0.35 * t, 0.12 - 0.1 * t)
            self.alpha, self.size = 1.0 - t, self.size0 * (1.0 - 0.5 * t)
        elif k == "gun_plasmite_blast":
            self.size = 1.0 + 8.0 * math.sqrt(t)
            self.rgb, self.alpha = (1.0, 0.4 + 0.3 * (1.0 - t), 0.1 + 0.3 * (1.0 - t)), 1.0 - t * t
        elif k == "gun_reflexor_bolt":
            self.size, self.alpha, self.rgb = self.size0 * (1.0 - 0.7 * t), 1.0 - 0.6 * t, (1.0, 0.98 - 0.2 * t, 0.72 - 0.4 * t)
        elif k in ("gun_reflexor_spark", "gun_gyro_spark"):
            self.alpha = 1.0 - t
        elif k == "gun_gyro_tracer":
            self.alpha = 1.0 - 0.6 * t


def blit(target, tex, cx, cy, width_px, rgb, alpha, roll=0.0):
    """Un carre face camera, texture teintee, melange alpha (PARTICLE_SHEET_TRANSLUCENT), filtrage au plus proche."""
    w = max(1, int(round(width_px)))
    sprite = tex.resize((w, w), Image.NEAREST)
    if roll:
        sprite = sprite.rotate(math.degrees(roll), resample=Image.NEAREST, expand=False)
    r, g, b = rgb
    px = sprite.load()
    for y in range(w):
        for x in range(w):
            pr, pg, pb, pa = px[x, y]
            px[x, y] = (int(pr * r), int(pg * g), int(pb * b), int(pa * max(0.0, min(1.0, alpha))))
    target.alpha_composite(sprite, (int(round(cx - w / 2)), int(round(cy - w / 2))))


def render(parts, textures, size, ppb, origin, view="side", bg=(150, 182, 222)):
    """Vue de cote (x a droite, y en haut) ou de dessus (x a droite, z vers le bas) ; ppb pixels par bloc."""
    img = Image.new("RGBA", size, bg + (255,))
    for p in parts:
        if not p.alive and p.age >= p.life:
            continue
        x = origin[0] + p.pos[0] * ppb
        y = origin[1] - p.pos[1] * ppb if view == "side" else origin[1] + p.pos[2] * ppb
        blit(img, textures[p.kind], x, y, 2.0 * p.size * ppb, p.rgb, p.alpha, p.roll if p.kind in ("gun_scatter_flash", "gun_peace_mote") else 0.0)
    return img


def step(parts):
    for p in parts:
        p.tick()
    parts[:] = [p for p in parts if p.alive]


# ============================================================== scenes (miroir de GunClient, des entites et du serveur)

def scene_scatter(textures):
    rng = random.Random(7)
    parts = []
    origin = (0.0, 1.6, 0.0)
    dirs = []
    for _ in range(19):
        yaw = math.radians((rng.random() * 2 - 1) * 45.0)
        dirs.append((math.cos(yaw), 0.0, math.sin(yaw)))
    wall = 8.0
    for batch, n in enumerate((7, 7, 5)):
        parts.append(P("gun_scatter_flash", origin, rng=rng))
        for d in dirs[sum((7, 7, 5)[:batch]):sum((7, 7, 5)[:batch]) + n]:
            length = 15.0
            hit = False
            if d[0] > 0 and abs(d[2] / d[0] * wall) <= 4.0:
                length, hit = wall / d[0], True
            end = tuple(origin[k] + d[k] * length for k in range(3))
            parts.append(P("gun_scatter_pellet", origin, tuple((end[k] - origin[k]) / 3.0 for k in range(3)), rng))
            dots = min(10, int(length / 1.5))
            for j in range(1, dots + 1):
                t = j / (dots + 1)
                parts.append(P("gun_scatter_pellet", tuple(origin[k] + (end[k] - origin[k]) * t for k in range(3)), rng=rng))
            if hit:
                for _ in range(3):
                    parts.append(P("gun_scatter_pellet", end, ((rng.random() - 0.5) * 0.12, rng.random() * 0.1,
                                                              (rng.random() - 0.5) * 0.12), rng))
        if batch < 2:
            step(parts)
    return parts, "vue de dessus, 3e lot de sondes (tique 2) ; mur a 8 blocs, 4 de large"


def scene_blaster(textures):
    rng = random.Random(3)
    parts = []
    x = 0.0
    for tick in range(3):
        if tick:
            step(parts)
        for i in range(40):
            parts.append(P("gun_blaster_bolt", (x + 10.0 * i / 40.0, 1.6, 0.0), rng=rng))
        x += 10.0
    impact = []
    for _ in range(10):
        pos = (22.0 + rng.gauss(0, 0.08), 1.6 + rng.gauss(0, 0.08), rng.gauss(0, 0.08))
        vel = tuple(rng.gauss(0, 0.22) for _ in range(3))
        impact.append(P("gun_blaster_spark", pos, vel, rng))
    step(impact)
    step(impact)
    return parts, impact


def scene_vulcan(textures):
    rng = random.Random(5)
    parts = []
    end = (20.0, 1.0, 0.0)
    origin = (0.0, 1.6, 0.0)
    d = tuple(end[k] - origin[k] for k in range(3))
    parts.append(P("gun_vulcan_tracer", origin, tuple(v / 2.0 for v in d), rng))
    length = math.sqrt(sum(v * v for v in d))
    dots = min(160, int(length / 0.5))
    for j in range(1, dots + 1):
        t = j / (dots + 1)
        parts.append(P("gun_vulcan_tracer", tuple(origin[k] + d[k] * t for k in range(3)), rng=rng))
    for _ in range(4):
        parts.append(P("gun_vulcan_spark", end, ((rng.random() - 0.5) * 0.3, rng.random() * 0.2, (rng.random() - 0.5) * 0.3), rng))
    return parts


def scene_peace(textures):
    rng = random.Random(9)
    trail = []
    # la boule vole a 5,4 blocs par tique, spirale de 0 a 0,5 bloc en 6 tiques, 33 degres par tique
    positions = []
    for tick in range(6):
        t = tick + 1
        radius = 0.5 * t / 6.0 if t < 6 else 0.5
        a = math.radians(33.0 * t)
        cx = 5.4 * tick
        off = (0.0, math.cos(a) * radius, math.sin(a) * radius)
        for i in range(5):
            trail.append(P("gun_peace_mote", (cx - 5.4 + 5.4 * i / 5.0, 1.6 + off[1], off[2]), rng=rng))
        positions.append((cx, 1.6 + off[1]))
        step(trail)
    blast = []
    at = (6.0, 1.6, 0.0)
    blast.append(P("gun_peace_blast", at, rng=rng))
    for _ in range(36):
        pos = (at[0] + rng.gauss(0, 0.6), at[1] + rng.gauss(0, 0.6), at[2] + rng.gauss(0, 0.6))
        blast.append(P("gun_peace_mote", pos, tuple(rng.gauss(0, 0.3) for _ in range(3)), rng))
    frames = []
    for n in range(8):
        step(blast)
        if n in (0, 2, 5):
            frames.append([P.__new__(P)] and [q for q in blast])
            frames[-1] = [clone(q) for q in blast]
    return trail, positions[-1], frames


def clone(p):
    q = P.__new__(P)
    q.__dict__.update({k: (list(v) if isinstance(v, list) else v) for k, v in p.__dict__.items()})
    return q


def arc_points(length, seed):
    rng = random.Random(seed * 31)
    n = max(3, int(math.ceil(length / 0.9)))
    pts = []
    for i in range(n + 1):
        x = length * i / n
        y = (rng.random() - 0.5) * 0.6 if 0 < i < n else 0.0
        pts.append((x, y))
    return pts


def draw_arc(img, pts, origin, ppb, life):
    """Rubans additifs (RenderType.lightning) : violet large puis coeur lavande."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dr = ImageDraw.Draw(layer)
    for width, rgb, a in ((0.16, (0.55, 0.25, 1.0), 0.5 * life), (0.05, (0.95, 0.88, 1.0), 0.9 * life)):
        for i in range(len(pts) - 1):
            (x0, y0), (x1, y1) = pts[i], pts[i + 1]
            dr.line([(origin[0] + x0 * ppb, origin[1] - y0 * ppb), (origin[0] + x1 * ppb, origin[1] - y1 * ppb)],
                    fill=(int(255 * rgb[0] * a), int(255 * rgb[1] * a), int(255 * rgb[2] * a), 255),
                    width=max(1, int(round(2 * width * ppb))))
    base = img.convert("RGB")
    add = layer.convert("RGB")
    out = Image.eval(base, lambda v: v)
    bp, ap = out.load(), add.load()
    for y in range(out.height):
        for x in range(out.width):
            r0, g0, b0 = bp[x, y]
            r1, g1, b1 = ap[x, y]
            if r1 or g1 or b1:
                bp[x, y] = (min(255, r0 + r1), min(255, g0 + g1), min(255, b0 + b1))
    img.paste(out.convert("RGBA"))


# ============================================================== police de Minecraft et HUD (miroir de GunHud.draw)

class McFont:
    def __init__(self):
        self.glyphs = {}
        jar = zipfile.ZipFile(CLIENT_JAR)
        default = json.loads(jar.read("assets/minecraft/font/include/default.json").decode("utf-8"))
        for p in default["providers"]:
            if p.get("type") != "bitmap":
                continue
            name = p["file"].split(":")[-1]
            import io
            img = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + name))).convert("RGBA")
            rows = p["chars"]
            cw = img.width // len(rows[0])
            ch = img.height // len(rows)
            height = p.get("height", 8)
            ascent = p["ascent"]
            scale = height / ch
            for r, row in enumerate(rows):
                for c, char in enumerate(row):
                    if char in self.glyphs or char == "\u0000":
                        continue
                    cell = img.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch))
                    px = cell.load()
                    width = 0
                    for x in range(cw - 1, -1, -1):
                        if any(px[x, y][3] > 0 for y in range(ch)):
                            width = x + 1
                            break
                    if scale != 1.0:
                        cell = cell.resize((max(1, int(cw * scale)), max(1, int(ch * scale))), Image.NEAREST)
                    self.glyphs[char] = (cell, int(0.5 + width * scale) + 1, ascent)

    def width(self, text):
        return sum(4 if ch == " " else self.glyphs.get(ch, (None, 6, 7))[1] for ch in text)

    def draw(self, img, text, x, y, argb, shadow=True):
        def one(dx, dy, rgb):
            cx = x + dx
            for ch in text:
                if ch == " ":
                    cx += 4
                    continue
                glyph = self.glyphs.get(ch)
                if glyph is None:
                    cx += 6
                    continue
                cell, advance, ascent = glyph
                tinted = cell.copy()
                px = tinted.load()
                for yy in range(tinted.height):
                    for xx in range(tinted.width):
                        a = px[xx, yy][3]
                        px[xx, yy] = (rgb[0], rgb[1], rgb[2], a)
                img.alpha_composite(tinted, (cx, y + dy + 7 - ascent))
                cx += advance
        rgb = ((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF)
        if shadow:
            one(1, 1, tuple(int(v * 0.25) for v in rgb))
        one(0, 0, rgb)


def fill(img, x0, y0, x1, y1, argb):
    a = (argb >> 24) & 0xFF
    layer = Image.new("RGBA", (max(0, x1 - x0), max(0, y1 - y0)), ((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, a))
    if layer.width and layer.height:
        img.alpha_composite(layer, (x0, y0))


CELL_W, CELL_H, GAP, NAME_H = 44, 15, 2, 11
WIDTH = 4 * CELL_W + 3 * GAP
CAPACITY = (100, 200, 200, 15)
TEXT = (0xFF6A5A, 0xFFD84A, 0x5CC8FF, 0xB48CFF)


def hud(img, font, left, bottom, form_family, ecos, name, time, empty_fmt):
    top = bottom - CELL_H
    for fam in range(4):
        x = left + fam * (CELL_W + GAP)
        eco = ecos[fam]
        text = 0xFF000000 | TEXT[fam]
        fill(img, x, top, x + CELL_W, bottom, 0xB0101014)
        if fam == form_family:
            fill(img, x - 1, top - 1, x + CELL_W + 1, top, text)
            fill(img, x - 1, bottom, x + CELL_W + 1, bottom + 1, text)
            fill(img, x - 1, top, x, bottom, text)
            fill(img, x + CELL_W, top, x + CELL_W + 1, bottom, text)
        x0, x1 = x + 3, x + CELL_W - 3
        fill(img, x0, bottom - 4, x1, bottom - 2, 0xFF2A2A30)
        filled = int(round((x1 - x0) * eco / CAPACITY[fam] + 1e-9))
        if eco > 0 and filled == 0:
            filled = 1
        fill(img, x0, bottom - 4, x0 + filled, bottom - 2, 0xFF000000 | FAMILY_REFLECT[fam])
        number = str(eco)
        color = text if eco > 0 else (0xFFFF5050 if (time // 6) % 2 == 0 else 0xFF803030)
        font.draw(img, number, x + (CELL_W - font.width(number)) // 2, top + 2, color)
    label = name if ecos[form_family] > 0 else empty_fmt.replace("%s", name)
    font.draw(img, label, left + WIDTH // 2 - font.width(label) // 2, top - NAME_H + 1, 0xFF000000 | TEXT[form_family])


def vanilla_bar(img, jar, font, w, h):
    import io

    def sprite(path):
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/gui/sprites/hud/" + path))).convert("RGBA")
    img.alpha_composite(sprite("hotbar.png"), (w // 2 - 91, h - 22))
    img.alpha_composite(sprite("experience_bar_background.png"), (w // 2 - 91, h - 29))
    heart_c, heart = sprite("heart/container.png"), sprite("heart/full.png")
    food = sprite("food_full.png")
    for i in range(10):
        img.alpha_composite(heart_c, (w // 2 - 91 + i * 8, h - 39))
        img.alpha_composite(heart, (w // 2 - 91 + i * 8, h - 39))
        img.alpha_composite(food, (w // 2 + 91 - 9 - i * 8, h - 39))


LINE_H = 10


def vanilla_texts(img, font, w, h, height, item, overlay, overlay_rgb):
    """Les deux calques vanilla qui lisent Gui.leftHeight/rightHeight APRES le HUD (NeoForge 21.1.193, Gui.java) :
    nom de l'objet tenu a h - max(L, 59) (:621, :789) et barre d'action centree sur h - max(L + 9, 68), texte a -4
    (:326-338). Sans fond : drawStringWithBackdrop ne peint rien quand le fond est reserve au chat (reglage par defaut)."""
    k = h - max(height, 59)
    font.draw(img, item, (w - font.width(item)) // 2, k, 0xFFFFFFFF)
    y = h - max(height + 9, 68)
    font.draw(img, overlay, w // 2 - font.width(overlay) // 2, y - 4, 0xFF000000 | overlay_rgb)


def hud_board():
    jar = zipfile.ZipFile(CLIENT_JAR)
    font = McFont()
    with open(LANG, encoding="utf-8") as handle:
        lang = json.load(handle)
    empty_fmt = lang.get("hud.emeraldweapons.morph_gun.empty", "%s — à sec")
    names = {k: lang["item.emeraldweapons.morph_gun.form." + k] for k in ("scatter_gun", "blaster", "vulcan_fury", "peace_maker")}
    item = lang.get("item.emeraldweapons.morph_gun", "Morph Gun")
    vote = lang["game.emeraldweapons.haven.vote.count"].replace("%s", "5").replace("%1$s", "5")
    invasion = lang["game.emeraldweapons.haven.invasion.bar.on"]
    gold, red = 0xFFAA00, 0xFF5555
    # (titre, famille, reserves, nom, temps, armure, barre d'action, couleur)
    states = [
        ("Scatter Gun, sans armure : nom de l'objet et compte a rebours du vote", 0, (100, 200, 200, 15), names["scatter_gun"], 0, False, vote, gold),
        ("Vulcan Fury, avec armure : nom de l'objet et bascule en invasion", 2, (37, 120, 64, 3), names["vulcan_fury"], 0, True, invasion, red),
        ("Blaster a sec (clignotement allume), sans armure", 1, (41, 0, 88, 9), names["blaster"], 0, False, vote, gold),
        ("Blaster a sec (clignotement eteint), avec armure", 1, (41, 0, 88, 9), names["blaster"], 6, True, invasion, red),
    ]
    w, h = 300, 140
    panels = []
    for title, fam, ecos, name, time, armor, overlay, overlay_rgb in states:
        img = Image.new("RGBA", (w, h), (120, 150, 190, 255))
        # un bout de rue ensoleillee derriere, pour juger du contraste
        fill(img, 0, h - 60, w, h, 0xFF8A8478)
        vanilla_bar(img, jar, font, w, h)
        below = 49
        if armor:
            import io
            plate = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/gui/sprites/hud/armor_full.png"))).convert("RGBA")
            for i in range(10):
                img.alpha_composite(plate, (w // 2 - 91 + i * 8, h - 49))
            below = 59
        bottom = h - below - 2
        hud(img, font, w // 2 - WIDTH // 2, bottom, fam, ecos, name, time, empty_fmt)
        # GunHud.heightAbove : le HUD releve leftHeight et rightHeight pour les calques suivants
        vanilla_texts(img, font, w, h, below + 2 + CELL_H + NAME_H + LINE_H, item, overlay, overlay_rgb)
        panels.append((title, img.resize((w * 3, h * 3), Image.NEAREST)))
    return panels


# ============================================================== planche

def label_font(size):
    for name in ("C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def ammo_panels(textures):
    """Les munitions (modeles cuits, rendus par la planche de la phase 0) avec leur halo et leurs etincelles."""
    scratch = os.environ.get("ARMES3")
    if not scratch:
        return []
    sys.path.insert(0, scratch)
    import planche as pl  # noqa: E402  (scratchpad armes3 : rasteriseur des .bin)
    out = []
    for fam, model in enumerate(("gun_ammo_red", "gun_ammo_yellow", "gun_ammo_blue", "gun_ammo_dark")):
        back = pl.jg.read_bin(os.path.join(ASSETS, "jak_gun", model + ".bin"))
        tris = pl.gun_tris(back, pl.jg.rest_table(back))
        view = pl.VIEWS["3q"]
        ppm = 260
        frame = (-0.55, 0.55, -0.25, 0.75)
        base = Image.new("RGBA", (int(1.1 * ppm) + 24, int(1.0 * ppm) + 24), (150, 182, 222, 255))
        mid = back["max"][1] * 0.5
        cx, cy = 12 + 0.55 * ppm, 12 + (0.75 - mid) * ppm
        blit(base, textures["eco_halo"], cx, cy, 0.85 * ppm, tuple(c / 255.0 for c in FAMILY_TEXT[fam]), 140 / 255.0)
        canvas = pl.Canvas(base.convert("RGB"))
        light = pl.light_of(view)
        r, u, f = view
        for t in tris:
            P3 = t["p"]
            S = [((pl.dot(p, r) - frame[0]) * ppm + 12, (frame[3] - pl.dot(p, u)) * ppm + 12, -pl.dot(p, f), 1.0) for p in P3]
            n = pl.normalize(pl.cross(pl.sub(P3[1], P3[0]), pl.sub(P3[2], P3[0])))
            canvas.tri(S, t["uv"], t["rgba"], 1.0, t["solid"])   # pleine lumiere : sa lueur propre
        img = canvas.image().convert("RGBA")
        rng = random.Random(fam)
        for _ in range(5):
            q = P("gun_eco_glint%d" % fam, (0, 0, 0), rng=rng)
            for _ in range(rng.randrange(8)):
                q.tick()
            blit(img, textures["gun_eco_glint"], cx + (rng.random() - 0.5) * 0.5 * ppm, cy - rng.random() * 0.4 * ppm,
                 2 * q.size * ppm, q.rgb, q.alpha)
        out.append(img)
    return out


def planche(textures):
    os.makedirs(BUILD, exist_ok=True)
    F14, F18, F24 = label_font(14), label_font(18), label_font(24)
    sky = (150, 182, 222)
    rows = []

    # 1. les textures, brutes et teintees
    tiles = []
    tints = {"gun_scatter_pellet": (1.0, 0.55, 0.18), "gun_scatter_flash": (1.0, 0.6, 0.25), "gun_blaster_bolt": (1.0, 0.93, 0.45),
             "gun_blaster_spark": (1.0, 0.85, 0.3), "gun_vulcan_tracer": (0.2, 0.75, 1.0), "gun_vulcan_spark": (0.55, 0.9, 1.0),
             "gun_peace_mote": (0.62, 0.32, 1.0), "gun_peace_blast": (0.75, 0.5, 1.0), "gun_eco_glint": (1.0, 0.85, 0.29),
             "gun_wave_charge": (1.0, 0.45, 0.12), "gun_wave_dust": (1.0, 0.45, 0.12), "gun_plasmite_trail": (1.0, 0.45, 0.12),
             "gun_plasmite_blast": (1.0, 0.55, 0.25), "gun_reflexor_bolt": (1.0, 0.98, 0.72),
             "gun_reflexor_spark": (1.0, 0.97, 0.7), "gun_gyro_tracer": (1.0, 0.72, 0.18), "gun_gyro_spark": (1.0, 0.75, 0.2),
             "eco_halo": (0.36, 0.78, 1.0), "peace_orb": (0.59, 0.31, 1.0)}
    for name, tex in textures.items():
        tile = Image.new("RGBA", (150, 190), (24, 26, 32, 255))
        blit(tile, tex, 40, 50, 64, (1, 1, 1), 1.0)
        sky_tile = Image.new("RGBA", (64, 64), sky + (255,))
        blit(sky_tile, tex, 32, 32, 64, tints[name], 1.0)
        tile.alpha_composite(sky_tile, (78, 18))
        d = ImageDraw.Draw(tile)
        d.text((6, 100), name.replace("gun_", ""), font=F14, fill=(235, 235, 235))
        d.text((6, 120), "%dx%d, blanche" % tex.size, font=F14, fill=(160, 160, 160))
        d.text((6, 140), "teinte du code sur le ciel", font=F14, fill=(160, 160, 160))
        tiles.append(tile)
    rows.append(("Textures neuves (a gauche brute x4 sur fond noir, a droite teinte de depart du Java sur le ciel de Haven)", tiles))

    # 2. les effets, simules tique par tique
    scenes = []
    parts, caption = scene_scatter(textures)
    img = render(parts, textures, (560, 600), 34, (30, 300), view="top", bg=sky)
    d = ImageDraw.Draw(img)
    d.line([(30 + 8 * 34, 300 - 4 * 34), (30 + 8 * 34, 300 + 4 * 34)], fill=(110, 110, 110), width=6)
    scenes.append(("Scatter Gun : " + caption, img))

    trail, impact = scene_blaster(textures)
    img = render(trail, textures, (1000, 170), 34, (20, 115), bg=sky)
    wall = Image.new("RGBA", (300, 170), (125, 125, 125, 255))
    wall.alpha_composite(render(impact, textures, (300, 170), 34, (20 - 20 * 34, 115), bg=(125, 125, 125)), (0, 0))
    img.alpha_composite(wall.crop((20 + 2 * 34, 0, 300, 170)), (1000 - 300 + 20 + 2 * 34, 0))
    scenes.append(("Blaster, vu a ~20 blocs : trait apres 3 tiques (10 blocs/tique, 40 points par tique, 2 tiques de vie) ; a droite, eclats d'impact 2 tiques apres, sur un mur", img))

    parts = scene_vulcan(textures)
    img = render(parts, textures, (760, 130), 34, (20, 90), bg=sky)
    scenes.append(("Vulcan Fury : une balle, 1 tique apres (points tous les 0,5 bloc, tete mobile, 4 eclats au bout)", img))

    trail, last, frames = scene_peace(textures)
    img = render(trail, textures, (1000, 170), 34, (60, 110), bg=sky)
    size = 0.25 + 0.45
    blit(img, textures["peace_orb"], 60 + last[0] * 34, 110 - last[1] * 34, size * 34, (150 / 255, 80 / 255, 1.0), 0.9)
    blit(img, textures["peace_orb"], 60 + last[0] * 34, 110 - last[1] * 34, size * 0.45 * 34, (245 / 255, 230 / 255, 1.0), 1.0)
    scenes.append(("Peace Maker : la boule en vol (spirale, motes), 6 tiques apres le lancer", img))
    strip = Image.new("RGBA", (660, 220), sky + (255,))
    for i, fr in enumerate(frames):
        panel = render(fr, textures, (220, 220), 22, (110 - 6 * 22, 110 + 1.6 * 22), bg=sky)
        strip.alpha_composite(panel, (i * 220, 0))
    pts = arc_points(6.0, 4)
    arc = Image.new("RGBA", (220, 220), sky + (255,))
    draw_arc(arc, pts, (30, 110), 26, 1.0)
    strip2 = Image.new("RGBA", (880, 220), sky + (255,))
    strip2.alpha_composite(strip, (0, 0))
    strip2.alpha_composite(arc, (660, 0))
    scenes.append(("Peace Maker : explosion 1, 3 et 6 tiques apres (anneau de 0,5 a 5 blocs, 36 motes) ; foudre vers un monstre a 6 blocs", strip2))

    ammo = ammo_panels(textures)
    if ammo:
        strip = Image.new("RGBA", (sum(p.width for p in ammo) + 10 * len(ammo), ammo[0].height), sky + (255,))
        x = 0
        for p in ammo:
            strip.alpha_composite(p, (x, 0))
            x += p.width + 10
        scenes.append(("Munitions d'eco a ramasser : modele de Jak 3 en pleine lumiere, halo de la famille, etincelles (rouge, jaune, bleu, sombre)", strip))

    # 3. le HUD
    huds = hud_board()

    width = 1900
    height = 60 + 230 + sum(img.height + 40 for _, img in scenes) + ((len(huds) + 1) // 2) * (huds[0][1].height + 36) + 30
    board = Image.new("RGBA", (width, height), (14, 15, 18, 255))
    d = ImageDraw.Draw(board)
    d.text((12, 10), "Morph Gun, le tir : particules neuves, effets simules et HUD (tools/gun_particles.py)", font=F24, fill=(255, 255, 255))
    y = 52
    d.text((12, y), rows[0][0], font=F18, fill=(220, 220, 220))
    x = 12
    for tile in rows[0][1]:
        board.alpha_composite(tile, (x, y + 28))
        x += tile.width + 8
    y += 230
    for title, img in scenes:
        d.text((12, y), title, font=F18, fill=(220, 220, 220))
        board.alpha_composite(img, (12, y + 28))
        y += img.height + 40
    for i, (title, img) in enumerate(huds):
        col = i % 2
        if col == 0 and i > 0:
            y += huds[i - 1][1].height + 36
        xx = 12 + col * (img.width + 30)
        d.text((xx, y), "HUD, echelle d'interface 3 : " + title, font=F18, fill=(220, 220, 220))
        board.alpha_composite(img, (xx, y + 26))
    path = os.path.join(BUILD, "planche-tir.png")
    board.convert("RGB").save(path)
    print("planche ->", path, board.size)


# ============================================================== planche du jalon B (rouge et jaune ameliores)

def ring_layer(size, ppb, origin, radius, glow):
    """L'anneau de GunRenderers.Shockwave vu de dessus : couronne fondue vers le centre, bord vif."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    px = layer.load()
    inner = max(0.0, radius - 2.2)
    for y in range(size[1]):
        for x in range(size[0]):
            d = math.hypot((x - origin[0]) / ppb, (y - origin[1]) / ppb)
            if inner <= d <= radius:
                f = (d - inner) / max(1e-6, radius - inner)
                px[x, y] = (255, int(255 * (0.3 + 0.2 * f)), int(255 * (0.08 + 0.07 * f)), int(255 * 0.75 * glow * f))
            elif radius < d <= radius + 0.12:
                px[x, y] = (255, 107, 31, int(255 * 0.8 * glow))
    return layer


def scene_wave(textures, age, strength=1.0):
    """L'onde a un age donne, vue de dessus : anneau du rendu et braises du front (GunShockwaveEntity.clientEmbers)."""
    rng = random.Random(11 + age)
    size, ppb = (470, 470), 12.0
    origin = (size[0] / 2.0, size[1] / 2.0)
    max_radius = 3.0 + 15.0 * strength
    img = Image.new("RGBA", size, (70, 72, 78, 255))
    parts = []
    for tick in range(max(0, age - 8), age + 1):
        radius = min(max_radius, 3.0 + 15.0 * tick / 14.0)
        span = max_radius - 3.0
        intensity = max(0.15, strength * (1.0 - (radius - 3.0) / span))
        for _ in range(int(min(64, 10 + radius * 3.0 * intensity))):
            a = rng.random() * math.tau
            r = radius - rng.random() * 0.6
            parts.append(P("gun_wave_dust", (math.cos(a) * r, 0.1 + rng.random() * 0.3, math.sin(a) * r),
                           (math.cos(a) * 0.08, 0.08 + rng.random() * 0.12 * intensity, math.sin(a) * 0.08), rng))
        if tick < age:
            step(parts)
    radius = min(max_radius, 3.0 + 15.0 * age / 14.0)
    glow = max(0.25, strength * (1.0 - (radius - 3.0) / (max_radius - 3.0)))
    img.alpha_composite(ring_layer(size, ppb, origin, radius, glow))
    for q in parts:
        blit(img, textures[q.kind], origin[0] + q.pos[0] * ppb, origin[1] + q.pos[2] * ppb, 2.0 * q.size * ppb, q.rgb, q.alpha)
    d = ImageDraw.Draw(img)
    d.ellipse([origin[0] - 4, origin[1] - 4, origin[0] + 4, origin[1] + 4], fill=(240, 240, 240, 255))
    return img, "tique %d : rayon %.1f blocs, intensite %.2f" % (age, radius, glow)


def scene_plasmite(textures):
    """La grenade : cloche de GunGrenadeEntity (3,25 blocs par tique, gravite 0,1125), un rebond sur un mur, puis le souffle."""
    rng = random.Random(21)
    size, ppb = (960, 300), 12.0
    origin = (30.0, 250.0)
    img = Image.new("RGBA", size, (150, 182, 222, 255))
    d = ImageDraw.Draw(img)
    wall_x = 46.0
    d.rectangle([origin[0] + wall_x * ppb, 20, origin[0] + wall_x * ppb + ppb, origin[1]], fill=(96, 98, 104, 255))
    d.rectangle([0, origin[1], size[0], size[1]], fill=(86, 88, 94, 255))
    pos, vel = [0.0, 1.5], [3.25 * 0.958, 3.25 * 0.2873]
    trail, path = [], [tuple(pos)]
    for tick in range(60):
        vel[1] -= 0.1125
        nxt = [pos[0] + vel[0], pos[1] + vel[1]]
        if vel[0] > 0 and nxt[0] >= wall_x:
            f = (wall_x - pos[0]) / vel[0]
            nxt = [wall_x - 0.05, pos[1] + vel[1] * f]
            vel = [-vel[0] * 0.6, vel[1] * 0.6]
        if nxt[1] <= 0.16:
            nxt[1] = 0.16
            vel = [vel[0] * 0.6, -vel[1] * 0.6]
            if abs(vel[1]) < 0.12:
                pos = nxt
                path.append(tuple(pos))
                break
        for i in range(4):
            f = i / 4.0
            trail.append(P("gun_plasmite_trail", (pos[0] + (nxt[0] - pos[0]) * f, pos[1] + (nxt[1] - pos[1]) * f + 0.16, 0.0), rng=rng))
        pos = nxt
        path.append(tuple(pos))
        step(trail)
    for a, b in zip(path, path[1:]):
        d.line([origin[0] + a[0] * ppb, origin[1] - a[1] * ppb, origin[0] + b[0] * ppb, origin[1] - b[1] * ppb],
               fill=(255, 255, 255, 60), width=1)
    blast = [P("gun_plasmite_blast", (pos[0], pos[1] + 0.5, 0.0), rng=rng)]
    for _ in range(60):
        blast.append(P("gun_plasmite_trail", (pos[0] + rng.gauss(0, 1.2), pos[1] + 0.5 + rng.gauss(0, 1.2), rng.gauss(0, 1.2)),
                       tuple(rng.gauss(0, 0.45) for _ in range(3)), rng))
    for _ in range(4):
        step(blast)
    for q in trail + blast:
        if q.alive:
            blit(img, textures[q.kind], origin[0] + q.pos[0] * ppb, origin[1] - q.pos[1] * ppb, 2.0 * q.size * ppb, q.rgb, q.alpha)
    halo_tex = textures["eco_halo"]
    blit(img, halo_tex, origin[0] + path[6][0] * ppb, origin[1] - (path[6][1] + 0.16) * ppb, 1.1 * ppb, (1.0, 0.35, 0.16), 0.67)
    return img, "vue de cote : la cloche (trait fin), un mur qui la renvoie a 60 %, les rebonds au sol, puis le souffle a la 4e tique"


def scene_reflexor(textures):
    """Le tir a rebonds vu de dessus : GunClient.reflexor seme 4 points par bloc sur la ligne brisee de chaque tique."""
    rng = random.Random(31)
    size, ppb = (960, 330), 12.0
    origin = (30.0, 40.0)
    img = Image.new("RGBA", size, (70, 72, 78, 255))
    d = ImageDraw.Draw(img)
    walls = [((0.0, 22.0), (76.0, 23.0)), ((0.0, -2.0), (76.0, -1.0)), ((70.0, -1.0), (71.0, 22.0))]
    for (x0, z0), (x1, z1) in walls:
        d.rectangle([origin[0] + x0 * ppb, origin[1] + z0 * ppb, origin[0] + x1 * ppb, origin[1] + z1 * ppb], fill=(112, 114, 120, 255))
    pos, direction, speed = [0.0, 3.0], [0.8, 0.6], 10.0
    parts = []
    for tick in range(9):
        remaining = speed
        while remaining > 1e-3:
            hit = None
            for axis, limit in ((1, 22.0), (1, -1.0), (0, 70.0)):
                if direction[axis] > 0 and limit > pos[axis] or direction[axis] < 0 and limit < pos[axis]:
                    dist = (limit - pos[axis]) / direction[axis]
                    if 0 < dist <= remaining and (hit is None or dist < hit[0]):
                        hit = (dist, axis)
            travel = hit[0] if hit else remaining
            end = [pos[0] + direction[0] * travel, pos[1] + direction[1] * travel]
            dots = min(64, max(1, int(travel * 4.0)))
            for k in range(dots):
                f = k / dots
                parts.append(P("gun_reflexor_bolt", (pos[0] + (end[0] - pos[0]) * f, 0.0, pos[1] + (end[1] - pos[1]) * f), rng=rng))
            pos = end
            remaining -= travel
            if hit:
                direction[hit[1]] = -direction[hit[1]]
                speed = 20.0 / 3.0
                remaining = min(remaining, speed)
                for _ in range(6):
                    parts.append(P("gun_reflexor_spark", (pos[0], 0.0, pos[1]),
                                   ((rng.random() - 0.5) * 0.3, rng.random() * 0.2, (rng.random() - 0.5) * 0.3), rng))
        if tick < 8:
            step(parts)
    for q in parts:
        if q.alive:
            blit(img, textures[q.kind], origin[0] + q.pos[0] * ppb, origin[1] + q.pos[2] * ppb, 2.0 * q.size * ppb, q.rgb, q.alpha)
    return img, "vue de dessus, 9e tique : le trait ne dure que 4 tiques, on voit donc la fin du chemin et l'eclat du dernier rebond"


def scene_gyro(textures):
    """La soucoupe en rafale, vue de cote : deux traits par tique (GunClient.gyro), eclats au bout, halo jaune."""
    rng = random.Random(41)
    size, ppb = (960, 300), 12.0
    origin = (480.0, 70.0)
    img = Image.new("RGBA", size, (150, 182, 222, 255))
    d = ImageDraw.Draw(img)
    ground = 17.0
    d.rectangle([0, origin[1] + ground * ppb, size[0], size[1]], fill=(86, 88, 94, 255))
    parts = []
    targets = [(-22.0, ground - 1.0), (14.0, ground - 1.0), (25.0, ground - 1.0), (-6.0, ground)]
    for tick in range(2):
        for end in rng.sample(targets, 2):
            e = (end[0] + rng.uniform(-0.5, 0.5), end[1] + rng.uniform(-0.5, 0.5))
            parts.append(P("gun_gyro_tracer", (0.0, 0.0, 0.0), (e[0] / 2.0, -e[1] / 2.0, 0.0), rng))
            length = math.hypot(*e)
            dots = min(72, int(length / 0.5))
            for k in range(1, dots + 1):
                f = k / (dots + 1)
                parts.append(P("gun_gyro_tracer", (e[0] * f, -e[1] * f, 0.0), rng=rng))
            for _ in range(3):
                parts.append(P("gun_gyro_spark", (e[0], -e[1], 0.0), ((rng.random() - 0.5) * 0.25, rng.random() * 0.15,
                                                                      (rng.random() - 0.5) * 0.25), rng))
        if tick == 0:
            step(parts)
    blit(img, textures["eco_halo"], origin[0], origin[1], 2.6 * ppb, (1.0, 0.84, 0.24), 0.6)
    w = 0.5 * 3.5 * ppb
    d.ellipse([origin[0] - w / 2, origin[1] - 3, origin[0] + w / 2, origin[1] + 3], fill=(58, 60, 50, 255), outline=(240, 200, 40, 255))
    for q in parts:
        if q.alive:
            blit(img, textures[q.kind], origin[0] + q.pos[0] * ppb, origin[1] - q.pos[1] * ppb, 2.0 * q.size * ppb, q.rgb, q.alpha)
    return img, "vue de cote : deux salves de deux tirs (une par tique), la premiere deja palie ; la soucoupe (1,75 bloc) dans son halo"


def planche_b(textures):
    os.makedirs(BUILD, exist_ok=True)
    F14, F18, F24 = label_font(14), label_font(18), label_font(24)
    blocks = []
    waves = [scene_wave(textures, age) for age in (2, 7, 14)]
    row = Image.new("RGBA", (sum(w[0].width for w in waves) + 20, waves[0][0].height + 24), (24, 26, 32, 255))
    x = 0
    rd = ImageDraw.Draw(row)
    for img, caption in waves:
        row.alpha_composite(img, (x, 0))
        rd.text((x + 6, img.height + 4), caption, font=F14, fill=(200, 200, 200))
        x += img.width + 10
    blocks.append(("Wave Concussor (Deferlonator) : l'onde a pleine charge, vue de dessus, 12 pixels par bloc ; le tireur au centre", row))
    for title, scene in (("Plasmite RPG (RPG plasmique)", scene_plasmite), ("Beam Reflexor (Reflectorayon)", scene_reflexor),
                         ("Gyro Burster (Tournoyeur)", scene_gyro)):
        img, caption = scene(textures)
        blocks.append((title + " : " + caption, img))
    width = max(b[1].width for b in blocks) + 24
    height = 60 + sum(b[1].height + 44 for b in blocks)
    board = Image.new("RGBA", (width, height), (24, 26, 32, 255))
    d = ImageDraw.Draw(board)
    d.text((12, 10), "Morph Gun, jalon B : les ameliorations rouges et jaunes (memes couleurs, tailles et durees que le Java)",
           font=F24, fill=(255, 255, 255))
    y = 52
    for title, img in blocks:
        d.text((12, y), title, font=F18, fill=(220, 220, 220))
        board.alpha_composite(img, (12, y + 28))
        y += img.height + 44
    path = os.path.join(BUILD, "planche-tir-b.png")
    board.convert("RGB").save(path)
    print("planche B ->", path, board.size)


def main():
    if "--planche-b" in sys.argv:
        planche_b(read_textures())
        return
    if "--planche" in sys.argv:
        textures = read_textures()
    else:
        textures = write_textures()
    planche(textures)
    planche_b(textures)


if __name__ == "__main__":
    main()
