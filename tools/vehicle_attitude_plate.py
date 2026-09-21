"""Planche de controle de l'equilibre des vehicules (VehicleAttitude, 21 sept. 2026).

Dessine les vehicules cuits (.bin + atlas) inclines EXACTEMENT comme JakVehicleRenderer
les incline : translation au centre de masse (0, 0, cmZ), XP(-tangage), ZP(roulis),
translation inverse -- les matrices de JOML, rotations a droite (XP d'un angle a envoie
y vers z : le nez plonge ; ZP envoie x vers y : la gauche monte). On y verifie le sens :
- vu de derriere, un virage a gauche couche la GAUCHE du vehicule (a gauche de l'image) ;
- vu de profil (gauche du vehicule face a nous, avant a droite), un mur de face fait
  plonger le NEZ (a droite de l'image) ;
- un choc de flanc sous le centre de masse, un souffle sur le flanc gauche.

Les angles sont ceux du banc hors jeu (le plus bas mesure, ou l'angle tenu).

    python tools/vehicle_attitude_plate.py            -> build/jak/vehicles/equilibre.png
"""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jak_vehicle as jv  # noqa: E402

# (modele, centre de masse z) : VehicleSpec.Balance
CM = {"cara": 0.0, "carb": 0.0, "carc": -1.0, "bikea": 0.6, "bikeb": 0.6, "bikec": 0.6}

# (titre, modele, vue, tangage en degres, roulis en degres)
PANELS = [
    ("car-a a plat, vue de derriere", "cara", "back", 0.0, 0.0),
    ("car-a, virage a gauche pilote : roulis -22,3 (gauche en bas)", "cara", "back", 0.0, -22.3),
    ("moto bike-a, virage a gauche pilote : roulis -39,8", "bikea", "back", 0.0, -39.8),
    ("car-a, choc de flanc : roulis +35 (gauche en haut)", "cara", "back", 0.0, 35.0),
    ("car-a a plat, de profil (avant a droite)", "cara", "side", 0.0, 0.0),
    ("car-a, mur de face : tangage -13,3 (nez en bas)", "cara", "side", -13.3, 0.0),
    ("car-c, centre de masse 1 m en arriere : tangage -13,3", "carc", "side", -13.3, 0.0),
    ("moto, mur de face : tangage -16,5", "bikea", "side", -16.5, 0.0),
]


def rot_x(p, a):
    c, s = math.cos(a), math.sin(a)
    x, y, z = p
    return (x, y * c - z * s, y * s + z * c)


def rot_z(p, a):
    c, s = math.cos(a), math.sin(a)
    x, y, z = p
    return (x * c - y * s, x * s + y * c, z)


def tilt(p, cm, pitch_deg, roll_deg):
    """Le point du modele comme le pose JakVehicleRenderer.PivotTilt (roulis d'abord, puis tangage)."""
    x, y, z = p
    q = (x, y, z - cm)
    q = rot_z(q, math.radians(roll_deg))
    q = rot_x(q, math.radians(-pitch_deg))
    return (q[0], q[1], q[2] + cm)


def tilted_copy(back, cm, pitch, roll):
    tris = []
    lo = [1e9] * 3
    hi = [-1e9] * 3
    for tri in back["tris"]:
        verts = []
        for v in tri["verts"]:
            p = tilt(v["pos"], cm, pitch, roll)
            for k in range(3):
                lo[k] = min(lo[k], p[k])
                hi[k] = max(hi[k], p[k])
            verts.append({"pos": p, "uv": v["uv"], "normal": v["normal"], "rgba": v["rgba"]})
        tris.append({"bone": tri["bone"], "flags": tri["flags"], "verts": verts})
    return {"atlas": back["atlas"], "min": tuple(lo), "max": tuple(hi), "bones": back["bones"], "tris": tris}


def mirrored_x(back):
    """Vu de derriere, la droite de l'image est la droite du vehicule (-x) : on retourne x."""
    tris = []
    for tri in back["tris"]:
        verts = [{"pos": (-v["pos"][0], v["pos"][1], -v["pos"][2]), "uv": v["uv"], "normal": v["normal"],
                  "rgba": v["rgba"]} for v in tri["verts"]]
        tris.append({"bone": tri["bone"], "flags": tri["flags"], "verts": verts})
    mn, mx = back["min"], back["max"]
    return {"atlas": back["atlas"], "min": (-mx[0], mn[1], -mx[2]), "max": (-mn[0], mx[1], -mn[2]),
            "bones": back["bones"], "tris": tris}


def main():
    atlas = Image.open(jv.ATLAS_PATH).convert("RGBA")
    cache = {}
    panels = []
    for title, model, view, pitch, roll in PANELS:
        if model not in cache:
            cache[model] = jv.read_bin(os.path.join(jv.BIN_DIR, "%s.bin" % model))
        back = tilted_copy(cache[model], CM[model], pitch, roll)
        if view == "back":
            # vue de derriere : on regarde vers +z ; x retourne pour que la gauche du vehicule soit a gauche
            image = jv.render_view(mirrored_x(back), atlas, ("", 0, 1, 2), 40)
        else:
            # profil : la gauche (+x) face a nous, l'avant (+z) a droite
            image = jv.render_view(back, atlas, ("", 2, 1, 0), 40)
        draw = ImageDraw.Draw(image)
        draw.rectangle([0, 0, image.width, 13], fill=(40, 44, 52))
        draw.text((4, 1), title, fill=(255, 255, 255))
        panels.append(image)
    cols = 4
    cell_w = max(p.width for p in panels)
    cell_h = max(p.height for p in panels)
    rows = (len(panels) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (cell_w + 8), rows * (cell_h + 8) + 20), (20, 22, 26))
    ImageDraw.Draw(sheet).text((4, 4), "Equilibre des vehicules (JakVehicleRenderer) : vues de derriere (gauche du"
                               " vehicule a gauche) et de profil (avant a droite)", fill=(255, 255, 0))
    for i, panel in enumerate(panels):
        x = (i % cols) * (cell_w + 8)
        y = 20 + (i // cols) * (cell_h + 8)
        sheet.paste(panel, (x + (cell_w - panel.width) // 2, y + (cell_h - panel.height)))
    os.makedirs(jv.OUT_DIR, exist_ok=True)
    out = os.path.join(jv.OUT_DIR, "equilibre.png")
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
