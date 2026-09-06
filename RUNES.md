# Les runes — toutes les statistiques, et ce qu'elles font vraiment

Releve tire du code (`rune/Rune.java`, `rune/RuneEvents.java`), le 6 septembre
2026. Chaque nombre ci-dessous est celui qui sort en jeu, pas une intention.

## Comment lire une rune

Une rune porte de une a six **options**, chacune avec un **grade** (C, B, A, S)
et une **valeur tiree** dans la fourchette de ce grade. Le **rang** de la rune
(1 a 8) decide de la forme de ses cases, exactement comme la rarete d'une piece :

| Rang | Cases | Rang | Cases |
|---|---|---|---|
| 1 | C | 5 | C B A |
| 2 | C C | 6 | C B A A |
| 3 | C B | 7 | C B A A S |
| 4 | C B B | 8 | C B A A S S |

Une case ne recoit que les options qui ont le DROIT d'y etre : une case C ne
verra jamais Ravage, une case S ne verra jamais Chance. C'est ce qui rend une
case S precieuse.

**Les valeurs s'additionnent** entre toutes les pieces portees — sauf les
options SL, dont seule **la meilleure** compte (deux runes SL Attaque ne se
cumulent pas ; en revanche SL Attaque et SL Generale, oui, ce sont deux options
differentes).

Les minima ne sont pas connus de la source NosTale : on prend **68 % du
maximum** de chaque grade, sauf pour les SL dont les fourchettes sont donnees en
niveaux entiers.

## Runes d'arme

Se gravent sur une arme **ou sur le casque**.

| Option | Grades | C | B | A | S |
|---|---|---|---|---|---|
| **Tranchant** | C a A | 0.65 a 0.95 | 0.97 a 1.42 | 1.29 a 1.90 | — |
| **Chance** | C | 4.08 a 6.00 | — | — | — |
| **Fureur** | C | 38.8 a 57.0 | — | — | — |
| **Cadence** | B a A | — | 0.06 a 0.09 | 0.10 a 0.14 | — |
| **Allonge** | B a A | — | 0.14 a 0.20 | 0.22 a 0.32 | — |
| **Percee** | A | — | — | 3.40 a 5.00 | — |
| **SL Attaque** | C a A | 9 a 10 | 11 a 13 | 14 a 17 | — |
| **SL Element** | C a A | 9 a 10 | 11 a 13 | 14 a 17 | — |
| **Ravage** | S | — | — | — | 8.2 a 12.0 |
| **Syncope** | C | 2.72 a 4.00 | — | — | — |
| **Saignee** | C | 2.72 a 4.00 | — | — | — |
| **Curee** | B a A | — | 0.97 a 1.42 | 1.29 a 1.90 | — |
| **Aubaine** | B | — | 4.08 a 6.00 | — | — |
| **Acharnement** | A | — | — | 5.44 a 8.00 | — |
| **Cerne** | A | — | — | 0.95 a 1.40 | — |
| **Cataclysme** | S | — | — | — | 2.72 a 4.00 |
| **SL Generale** | S | — | — | — | 9 a 13 |

### Ce que chacune fait

- **Tranchant** — degats d'arme en dur, ajoutes a l'attribut d'attaque. L'option
  de base, celle qu'on trouve le plus souvent.
- **Chance** — chance de coup critique, en points de pourcentage. S'ajoute a
  celle de la voie Attaque du Heros.
- **Fureur** — ce qu'un critique ajoute en degats, en pourcentage. Sans Chance,
  elle ne sert que quand un critique tombe tout seul.
- **Cadence** — vitesse d'attaque (attribut). +0,14 sur une base de 4,0 vaut
  environ 3,5 % d'attaques en plus.
- **Allonge** — portee d'interaction, en blocs. Vous touchez d'un tiers de bloc
  plus loin au mieux.
- **Percee** — une part des degats ignore l'armure adverse. La valeur affichee
  n'est pas appliquee telle quelle : les degats sont majores de
  `valeur % x (armure de la cible / 20)`, plafonne a 60 %. Contre une cible
  sans armure, elle ne fait rien.
- **SL Attaque / SL Element** — des **niveaux** gratuits dans la voie du meme nom
  de la fiche du Heros, par-dessus ce que vous avez achete, jusqu'a 120 (le
  plafond d'achat est 100).
- **Ravage** — un pourcentage sur les degats TOTAUX, applique apres tout le
  reste. La meilleure option offensive du catalogue.
- **Syncope** — chance, par coup, de clouer la cible : Lenteur VII et Fatigue IV
  pendant 1,5 seconde.
- **Saignee** — chance, par coup, d'appliquer Poison pendant 5 secondes. Le
  poison ne tue jamais seul.
- **Curee** — vie rendue a chaque mise a mort.
- **Aubaine** — chance d'effacer les recharges du Glaive et du Sceptre a la mise
  a mort. **Attention** : la chance reelle est la valeur **x 10** — une Aubaine
  a 5,2 donne 52 %, pas 5,2 %.
- **Acharnement** — degats majores tant que vous etes sous 30 % de vie.
- **Cerne** — armure gagnee tant que **trois ennemis ou plus** sont a 5 blocs.
- **Cataclysme** — chance, par coup, de frapper tout ce qui entoure la cible
  dans 4 blocs, pour la moitie des degats.
- **SL Generale** — des niveaux dans **les quatre** voies a la fois. L'option la
  plus forte du catalogue : elle vaut quatre SL de grade A.

## Runes d'armure

Se gravent sur une piece d'armure.

| Option | Grades | C | B | A | S |
|---|---|---|---|---|---|
| **Carapace** | C a A | 0.45 a 0.66 | 0.78 a 1.14 | 1.29 a 1.90 | — |
| **Egide** | C a A | 2.58 a 3.80 | 2.58 a 3.80 | 3.20 a 4.70 | — |
| **Endurance** | B a A | — | 0.88 a 1.30 | 1.36 a 2.00 | — |
| **Esquive** | B a A | — | 0.82 a 1.20 | 1.22 a 1.80 | — |
| **Absorption** | A | — | — | 0.34 a 0.50 | — |
| **Regeneration** | S | — | — | — | 0.20 a 0.30 |
| **Sauvegarde** | S | — | — | — | 3.06 a 4.50 |
| **SL Defense** | C a A | 9 a 10 | 11 a 13 | 14 a 17 | — |
| **SL PV/PM** | C a A | 9 a 10 | 11 a 13 | 14 a 17 | — |

### Ce que chacune fait

- **Carapace** — armure en dur (attribut). Un point d'armure vaut 4 % de degats
  en moins, jusqu'a 20 points.
- **Egide** — reduit ce que les critiques SUBIS ajoutent, en pourcentage.
- **Endurance** — points de vie maximum. 2,0 = un coeur.
- **Esquive** — chance d'annuler un coup entierement.
- **Absorption** — resistance d'armure (armor toughness) : elle protege surtout
  contre les gros coups.
- **Regeneration** — vie rendue chaque seconde, en continu. 0,30 = un coeur
  toutes les sept secondes.
- **Sauvegarde** — pourcentage ajoute a l'armure de base deja acquise.
- **SL Defense / SL PV-PM** — des niveaux gratuits dans les voies Defense et
  Vitalite du Heros.

## Les quatre voies que les SL nourrissent

| Voie | Ce qu'elle donne, par paliers de dix niveaux |
|---|---|
| **Attaque** | chance de critique (2 a 6 %) et degats critiques (10 a 30 %) |
| **Element** | resistance elementaire (1 a 6) |
| **Defense** | esquive (1 a 5 %) et reduction des critiques subis (5 a 15 %) |
| **Vitalite** | attaque en dur (0,3 a 1,1) et armure en dur (0,5 a 1,5) |

Chaque niveau donne aussi du lineaire : 0,05 par niveau en Attaque et en
Defense, 0,12 en Vitalite, 0,35 en Element.

## Les points a trancher

Six endroits ou le releve me parait douteux ou illisible, dans l'ordre ou je les
corrigerais :

1. **Aubaine ment sur son chiffre.** L'infobulle montre 4,1 a 6,0 et la chance
   reelle est de 41 a 60 %. Il faut afficher le vrai pourcentage.
2. **Percee ment aussi.** « 5 % d'armure ignoree » decrit mal une formule qui
   depend de l'armure de la cible et qui ne fait rien contre une cible nue.
3. **Egide ne progresse pas de C a B** : 3,8 dans les deux cas. Une case B vaut
   alors exactement une case C.
4. **Allonge et Cadence sont presque invisibles** : un tiers de bloc, 3,5 %
   d'attaques. A monter, ou a retirer.
5. **Sauvegarde dit « toutes les defenses »** mais ne touche que l'armure.
6. **SL Element est la seule voie dont on ne voit pas l'effet en combat** : la
   resistance elementaire ne s'affiche nulle part.
