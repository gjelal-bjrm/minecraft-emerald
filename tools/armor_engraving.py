"""
La GRAVURE de l'armure d'Arcencium : les jointures des plaques, et rien d'autre.

Derivee de la texture portee elle-meme, pas dessinee a cote : la ou la
luminance saute d'un texel a son voisin, il y a un bord de plaque. On garde ces
bords en blanc sur fond transparent, a la resolution de la texture (256 x 128,
soit quatre texels par unite du modele). Le calque d'amelioration les colore
et les allume selon le cran : c'est ce qui fait qu'une piece +7 porte des
lignes violettes la ou une piece +0 n'a que du metal.

Sortie : textures/models/armor/arcencium_engraving_{1,2}.png
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ARMOR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons",
                     "textures", "models", "armor")
THRESHOLD = 16          # saut de luminance (0-255) qui fait un bord


def luminance(px):
    r, g, b, a = px
    return (0.299 * r + 0.587 * g + 0.114 * b) if a > 0 else -1


def engrave(name_in, name_out):
    src = Image.open(os.path.join(ARMOR, name_in)).convert("RGBA")
    w, h = src.size
    lum = [[luminance(src.getpixel((x, y))) for x in range(w)] for y in range(h)]
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    count = 0
    for y in range(h):
        for x in range(w):
            here = lum[y][x]
            if here < 0:
                continue                      # hors de la piece : transparent
            edge = 0.0
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h:
                    other = lum[ny][nx]
                    if other < 0:
                        edge = max(edge, 255.0)   # le bord exterieur de la piece
                    else:
                        edge = max(edge, abs(here - other))
            if edge >= THRESHOLD:
                # plus le saut est net, plus la ligne est pleine ; jamais moins qu'un tiers
                a = int(min(255, 90 + (edge - THRESHOLD) * 2.2))
                out.putpixel((x, y), (255, 255, 255, a))
                count += 1
    out.save(os.path.join(ARMOR, name_out))
    print(f"{name_out} : {count} texels graves sur {w}x{h}")


if __name__ == "__main__":
    engrave("arcencium_layer_1.png", "arcencium_engraving_1.png")
    engrave("arcencium_layer_2.png", "arcencium_engraving_2.png")
