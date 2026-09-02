# Prompts ChatGPT -- les ailes de specialisation

Ce fichier contient tout ce qu'il faut donner a ChatGPT (generation d'images)
pour obtenir les textures d'ailes du mode. Le mod les affichera comme des
plans textures attaches au dos du joueur, qui battent lentement ; leur
taille grandit avec le palier (+1 a +15), donc **une seule image par
apparence suffit** : je l'agrandis moi-meme.

## 1. Le cahier des charges commun (a coller EN TETE de chaque prompt)

```
Create a game asset: a SINGLE RIGHT WING for a character, seen from BEHIND
the character (the wing as it appears on a character's back, viewed from
behind). Only the wing: no character, no body, no shoulder, no shadow, no
ground, no background, no text, no watermark, no frame.

Format: PNG, 1024 x 1024 pixels, fully TRANSPARENT background (alpha), crisp
clean edges (no soft halo bleeding into the transparency).

Layout: the wing's ROOT (the point where it attaches to the back) must be in
the BOTTOM-LEFT area of the image, at about 12% from the left edge and 78%
from the top. From that root the wing spreads UP and to the RIGHT, filling
about 85% of the canvas. The wing tip reaches the top-right region. Keep a
small transparent margin all around; nothing touches the image border.

View: flat, orthographic, like a 2D game sprite (no perspective, no tilt).
Lighting: soft, from the top-left, consistent. Style: high quality 2D game
art with clean shapes, rich detail and glossy highlights, in the spirit of
the specialist wings of the MMORPG NosTale -- NOT a photo, NOT 3D render,
NOT pixel art, NOT a sketch.
```

Je m'occupe du miroir pour l'aile gauche : ne demandez jamais les deux
ailes dans la meme image.

## 2. Un prompt par apparence (a coller APRES le bloc commun)

Pour chaque image obtenue, enregistrez-la sous le nom indique dans
`tools/wings_input/` (je cree le dossier), telle quelle.

### Prismatiques -- les ailes de base, `wing_prismatiques.png`

```
Subject: a wing made of PALE PRISMATIC CRYSTAL. Structure: a fan of long
crystal blades radiating from the root, like spread feathers -- the longest
blades point up and out, the shorter ones lower; three overlapping rows
(long blades at the back, medium in the middle, short bright blades near the
root). Each blade is a faceted crystal shard: translucent, silver-white with
faint iridescent hints of violet, cyan and gold catching the light (subtle,
not a rainbow), a bright edge and a soft inner glow. Elegant, impressive,
sober colors. Mostly white and silver.
```

### Rubis, `wing_rubis.png`

```
Subject: the same fan-of-crystal-blades wing, but made of deep RUBY crystal:
saturated crimson red blades with bright pink highlights, dark garnet cores,
sharp faceted edges and a warm red glow along the edges. Luxurious, rare.
```

### Aurore, `wing_aurore.png`

```
Subject: a wing made of curtains of AURORA LIGHT: long soft ribbons of
mint-green and pale cyan light with hints of violet, rippling like the
northern lights, layered in three rows from the root, translucent, glowing,
with tiny bright motes floating along the ribbons. Ethereal, luminous.
```

### Pierres precieuses, `wing_pierres_precieuses.png`

```
Subject: a wing made of large polished OVAL GEMSTONES arranged like feathers:
three overlapping rows of glossy gem "feathers" radiating from the root,
each gem a different color in rainbow order along the wing (ruby red,
amber orange, topaz yellow, emerald green, sapphire blue, amethyst purple),
each with a dark outline, a bright specular highlight and a soft glow.
Opulent, jewelled, like a crown-jeweller's wing.
```

### Braise, `wing_braise.png`

```
Subject: a wing of living EMBERS and FLAME: dark charred feather shapes
whose edges glow orange and gold, cracks of fire running along each
feather, wisps of flame and rising embers at the tips, three layered rows
from the root. Warm, dangerous, glowing from within.
```

### Tempete, `wing_tempete.png`

```
Subject: a wing of STORM: steel-blue and slate-grey crystal blades in a fan,
with small white electric arcs crawling between the blades, a cold
blue-white glow at the edges, faint rain streaks. Sharp, tense, electric.
```

### Emeraude, `wing_emeraude.png`

```
Subject: the fan-of-crystal-blades wing made of EMERALD: deep green faceted
blades with bright mint highlights, dark green cores, a soft green glow at
the edges and tiny green sparkles. Regal, precious.
```

### Obscures, `wing_obscures.png`

```
Subject: a DARK DRAGON wing: a short bony arm from the root to a wrist,
then five long clawed finger bones spreading from the wrist (from straight
up to down-and-out), with a leathery membrane stretched between the fingers,
scalloped along its trailing edge. Colors: near-black membrane with deep
violet and dark crimson undertones, blackened bones, red-violet glow in the
membrane veins, small dark wisps rising from it. Menacing but beautiful,
detailed, NOT a cartoon bat.
```

### Givre, `wing_givre.png`

```
Subject: a wing of ICE: translucent pale-blue and white crystal blades in a
fan, frosted edges, hoarfrost patterns on the surface, a cold white glow,
tiny ice sparkles drifting off the tips. Pure, crystalline, cold.
```

### Papillon, `wing_papillon.png`

```
Subject: a BUTTERFLY wing pair for one side: a large upper forewing pointing
up-and-out and a smaller lower hindwing pointing down-and-out, both
attached at the root. Black rim with a row of white dots along the margin,
cream-white cells separated by black veins, delicate scales texture. Like
a swallowtail or a black-and-white nymphalid. Elegant, precise.
(Optional second version: iridescent morpho blue cells instead of cream.)
```

## 3. Si ChatGPT se trompe

- **Deux ailes ou un personnage** : redemander « ONE right wing only, no
  character ».
- **Fond non transparent** : demander « transparent PNG background, alpha
  channel » ; sinon je peux detourer un fond uni (vert ou magenta), dites-le
  moi.
- **Aile qui touche le bord** : demander « keep a transparent margin ».
- **Perspective / 3D** : redire « flat orthographic 2D sprite ».

## 4. Ce que j'en fais

Je decoupe chaque image, je la miroir pour l'aile gauche, je la pose sur
deux ou trois plans articules dans le dos du joueur (battement lent), je
l'agrandis de +1 a +15, et j'ajoute par-dessus les animations de +16 a +20
(motes, anneau au sol, trainee, arcs, onde) avec nos propres particules.
