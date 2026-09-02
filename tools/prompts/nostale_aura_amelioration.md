# Prompt a coller dans ChatGPT (ou Grok)

But : decrire precisement l'EFFET VISUEL que NosTale donne a un equipement selon
son niveau d'amelioration (+1 a +10), pour le reproduire dans Minecraft.

Je ne demande pas d'opinion sur ce qui serait joli : je demande ce que le jeu
FAIT, cran par cran. Une case « inconnu » vaut mieux qu'une description
inventee.

---

Tu es documentaliste sur le jeu NosTale (Gameforge). Quand un joueur ameliore
son arme ou son armure (+1 a +10, voire au-dela), le jeu affiche un effet visuel
autour de l'objet ou du personnage. Je veux la description exacte de cet effet.

Reponds **uniquement par des tableaux**, sans introduction ni conclusion.

**1. Le seuil.** A partir de quel cran l'effet apparait-il ? (+1 ? +5 ? +7 ?)
Est-ce le meme seuil pour l'arme et pour l'armure ?

**2. L'effet cran par cran.** Un tableau, une ligne par cran, colonnes :

| Cran | Arme : effet | Armure : effet | Couleur(s) dominante(s) | Intensite |
|---|---|---|---|---|

Dans « effet », dis concretement ce qu'on voit : une lueur autour de la lame ?
des particules qui s'elevent ? un halo autour du corps ? une trainee quand on
bouge ? un eclat sur les bords ? Sois aussi precis que si tu decrivais une
capture d'ecran.

**3. La forme et le mouvement.** Pour l'effet le plus abouti (+10 ou le plus
haut que tu connaisses) :

- l'effet est-il FIXE ou ANIME (pulsation, rotation, montee de particules) ?
- entoure-t-il l'objet seul, ou tout le personnage ?
- change-t-il de forme selon le cran, ou seulement de couleur et d'intensite ?
- l'arme et l'armure ont-elles le MEME effet, ou deux effets differents ?

**4. Les couleurs.** Si les couleurs changent avec le cran, donne la
progression complete (par exemple : blanc puis bleu puis violet puis rouge).
Si tu connais les teintes approximatives en hexadecimal, donne-les ; sinon,
nomme-les.

**5. Le cumul.** Si un joueur porte une arme +10 et une armure +10, les deux
effets se superposent-ils ? Y a-t-il un effet supplementaire quand TOUT
l'equipement est a un certain cran ?

**6. La rarete.** L'effet depend-il aussi du niveau de rarete de la piece, ou
seulement de l'amelioration ?

**Contraintes de reponse :**

- Precise la version ou le serveur (Gameforge EU si possible) : l'effet a pu
  changer au fil des mises a jour.
- Cite ta source pour chaque tableau (wiki, video, forum, capture).
- **Si tu ne sais pas, ecris « inconnu ».** N'invente pas une couleur, ne
  deduis pas d'un autre jeu.

---

## Ce que je peux rendre, pour que ta reponse me serve

Le jeu cible est Minecraft, en pixel-art seize pixels, avec ces outils :

| Outil | Ce qu'il permet | Ce qu'il ne permet pas |
|---|---|---|
| Calque d'armure emissif anime | une lueur sur le CORPS, qui brille dans le noir, animee sur 12 images, teintee a volonte | un contour net autour de la silhouette |
| Particules | des points colores qui montent, tournent ou trainent autour du joueur ou de l'objet | une forme continue (halo plein, anneau net) |
| Surbrillance d'objet | l'eclat mouvant des objets enchantes, sur l'arme tenue et en inventaire | une couleur libre : c'est toujours le meme violet-blanc |
| Texture d'arme animee | l'arme elle-meme qui pulse ou change de teinte | un effet qui deborde de l'arme |

Une description du type « halo continu net autour du personnage » ne se
fera donc pas telle quelle ; « particules colorees qui montent le long du
corps » ou « la lame qui pulse d'une couleur » se font tres bien. Si tu connais
l'effet reel, decris-le tel quel et laisse-moi traduire -- mais si tu hesites
entre deux formulations, choisis celle qui entre dans le tableau ci-dessus.
