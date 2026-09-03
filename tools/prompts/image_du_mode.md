# L'image du Mode Arcencium

À donner à un générateur d'images (ChatGPT, Midjourney, etc.). Deux formats :
**l'icône carrée** est celle qui compte — c'est la vignette du profil CurseForge,
vue en petit. La bannière est facultative.

Le résultat va dans `tools/pack/icon.png` (1024 × 1024) : `tools/export_modpack.py`
le place alors à la racine du pack. Dans CurseForge, l'icône se règle à la main :
profil → les trois points → *Change Image*.

---

## Ce que l'image doit raconter

Une partie de 90 minutes sur un monde neuf : trois **sanctuaires** à conquérir,
un **Arc-en-ciel** qui se lève à la fin au-dessus de l'arène du boss, et une
**Marée Prismatique** qui referme le monde pendant qu'on joue. La matière du
mode est l'**Arcencium**, un minerai aux couleurs du prisme.

**Aucun être vivant.** Pas de joueur, pas de monstre, pas d'animal, pas de
villageois, pas de silhouette, pas de main tenant un objet. Le décor seul.

---

## Prompt — icône carrée (1024 × 1024)

> Minecraft-style voxel landscape, no characters, no creatures, no living
> beings of any kind. Isometric three-quarter view of a stepped stone pyramid
> made of pale grey-beige cubic blocks, standing alone on a snowy plain. At its
> summit, a single floating crystal anchor glowing with prismatic rainbow light,
> casting a thin beam into the sky. A wide rainbow arch spans the sky behind the
> pyramid, made of seven clean translucent bands, rising from the horizon.
> Scattered on the ground, small cubic ore blocks with rainbow-coloured crystal
> facets. Far in the background, a soft wall of violet prismatic mist closing in
> around the scene like a distant fog barrier. Dawn sky, deep blue to warm
> amber, a few blocky clouds. Strictly cubic geometry, 16x16 pixel-art block
> textures, sharp voxel edges, no smooth or rounded shapes, no realistic
> photography, no depth-of-field blur. Clean readable silhouette, strong
> contrast, centred composition with generous empty space, meant to be legible
> as a small square icon. No text, no logo, no watermark, no user interface.

**Version française**, si l'outil préfère :

> Paysage voxel dans le style de Minecraft, sans aucun personnage ni créature
> ni être vivant. Vue isométrique trois quarts : une pyramide à degrés en blocs
> cubiques gris pâle, seule sur une plaine enneigée. À son sommet, une ancre de
> cristal flottante qui rayonne d'une lumière arc-en-ciel et lance un fin
> faisceau vers le ciel. Derrière, un grand arc-en-ciel à sept bandes nettes
> traverse le ciel d'un horizon à l'autre. Au sol, quelques blocs de minerai aux
> facettes arc-en-ciel. Au loin, un mur de brume violette qui referme la scène.
> Ciel d'aube, bleu profond virant à l'ambre, quelques nuages cubiques.
> Géométrie strictement cubique, textures pixel 16×16, arêtes franches, aucun
> rendu photoréaliste, aucun flou. Silhouette lisible, fort contraste,
> composition centrée, pensée pour une petite icône carrée. Aucun texte, aucun
> logo, aucune interface.

---

## Prompt — bannière large (1920 × 1080), facultatif

Le même monde, mais en paysage : le regard part des trois sanctuaires alignés au
loin et monte vers l'Arc-en-ciel.

> Minecraft-style voxel landscape banner, no characters, no creatures, no living
> beings. Wide panoramic view at dawn over a snowy plain: three distant stepped
> stone pyramids spread across the horizon, each crowned by a small glowing
> prismatic crystal beaming light upward. A huge rainbow arch rises above the
> central one, seven translucent bands, and beneath it a dark frozen fortress of
> pale blue ice bricks. On the left, a wall of violet prismatic fog rolls over
> the land, swallowing the terrain. Foreground: a broken cliff of stone with
> exposed rainbow-crystal ore veins and a narrow crack splitting the ground.
> Strictly cubic voxel geometry, 16x16 pixel-art textures, sharp edges, no
> photorealism, no blur. Cinematic wide composition, empty sky space on the
> right for a title. No text, no logo, no watermark.

---

## Ce qui fait rater l'image

- **Un personnage, même de dos.** Le mode se joue à plusieurs et se présente par
  son monde, pas par un héros.
- **Le rendu réaliste.** Un paysage « fantasy » lisse ne se reconnaît pas comme
  du Minecraft. Il faut des cubes visibles et des textures pixel.
- **Le détail partout.** L'icône se voit en 64 pixels de côté dans la liste des
  profils : une composition chargée devient une bouillie. Une pyramide, un arc,
  un ciel.
- **Le texte incrusté.** Les générateurs écrivent mal, et CurseForge affiche
  déjà le nom du profil sous la vignette.
