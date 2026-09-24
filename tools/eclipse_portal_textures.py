"""
Les textures des portails de l'Eclipse (24 sept. 2026, cahier §88).

« Je ne veux rien en lien avec quelque chose de vivant. Je veux vraiment que
ce soit des portails d'horreur. » Pas de chair, pas d'oeil, pas de bouche :
de la PIERRE NOIRE fendue dont les fissures rougeoient, des CHAINES de fer
qui tiennent le portail ferme et cedent, un VIDE noir ou tourne une fumee
cramoisie, des cendres au sol. L'horreur vient de ce qui passe au travers.

    python tools/eclipse_portal_textures.py [--preview]

Ecrit dans src/main/resources/assets/emeraldweapons/textures/block/eclipse_porte/ ;
--preview ajoute une planche agrandie dans le dossier courant.
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons", "textures", "block", "eclipse_porte")


def noise(w, h, seed, scale):
    """Un bruit de valeur lisse, en carreaux, sans numpy."""
    rnd = random.Random(seed)
    gw, gh = w // scale + 2, h // scale + 2
    grid = [[rnd.random() for _ in range(gw)] for _ in range(gh)]

    def at(x, y):
        gx, gy = x / scale, y / scale
        x0, y0 = int(gx) % (gw - 1), int(gy) % (gh - 1)
        fx, fy = gx - int(gx), gy - int(gy)
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = grid[y0][x0] + (grid[y0][x0 + 1] - grid[y0][x0]) * fx
        b = grid[y0 + 1][x0] + (grid[y0 + 1][x0 + 1] - grid[y0 + 1][x0]) * fx
        return a + (b - a) * fy
    return at


def fbm(w, h, seed, scales=(16, 8, 4, 2)):
    layers = [noise(w, h, seed + i, s) for i, s in enumerate(scales)]
    weights = [0.5, 0.25, 0.15, 0.1][:len(scales)]

    def at(x, y):
        return sum(wt * f(x, y) for wt, f in zip(weights, layers))
    return at


def clamp(v):
    return max(0, min(255, int(v)))


# ------------------------------------------------------------------ la pierre

def pierre():
    """La pierre du cadre : un basalte noir, grain serre, quelques eclats gris."""
    w = h = 64
    img = Image.new("RGBA", (w, h))
    f = fbm(w, h, 11)
    rnd = random.Random(12)
    for y in range(h):
        for x in range(w):
            v = f(x, y)
            base = 14 + v * 34
            px = (clamp(base + 3), clamp(base), clamp(base + 1), 255)
            if rnd.random() < 0.025:
                g = clamp(base + 28)
                px = (g, g - 2, g, 255)
            img.putpixel((x, y), px)
    return img


def fissures(seed=21, w=64, h=64, count=5):
    """Des fissures qui rougeoient : sur fond transparent, un coeur clair, un halo sombre."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rnd = random.Random(seed)
    for _ in range(count):
        x, y = rnd.uniform(0, w), rnd.uniform(0, h)
        angle = rnd.uniform(0, math.pi * 2)
        for step in range(rnd.randint(18, 40)):
            angle += rnd.uniform(-0.6, 0.6)
            x = (x + math.cos(angle)) % w
            y = (y + math.sin(angle)) % h
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    px, py = int(x + dx) % w, int(y + dy) % h
                    core = dx == 0 and dy == 0
                    old = img.getpixel((px, py))
                    new = (255, 70, 80, 255) if core else (150, 12, 28, 150)
                    if old[3] < new[3]:
                        img.putpixel((px, py), new)
            if rnd.random() < 0.08:
                angle += rnd.choice((-1, 1)) * 1.2
    return img


# -------------------------------------------------------------------- le vide

def vide():
    """Le vide : presque noir, un grain violet-rouge a peine perceptible. Opaque."""
    w = h = 128
    img = Image.new("RGBA", (w, h))
    f = fbm(w, h, 31, scales=(32, 16, 8, 4))
    for y in range(h):
        for x in range(w):
            v = f(x, y)
            img.putpixel((x, y), (clamp(4 + v * 22), clamp(1 + v * 4), clamp(6 + v * 16), 255))
    return img


def fumee():
    """La fumee qui tourne dans le vide : trois bras de spirale cramoisis, transparents au bord."""
    w = h = 128
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    f = fbm(w, h, 41, scales=(24, 12, 6))
    cx, cy = w / 2, h / 2
    for y in range(h):
        for x in range(w):
            dx, dy = (x - cx) / cx, (y - cy) / cy
            r = math.hypot(dx, dy)
            if r > 1.0:
                continue
            theta = math.atan2(dy, dx)
            arm = 0.5 + 0.5 * math.cos(3 * theta + r * 7.5 + f(x, y) * 3.0)
            # le coeur reste noir : c'est un puits, la fumee tourne AUTOUR
            hollow = min(1.0, max(0.0, (r - 0.06) / 0.22))
            wisp = arm ** 6 * (1.0 - r) ** 0.8 * (0.55 + 0.45 * f(x, y)) * hollow
            a = clamp(wisp * 520)
            # LA COULEUR S'ETEINT AVEC L'OPACITE. Sous Complementary, une couche emissive
            # allume la couleur du pixel meme ou il est transparent : la fumee peignait tout
            # le vide d'un rouge-violet uniforme. Couleur multipliee par l'opacite.
            k = min(1.0, a / 255.0)
            red = clamp((120 + wisp * 150) * k)
            img.putpixel((x, y), (red, clamp((8 + wisp * 30) * k), clamp((30 + wisp * 60) * k), a))
    return img


def bord():
    """Le liseré du vide : une ligne cramoisie, le coeur presque blanc, qui s'eteint vers l'exterieur."""
    w, h = 16, 64
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    f = noise(w, h, 51, 8)
    for y in range(h):
        for x in range(w):
            t = x / (w - 1)                       # 0 dedans, 1 dehors
            glow = max(0.0, 1.0 - abs(t - 0.3) / 0.7) ** 1.6 * (0.7 + 0.3 * f(x, y))
            core = max(0.0, 1.0 - abs(t - 0.3) / 0.12)
            k = min(1.0, glow)
            img.putpixel((x, y), (clamp((160 + 95 * glow) * k), clamp((20 + 150 * core) * k),
                                  clamp((40 + 120 * core) * k), clamp(255 * glow)))
    return img


# --------------------------------------------------------------- les chaines

def chaine():
    """Une chaine de fer noirci, maillons alternes, sur une bande verticale transparente."""
    w, h = 16, 64
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    link = 16
    for y in range(h):
        for x in range(w):
            k = y // link
            ly = (y % link) + 0.5
            lx = x + 0.5
            if k % 2 == 0:                          # maillon de face : un anneau ovale, epais
                rx, ry = 7.2, 9.4
                d = ((lx - 8) / rx) ** 2 + ((ly - 8) / ry) ** 2
                ring = 0.30 < d < 1.0
            else:                                   # maillon de chant : une barre large
                ring = 5.0 < lx < 11.0 and 0 < ly < 16
            if ring:
                shade = 78 + (26 if lx < 8 else 0) - (14 if ly > 11 else 0)
                rust = 1.0 if (x * 7 + y * 13) % 11 == 0 else 0.0
                img.putpixel((x, y), (clamp(shade + 20 * rust), clamp(shade - 4), clamp(shade - 2 - 8 * rust), 255))
    return img


def cendre():
    """Le sol brule sous le portail : cendre noire, bord effiloche."""
    w = h = 64
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    f = fbm(w, h, 61)
    for y in range(h):
        for x in range(w):
            r = math.hypot(x - w / 2 + 0.5, y - h / 2 + 0.5) / (w / 2)
            edge = 1.0 - r + (f(x, y) - 0.5) * 0.5
            if edge <= 0:
                continue
            a = clamp(min(1.0, edge * 2.2) * 235)
            v = clamp(10 + f(x, y) * 30)
            img.putpixel((x, y), (v, v - 2 if v > 2 else 0, v, a))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    made = {
        "pierre": pierre(),
        "pierre_fissures": fissures(),
        "vide": vide(),
        "fumee": fumee(),
        "bord": bord(),
        "chaine": chaine(),
        "cendre": cendre(),
    }
    for name, img in made.items():
        img.save(os.path.join(OUT, name + ".png"))
        print("ecrit", name, img.size)
    if "--preview" in sys.argv:
        tiles = [img.resize((img.width * 4, img.height * 4), Image.NEAREST) for img in made.values()]
        sheet = Image.new("RGBA", (sum(t.width for t in tiles) + 8 * len(tiles), max(t.height for t in tiles)),
                          (90, 90, 96, 255))
        x = 0
        for t in tiles:
            sheet.alpha_composite(t, (x, 0))
            x += t.width + 8
        sheet.save("eclipse_porte_planche.png")
        print("planche : eclipse_porte_planche.png")


if __name__ == "__main__":
    main()
