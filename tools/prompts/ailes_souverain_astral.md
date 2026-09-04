# Les Ailes du Souverain Astral — trois propositions

Les ailes les plus puissantes du mode, et les plus dures a obtenir. Chaque
proposition donne l'APPARENCE, le BONUS, l'ANIMATION que le mod ajoute
lui-meme par-dessus la texture, et le PROMPT pour peindre l'aile.

Contraintes communes au prompt (les memes que pour les autres ailes, voir
`tools/wings_import.py`) : **une aile DROITE seule**, vue de dos, racine en bas
a gauche, pointe en haut a droite, fond **transparent ou uni** (noir pur), sans
personnage, sans corps, sans texte. Carre, 1024 x 1024 ou plus. Le joueur a
refait le Givre et les Pierres Precieuses avec **plusieurs branches** : on garde
ce langage -- des ailes a lobes multiples, pas une seule plume.

---

## 1. La Couronne d'Astres *(recommandee)*

**Apparence.** Une aile de nuit profonde, bleu-noir, dont chaque branche se
termine par une **etoile a quatre pointes** en or pale. Les branches sont
reliees par des **filaments de lumiere** comme les lignes d'une constellation ;
la racine porte une petite couronne d'or. Trois lobes : un grand, deux plus
courts, tous piquetes d'astres.

**Bonus** (a +15 et au-dela, comme les autres apparences) :
- +12 % de chance de critique, +20 % de degats critiques ;
- **la Constellation** : chaque critique allume une etoile (jusqu'a 5) ;
  a cinq, le prochain coup est un critique garanti qui frappe **tout autour**
  (rayon 3) et eteint les etoiles ;
- chute annulee, plane au maximum (voir WingsFlightClient), et le double saut
  laisse une trainee d'etoiles.

**Animation ajoutee par le mod.** Les etoiles des pointes **scintillent** une a
une (une lueur emissive qui passe de branche en branche, jamais deux a la
fois) ; quand la Constellation est pleine, les filaments s'allument en continu
et l'aile bat plus lentement, plus ample. En plane, une trainee de motes
prismatiques part de chaque pointe.

**Prompt.**
> A single right wing, seen from behind, root at the bottom-left, tip at the
> top-right, isolated on a pure black background, no body, no character, no
> text. Fantasy game asset, painted style with crisp edges. The wing is deep
> midnight blue fading to black, made of three long feathered lobes; every
> lobe ends in a small four-pointed golden star, and thin lines of pale gold
> light connect the stars like a constellation. A small golden crown sits at
> the root. Subtle nebula glow inside the feathers, violet and teal. Square
> image, high detail, symmetrical lighting.

---

## 2. Le Firmament Brise

**Apparence.** Une aile de **verre astral fendu** : des eclats de ciel nocturne
tenus ensemble par des **fissures d'or fondu**, comme un vitrail qu'on aurait
brise et ressoude. Derriere chaque eclat, un fragment de ciel different -- une
lune, un soleil noir, une pluie d'etoiles. Quatre branches inegales, tranchantes.

**Bonus** :
- +10 % de degats, +8 % de chance de critique ;
- **l'Eclat** : quand le porteur passe sous 30 % de vie, l'aile se brise --
  invulnerabilite 2 s, onde de choc (rayon 5, repousse, 6 degats), puis les
  eclats se recollent pendant 60 s (bonus inactif le temps de la reforme) ;
- chute annulee, plane au maximum.

**Animation ajoutee par le mod.** Les fissures d'or **coulent** : une lueur
emissive parcourt les fentes en boucle lente, comme du metal qui circule.
A l'Eclat, les branches s'ecartent (rotation de chaque lobe, 10 ticks) puis
reviennent ; pendant la reforme, l'aile est rendue a moitie transparente.

**Prompt.**
> A single right wing, seen from behind, root at the bottom-left, tip at the
> top-right, isolated on a pure black background, no body, no character, no
> text. Fantasy game asset, painted style, crisp edges. The wing is made of
> shards of night sky held together by cracks of molten gold, like a
> shattered and re-fused stained-glass window; four uneven sharp blades.
> Inside each shard a different sky: a crescent moon, a black sun, a rain of
> stars, a violet nebula. Gold veins glow. Square image, high detail.

---

## 3. L'Aurore Souveraine

**Apparence.** Une aile faite de **rideaux d'aurore boreale** : des voiles
verticaux verts, violets et roses qui pendent d'une armature d'or blanc, cinq
branches longues et souples, translucides, avec des **eclats d'Arcencium** a
chaque jointure. C'est l'aile la plus « lumiere » des trois -- elle se rend en
emissif plein.

**Bonus** :
- +8 % de resistance elementaire a tous les elements, +10 % de degats
  elementaires ;
- **le Rideau** : les allies dans un rayon de 6 blocs recoivent Regeneration I
  et +5 % de resistance ; les ennemis qui entrent dans le rideau sont
  ralentis 20 % ;
- **la Meteo docile** : les meteos agressives (Nuit, Meteores, Dechirure,
  Orage) ne ciblent plus le porteur pour leurs eclairs, seulement autour.

**Animation ajoutee par le mod.** Les voiles **ondulent** : la texture est
rendue en deux bandes decalees dont l'opacite suit une sinusoide lente, et la
teinte glisse du vert au rose sur dix secondes (la meme rotation de teinte que
le +10 prismatique). En plane, les voiles s'allongent (echelle verticale x1,3).

**Prompt.**
> A single right wing, seen from behind, root at the bottom-left, tip at the
> top-right, isolated on a pure black background, no body, no character, no
> text. Fantasy game asset, painted style, crisp edges. The wing is made of
> hanging curtains of aurora borealis -- translucent vertical veils of green,
> violet and pink light -- held by a slender white-gold frame with five long
> supple branches; small rainbow crystal shards at every joint. Soft glow,
> luminous, ethereal. Square image, high detail.

---

## L'obtention (commune)

La plume d'apparence du Souverain Astral ne tombe **que du boss final**, et
seulement si les **trois sanctuaires** sont tombes dans la partie -- une chance
sur trois par victoire. Elle se garde entre les parties, comme toutes les
apparences. On ne la trouve ni en coffre, ni sur un monstre ordinaire.
