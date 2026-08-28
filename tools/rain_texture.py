"""Repeint la pluie du jeu aux couleurs de l'Arcencium.

Pourquoi remplacer la texture plutot que superposer des particules : la pluie
est dessinee par le moteur en blanc pur (setColor(1,1,1,alpha) dans
LevelRenderer.renderSnowAndRain), sans aucun point d'entree pour la teinter.
Des particules par-dessus l'averse grise se voient pour ce qu'elles sont --
« on voit clairement que ce n'est pas la pluie qui est coloree ». La seule
facon d'avoir une pluie REELLEMENT coloree est de fournir notre propre image.

Elle s'applique alors a toute pluie du monde. C'est sans consequence ici parce
que le mode coupe le cycle meteo vanilla (GameManager.setup) : il ne pleut plus
que quand la Nuit d'Arcencium le decide.

On garde la forme et l'alpha de l'original -- ce sont eux qui donnent le grain
et la vitesse de l'averse -- et on ne remplace que la couleur, par des bandes
verticales de teintes qui se decalent. Les filets tombent donc en colonnes de
couleurs differentes, ce qui se lit en mouvement.
"""

import colorsys
import io as _io
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                       "minecraft", "textures", "environment")

VANILLA_JAR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge",
                           "minecraft", "Install", "versions", "1.21.1", "1.21.1.jar")


def recolour(src, saturation, value_boost):
    """Colore une texture d'averse en conservant sa silhouette.

    La teinte suit la COLONNE, pas le pixel : une goutte occupe une colonne
    entiere, et lui donner une couleur par pixel la ferait scintiller au lieu
    de tomber. Une legere derive verticale suffit a eviter les bandes plates.
    """
    w, h = src.size
    px = src.load()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = out.load()
    for x in range(w):
        base = (x / w) * 0.85          # presque un tour de roue sur la largeur
        for y in range(h):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hue = (base + (y / h) * 0.12) % 1.0
            # la luminance d'origine porte le relief de la goutte : on la garde
            lum = (r * 299 + g * 587 + b * 114) / 255000.0
            rr, gg, bb = colorsys.hsv_to_rgb(hue, saturation,
                                             min(1.0, lum * value_boost))
            dst[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
    return out


def main():
    if not os.path.isfile(VANILLA_JAR):
        sys.exit("jar vanilla introuvable : %s" % VANILLA_JAR)
    os.makedirs(OUT_DIR, exist_ok=True)
    with zipfile.ZipFile(VANILLA_JAR) as z:
        for name, sat, boost in (("rain", 0.80, 1.15), ("snow", 0.55, 1.0)):
            src = Image.open(_io.BytesIO(
                z.read("assets/minecraft/textures/environment/%s.png" % name))
            ).convert("RGBA")
            out = recolour(src, sat, boost)
            path = os.path.join(OUT_DIR, "%s.png" % name)
            out.save(path)
            print("ecrit %s (%dx%d)" % (path, out.size[0], out.size[1]))


if __name__ == "__main__":
    main()
