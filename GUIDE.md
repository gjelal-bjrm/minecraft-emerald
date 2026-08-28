# Mode Arcencium — guide du joueur

Une partie dure **60 minutes**. À la fin, soit vous avez tué le boss, soit vous
avez perdu. Ce guide dit ce qu'il faut savoir pour jouer ; le pourquoi de chaque
choix est dans `MODE_ARCENCIUM.md`, qui est le cahier de conception.

---

## 1. Le déroulé

Vous démarrez tous dans un village, avec une **épée plantée dans un socle** au
centre. Tant que personne ne la retire, rien ne commence : vous ne pouvez ni
sortir du village ni creuser. C'est le temps de vous organiser.

**Quand un joueur retire l'épée**, le village est attaqué. Les autres joueurs
reçoivent au hasard un arc ou un sceptre, prêtés pour l'occasion. Trois vagues.

- Vous **perdez** si tous les villageois meurent. Pas avant.
- Vous **gagnez** en survivant : l'épée se dissout en **trois ancres**, posées à
  450 blocs du village, et le chronomètre de 60 minutes démarre.

Ensuite, la partie se joue en quatre phases. Elles changent la météo, la force
des monstres et la qualité du butin.

| Phase | Minutes | Ce qui change |
|---|---|---|
| Exploration | 0-18 | Météos douces seulement |
| Montée | 18-36 | La Nuit d'Arcencium apparaît |
| Pression | 36-48 | Toutes les météos. **La Marée commence** |
| Assaut | 48-60 | Uniquement les tempêtes, presque sans répit |

**Pour gagner :** activer les trois ancres, faire apparaître l'Arc-en-ciel, et
tuer le boss à son sommet avant la fin du temps.

---

## 2. La Marée Prismatique

À partir de la **36ᵉ minute**, la zone vivable se referme : de 750 blocs autour
du village jusqu'à 120 à la 60ᵉ. Une barre violette en haut de l'écran donne le
rayon courant, et un mur de particules marque la limite quand vous en approchez.

Dehors, vous ne mourez pas d'un coup — vous êtes **rongé** : environ 1 cœur près
de la lisière, jusqu'à 4 loin dedans, toutes les 2 secondes, plus Faiblesse.
Sortir un instant pour ramasser quelque chose reste anodin. S'enfoncer de deux
cents blocs est une expédition qui se prépare.

**Mais c'est aussi là que ça paie.** La zone morte est habitée par des monstres
du dernier palier, et de loin en loin par un **seigneur de passage** annoncé à
tout le monde. C'est là que les matériaux rares tombent le mieux.

Les **Jambières de Marée** annulent la corrosion entièrement.

---

## 3. Les six météos

La météo est **globale** — elle touche toute la zone en même temps — et suit la
phase. Toute météo agressive s'annonce **15 secondes à l'avance** et se termine
par une **Embellie** (une accalmie de 60-90 s pendant laquelle plus aucun
monstre n'apparaît naturellement). Jamais deux fois la même de suite.

Les tempêtes font apparaître leurs propres monstres, et ceux-là lâchent les
matériaux d'Apotheosis.

### Les douces

| | Ce que vous voyez | Ce que ça vous donne |
|---|---|---|
| **Brume** | Brouillard pastel, on voit à 50 blocs | Les monstres perdent **70 %** de leur portée de détection. Le moment de traverser |
| **Aurore** | Rideaux de lumière dans tout le ciel, carillon de fond | Les veines d'Arcencium proches **scintillent et carillonnent** à travers la roche. Le moment de descendre miner |

### Les agressives

**Nuit d'Arcencium** — Il fait nuit d'un coup, il pleut, et des éclairs colorés
tombent sans arrêt. **La couleur annonce l'effet**, et ça s'apprend :

| Éclair | À l'impact |
|---|---|
| Rouge | Met le feu |
| Bleu | Gèle l'eau en glace, frigorifie et ralentit |
| Jaune | Onde électrique au ras du sol sur 10 blocs — touche monstres **et** joueurs |
| Rose | Pose la Marque Prismatique sur les monstres |
| Vert | Laisse une cicatrice luisante : la miner dans les 30 s donne de l'arcencium |

Le jaune ne sort qu'un éclair sur cinq au plus, et jamais deux ondes à la fois.

**Pluie de Météores** — Un cercle de flammes marque le sol ~3 secondes avant
chaque impact, et vous voyez le météore arriver de biais dans le ciel, précédé
d'un sifflement. L'impact blesse, casse les blocs fragiles, **laisse de
l'arcencium** et perce parfois jusqu'aux grottes — un raccourci vers le minage.

**Déchirure Prismatique** — La gravité tombe à 22 % : vous sautez cinq fois plus
haut et les chutes ne font plus mal *pendant* la tempête. Trois choses en
découlent :

- des **éclats d'arcencium flottent** à 4-8 blocs du sol, signalés par un halo
  et une colonne — inatteignables sans l'apesanteur, c'est tout leur intérêt ;
- des **failles** s'ouvrent, larges comme une porte : y entrer vous dépose près
  d'une **ancre non tenue** ;
- attention à la fin : la gravité revient d'un coup.

**Orage Prismatique** — Un cercle se resserre au sol avec un tic-tac qui
s'accélère, puis la foudre tombe : **10 dégâts** sur 3,5 blocs. Mais rester dans
le rayon donne la **Surcharge** (force et vitesse pendant 30 s). C'est la seule
météo où l'on *cherche* à être touché.

Le **Filtre de Brume** immunise à tous les dégâts de météo — mais laisse la
Surcharge.

---

## 4. L'équipement

### Les matériaux

| Matériau | Où | Sert à |
|---|---|---|
| **Arcencium brut** | Minerai en grotte, cicatrices vertes, cratères de météores, éclats de Déchirure | Se fond en lingot |
| **Lingot d'Arcencium** | Fonte | Tout l'équipement, activer les ancres |
| **Branche de Prisme** | Arbres de Prisme | Manches des armes |
| **Fibre de Prisme** | Arbres de Prisme | Liens et cordes |

Miner et bûcheronner restent utiles toute la partie : les météos donnent de
l'arcencium par à-coups, jamais de bois.

### Les armes

| Arme | Comment elle marche |
|---|---|
| **Lame d'Arcencium** | Frapper monte la **Fureur Cristalline** ; à pleine fureur les coups portent plus loin et plus fort |
| **Arc d'Arcencium** | Bander monte la **Tension Prismatique** ; à pleine tension la flèche pose la Marque Prismatique |
| **Sceptre d'Arcencium** | Clic gauche : rayon. Clic droit : onde qui repousse. **Tirer en rafale réduit les dégâts** ; ça remonte après 1 s sans tirer |

### L'armure

Légèrement meilleure que la netherite : **22 de protection** contre 20, même
résistance aux enchantements. Elle est noire, parcourue de fissures qui brillent
de toutes les couleurs.

### Les artefacts

Vous en sertissez **un par pièce**, à l'**Établi de Sertissage**. Ils ne
s'ajoutent qu'à *notre* équipement, jamais au vanilla. **Retirer un artefact le
détruit** : on peut changer d'avis, mais ça coûte.

Un artefact ne donne pas des points — il change une façon de jouer.

| Casque | |
|---|---|
| Lentille du Prisme | Voit ancres, coffres et artefacts à travers les murs (40 blocs) |
| Filtre de Brume | Immunise aux dégâts des météos |
| Repère d'Écho | Fait luire l'ennemi le plus coriace des environs |
| Lentille d'Aurore | Vision nocturne permanente |

| Plastron | |
|---|---|
| Plaque de Gangue | Le coup fatal vous laisse à 1 PV (recharge 3 min) |
| Coque Prismatique | Accumule les dégâts subis, puis libère une onde de choc |
| Réservoir de Prisme | Régénération lente, doublée hors combat |
| Plastron de Résonance | +5 % de dégâts par coup reçu, jusqu'à +50 % |

| Jambières | |
|---|---|
| Lest de Gangue | Immunité au recul |
| Jambières de Marée | La Marée ne vous ronge plus |
| Champ de Cristal | Ralentit les ennemis à moins de 4 blocs |
| Renfort de Siège | Armure renforcée quand plusieurs ennemis vous pressent |

| Bottes | |
|---|---|
| Semelle de Prisme | +20 % de vitesse |
| Bottes d'Éclair | Un second saut en plein vol |
| Semelle Vaporeuse | Marche sur l'eau et sur la lave |
| Bottes de Retour | Retour au point de réapparition (recharge 2 min) |

| Épée | |
|---|---|
| Régulateur de Lame | La Fureur monte deux fois plus vite |
| Lame de Chaîne | Les coups touchent aussi les ennemis adjacents |
| Drain de Cristal | 15 % des dégâts infligés rendus en vie |
| Éclat Final | Tuer un ennemi déclenche une détonation |

| Arc | |
|---|---|
| Tension Rapide | La Tension monte deux fois plus vite |
| Flèche Fourchue | Le tir à pleine tension part en trois flèches |
| Marque Prolongée | La Marque Prismatique dure trois fois plus longtemps |
| Flèche Traçante | Les flèches infléchissent leur course vers la cible |

> Le sceptre a un emplacement d'artefact, mais **aucun artefact ne lui est
> encore attribué**. À faire.

---

## 5. Les ancres

Trois ancres, à 450 blocs du village. Les activer coûte de l'arcencium, de plus
en plus cher, et déclenche un siège qu'il faut tenir.

| Ancre | Coût | Siège |
|---|---|---|
| 1ʳᵉ | 8 lingots | 3 vagues |
| 2ᵉ | 16 lingots | 4 vagues |
| 3ᵉ | 32 lingots | 5 vagues + mini-boss |

La difficulté suit **l'ordre d'activation**, pas la position : la première ancre
activée est toujours la plus facile, quelle que soit celle que vous choisissez.
Vous pouvez donc vous répartir sur la carte.

Si tout le monde meurt dans la zone, l'ancre se désactive et l'arcencium est
perdu. Une ancre tenue devient votre **point de réapparition**.

Le panneau en haut à gauche montre en permanence les trois ancres : direction,
distance, et un losange plein quand elle est tenue.

---

## 6. Apotheosis

Le système d'équipement d'Apotheosis se joue normalement sur des dizaines
d'heures. En une heure, rien n'aurait le temps d'arriver — tout est donc
débloqué automatiquement.

**Les paliers de monde montent tout seuls** avec la phase (Frontier, Ascent,
Summit, Pinnacle). Vous n'avez rien à activer : un message doré vous prévient à
chaque montée. C'est ce qui fait apparaître les **Envahisseurs**, ces monstres
nommés, rares et bien équipés.

**Votre Chance** monte de 2 à 10 points selon la phase : le butin devient
franchement meilleur.

**Les matériaux tombent** des monstres de tempête (1 sur 4) et de ceux de la
Marée (1 sur 2), à une rareté qui suit la phase. Les sigils et les fioles
suivent, plus rarement. De quoi reforger et sertir sans avoir à farmer.

---

## 7. Les commandes

Toutes commencent par `/arcencium` et demandent le niveau opérateur.

| Commande | Ce qu'elle fait |
|---|---|
| `weather <nom> [secondes]` | Déclenche une météo tout de suite, sans préavis |
| `weather stop` | Arrête la météo en cours |
| `skip <minutes>` | Avance le chronomètre — pour voir la Marée sans jouer 36 minutes |
| `find` | Donne les coordonnées de la Lame du Serment |

Les noms de météo : `brume`, `aurore`, `nuit`, `meteores`, `dechirure`, `orage`,
`embellie`. L'autocomplétion les propose toutes.
