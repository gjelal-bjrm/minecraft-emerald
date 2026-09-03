# L'image de la page d'accueil

## L'idee

Une seule image doit dire ce qu'est le mode : **une course de quatre-vingt-dix
minutes vers une forteresse de glace, sous un ciel qui change**. On la
construit donc en trois plans, du fond vers l'avant :

1. **Le fond** : la Prison Givree, l'arene du boss -- une masse de glace
   compacte et de pierre sombre, contreforts, chaines, tours trapues, a demi
   ensevelie dans la neige. Elle occupe la DROITE de l'image et monte haut :
   c'est le but de la partie, il doit ecraser le reste.
2. **Le milieu** : la plaine enneigee et, tres loin sur l'horizon, **trois
   silhouettes de sanctuaires** -- les trois ancres a prendre avant elle. Elles
   sont petites, presque des reperes de carte : on les lit sans les regarder.
3. **L'avant** : ce qui appartient a nous seuls -- de **fins rais de lumiere
   prismatique qui sortent du sol** (l'Aurore revelant les veines d'Arcencium),
   et un **arc-en-ciel bas et pale** derriere la forteresse, discret, pas un
   arc de dessin anime.

**Le centre reste vide.** Les boutons du menu s'y posent : ciel, neige, rien a
regarder. Le coin en bas a gauche reste calme aussi (le nom du pack s'y ecrit).

**Aucun etre vivant** : ni joueur, ni monstre, ni animal, ni silhouette
humaine, ni oiseau.

**Pas de texte dans l'image.** Les modeles ecrivent mal, et le titre merite la
vraie typographie de Minecraft : on genere l'image nue et l'on pose « MODE
ARCENCIUM » par-dessus, dans le logo du menu.

---

## Le prompt (francais)

> Paysage Minecraft en blocs, rendu comme une capture de jeu avec shaders
> realistes, format 16:9, sans aucun personnage ni creature.
>
> A droite, une colossale forteresse-prison de glace compacte bleue et de
> pierre sombre : contreforts massifs, tours carrees trapues, arcs brises,
> longues chaines de fer pendantes, portes gelees. Elle est a demi ensevelie
> dans une plaine de neige et domine tout le cadre par sa masse.
>
> Au centre et a gauche, une etendue de neige vide et un ciel degage de fin de
> journee, bleu froid virant a l'or pale pres de l'horizon : cette moitie doit
> rester calme et peu chargee.
>
> Sur l'horizon lointain, trois petites silhouettes de forteresses carrees a
> tours d'angle, minuscules, a peine visibles dans la brume de distance.
>
> Du sol enneige s'elevent quelques fins faisceaux de lumiere prismatique --
> magenta, or, turquoise, violet -- verticaux, translucides, comme des colonnes
> de poussiere lumineuse qui percent la neige.
>
> Derriere la forteresse, un arc-en-ciel large, bas et pale, delave dans le
> ciel, discret.
>
> Palette froide dominante : blanc, bleu glace, gris ardoise, avec des accents
> prismatiques satures uniquement dans les faisceaux et l'arc.
> Lumiere rasante, ombres longues, ambiance de fin de monde paisible et
> monumentale. Geometrie cubique de Minecraft assumee, textures de blocs
> visibles, aucune surface lisse ou organique.
>
> Sans texte, sans logo, sans interface, sans personnage, sans animal, sans
> silhouette humaine.

## The prompt (english, souvent mieux rendu)

> Minecraft-style blocky landscape, rendered like a shader-enhanced in-game
> screenshot, 16:9, absolutely no characters or creatures.
>
> On the right, a colossal frozen prison-fortress of packed blue ice and dark
> stone: massive buttresses, squat square towers, broken arches, long hanging
> iron chains, frozen gates, half-buried in a snowfield, dominating the frame.
>
> Center and left: empty snow plain and a clear late-day sky, cold blue fading
> to pale gold near the horizon. This half must stay calm and uncluttered.
>
> On the far horizon, three tiny square fortress silhouettes with corner
> towers, barely visible through distance haze.
>
> Thin vertical beams of prismatic light -- magenta, gold, turquoise, violet --
> rise out of the snowy ground, translucent, like glowing dust columns.
>
> Behind the fortress, a wide, low, pale rainbow, washed out and subtle.
>
> Cold palette: white, ice blue, slate grey, with saturated prismatic accents
> only in the beams and the rainbow. Low raking light, long shadows,
> monumental and peaceful end-of-world atmosphere. Deliberately cubic Minecraft
> geometry, visible block textures, no smooth organic surfaces.
>
> No text, no logo, no UI, no characters, no animals, no human silhouettes.

---

## Ce qu'il faut demander au generateur

- **Format** : 16:9. L'image du menu fait **1920 x 1080**. Si le modele ne
  donne que du carre (1024 x 1024), demander « wide 16:9 landscape » et
  recadrer ensuite -- mieux vaut generer large.
- **Trois essais** valent mieux qu'un : c'est la composition qui rate le plus
  souvent (forteresse au centre, ciel encombre).
- **Ce qui doit faire echouer un essai** : un personnage, meme minuscule ; du
  texte ; un arc-en-ciel qui coupe l'image en deux ; un centre charge.

## Ensuite

M'envoyer le fichier. Je m'occupe du reste :

- recadrage exact en 1920 x 1080 et pose dans
  `packmenu/resources/assets/packmenu/textures/gui/background.png` ;
- le titre **MODE ARCENCIUM** compose par-dessus, dans le logo du menu
  (`logo.png`, 300 x 300) ;
- la meme image, recadree carre, en icone du profil (`icon.png`) ;
- l'export du pack, pour que tout voyage sur l'autre ordinateur.
