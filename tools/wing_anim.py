#!/usr/bin/env python3
"""
L'ANIMATION DE CHAQUE APPARENCE D'AILES (cahier §97), comme l'epee (legendary_sword.py) :
une bande d'images empilees, que le jeu fait defiler.

« Tu as anime le squelette. Moi, je voulais que tu animes chaque skin des ailes, un peu comme on
a anime avec des frames l'epee » (le joueur, 24 sept.).

Une peinture d'ailes n'est pas dans l'atlas des objets : le jeu ne l'anime pas de lui-meme. Le mod
la garde FIXE et pose PAR-DESSUS, en lumiere, une bande de FRAMES images de ce qui bouge
(WingsLayer : chaque image fondue dans la suivante). Chaque effet est tire de la peinture elle-meme
-- ses parties claires, ses couleurs, ses cristaux, ses veines -- et garde ses couleurs : rien ne
denature une apparence (le joueur l'a refuse pour les reflets, §96 B).

    python tools/wing_anim.py              # toutes les apparences
    python tools/wing_anim.py braise       # une seule
    python tools/wing_anim.py --apercu     # plus un GIF de controle par apparence (build/wings)

Sortie : assets/emeraldweapons/textures/wings/anim/<apparence>.png (SIZE x SIZE*FRAMES).
PIL seul (pas de numpy) : tout passe par des operations d'image entieres.
"""
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WINGS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'emeraldweapons', 'textures', 'wings')
OUT = os.path.join(WINGS, 'anim')
PREVIEW = os.path.join(ROOT, 'build', 'wings')

SIZE = 256
FRAMES = 20

# la racine de chaque peinture (WingSkin.rootU / rootV), d'ou partent les ondes
ROOTS = {
    'prismatiques': (0.14, 0.85), 'rubis': (0.15, 0.85), 'aurore': (0.15, 0.87),
    'pierres_precieuses': (0.12, 0.80), 'braise': (0.16, 0.85), 'tempete': (0.16, 0.84),
    'emeraude': (0.17, 0.85), 'obscures': (0.12, 0.85), 'givre': (0.16, 0.83),
    'papillon': (0.21, 0.79), 'souverain_astral': (0.18, 0.87),
}


# ================================================================ masques tires de la peinture

def load(name):
    im = Image.open(os.path.join(WINGS, name + '.png')).convert('RGBA')
    return im.resize((SIZE, SIZE), Image.LANCZOS)


def channel_curve(channel, lo, hi):
    """Un canal (0-255) en masque doux : 0 sous lo, 255 au-dessus de hi."""
    span = max(1, hi - lo)
    return channel.point(lambda v: 0 if v <= lo else 255 if v >= hi else int(255 * (v - lo) / span))


def masks(img):
    """Les masques d'une peinture : sa forme, sa clarte, sa saturation, sa teinte."""
    alpha = img.getchannel('A').point(lambda a: 255 if a >= 128 else int(a * 2))
    h, s, v = img.convert('RGB').convert('HSV').split()
    return alpha, h, s, v


def distance_field(root):
    """La distance a la racine, de 0 (la racine) a 255 (le coin le plus loin) : les ondes partent de la."""
    big = Image.radial_gradient('L').resize((SIZE * 4, SIZE * 4), Image.BILINEAR)   # 0 au centre
    cx, cy = int(root[0] * SIZE), int(root[1] * SIZE)
    field = big.crop((SIZE * 2 - cx, SIZE * 2 - cy, SIZE * 3 - cx, SIZE * 3 - cy))
    # la gradiente radiale va de 0 au centre a 255 a SIZE*2 : on l'etire pour que la pointe tombe pres de 255
    far = max(math.hypot(x - cx, y - cy) for x, y in ((0, 0), (SIZE, 0), (0, SIZE), (SIZE, SIZE)))
    k = (SIZE * 2) / far
    return field.point(lambda d: min(255, int(d * k)))


def along_field(angle_deg):
    """Une coordonnee lineaire le long d'une direction (0 a 255) : pour les balayages."""
    g = Image.linear_gradient('L').resize((SIZE * 2, SIZE * 2), Image.BILINEAR)
    g = g.rotate(angle_deg, resample=Image.BILINEAR, expand=False)
    return g.crop((SIZE // 2, SIZE // 2, SIZE // 2 + SIZE, SIZE // 2 + SIZE))


def band(field, center, width, strength=255):
    """Une bande claire sur un champ (0-255) : un pic doux de cette largeur, centre la."""
    c = center * 255.0
    w = max(1.0, width * 255.0)

    def f(d):
        t = abs(d - c) / w
        return 0 if t >= 1.0 else int(strength * (1.0 - t * t) ** 2)
    return field.point(f)


def lighten(img, gain=1.45, lift=35):
    """La peinture eclaircie dans ses propres couleurs : la lumiere d'un effet."""
    rgb = img.convert('RGB')
    return rgb.point(lambda v: min(255, int(v * gain + lift)))


def tint(img, color, amount):
    """La peinture tiree vers une couleur (un reflet blanc, un eclat d'or)."""
    rgb = img.convert('RGB')
    solid = Image.new('RGB', rgb.size, color)
    return Image.blend(rgb, solid, amount)


def compose(rgb, alpha):
    frame = rgb.convert('RGBA')
    frame.putalpha(alpha)
    return frame


def mul(*layers):
    out = layers[0]
    for layer in layers[1:]:
        out = ImageChops.multiply(out, layer)
    return out


def hotspots(img, alpha, v, count, seed, min_v=200, spread=10):
    """Des points clairs de la peinture, dans sa forme, espaces : la ou naissent les scintillements."""
    rnd = random.Random(seed)
    pa = alpha.load()
    pv = v.load()
    spots = []
    tries = 0
    while len(spots) < count and tries < 20000:
        tries += 1
        x = rnd.randrange(4, SIZE - 4)
        y = rnd.randrange(4, SIZE - 4)
        if pa[x, y] < 200 or pv[x, y] < min_v:
            continue
        if any((x - sx) ** 2 + (y - sy) ** 2 < spread * spread for sx, sy, _ in spots):
            continue
        spots.append((x, y, rnd.random()))
    return spots


def star(draw, x, y, r, color, a):
    """Un eclat a quatre branches, de rayon r, d'opacite a."""
    col = color + (int(a),)
    draw.line([(x - r, y), (x + r, y)], fill=col, width=1)
    draw.line([(x, y - r), (x, y + r)], fill=col, width=1)
    k = max(1, r // 3)
    draw.ellipse([x - k, y - k, x + k, y + k], fill=col)


# ================================================================ les effets (une image a la phase p, de 0 a 1)
#
# Premiere serie en jeu (§97 D) : des effets justes, mais trop sages -- un reflet sur une partie
# deja claire ne se voit pas, et le battement des ailes noyait le reste. Chaque effet porte
# maintenant sur TOUTE l'aile (un plancher sur la forme, plus fort sur ses parties), eclaire plus
# franchement, avec un halo ; les etincelles durent plus longtemps.

def broaden(mask, alpha, floor):
    """Un masque d'effet avec un plancher sur toute l'aile : la vague se voit partout, plus fort sur ses parties."""
    return ImageChops.lighter(mask, alpha.point(lambda a: int(a * floor)))


def bloom(frame, alpha, radius=2.5, amount=0.8):
    """La lumiere deborde un peu autour d'elle : un halo flou, dans la forme de l'aile."""
    halo = frame.convert('RGBa').filter(ImageFilter.GaussianBlur(radius)).convert('RGBA')
    halo.putalpha(mul(halo.getchannel('A').point(lambda a: min(255, int(a * amount))), alpha))
    return over(halo, frame)


def clip(frame, alpha):
    """Rien ne deborde de la forme de l'aile."""
    frame = frame.copy()
    frame.putalpha(mul(frame.getchannel('A'), alpha))
    return frame


def fx_pulse(img, alpha, mask, dist, p, width=0.2, gain=1.6, lift=50, strength=240):
    """Une lueur qui court de la racine vers la pointe, plus forte dans les parties du masque, a leurs couleurs."""
    b = band(dist, p * 1.4 - 0.2, width, strength)
    return compose(lighten(img, gain, lift), mul(b, mask, alpha))


def fx_sweep(img, alpha, mask, along, p, color=(255, 255, 255), amount=0.3, width=0.12, strength=175):
    """Un reflet qui balaie l'aile en diagonale, de la racine vers la pointe, dans ses couleurs a peine blanchies."""
    b = band(along, 1.25 - p * 1.5, width, strength)
    return compose(tint(lighten(img, 1.45, 35), color, amount), mul(b, mask, alpha))


def fx_twinkle(img, spots, p, radius=(3, 7), color=None, sharp=3.0):
    """Des eclats qui scintillent chacun a son tour, a la couleur de la peinture sous eux, avec leur halo."""
    frame = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    halo = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(frame)
    glow = ImageDraw.Draw(halo)
    px = img.load()
    for x, y, phase in spots:
        s = math.sin(2.0 * math.pi * (p + phase))
        if s <= 0.0:
            continue
        a = 255 * s ** sharp
        if a < 8:
            continue
        r = int(round(radius[0] + (radius[1] - radius[0]) * s ** 2))
        c = color or tuple(min(255, int(ch * 0.4 + 170)) for ch in px[x, y][:3])
        star(draw, x, y, r, c, a)
        k = r * 0.9
        glow.ellipse([x - k, y - k, x + k, y + k], fill=c + (int(a * 0.55),))
    # flou en opacite premultipliee : flouee droite, chaque etincelle gardait un liseret noir
    frame = frame.convert('RGBa').filter(ImageFilter.GaussianBlur(0.6)).convert('RGBA')
    halo = halo.convert('RGBa').filter(ImageFilter.GaussianBlur(radius[1] * 0.5)).convert('RGBA')
    return over(halo, frame)


def periodic_noise(seed, cells, size):
    """Un bruit doux qui se repete de haut en bas tous les size pixels : sa boucle n'a pas de couture."""
    rnd = random.Random(seed)
    tile = Image.new('L', (cells, cells))
    pt = tile.load()
    for y in range(cells):
        for x in range(cells):
            pt[x, y] = rnd.randrange(0, 256)
    tall = Image.new('L', (cells, cells * 4))
    for k in range(4):
        tall.paste(tile, (0, k * cells))
    # redimensionne sur quatre tuiles : les fenetres, prises dans les deux du milieu, ne voient pas les bords
    return tall.resize((size, size * 4), Image.BICUBIC)


def fx_flicker(img, alpha, mask, p, seed, gain=1.8, lift=70, strength=255, low=90):
    """Des flammes qui vacillent : deux bruits doux qui montent vers la pointe, a leurs couleurs."""
    layers = []
    for k, (cells, laps) in enumerate(((10, 2), (22, 3))):
        tall = periodic_noise(seed + k, cells, SIZE)
        off = int(round(p * laps * SIZE)) % SIZE
        layers.append(tall.crop((0, SIZE + off, SIZE, 2 * SIZE + off)))
    window = ImageChops.multiply(layers[0], layers[1].point(lambda v: min(255, v + 90)))
    window = window.point(lambda v: 0 if v < low else min(255, int((v - low) * strength / (200 - low))))
    return compose(lighten(img, gain, lift), mul(window, mask, alpha))


def fx_embers(img, alpha, root, p, count, seed, color=(255, 200, 90), travel=46):
    """Des braises qui montent : des points qui naissent dans l'aile et filent vers la pointe en s'eteignant."""
    rnd = random.Random(seed)
    pa = alpha.load()
    frame = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(frame)
    rx, ry = root[0] * SIZE, root[1] * SIZE
    embers = []
    for _ in range(20000):
        if len(embers) >= count:
            break
        x, y = rnd.randrange(8, SIZE - 8), rnd.randrange(8, SIZE - 8)
        if pa[x, y] >= 200:
            embers.append((x, y, rnd.random(), rnd.choice((1, 1, 2))))
    for x, y, phase, r in embers:
        t = (p * 2 + phase) % 1.0
        dx, dy = x - rx, y - ry
        n = max(1.0, math.hypot(dx, dy))
        cx, cy = x + dx / n * travel * t, y + dy / n * travel * t
        a = int(255 * math.sin(math.pi * t) ** 1.5)
        if a < 8:
            continue
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (a,))
    return bloom(frame.convert('RGBa').filter(ImageFilter.GaussianBlur(0.7)).convert('RGBA'), alpha, 2.5, 1.0)


def fx_flash(img, alpha, mask, p, seed, flashes=3, strength=235):
    """Des eclairs qui claquent, en double coup : les traits clairs s'allument d'un coup et rayonnent."""
    rnd = random.Random(seed)
    times = sorted(rnd.random() for _ in range(flashes))
    a = 0.0
    for t in times:
        d = (p - t) % 1.0
        if d < 0.05:
            a = max(a, 1.0 - d / 0.05)
        elif 0.08 <= d < 0.13:
            a = max(a, 0.7 * (1.0 - (d - 0.08) / 0.05))
    level = int(strength * (0.1 + 0.9 * a))
    lit = compose(tint(lighten(img, 2.0, 80), (225, 240, 255), 0.5), mul(Image.new('L', (SIZE, SIZE), level), mask, alpha))
    return bloom(lit, alpha, 2.0 + 3.0 * a, 0.4 + 0.6 * a)


def fx_hueflow(img, alpha, mask, dist, p, shift=50, width=0.3, strength=235):
    """Les couleurs qui ondoient : une vague de teinte qui court le long des voiles."""
    b = band(dist, p * 1.4 - 0.2, width, strength)
    h, s, v = img.convert('RGB').convert('HSV').split()
    h2 = h.point(lambda x: (x + int(shift * 255 / 360)) % 256)
    shifted = Image.merge('HSV', (h2, s, v.point(lambda x: min(255, int(x * 1.35 + 30))))).convert('RGB')
    return compose(shifted, mul(b, mask, alpha))


def over(*frames):
    out = frames[0]
    for f in frames[1:]:
        out = Image.alpha_composite(out, f)
    return out


# ================================================================ les recettes, une par apparence

def recipe(name, img):
    alpha, h, s, v = masks(img)
    root = ROOTS[name]
    dist = distance_field(root)
    diag = along_field(-35)
    bright = channel_curve(v, 150, 235)
    colorful = mul(channel_curve(s, 90, 200), channel_curve(v, 110, 200))
    seed = sum(map(ord, name))

    def done(effect):
        return lambda p: clip(effect(p), alpha)

    if name in ('prismatiques', 'rubis'):
        color = (255, 255, 255) if name == 'prismatiques' else (255, 205, 215)
        spots = hotspots(img, alpha, v, 18, seed=seed, min_v=225)
        mask = broaden(bright, alpha, 0.25)
        # une aile deja claire blanchit vite : un reflet plus doux que sur les autres
        return done(lambda p: over(bloom(fx_sweep(img, alpha, mask, diag, p, color=color,
                                                  strength=145 if name == 'prismatiques' else 175), alpha),
                                   fx_twinkle(img, spots, (p * 2) % 1.0)))
    if name == 'aurore':
        spots = hotspots(img, alpha, v, 12, seed=7, min_v=215)
        mask = broaden(colorful, alpha, 0.3)
        return done(lambda p: over(bloom(over(fx_hueflow(img, alpha, mask, dist, p, strength=200),
                                              fx_hueflow(img, alpha, mask, dist, (p + 0.5) % 1.0, shift=-40,
                                                         strength=140)), alpha, 2.0, 0.6),
                                   fx_twinkle(img, spots, p, radius=(3, 6))))
    if name == 'pierres_precieuses':
        gems = hotspots(img, alpha, v, 24, seed=11, min_v=205, spread=14)
        # l'or du filigrane : une teinte vers quarante degres, assez saturee
        gold = broaden(mul(channel_curve(s, 90, 180), band(h, 40 / 360.0, 0.05, 255), alpha), alpha, 0.2)
        return done(lambda p: over(bloom(fx_sweep(img, alpha, gold, diag, p, color=(255, 230, 150), amount=0.35,
                                                  strength=190), alpha),
                                   fx_twinkle(img, gems, p)))
    if name == 'braise':
        flames = mul(channel_curve(v, 150, 230), channel_curve(s, 100, 200))
        return done(lambda p: over(bloom(over(fx_flicker(img, alpha, flames, p, seed=3),
                                              fx_pulse(img, alpha, broaden(flames, alpha, 0.35), dist, p,
                                                       width=0.25, strength=200)), alpha, 3.0, 0.9),
                                   fx_embers(img, alpha, root, p, 26, seed=4)))
    if name == 'tempete':
        # les eclairs : les traits clairs et peu satures (blancs, bleu pale)
        bolts = mul(channel_curve(v, 200, 250), s.point(lambda x: 255 if x < 90 else max(0, 255 - (x - 90) * 4)))
        sparks = hotspots(img, alpha, v, 14, seed=6, min_v=225, spread=12)
        mask = broaden(bright, alpha, 0.25)
        return done(lambda p: over(bloom(fx_sweep(img, alpha, mask, diag, p, color=(200, 225, 255), amount=0.25,
                                                  strength=150), alpha, 2.0, 0.6),
                                   fx_flash(img, alpha, bolts, p, seed=5),
                                   fx_twinkle(img, sparks, (p * 3) % 1.0, radius=(2, 5), color=(215, 235, 255))))
    if name == 'emeraude':
        glow = broaden(mul(channel_curve(v, 130, 220), channel_curve(s, 100, 200)), alpha, 0.35)
        spots = hotspots(img, alpha, v, 14, seed=13, min_v=210)
        # peu de blanc dans la vague : elle reste verte, meme la nuit
        return done(lambda p: over(bloom(fx_pulse(img, alpha, glow, dist, p, width=0.22, lift=25, strength=215), alpha),
                                   fx_twinkle(img, spots, p, radius=(3, 6))))
    if name == 'obscures':
        veins = broaden(mul(channel_curve(v, 95, 190), channel_curve(s, 120, 210)), alpha, 0.15)
        # deux ondes a la fois, a une demi-boucle l'une de l'autre : un coeur qui bat dans les veines
        return done(lambda p: bloom(over(fx_pulse(img, alpha, veins, dist, p, width=0.16, gain=1.9, strength=250),
                                         fx_pulse(img, alpha, veins, dist, (p + 0.5) % 1.0, width=0.16, gain=1.9,
                                                  strength=250)), alpha, 2.5, 0.9))
    if name == 'givre':
        spots = hotspots(img, alpha, v, 24, seed=17, min_v=225, spread=9)
        mask = broaden(bright, alpha, 0.25)
        return done(lambda p: over(bloom(fx_sweep(img, alpha, mask, diag, p, color=(235, 245, 255), amount=0.3,
                                                  strength=145), alpha, 2.0, 0.7),
                                   fx_twinkle(img, spots, (p * 2) % 1.0, radius=(2, 6), color=(240, 250, 255))))
    if name == 'papillon':
        pearl = broaden(mul(channel_curve(v, 170, 240), s.point(lambda x: 255 if x < 70 else max(0, 255 - (x - 70) * 3))),
                        alpha, 0.3)
        dots = hotspots(img, alpha, v, 12, seed=19, min_v=235, spread=18)
        return done(lambda p: over(fx_hueflow(img, alpha, broaden(colorful, alpha, 0.2), dist, p, shift=25, strength=150),
                                   bloom(fx_sweep(img, alpha, pearl, along_field(-60), p, color=(255, 240, 250),
                                                  amount=0.3, width=0.18, strength=180), alpha, 2.0, 0.6),
                                   fx_twinkle(img, dots, p, radius=(2, 5))))
    if name == 'souverain_astral':
        gold = broaden(mul(channel_curve(v, 140, 230), channel_curve(s, 80, 180)), alpha, 0.15)
        stars = hotspots(img, alpha, v, 28, seed=23, min_v=200, spread=8)
        return done(lambda p: over(fx_hueflow(img, alpha, colorful, dist, (p + 0.3) % 1.0, shift=30, strength=150),
                                   bloom(fx_pulse(img, alpha, gold, dist, p, width=0.16, strength=245), alpha),
                                   fx_twinkle(img, stars, (p * 2) % 1.0, radius=(2, 6))))
    raise SystemExit('apparence inconnue : ' + name)


# ================================================================ sortie

def build(name, preview):
    img = load(name)
    effect = recipe(name, img)
    frames = [effect(i / FRAMES) for i in range(FRAMES)]
    strip = Image.new('RGBA', (SIZE, SIZE * FRAMES), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        strip.paste(frame, (0, SIZE * i))
    os.makedirs(OUT, exist_ok=True)
    target = os.path.join(OUT, name + '.png')
    strip.save(target, optimize=True)
    print('%-20s %d images -> %s (%d Ko)' % (name, FRAMES, target, os.path.getsize(target) // 1024))
    if preview:
        os.makedirs(PREVIEW, exist_ok=True)
        base = Image.new('RGBA', (SIZE, SIZE), (30, 34, 48, 255))
        base.alpha_composite(img)
        gif = []
        # trois fois la boucle, fondue comme au jeu (WingAnims : un tiers par tique, pondere par l'opacite)
        for k in range(FRAMES * 3):
            a = frames[k % FRAMES].convert('RGBa')
            b = frames[(k + 1) % FRAMES].convert('RGBa')
            for t in (0.0, 1 / 3, 2 / 3):
                mix = Image.blend(a, b, t).convert('RGBA')
                shot = base.copy()
                # la lumiere ajoutee, comme la passe emissive du jeu
                shot = Image.alpha_composite(shot, mix)
                gif.append(shot.convert('RGB').convert('P', palette=Image.ADAPTIVE, colors=255))
        gif[0].save(os.path.join(PREVIEW, name + '.gif'), save_all=True, append_images=gif[1:],
                    duration=50, loop=0, optimize=True)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    preview = '--apercu' in sys.argv
    names = args or list(ROOTS)
    for name in names:
        build(name, preview)


if __name__ == '__main__':
    main()
