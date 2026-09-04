# Les Ailes de Pierres Précieuses : ce qui cloche, et quatre patrons possibles

## Je m'étais trompé de coupable

J'avais accusé les **branches séparées**. C'est faux, et le joueur l'a dit :
elles sont voulues, et le **Givre** a exactement les mêmes — il rend très bien.
Une texture ne se juge pas sur son plan mais sur ce qu'il en reste réduit à un
bloc de large, et j'avais lu la mauvaise différence.

## La vraie cause, mesurée sur les dix ailes

J'ai relevé, pour chaque aile du mode, l'**étalement de teinte** (0 = une seule
couleur, 1 = arc-en-ciel complet) et la **saturation moyenne** :

| Aile | Étalement de teinte | Saturation |
|---|---|---|
| Givre, Rubis, Tempête, Braise | 0,01 – 0,02 | 0,47 – 0,80 |
| Obscures, Papillon | 0,06 – 0,09 | 0,20 – 0,69 |
| Émeraude, Aurore | 0,16 – 0,17 | 0,44 – 0,71 |
| Prismatiques | 0,33 | **0,16** *(pastel)* |
| **Pierres Précieuses** | **0,56** | **0,71** |

Tout ton jeu d'ailes suit, sans que tu l'aies formulé, **une règle** :

> Soit **une famille de teintes**, même très saturée (Givre, Rubis, Braise) ;
> soit **beaucoup de teintes, mais pâles** (Prismatiques, saturation 0,16).

Les Pierres Précieuses enfreignent les deux à la fois : sept teintes franches à
saturation 0,71. À un bloc de large, l'œil ne peut plus **grouper** les plumes
en un seul objet — chaque plume devient une tache indépendante, et l'ensemble
se lit comme des confettis. Les branches n'y sont pour rien ; le Givre le
prouve.

**Corollaire utile pour la suite** : ce n'est pas la couleur qui doit porter la
variété d'une aile à l'autre — c'est le **patron**.

---

## Quatre patrons, tous différents des plumes

Chacun garde la règle de couleur, se distingue franchement des neuf autres, et
survit à la réduction. Le prompt commun à tous : **une aile DROITE seule, vue
de dos, racine en bas à gauche, pointe en haut à droite, fond noir pur, sans
corps ni personnage ni texte, carré, 1024 × 1024 au moins.**

### 1. Le Vitrail *(recommandé pour des « pierres précieuses »)*

**Le patron.** Pas de plumes : de larges **panneaux de verre taillé** tenus par
un **plombage noir épais**, comme une verrière de cathédrale découpée en forme
d'aile. Huit à douze panneaux seulement, chacun d'un seul bloc de couleur.

**Pourquoi ça marche là où l'actuelle échoue.** Le plombage noir fait exactement
ce qui manque : il **groupe**. Des lignes sombres épaisses survivent à la
réduction et dessinent la silhouette, si bien que les couleurs peuvent rester
nombreuses et franches sans devenir des confettis — c'est le principe même du
vitrail, lisible à trente mètres dans une nef.

> A single right wing shaped like a cathedral stained-glass window: eight to
> twelve LARGE flat panes of coloured glass — ruby red, amber, emerald,
> sapphire, amethyst — each a single flat colour, separated by THICK black
> lead came lines, four to six pixels wide, that also outline the whole wing.
> The panes are cut in long tapering shapes that follow the wing's sweep, split
> into two branches. Light shines through the glass from behind. No feathers,
> no filigree, no small ornaments. Crisp graphic style, heavy dark outlines,
> high contrast on pure black.

### 2. La Géode

**Le patron.** Une aile de **cristaux bruts** poussés en grappe, comme
l'intérieur d'une géode d'améthyste : des prismes épais de longueurs inégales,
serrés à la racine, ouverts en éventail irrégulier vers la pointe. Aucune
symétrie, aucun bord lisse.

**Pourquoi ça marche.** Les formes sont grosses et anguleuses, et la teinte
reste **une** (violet, ou vert selon la variante) avec un cœur plus clair : le
contraste clair/sombre porte le relief, pas la couleur.

> A single right wing made of raw crystal clusters, like the inside of an
> amethyst geode: thick hexagonal prisms of uneven length, densely packed at
> the root, fanning out irregularly toward the tip in two clusters. All in one
> hue — deep violet — with pale, almost white glowing cores and dark violet
> shadows between the prisms. Rough broken facets, no smooth edges, no
> feathers, no metal, no ornament.

### 3. Les Lames

**Le patron.** Un éventail de **lames courbes** — des cimeterres emboîtés
plutôt que des plumes. Métal sombre, tranchant clair, une seule couleur
d'accent qui court le long des fils.

**Pourquoi ça marche.** La silhouette devient **dure et anguleuse**, ce
qu'aucune autre aile du jeu n'est : on la reconnaît d'un coup d'œil. Et le
contraste métal sombre / fil clair est le plus haut de tout le jeu de textures,
donc le plus lisible de loin.

> A single right wing built from nine curved blades instead of feathers, like
> nested scimitars sharing one hilt at the root, splitting into two groups. The
> blades are dark blued steel with a bright razor edge; a single accent colour
> — molten gold — runs along every cutting edge and pools at the hilt. Hard
> angular silhouette, strong specular highlights, no feathers, no gems.

### 4. Les Éclats suspendus

**Le patron.** Aucune membrane : des **fragments détachés** qui flottent en
formation d'aile, comme un vitrail brisé tenu en l'air. Gros éclats près de la
racine, plus petits et plus espacés vers la pointe.

**Pourquoi ça marche — et son risque.** C'est le patron le plus original des
quatre, et le seul que je te signale comme **risqué** : trop d'espace entre les
fragments et l'aile disparaît en jeu. Il ne tient que si les éclats proches de
la racine sont **vraiment gros** et si l'on garde une seule teinte.

> A single right wing made of floating detached shards with no membrane between
> them: large angular fragments of gemstone near the root, becoming smaller and
> more widely spaced toward the tip, arranged so their outline still reads as a
> wing. One hue only — deep sapphire blue — with bright edges catching light.
> Faint glowing dust in the gaps. No feathers, no connecting structure.

---

## Si tu préfères garder l'image actuelle

Le remède le moins cher, sans rien repeindre : **regrouper les couleurs**.
Plutôt qu'une teinte par plume, trois **zones** franches le long de l'aile.

**Je l'ai essayé sur ton fichier** (rouge à la racine, violet au milieu, bleu à
la pointe, l'or intact) et mesuré : l'étalement de teinte passe de **0,56 à
0,35** — soit le niveau des Prismatiques, la seule autre aile multicolore qui
rend bien. Réduite à 64 px, la silhouette se groupe enfin en un objet au lieu
de sept.

Ce n'est pas aussi net qu'un patron pensé pour ça (la saturation reste à 0,67,
là où les Prismatiques tiennent à 0,16), mais c'est une minute de travail contre
une repeinture. L'image d'essai est dans le bloc-notes ; dis-moi si tu veux que
je la pose.

## Ce qui est déjà en place

- **Écartement et envergure par apparence** (`WingSkin`) : les Pierres
  Précieuses ont +14° et +15 %, ce qui sépare les deux branches. Ça aide, mais
  ça ne traite pas la cause — la couleur.
- **Les pierres tombent** (`specialization/WingGems`) : une gemme toutes les
  trois tiques s'égrène et s'efface, six couleurs franches, visible par toute
  l'équipe.

## L'épreuve, quel que soit le patron

Réduis l'image à **64 × 64** et regarde-la. Si la silhouette reste une aile et
que les masses se distinguent, elle tiendra en jeu. C'est exactement ce que le
jeu en fait.
