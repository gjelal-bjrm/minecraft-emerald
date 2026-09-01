# Prompt a coller dans Grok (ou ChatGPT)

But : recuperer le systeme de RUNES de NosTale — les effets, et surtout les
**fourchettes de valeurs min/max par statistique et par rang de rarete**.

Meme regle que la derniere fois : des tableaux chiffres, et une case « inconnu »
vaut mieux qu'une valeur inventee.

---

Tu es documentaliste sur le jeu NosTale (Gameforge). Je veux le systeme de
**runes** (les effets que l'on grave sur une arme ou une armure — selon la
version le jeu parle de runes, d'options, de cartes de rune ou de gravure ;
dis-moi le terme officiel FR et EN si le mien est faux).

Reponds **uniquement par des tableaux**. Pas d'introduction, pas de conclusion.

**1. Les familles.** Combien de familles de runes existe-t-il, et sur quoi
chacune se pose ? (Je crois qu'il y a des runes d'ARME et des runes d'ARMURE, et
que l'arme secondaire prend des runes d'arme — confirme ou corrige.)

**2. Les effets, famille par famille.** Un tableau par famille :

| Nom de la rune (FR / EN) | Effet | Statistique touchee | Conditionnelle ? |
|---|---|---|---|

Dans « Conditionnelle », dis si l'effet est permanent ou s'il se declenche sur
un evenement (a la mise a mort, sous X % de vie, sur un coup critique, quand
plusieurs ennemis sont proches, etc.). **Cette colonne m'importe autant que
l'effet lui-meme.**

**3. LE POINT LE PLUS IMPORTANT — les fourchettes min/max par rang.**

Une rune n'a pas une valeur fixe : chaque statistique est tiree au hasard entre
un minimum et un maximum, et ces deux bornes dependent du rang de rarete. Je
veux ces bornes, statistique par statistique et rang par rang :

| Statistique | Rang | Valeur MIN | Valeur MAX | Unite |
|---|---|---|---|---|

Exemple de ce que j'attends (chiffres approximatifs a corriger) :
degats critiques, borne basse 39 %, borne haute 57 % ; probabilite de critique,
borne basse 5, borne haute 9.

Precise aussi :
- combien de rangs existent au total,
- si l'ecart entre min et max se creuse avec le rang ou reste proportionnel,
- si le tirage est uniforme entre les deux bornes, ou penche vers le bas.

**4. Le rang de la rune face au rang de l'objet.** Y a-t-il une contrainte ?
Peut-on graver une rune de haut rang sur un objet de bas rang, ou faut-il que
l'objet soit au moins aussi bon que la rune ?

**5. Combien de runes par piece**, et le bareme s'il depend de quelque chose.

**6. L'obtention.** Les runes tombent-elles des monstres ? Si oui, **le rang de
la rune depend-il de la force du monstre** (niveau, type, boss) ? Donne le lien
si tu le connais :

| Source | Rangs possibles | Probabilites |
|---|---|---|

**7. La pose et le retrait.** Ou pose-t-on une rune, que faut-il, peut-on la
retirer, et que perd-on en la retirant ?

**Contraintes de reponse :**

- Precise la VERSION ou le serveur (Gameforge EU si possible).
- Indique ta source pour chaque tableau (wiki, forum, patch note, simulateur).
- **Si tu n'as pas le chiffre exact, ecris « inconnu ».** Ne l'estime pas, ne
  l'interpole pas, ne le deduis pas d'un autre jeu.
- Si le terme « rune » ne correspond a rien chez NosTale, dis-le franchement et
  decris a la place le systeme le plus proche (options d'equipement, cellons,
  cartes) — mais dis clairement que tu as change de sujet.

---

## Ce que ces chiffres vont alimenter

Trois familles, decidees (MODE_ARCENCIUM.md §17.2) :

| Famille | Support | Nature voulue |
|---|---|---|
| Arme | l'arme tenue | offensif **direct** — degats, cadence, portee |
| Armure | les 4 pieces | defensif — reduction, resistances, regeneration |
| Secondaire | le **casque seul** | offensif **conditionnel** — a la mise a mort, sous un seuil de vie, sur un critique, quand on est encercle |

La famille « secondaire » remplace l'arme secondaire de NosTale, que nous
n'avons pas. C'est pourquoi la colonne « Conditionnelle ? » du point 2
m'interesse tant : c'est elle qui me dira si la distinction que j'ai posee
existe deja chez vous, ou si je dois l'inventer.

Regles deja fixees de notre cote :

- une seule rune par emplacement d'equipement ;
- **le rang de la rune ne peut pas depasser le rang de la piece** — une piece
  Phenomenale accepte tout, une piece Utile n'accepte que de l'Utile ;
- les monstres faibles laissent des runes de bas rang, les monstres forts
  ouvrent les rangs eleves ;
- chaque rune tire sa valeur entre un minimum et un maximum propres a son rang.

Les runes portent la meme echelle a huit crans que notre equipement (Utile, Bon,
De bonne qualite, Excellent, Ancestral, Mysterieux, Legendaire, Phenomenal). Le
point 3 est donc celui qui compte le plus : je sais quels effets je veux, je ne
sais pas **entre quelles bornes** un rang doit les faire tomber.
