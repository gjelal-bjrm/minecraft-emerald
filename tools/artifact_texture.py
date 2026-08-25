#!/usr/bin/env python3
"""
Textures des artefacts.

On ne redessine rien : chaque artefact part d'une silhouette vanilla dont la
lecture a seize pixels est deja eprouvee, puis on la retravaille pour qu'elle
appartienne a la famille d'Arcencium.

Trois traitements, dans cet ordre :
  1. la piece est poussee au noir, comme l'armure et le coffre ;
  2. ses hautes lumieres reprennent la couleur propre a l'artefact, ce qui
     permet de le reconnaitre d'un coup d'oeil dans un coffre ;
  3. un serti dore vient border la silhouette, marque commune a tous les
     artefacts -- c'est lui qui les designe comme des pieces a sertir plutot
     que comme des ingredients.

Chaque texture est animee : la couleur pulse doucement, plus discretement que
les fissures de l'armure.

Usage :
    python tools/artifact_texture.py [--preview]
"""

import colorsys
import io as _io
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons",
                        "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

VANILLA_JAR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge",
                           "minecraft", "Install", "versions", "1.21.1", "1.21.1.jar")

NFRAMES = 8
FRAMETIME = 5

GOLD = (0xC9, 0x96, 0x26)
BLACK_LO, BLACK_HI = 0x0A, 0x2A

# artefact -> (silhouette vanilla, couleur)
# La silhouette est choisie pour ce qu'elle evoque : une lunette pour une
# lentille, un fragment de netherite pour une plaque, un lingot pour un lest.
ARTIFACTS = [
    # casque : la perception
    ("lentille_du_prisme",     "spyglass",             0xB98CFF),
    ("filtre_de_brume",        "glass_bottle",         0xB9C6D6),
    ("repere_d_echo",          "echo_shard",           0xFFD36B),
    ("lentille_d_aurore",      "prismarine_crystals",  0x9CE8FF),
    # plastron : la survie
    ("plaque_de_gangue",       "netherite_scrap",      0x78E8AE),
    ("coque_prismatique",      "heart_of_the_sea",     0x6BE0FF),
    ("reservoir_de_prisme",    "dragon_breath",        0x9CFF8C),
    ("plastron_de_resonance",  "copper_ingot",         0xFF9C4A),
    # jambieres : le controle
    ("lest_de_gangue",         "netherite_ingot",      0xC9A26B),
    ("jambieres_de_maree",     "prismarine_shard",     0x7DB8FF),
    ("champ_de_cristal",       "quartz",               0xA8B4FF),
    ("renfort_de_siege",       "brick",                0xD6D6C0),
    # bottes : le deplacement
    ("semelle_de_prisme",      "phantom_membrane",     0xFF7DD6),
    ("bottes_d_eclair",        "firework_rocket",      0xFFF06B),
    ("semelle_vaporeuse",      "snowball",             0xC0E8FF),
    ("bottes_de_retour",       "ender_pearl",          0xB08CFF),
    # epee : le corps-a-corps
    ("regulateur_de_lame",     "redstone",             0x78E8AE),
    ("lame_de_chaine",         "chain",                0xE8E8F0),
    ("drain_de_cristal",       "amethyst_shard",       0xFF616B),
    ("eclat_final",            "firework_star",        0xFF9C30),
    # arc : la distance
    ("tension_rapide",         "string",               0x61C4FF),
    ("fleche_fourchue",        "arrow",                0x8CFFB0),
    ("marque_prolongee",       "glow_ink_sac",         0xE478FF),
    ("fleche_tracante",        "spectral_arrow",       0xFFB84A),
]


def vanilla(name):
    with zipfile.ZipFile(VANILLA_JAR) as z:
        data = z.read("assets/minecraft/textures/item/%s.png" % name)
    img = Image.open(_io.BytesIO(data)).convert("RGBA")
    # certaines textures vanilla sont animees : on ne garde que la premiere image
    if img.height > img.width:
        img = img.crop((0, 0, img.width, img.width))
    return img


def border(img):
    """Pixels transparents jouxtant la silhouette : le serti se pose la."""
    w, h = img.size
    px = img.load()
    out = set()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3] != 0:
                    out.add((x, y))
                    break
    return out


def build(source, color, shift):
    src = vanilla(source)
    w, h = src.size
    px = src.load()
    lums = [((px[x, y][0] * 299 + px[x, y][1] * 587 + px[x, y][2] * 114) // 1000)
            for y in range(h) for x in range(w) if px[x, y][3] > 0]
    if not lums:
        return src
    lo, hi = min(lums), max(lums)
    span = max(1, hi - lo)

    cr, cg, cb = (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF
    hue, sat, _ = colorsys.rgb_to_hsv(cr / 255, cg / 255, cb / 255)
    # la teinte respire autour de sa valeur propre au lieu de faire le tour du
    # cercle : l'artefact reste identifiable a sa couleur pendant tout le cycle
    hue = (hue + 0.045 * (shift * 2 - 1)) % 1.0

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            t = (((r * 299 + g * 587 + b * 114) // 1000) - lo) / span
            if t < 0.55:
                # la masse : noire, avec juste assez de modele pour se lire
                v = int(BLACK_LO + t / 0.55 * (BLACK_HI - BLACK_LO))
                dst[x, y] = (v, v, int(v * 1.15) + 1, 255)
            else:
                k = (t - 0.55) / 0.45
                rr, gg, bb = colorsys.hsv_to_rgb(hue, sat * (1 - 0.25 * k), 0.45 + 0.55 * k)
                dst[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), 255)

    for (x, y) in border(src):
        dst[x, y] = GOLD + (255,)
    return out


def write(name, frames):
    dest = os.path.join(ITEM_DIR, "artifact_" + name + ".png")
    w = frames[0].width
    sheet = Image.new("RGBA", (w, w * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, i * w))
    sheet.save(dest)
    with open(dest + ".mcmeta", "w") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}\n' % FRAMETIME)


def main():
    if not os.path.isfile(VANILLA_JAR):
        sys.exit("jar vanilla introuvable : %s" % VANILLA_JAR)
    os.makedirs(ITEM_DIR, exist_ok=True)
    firsts = []
    for name, source, color in ARTIFACTS:
        frames = [build(source, color, f / NFRAMES) for f in range(NFRAMES)]
        write(name, frames)
        firsts.append(frames[0])
        print("  artifact_%-20s d'apres %-18s %d images" % (name, source, NFRAMES))

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        s = 12
        board = Image.new("RGBA", (16 * s * len(firsts), 16 * s), (22, 22, 26, 255))
        for i, img in enumerate(firsts):
            r = img.resize((16 * s, 16 * s), Image.NEAREST)
            board.paste(r, (i * 16 * s, 0), r)
        p = os.path.join(PREVIEW, "artefacts.png")
        board.save(p)
        print("  apercu %s" % p)


if __name__ == "__main__":
    main()
