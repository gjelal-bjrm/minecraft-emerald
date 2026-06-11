from PIL import Image
import colorsys
import os

NUM_FRAMES = 12

# Décalage de teinte de base (0.0 = couleurs originales)
# 0.73 → thème vert/émeraude
# 0.5  → thème orange/feu
# 0.0  → thème bleu/violet original
BASE_HUE_SHIFT = 0.73

def rgb_to_hsv(r, g, b):
    return colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)

def hsv_to_rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return int(r * 255), int(g * 255), int(b * 255)

base_path = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "src", "main", "resources", "assets", "emeraldweapons",
    "textures", "item", "fulgurite.png"
)
base = Image.open(base_path).convert('RGBA')
w, h = base.size
print(f"Texture chargee : {w}x{h}")

base_pixels = list(base.getdata())

# 'bg'      → fond transparent → rendu transparent
# 'dark'    → corps sombre (garde, manche) → inchangé
# 'colored' → lame colorée → animée
classified = []

for idx, (r, g, b, a) in enumerate(base_pixels):
    x = idx % w
    y = idx // w

    if a < 10 or (r > 240 and g > 240 and b > 240):
        classified.append(('bg', 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0))
        continue

    if max(r, g, b) < 80:
        classified.append(('dark', r, g, b, a, 0.0, 0.0, 0.0, 0.0))
        continue

    hv, sv, vv = rgb_to_hsv(r, g, b)

    if sv < 0.18:
        classified.append(('gray', r, g, b, a, 0.0, 0.0, 0.0, 0.0))
        continue

    blade_pos = (x - y + h) / (w + h)
    classified.append(('colored', r, g, b, a, hv, sv, vv, blade_pos))

colored_count = sum(1 for c in classified if c[0] == 'colored')
print(f"Pixels animes : {colored_count} / {w*h}")
print(f"Decalage de teinte de base : {BASE_HUE_SHIFT} ({int(BASE_HUE_SHIFT * 360)}°)")

out = Image.new('RGBA', (w, h * NUM_FRAMES), (0, 0, 0, 0))

for frame_idx in range(NUM_FRAMES):
    frame_offset = frame_idx / NUM_FRAMES
    new_pixels = []

    for item in classified:
        kind = item[0]
        if kind in ('bg', 'dark', 'gray'):
            new_pixels.append((item[1], item[2], item[3], item[4]))
        else:
            _, r, g, b, a, hv, sv, vv, blade_pos = item
            hue_shift = (blade_pos * 0.45 + frame_offset) % 1.0
            new_h = (hv + BASE_HUE_SHIFT + hue_shift) % 1.0
            nr, ng, nb = hsv_to_rgb(new_h, sv, vv)
            new_pixels.append((nr, ng, nb, a))

    frame = Image.new('RGBA', (w, h))
    frame.putdata(new_pixels)
    out.paste(frame, (0, frame_idx * h))
    print(f"  Frame {frame_idx + 1}/{NUM_FRAMES} generee")

out.save(base_path)
print(f"\nTexture animee sauvegardee : {base_path}")
print(f"Dimensions finales : {w}x{h * NUM_FRAMES} ({NUM_FRAMES} frames de {w}x{h})")
