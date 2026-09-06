# Les runes — toutes les statistiques, et ce qu'elles font vraiment

Releve tire du code (`rune/Rune.java`, `rune/RuneEvents.java`, `rune/RuneMark.java`),
le 6 septembre 2026, apres la refonte demandee par le joueur. Chaque nombre
ci-dessous est celui qui sort en jeu.

## Comment lire une rune

Une rune porte **de une a dix options**, chacune avec un **grade** (C, B, A, S)
et une **valeur tiree** dans la fourchette de ce grade. Les maxima par grade
sont **deux C, deux B, trois A, trois S** (« CC/BB/AAA/SSS »). Le **rang** de la
rune (1 a 8) dit dans quel vivier de lettres on tire, et l'on tire entre le
vivier moins deux et le vivier entier :

| Rang | Vivier | Options | Rang | Vivier | Options |
|---|---|---|---|---|---|
| 1 | C | 1 | 5 | CC BB A | 3 a 5 |
| 2 | CC | 1 a 2 | 6 | CC BB AAA | 5 a 7 |
| 3 | CC B | 1 a 3 | 7 | CC BB AAA SS | 7 a 9 |
| 4 | CC BB | 2 a 4 | 8 | CC BB AAA SSS | 8 a 10 |

Une case ne recoit que les options qui ont le DROIT d'y etre : une case C ne
verra jamais Ravage, une case S ne verra jamais Precision. Une rune ne porte
jamais deux fois la meme option.

**Les valeurs s'additionnent** entre toutes les pieces portees — sauf les
options PC, dont seule **la meilleure** compte par categorie (deux PC Attaque ne
se cumulent pas ; PC Attaque et PC Generale, si).

Les minima ne sont pas connus de la source : on prend **68 % du maximum** de
chaque grade, sauf pour les PC, donnees en points entiers.

## Runes d'arme

Se gravent sur une arme **ou sur le casque**.

| Option | Grades | C | B | A | S |
|---|---|---|---|---|---|
| **Tranchant** | C a A | 0.65 a 0.95 | 0.97 a 1.42 | 1.29 a 1.90 | — |
| **Precision** | C | 4.08 a 6.00 | — | — | — |
| **Fureur** | C | 38.8 a 57.0 | — | — | — |
| **Cadence** | B a A | — | 0.06 a 0.09 | 0.10 a 0.14 | — |
| **Allonge** | B a A | — | 0.14 a 0.20 | 0.22 a 0.32 | — |
| **Percee** | A | — | — | 13.6 a 20.0 | — |
| **PC Attaque** | C a A | 9 a 10 | 11 a 13 | 14 a 17 | — |
| **PC Element** | C a A | 9 a 10 | 11 a 13 | 14 a 17 | — |
| **Ravage** | S | — | — | — | 8.2 a 12.0 |
| **Syncope** | C | 2.72 a 4.00 | — | — | — |
| **Saignee** | C | 2.72 a 4.00 | — | — | — |
| **Curee** | B a A | — | 0.97 a 1.42 | 1.29 a 1.90 | — |
| **Aubaine** | B | — | 40.8 a 60.0 | — | — |
| **Acharnement** | A | — | — | 5.44 a 8.00 | — |
| **Execution** | A | — | — | 6.8 a 10.0 | — |
| **Cataclysme** | S | — | — | — | 2.72 a 4.00 |
| **PC Generale** | S | — | — | — | 9 a 13 |

### Ce que chacune fait

- **Tranchant** — degats d'arme en dur, ajoutes a l'attribut d'attaque.
- **Precision** — chance de coup critique, en points de pourcentage
  (anciennement « Chance »).
- **Fureur** — ce qu'un critique ajoute en degats, en pourcentage.
- **Cadence** — vitesse d'attaque (attribut). +0,14 sur une base de 4,0 vaut
  environ 3,5 % d'attaques en plus.
- **Allonge** — portee d'interaction, en blocs : jusqu'a un tiers de bloc.
- **Percee** — une part de l'ARMURE de la cible ne compte plus, calculee pour
  de vrai : le coup est gonfle du rapport entre ce qu'il donnerait sous
  l'armure amputee et sous l'armure entiere. Contre une cible sans armure,
  aucun effet. (Corrigee : l'ancienne formule ne faisait pas ce qu'elle disait.)
- **PC Attaque / PC Element** — des **points** gratuits dans la voie du meme nom
  de la fiche du Heros, par-dessus l'achat, jusqu'a 120 (l'achat s'arrete a 100).
- **Ravage** — un pourcentage sur les degats TOTAUX, applique apres tout le reste.
- **Syncope** — chance, par coup, de clouer la cible 1,5 s (Lenteur VII, Fatigue IV).
- **Saignee** — chance, par coup, d'appliquer Poison 5 s ; le poison ne tue jamais seul.
- **Curee** — vie rendue a chaque mise a mort.
- **Aubaine** — chance, affichee en pour cent, d'effacer les recharges du Glaive
  et du Sceptre a la mise a mort. (Corrigee : le chiffre affiche est la chance.)
- **Acharnement** — degats majores tant que VOUS etes sous 30 % de vie. C'est
  le bonus du desespoir : on frappe plus fort quand on est pres de mourir.
- **Execution** — degats majores sur une CIBLE sous 30 % de vie : on acheve.
  Remplace « Cerne », qui n'avait rien a faire sur une arme.
- **Cataclysme** — chance, par coup, de frapper tout ce qui entoure la cible
  dans 4 blocs, pour la moitie des degats.
- **PC Generale** — des points dans **les quatre** voies a la fois. L'option la
  plus forte du catalogue.

## Runes d'armure

Se gravent sur une piece d'armure.

| Option | Grades | C | B | A | S |
|---|---|---|---|---|---|
| **Carapace** | C a A | 0.45 a 0.66 | 0.78 a 1.14 | 1.29 a 1.90 | — |
| **Egide** | C a A | 2.04 a 3.00 | 2.58 a 3.80 | 3.20 a 4.70 | — |
| **Endurance** | B a A | — | 0.88 a 1.30 | 1.36 a 2.00 | — |
| **Esquive** | B a A | — | 0.82 a 1.20 | 1.22 a 1.80 | — |
| **Absorption** | A | — | — | 0.34 a 0.50 | — |
| **Garde** | C a A | 0.54 a 0.80 | 0.88 a 1.30 | 1.36 a 2.00 | — |
| **Pavois** | C a A | 0.54 a 0.80 | 0.88 a 1.30 | 1.36 a 2.00 | — |
| **Sceau** | C a A | 0.54 a 0.80 | 0.88 a 1.30 | 1.36 a 2.00 | — |
| **Regeneration** | S | — | — | — | 0.20 a 0.30 |
| **Bastion** | S | — | — | — | 3.06 a 4.50 |
| **Riposte** | S | — | — | — | 13.6 a 20.0 |
| **PC Defense** | C a A | 9 a 10 | 11 a 13 | 14 a 17 | — |
| **PC PV/PM** | C a A | 9 a 10 | 11 a 13 | 14 a 17 | — |

### Ce que chacune fait

- **Carapace** — armure en dur (attribut). Un point d'armure vaut environ 4 %
  de degats en moins, jusqu'a 20 points.
- **Egide** — reduit ce que les critiques SUBIS ajoutent, en pourcentage.
  (Corrigee : le grade B vaut desormais plus que le C.)
- **Endurance** — points de vie maximum. 2,0 = un coeur.
- **Esquive** — chance d'annuler un coup entierement.
- **Absorption** — resistance d'armure (« armor toughness », l'attribut du
  diamant et de la netherite). Elle ne soigne rien : elle empeche les GROS
  coups de percer l'armure aussi facilement que les petits. Un coup de 20
  contre 20 d'armure passe a 8 sans resistance, a 6,4 avec 4 de resistance.
- **Garde / Pavois / Sceau** — degats reduits d'un montant FIXE, en points de
  vie, selon la nature du coup : melee (une entite qui frappe), distance (une
  fleche, un trident, une boule de feu), magie (potions, sorts, souffle du
  dragon...). Une rune de 2,0 retire un coeur a chaque coup de ce type.
- **Regeneration** — vie rendue chaque seconde, en continu, que l'on soit
  frappe ou non. 0,30 = un coeur toutes les sept secondes. Elle ne repond pas
  aux coups ; elle repare pendant qu'on avance.
- **Bastion** — TOUS les degats subis reduits en pourcentage, apres les
  reductions fixes. L'option du tank (anciennement « Sauvegarde », qui ne
  touchait que l'armure).
- **Riposte** — une part des degats subis est renvoyee a l'assaillant. Jamais
  sur une riposte : deux porteurs ne se renvoient pas le meme coup.
- **PC Defense / PC PV-PM** — des points gratuits dans les voies Defense et
  Vitalite du Heros.

L'ordre des reductions sur un coup subi : esquive (tout ou rien), puis Garde /
Pavois / Sceau (fixe), puis Bastion (pour cent), puis l'armure du jeu.

## Les quatre voies que les PC nourrissent

| Voie | Ce qu'elle donne, par paliers de dix points |
|---|---|
| **Attaque** | chance de critique (2 a 6 %) et degats critiques (10 a 30 %) |
| **Element** | resistance elementaire (1 a 6) — voie en sursis, voir §50 de MODE_ARCENCIUM |
| **Defense** | esquive (1 a 5 %) et reduction des critiques subis (5 a 15 %) |
| **Vitalite** | attaque en dur (0,3 a 1,1) et armure en dur (0,5 a 1,5) |
