#!/usr/bin/env python3
"""
Arc legendaire d'Arcencium -- generateur de modeles animes 32x32.

Approche v2 : l'epine dorsale de l'arc est DESSINEE A LA MAIN (waypoints
pixel), epaissie au tampon, puis ombree automatiquement :
  - arete d'emeraude luisante partout ou le dos touche le vide
  - ombre chaude cote corde
  - corps en bois brun lisible
La geometrie procedurale (sinus) donnait des arcs trop fins et casses.

Design valide :
  - poignee bandee d'or, grande gemme emeraude sertie
  - les 5 cristaux de l'arc : rouge / orange / BLEU (gel) / rose / vert
  - corde BLANCHE au repos (le cycle prismatique = crans de tension en jeu)
  - silhouette manta : cornes prolongees au-dela des attaches, bouts
    d'emeraude luisants (reference : l'arc vert raie manta)

Usage :
    python tools/bow_designer.py            # genere les 3 modeles
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sword_designer as sd
import legendary_sword as ls

W, H = sd.W, sd.H
NFRAMES = ls.NFRAMES
RAMP = ls.RAMP

INV_GRAY = (139, 139, 139, 255)

# Corde prismatique permanente (choix valide) : cycle a travers les 5
# couleurs de charge -- rouge, orange, bleu (gel), rose, vert
STRING_CYCLE = [(255, 96, 106), (255, 156, 48), (96, 196, 255),
                (255, 124, 214), (120, 255, 190)]


def string_color(frame_idx):
    pos = frame_idx * len(STRING_CYCLE) / NFRAMES
    i = int(pos) % len(STRING_CYCLE)
    t = pos - int(pos)
    return ls.lerp(STRING_CYCLE[i],
                   STRING_CYCLE[(i + 1) % len(STRING_CYCLE)], t)


# ------------------------------------------------------------ primitives

def line_pts(a, b):
    """Pixels d'un segment (interpolation lineaire, sans trous)."""
    (x0, y0), (x1, y1) = a, b
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    return [(int(round(x0 + (x1 - x0) * i / n)),
             int(round(y0 + (y1 - y0) * i / n))) for i in range(n + 1)]


def path_pts(waypoints):
    """Chaine de segments -> liste de pixels dedupliquee, dans l'ordre."""
    pts = []
    seen = set()
    for a, b in zip(waypoints, waypoints[1:]):
        for p in line_pts(a, b):
            if p not in seen:
                seen.add(p)
                pts.append(p)
    return pts


def stamp(g, pts, brush, ch='i'):
    """Epaissit un chemin au tampon brush x brush."""
    off = brush // 2
    for x, y in pts:
        for dx in range(brush):
            for dy in range(brush):
                sd.put(g, x + dx - off, y + dy - off, ch)


def shade_wood(g):
    """Ombrage automatique du bois :
    - dos (voisin haut-gauche vide)  -> arete d'emeraude '4'/'5'
    - ventre (voisin bas-droit vide) -> ombre 'h'
    - interieur                      -> 'i', veine 'j' reguliere"""
    src = [row[:] for row in g]

    def empty(x, y):
        return not (0 <= x < W and 0 <= y < H) or src[y][x] == '.'

    # Tests sur les voisins ORTHOGONAUX : avec le voisin diagonal, une
    # bande a 45 degres a un bord en escalier ou presque chaque pixel
    # touche du vide -> tout devenait arete ou ombre, zero bois interieur
    for y in range(H):
        for x in range(W):
            if src[y][x] != 'i':
                continue
            if empty(x, y - 1) and empty(x - 1, y):
                g[y][x] = '5' if (x + y) % 3 == 0 else '4'
            elif empty(x, y + 1) and empty(x + 1, y):
                g[y][x] = 'h'
            elif (x + y) % 5 == 0:
                g[y][x] = 'j'          # %5 : %4 pointillait les gros corps


def recolor_zone(g, cx, cy, radius, pattern):
    """Recolore les pixels de bois autour d'un centre (poignee, coiffes)."""
    for y in range(H):
        for x in range(W):
            if g[y][x] in ('i', 'j', 'h', '4', '5') \
                    and max(abs(x - cx), abs(y - cy)) <= radius:
                g[y][x] = pattern[(x + y) % len(pattern)]


# ------------------------------------------------------------- variantes
# Epines dorsales dessinees a la main. Diagonale bas-gauche -> haut-droit,
# bombee vers le haut-gauche ; la corde ferme la silhouette en bas-droit.

# Epine manta v3 : EXACTEMENT l'epine de SPINE_RECURVE (la seule recette
# validee visuellement), sans son crochet final cote corde, prolongee de
# cornes aux deux bouts. Ne pas re-inventer une geometrie : deriver de ce
# qui marche.
SPINE_MANTA = [(3, 28), (4, 25), (5, 22), (6, 19), (8, 16), (10, 13),
               (12, 11), (15, 9), (18, 7), (21, 6), (24, 6), (26, 5)]
HORN_MANTA_LOW = [(3, 28), (2, 30), (1, 31)]
HORN_MANTA_UP = [(26, 5), (28, 4), (30, 3), (31, 2)]

# Recurve : les attaches se recourbent franchement vers la corde (crochets)
SPINE_RECURVE = [(3, 28), (4, 25), (5, 22), (6, 19), (8, 16), (10, 13),
                 (12, 11), (15, 9), (18, 7), (21, 6), (24, 6), (27, 7)]

BOW_VARIANTS = {
    # brush 2 interdit : sur un corps de 2 px, l'ombrage ne laisse AUCUN
    # bois interieur (tout est arete ou ombre) -> trait noir illisible
    "m1_manta": dict(
        desc="Manta - cornes fines aux coins, silhouette elancee",
        spine=SPINE_MANTA, horns=[HORN_MANTA_LOW, HORN_MANTA_UP],
        brush=3, horn_brush=1,
        nock_a=(4, 29), nock_b=(29, 6), spikes=False),
    "m2_manta_lourde": dict(
        desc="Manta lourde - cornes epaisses, branches massives, dards",
        spine=SPINE_MANTA, horns=[HORN_MANTA_LOW, HORN_MANTA_UP],
        brush=4, horn_brush=2,
        nock_a=(4, 29), nock_b=(29, 6), spikes=True),
    "m3_recurve": dict(
        desc="Recurve compact - crochets francs, trapu, sans cornes",
        spine=SPINE_RECURVE, horns=[],
        brush=3, horn_brush=1,
        nock_a=(4, 29), nock_b=(28, 7), spikes=True),
}


def build_bow(spine, horns, brush, horn_brush, nock_a, nock_b,
              spikes=False, desc="", flex=0.0):
    import math
    g = sd.blank()
    if flex > 0:
        # branches qui flechissent vers le dos quand on bande (profil sinus :
        # les attaches restent fixes, le centre recule)
        n = len(spine) - 1
        spine = [(x - int(round(math.sin(math.pi * i / n) * flex)),
                  y - int(round(math.sin(math.pi * i / n) * flex)))
                 for i, (x, y) in enumerate(spine)]
    pts = path_pts(spine)

    # 1. corps en bois
    stamp(g, pts, brush, 'i')
    for horn in horns:
        stamp(g, path_pts(horn), horn_brush, 'i')

    # 2. dards d'emeraude saillant du dos (variantes agressives)
    if spikes:
        for i in range(3, len(pts) - 3, 4):
            x, y = pts[i]
            sd.put(g, x - 2, y - 2, '5')

    # 3. ombrage automatique (arete / veine / ombre)
    shade_wood(g)

    # 4. poignee d'or au centre, coiffes aux attaches
    cx, cy = pts[len(pts) // 2]
    recolor_zone(g, cx, cy, 2, 'bcb')
    recolor_zone(g, spine[0][0], spine[0][1], 1, 'cb')
    recolor_zone(g, spine[-1][0], spine[-1][1], 1, 'cb')

    # 5. contour, puis gemmes serties (ecrins)
    sd.add_outline(g)
    ls.set_gem(g, spine[0][0], spine[0][1], 'k')       # rose  - attache basse
    ls.set_gem(g, spine[-1][0], spine[-1][1], 'r')     # rouge - attache haute
    q = len(pts) // 4
    ls.set_gem(g, pts[q][0], pts[q][1], 'n')           # orange - epaule basse
    ls.set_gem(g, pts[3 * q][0], pts[3 * q][1], 'u')   # bleu   - epaule haute
    ls.set_gem(g, cx, cy, 'G')                         # vert   - poignee

    # 6. corde : positions de repos. Elle n'est PAS ecrite dans la grille :
    #    le rendu la peint directement, pixel par pixel, pour pouvoir la
    #    faire vibrer et lui donner un degrade qui defile
    spts = line_pts(nock_a, nock_b)

    # 7. positions des gemmes (pour les flashs au pic de pulsation)
    gems = [(x, y, g[y][x]) for y in range(H) for x in range(W)
            if g[y][x] in 'rnukG']

    pal = sd.palette_of(ls.DARK_EMERALD, sd.GOLD, sd.HANDLE_LEATHER,
                        sd.GEM_BRIGHT)
    # Bois plus chaud et plus clair que HANDLE_LEATHER : sur une coupe de
    # 6 px dont 2 de contour, une ombre quasi noire enterre le bois --
    # la moitie de l'arc lisait noir
    pal.update({'h': (70, 45, 26, 255),
                'i': (112, 74, 42, 255),
                'j': (158, 112, 66, 255)})
    return dict(grid=g, pal=pal, spts=spts, pts=pts, gems=gems)


# ------------------------------------------------------------- animation

def rainbow(pos):
    """Couleur du degrade prismatique a la position pos (cyclique sur 1)."""
    n = len(STRING_CYCLE)
    p = (pos % 1.0) * n
    i = int(p) % n
    return ls.lerp(STRING_CYCLE[i], STRING_CYCLE[(i + 1) % n], p - int(p))


GEM_PHASE = {ch: ph for ch, _, _, ph in ls.ACCENT_GEMS}
GEM_PHASE['G'] = 0
WHITE = (245, 255, 250, 255)
VIB_AMP = 1.0          # amplitude de vibration de la corde (px)
RISE_LIFE = 4


def render_frames_bow(bow, pull=None):
    """pull = None -> arc au repos (Flux prismatique complet).
    pull = dict(color=(r,g,b), draw=px, full=bool) -> arc BANDE : corde en V
    tiree vers l'archer de `draw` px, couleur de charge fixe (pulse de
    brillance), fleche encochee ; pas de vibration ni de degrade.

    Animation 'Flux prismatique' :
      1. degrade arc-en-ciel qui DEFILE le long de la corde (5 couleurs
         presentes en meme temps)
      2. vibration de la corde (1 px au centre, sinusoidale)
      3. pulsation d'energie depuis la gemme centrale, symetrique le long
         des deux branches jusqu'aux pointes, puis deux eclats qui
         redescendent la corde et se rejoignent au centre dans un flash
      4. flash de l'ecrin de chaque gemme a son pic de pulsation
      5. motes ascendantes depuis le point d'encoche"""
    import math
    from PIL import Image
    grid, base_pal = bow['grid'], bow['pal']
    spts, pts, gems = bow['spts'], bow['pts'], bow['gems']

    # distance normalisee de chaque pixel d'arete au centre (le long de
    # l'epine) : 0 = poignee, 1 = pointes. Precalcule une fois.
    c = len(pts) // 2
    half = max(c, 1)
    ridge_dist = {}
    for y in range(H):
        for x in range(W):
            if grid[y][x] in RAMP:
                k = min(range(len(pts)),
                        key=lambda i: (pts[i][0] - x) ** 2 + (pts[i][1] - y) ** 2)
                ridge_dist[(x, y)] = abs(k - c) / half

    # geometrie de la corde : direction et perpendiculaire
    (ax, ay), (bx, by) = spts[0], spts[-1]
    dx, dy = bx - ax, by - ay
    nrm = math.hypot(dx, dy) or 1.0
    perp = (-dy / nrm, dx / nrm)
    n_s = len(spts)
    mid = n_s // 2
    rise_spawns = [(spts[mid][0], spts[mid][1], 0),
                   (spts[mid - 3][0], spts[mid - 3][1], 4),
                   (spts[mid + 3][0], spts[mid + 3][1], 8)]

    frames = []
    for f in range(NFRAMES):
        fg = [row[:] for row in grid]
        phase = f / NFRAMES

        # 3a. pulsation symetrique sur l'arete : de la poignee aux pointes
        for (x, y), dist in ridge_dist.items():
            d = dist - phase
            boost = 2 if abs(d) < 0.10 else (1 if abs(d) < 0.22 else 0)
            if boost:
                ch = grid[y][x]
                fg[y][x] = RAMP[min(RAMP.index(ch) + boost, len(RAMP) - 1)]

        # 4. flash d'ecrin au pic de chaque gemme (une frame par cycle)
        for gx, gy, ch in gems:
            peak = (3 - GEM_PHASE.get(ch, 0)) % NFRAMES
            if f == peak:
                for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = gx + ddx, gy + ddy
                    if 0 <= xx < W and 0 <= yy < H and fg[yy][xx] == 'o':
                        fg[yy][xx] = '*'

        # 5. motes ascendantes depuis l'encoche
        for x0, y0, birth in rise_spawns:
            age = (f - birth) % NFRAMES
            if age < RISE_LIFE:
                yy = y0 - 1 - age
                xx = x0 - 1
                if 0 <= xx < W and 0 <= yy < H and grid[yy][xx] == '.':
                    fg[yy][xx] = ls.FALL_FADE[age]

        pal = ls.frame_palette(base_pal, f)
        img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        px = img.load()
        for y in range(H):
            for x in range(W):
                px[x, y] = pal.get(fg[y][x], (255, 0, 255, 255))

        if pull is None:
            # 1+2+3b. la corde, peinte directement : degrade qui defile,
            # vibration, et deux eclats qui convergent vers le centre
            vib = math.sin(2 * math.pi * f / NFRAMES) * VIB_AMP
            g1 = int(round(phase * mid))      # eclat montant depuis le bas
            g2 = n_s - 1 - g1                 # eclat descendant depuis le haut
            for k, (x, y) in enumerate(spts):
                t = k / max(n_s - 1, 1)
                bow_off = math.sin(math.pi * t) * vib
                xx = int(round(x + perp[0] * bow_off))
                yy = int(round(y + perp[1] * bow_off))
                if not (0 <= xx < W and 0 <= yy < H):
                    continue
                col = rainbow(t - phase)
                if k in (g1, g2):
                    col = WHITE
                if f == NFRAMES - 1 and abs(k - mid) <= 1:
                    col = WHITE               # les eclats se rejoignent
                px[xx, yy] = col
        else:
            # corde BANDEE : V depuis les attaches jusqu'au point tire
            mx, my = spts[mid]
            P = (int(round(mx + perp[0] * pull['draw'])),
                 int(round(my + perp[1] * pull['draw'])))
            pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / NFRAMES)
            base = pull['color']
            if pull.get('full'):
                col = ls.lerp(base, (245, 255, 250), 0.35 + 0.55 * pulse)
            else:
                col = ls.lerp(base, (255, 255, 255), 0.10 + 0.20 * pulse)
            for (x, y) in line_pts(spts[0], P) + line_pts(P, spts[-1]):
                if 0 <= x < W and 0 <= y < H:
                    px[x, y] = col
            # fleche encochee, pointee vers le dos (direction -perp) :
            # empennage couleur de charge, fut de bois, tete d'emeraude
            ux, uy = -perp[0], -perp[1]
            for k in range(0, 18):
                x = int(round(P[0] + ux * k))
                y = int(round(P[1] + uy * k))
                if not (0 <= x < W and 0 <= y < H):
                    continue
                if k <= 2:
                    c = ls.lerp(base, (255, 255, 255), 0.3)
                elif k <= 13:
                    c = (100, 80, 54, 255) if k % 2 else (124, 100, 68, 255)
                elif k <= 15:
                    c = (66, 209, 138, 255)
                else:
                    c = (231, 255, 244, 255)
                px[x, y] = c
                if k == 14:       # barbes de la tete, de part et d'autre
                    for s in (1, -1):
                        sx = int(round(x + perp[0] * s))
                        sy = int(round(y + perp[1] * s))
                        if 0 <= sx < W and 0 <= sy < H:
                            px[sx, sy] = (52, 178, 116, 255)
        frames.append(img)
    return frames


# Les 5 etats de tension : (nom, couleur de corde, tirage px, flexion px)
# rouge / orange / bleu (gel) / rose / vert-blanc (pleine tension)
PULL_STATES = [
    ("pulling_0", (255, 96, 106), 3, 0.0, False),
    ("pulling_1", (255, 156, 48), 4, 0.0, False),
    ("pulling_2", (96, 196, 255), 5, 1.0, False),
    ("pulling_3", (255, 124, 214), 6, 1.0, False),
    ("pulling_4", (120, 255, 190), 7, 2.0, True),
]

MCMETA = '{\n  "animation": {\n    "frametime": 2,\n    "interpolate": true\n  }\n}\n'


def install_bow(variant="m3_recurve", item="arcencium_bow"):
    """Ecrit dans le mod : <item>.png (repos) + <item>_pulling_0..4.png,
    chacun en spritesheet 12 frames avec son .mcmeta."""
    from PIL import Image
    kw = dict(BOW_VARIANTS[variant])
    kw.pop("desc")
    item_dir = ls.ITEM_DIR
    os.makedirs(item_dir, exist_ok=True)

    def write(name, frames):
        sheet = Image.new('RGBA', (W, H * NFRAMES), (0, 0, 0, 0))
        for i, fr in enumerate(frames):
            sheet.paste(fr, (0, i * H))
        dest = os.path.join(item_dir, name + ".png")
        sheet.save(dest)
        with open(dest + ".mcmeta", "w", encoding="utf-8") as fh:
            fh.write(MCMETA)
        print("Installe :", os.path.relpath(dest, ls.ROOT))

    write(item, render_frames_bow(build_bow(**kw)))
    for suffix, color, draw, flex, full in PULL_STATES:
        bow = build_bow(flex=flex, **kw)
        frames = render_frames_bow(
            bow, pull=dict(color=color, draw=draw, full=full))
        write(item + "_" + suffix, frames)
        export_bow(variant + "_" + suffix, frames)     # apercus


def export_bow(name, frames):
    """Apercus sur le gris d'inventaire Minecraft : le seul test honnete
    ou corde blanche ET bois sombre doivent rester lisibles ensemble."""
    from PIL import Image
    os.makedirs(ls.PV_DIR, exist_ok=True)
    still = os.path.join(ls.PV_DIR, name + "_x14.png")
    bg = Image.new('RGBA', frames[0].size, INV_GRAY)
    bg.alpha_composite(frames[0])
    bg.resize((W * 14, H * 14), Image.NEAREST).save(still)
    sheet = Image.new('RGBA', (W, H * NFRAMES), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * H))
    sheet_path = os.path.join(ls.PV_DIR, name + "_sheet.png")
    sheet.save(sheet_path)
    gifs = []
    for fr in frames:
        b = Image.new('RGBA', fr.size, INV_GRAY)
        b.alpha_composite(fr)
        gifs.append(b.convert('P', palette=Image.ADAPTIVE)
                     .resize((W * 10, H * 10), Image.NEAREST))
    gif_path = os.path.join(ls.PV_DIR, name + "_anim.gif")
    gifs[0].save(gif_path, save_all=True, append_images=gifs[1:],
                 duration=110, loop=0)
    return still, sheet_path, gif_path


if __name__ == "__main__":
    if "--install" in sys.argv:
        i = sys.argv.index("--install")
        variant = sys.argv[i + 1] if len(sys.argv) > i + 1 else "m3_recurve"
        install_bow(variant)
        sys.exit(0)
    for name, kw in BOW_VARIANTS.items():
        bow = build_bow(**kw)
        frames = render_frames_bow(bow)
        still, sheet, gif = export_bow(name, frames)
        print("%-16s %s" % (name, kw["desc"]))
        print("   apercu : %s" % os.path.relpath(still, ls.ROOT))
        print("   anime  : %s" % os.path.relpath(gif, ls.ROOT))
