# Repeindre les Ailes de Pierres Précieuses

## Pourquoi la version actuelle ne tient pas en jeu

Le joueur les trouve belles en image et décevantes en jeu — « trop élevées,
trop serrées ». J'ai vérifié avant de conclure : le **cadrage** (boîte du
dessin dans la toile) et la **répartition de la matière** sont identiques à
celles du Givre et des Prismatiques, qui rendent très bien. Ce n'est donc ni un
décalage, ni un centrage.

Ce qui diffère est la **silhouette** :

| | Givre / Prismatiques *(rendent bien)* | Pierres Précieuses *(rend mal)* |
|---|---|---|
| Forme | **un** éventail plein | **deux** branches écartées |
| Structure | plumes larges qui se recouvrent | résille dorée fine, ajourée |
| Détail | quelques gros éléments | beaucoup de très petites pierres |
| Vides | presque aucun | grands vides entre les branches |

En jeu, une aile fait **environ un bloc de large**. À cette taille : la résille
disparaît, les petites pierres deviennent du bruit coloré, les vides ouvrent
des trous dans la silhouette, et les deux branches se lisent comme deux objets
posés l'un sur l'autre — d'où l'impression de « serré ».

**La règle à retenir** : ce qui compte n'est pas la finesse du dessin, c'est la
**lisibilité de la silhouette réduite**. Plisser les yeux devant l'image doit
encore donner une aile.

## Le prompt

> A single right wing, seen from behind, root at the bottom-left, tip sweeping
> to the top-right, isolated on a pure black background. No body, no character,
> no text, no frame.
>
> ONE solid fan of large overlapping crystal feathers — not two separate
> branches, not a filigree. Nine to twelve broad blade-shaped feathers, each
> cut like a faceted gemstone, overlapping enough that the wing reads as a
> single continuous silhouette with no gaps between the feathers.
>
> Each feather is a different gemstone colour, arranged as a smooth gradient
> along the wing: deep ruby red at the leading edge, then orange citrine,
> golden topaz, emerald green, turquoise, sapphire blue, and amethyst violet at
> the trailing tip. The colours are saturated and unmistakable at a glance.
>
> Set into the base of the wing, near the root, a few LARGE cut gems — six to
> eight, big and clearly readable, not a scattering of small ones. A thick gold
> spine runs from the root along the top edge of the wing and ends in a curved
> hook; a second, thinner gold rib traces the base of the feathers. No fine
> lace, no thin scrollwork, no tiny dangling ornaments.
>
> The wing is WIDE: it spans the full width of the image on the diagonal, more
> horizontal than vertical, its mass along a line from the bottom-left root to
> the top-right tip. Strong internal light inside the gems, crisp painted
> edges, high contrast against the black.
>
> Square image, at least 1024 x 1024. Fantasy game asset, painted style.

## Ce qui doit faire rejeter un essai

- deux branches séparées, ou un vide au milieu de l'aile ;
- de la résille, du filigrane, des chaînettes, des pendentifs ;
- beaucoup de petites pierres au lieu de quelques grosses ;
- une aile plus haute que large, ou dressée à la verticale ;
- la racine ailleurs qu'en bas à gauche.

**L'épreuve avant de me l'envoyer** : réduis l'image à 64 × 64 pixels et
regarde-la. Si la silhouette reste une aile lisible et que les couleurs se
distinguent encore, elle tiendra en jeu. Sinon, non — c'est exactement ce que
le jeu en fait.

## Ce que j'ai déjà fait en attendant

- **Plus d'envergure et plus d'écartement** pour cette apparence seule (+15 %
  de taille, +14° d'ouverture) : les deux branches se séparent et chaque pierre
  redevient lisible. Réglages par apparence dans `WingSkin`.
- **Les pierres tombent** (`specialization/WingGems`) : une gemme toutes les
  trois tiques s'égrène des ailes et s'efface en tombant, dans six couleurs
  franches — émeraude, redstone, lapis, or, améthyste, diamant. Côté serveur,
  donc visible par toute l'équipe.

Quand la nouvelle image arrivera, je remets l'envergure et l'écartement à zéro :
une aile bien composée n'en a pas besoin.
