# Mode Arcencium — guide du joueur

Une partie dure **60 minutes**. À la fin, soit vous avez tué le boss, soit vous
avez perdu. Ce guide dit ce qu'il faut savoir pour jouer ; le pourquoi de chaque
choix est dans `MODE_ARCENCIUM.md`, qui est le cahier de conception.

---

## 1. Début de partie à Haven

Une nouvelle partie commence dans la **ville de Haven**, le port de Jak 3, dans
sa propre dimension. C'est le cas dans un monde neuf, ou après
`/arcencium setup` dans un monde qui a déjà sa ville. Un ancien monde sans ville
commence au village, comme avant.

Dans la ville : mode aventure (à la main, rien ne se casse ni ne se pose), pas
de faim, pas de combat entre joueurs, et l'on ne sort pas de la ville. La ville
est **envahie de monstres** qui peuvent vous blesser, et chacun y reçoit le
**Morph Gun** de Jak 3 pour s'en défendre (voir plus bas). Votre inventaire n'est
pas touché. Votre mode de jeu habituel vous est rendu au village.

### Les appartements

Vous apparaissez dans un des **trois appartements**, les garages du bras ouest
du port. Un message vous dit dans quelle direction et à quelle distance est le
QG.

- Un appartement reçoit 3 joueurs, puis on passe au suivant. À partir du
  10ᵉ joueur, chacun va dans l'appartement le moins rempli.
- Votre place est gardée tant que le lobby est ouvert. Si vous vous reconnectez
  dans la ville, vous restez là où vous étiez.
- Si vous mourez dans la ville, vous réapparaissez dans votre appartement.
- Si la ville est encore en train de se poser (serveur dédié), vous attendez
  quelques instants près de la Lame, puis vous partez dans votre appartement.

### Le vote au QG

Le QG est le **bar du Hip Hog**, à l'est des appartements. La **borne de
vote** se tient au bout du comptoir, vitres tournées vers l'entrée du bar : un
pied rouille marqué de l'emblème du Hip Hog, et une tête inclinée à deux vitres
lumineuses. Tout le monde peut voter, même sans être opérateur.

- Clic droit sur la **vitre bleue** : tu votes **Monde ouvert**. Sur la
  **vitre rouge** : tu votes **Défi**. Ton choix s'affiche au-dessus de la barre
  d'objets, avec un son de confirmation.
- Clic droit ailleurs sur la borne (le cadre, le pied) : l'écran de vote.
- L'écran montre les votes, qui est dans le bar, chaque joueur avec son vote
  (ou « hors du bar »), et le compte à rebours.
- Pour partir, **tous les joueurs connectés dans la ville** doivent avoir voté
  la même chose **et être dans le bar**. Les joueurs inactifs comptent aussi.
  Un opérateur en chantier ne vote pas.
- On peut changer d'avis. Quand tout le monde est d'accord, un compte à rebours
  de **5 secondes** démarre. Il s'arrête si quelqu'un change d'avis, arrive,
  part ou sort du bar, et repart de 5 secondes si le groupe est encore d'accord.
- Au départ, le mode voté s'applique. Tout le monde arrive au village avec le
  **kit de départ** (il remplace l'inventaire) et son mode de jeu habituel.
  Ensuite, la partie se joue comme avant.
- Tant que le lobby est ouvert, la Lame du Serment refuse de venir : le mode
  se vote au QG. Après le départ, elle se tire sans redemander le mode.
- Un joueur qui se connecte après le départ arrive directement au village.

La borne est posée toute seule quand la ville accueille les joueurs (et
reposée après chaque `haven rebuild` ou réouverture du lobby), puis retirée au
départ. Elle est incassable et on ne peut pas la déplacer.

### Les voitures

Une voiture attend devant chaque appartement : la **A** (cara) devant le 1,
la **B** (carb) devant le 2, la **C** (carc) devant le 3. Elle plane à environ
trois blocs du sol. Les voitures disparaissent au départ vers le village.

- **Monter :** clic droit sur la voiture. On prend la première place libre ; la
  première est celle du conducteur. Trois places par voiture.
- **Descendre :** s'accroupir (Maj). On est posé au sol à côté de la voiture,
  même depuis la voie haute. Pour changer de conducteur, le passager descend
  puis remonte.
- **Conduire :** Z (ou W) pour les gaz, S pour freiner (S tenu à l'arrêt fait
  reculer), Q et D (ou A et D) pour tourner. Vitesse maximale : 40 m/s
  (144 km/h), soit deux blocs par tick, comme dans Jak 3. À pleine vitesse, la
  voiture tourne large (une quarantaine de blocs de rayon) : freinez avant les
  rues étroites.
- **Espace : changer de zone de survol.** La touche n'agit qu'au volant (à
  pied, Espace saute comme d'habitude) ; elle se change dans Options >
  Commandes > Emerald Weapons.
  - **Rase-sol :** la voiture plane juste au-dessus du sol ou de l'eau.
  - **Voie haute :** elle monte à l'altitude de la circulation de Jak 3, celle
    de la carte de trafic du port : neuf blocs au-dessus de la rue (hors de la
    ville, neuf blocs au-dessus du sol). Si quelque chose bloque la montée plus
    de deux secondes, elle redescend.
  - **La voie haute monte d'elle-même au-dessus du pont entre les deux tours.**
    Comme dans Jak 3, elle s'élève là de dix blocs, puis revient à sa hauteur
    habituelle : on traverse à pleine vitesse sans rien toucher, par n'importe
    quel côté. Partout ailleurs, elle garde la même hauteur qu'avant.
  - Un nouvel appui sur Espace la fait redescendre jusqu'au sol.
- Un rappel s'affiche à l'écran pendant qu'on conduit. En vue à la troisième
  personne, la caméra recule pour montrer toute la voiture.
- Les voitures sont **indestructibles et solides** : on s'y cogne, et elles
  écartent ceux qu'elles recouvrent.
- Une voiture **tombée à l'eau** ou **sortie de la ville** revient toute seule
  sur sa place. Garée ailleurs, elle reste là où vous l'avez laissée.
- Si vous vous déconnectez dans une voiture, vous êtes d'abord posé à côté :
  la voiture reste là.

#### Les motos monoplaces

À côté de chaque voiture, sur sa gauche quand on sort de l'appartement, attend
une moto volante de Jak 3 : la **moto A** (bikea) à l'appartement 1, la **B**
(bikeb) au 2, la **C** (bikec) au 3. Elle se monte, se conduit et change de
zone de survol exactement comme une voiture (mêmes touches, Espace compris), à
40 m/s au plus.

- **Une seule place**, celle du pilote. Si quelqu'un est déjà dessus, le clic
  droit ne fait rien.
- Plus légère, la moto atteint sa pleine vitesse en une seconde environ (une
  voiture en près de deux), freine plus fort et tourne un peu plus serré.
- Elle plane à environ trois blocs du sol, et s'abaisse à deux blocs et demi
  quand on la pilote, comme dans Jak 3. Elle prend la même voie haute que les
  voitures. Pas de saut : celui des motos de Jak 3 n'est pas repris.
- Comme les voitures, les motos sont indestructibles et solides, reviennent sur
  leur place si elles tombent à l'eau ou sortent de la ville, et disparaissent
  au départ vers le village.

### L'invasion

- On arrive en **invasion** : zombies, villageois zombies et squelettes casqués
  qui rôdent dans **toutes** les rues, phantoms dans le ciel. La ville entière
  est peuplée, pas seulement autour de vous : où que vous alliez en voiture, les
  rues sont habitées, et les monstres restent là où vous les avez laissés. Ils
  apparaissent à distance, jamais sous vos yeux quand ils sont proches, et à
  plus de 24 blocs des portes des appartements et de l'entrée du bar.
  **Les appartements et le bar du
  Hip Hog sont des abris** : aucun monstre n'y entre, aucun ne vous y blesse.
- Les monstres blessent, et l'on peut mourir. On réapparaît dans son
  appartement avec tout son inventaire, son expérience, le Morph Gun et des
  réserves d'éco pleines. Ni chute, ni faim, ni coup entre joueurs.
- **Le bouton du QG**, au bout du comptoir du Hip Hog, près de la borne : voyant
  rouge en invasion, bleu en paisible. N'importe quel joueur passe toute la ville
  en **mode paisible** : les monstres partent, des villageois des sept régions
  se promènent dans toutes les rues de la ville (invulnérables, sans commerce),
  et le **trafic de Jak 3** circule sur la voie haute : voitures et motos
  civiles, un habitant au volant, sur les voies du jeu, à leur vitesse (15 m/s,
  bien moins que vous). On ne monte pas dedans ; on s'y cogne, et elles freinent
  derrière vous si vous prenez leur voie. Un nouvel appui relance l'invasion
  (deux secondes entre deux appuis). Chaque réouverture du lobby remet
  l'invasion.

### Le Morph Gun

- **Donné à l'arrivée** dans la ville, dans la première case libre (ou la main
  gauche si l'inventaire est plein). Il ne se jette pas et ne se range dans aucun
  coffre ni sac : pendant qu'un coffre est ouvert, il quitte la barre et revient à
  la fermeture. Il **disparaît au départ** vers le village, quel que soit le mode
  voté ; il n'existe nulle part ailleurs que dans la ville.
- **Changer d'arme : les flèches**, comme la croix de Jak 3. ↑ rouge (Scatter Gun,
  « Pulvérisator »), ↓ jaune (Blaster), ← bleue (Vulcan Fury,
  « Vulcanoshooteur »), → sombre (Peace Maker, « Pacificateur »). Touches
  réassignables dans Options > Commandes > Morph Gun (Haven). Pendant la
  transformation (un tiers de seconde, presque une seconde vers le bleu), l'arme
  ne tire pas. Pendant qu'une boule du Peace Maker charge, on ne change pas d'arme.
- **Tirer : clic gauche**, arme en main, hors voiture et moto. Le clic ne frappe
  plus au corps à corps.

| Arme | Gâchette | Cadence | Coût | Ce qu'elle fait | Décor cassé |
|---|---|---|---|---|---|
| Scatter Gun | un clic par tir | 1,1 s | 1 éco rouge | 19 plombs dans un cône de ±45° sur 15 blocs, les premiers sur les monstres devant ; un monstre n'est touché qu'une fois par tir : 12 PV à moins de 6 blocs, 8 au-delà | le bloc touché par chaque plomb, 6 au plus |
| Blaster | un clic par tir (un clic un peu en avance est gardé) | 0,32 s | 1 éco jaune | un trait à 10 blocs par tique, 3 s de vol : 8 PV | 1 à 4 blocs autour de l'impact |
| Vulcan Fury | tenir | le canon s'emballe en 1,5 s : de 0,4 à 0,1 s entre deux balles | 1 éco bleue par balle | balle instantanée sur 80 blocs : 8 PV | le bloc touché |
| Peace Maker | tenir pour charger (0,3 s au moins), relâcher pour tirer | 0,85 s | 1 éco sombre à la charge (rendue si la charge est interrompue) | boule en spirale qui poursuit le monstre visé ; à l'impact, 64 PV au monstre le plus proche dans 10 blocs, puis la foudre saute de monstre en monstre toutes les 0,1 s (16 au plus) | 40 blocs au plus dans un rayon de 3 |

- Un zombie meurt en 3 tirs de Blaster, 2 ou 3 de Scatter Gun, 3 balles de
  Vulcan Fury ou une boule du Peace Maker. Les armes ne blessent **que les
  monstres** : jamais un joueur (ni dégât, ni recul), ni les villageois du mode
  paisible.
- **Les réserves** : rouge 100, jaune 200, bleue 200, sombre 15, pleines à
  l'arrivée et à chaque réapparition. Une réserve vide : l'arme passe d'elle-même
  à la première famille qui a de l'éco (jaune, puis rouge, bleue, sombre) et tire ;
  sinon, un clic. Le chargeur de la famille vide disparaît du modèle.
- **Recharger** : des munitions d'éco attendent aux **12 points** de la carte
  (10 jaunes ou 10 bleues, 5 rouges, 1 sombre) et reviennent 30 s après avoir été
  prises. **Chaque monstre tué en lâche une**, de la couleur qui vous manque le
  plus (au hasard si tout est plein), qui disparaît au bout de 20 s ou quand la
  ville passe en paisible. On les ramasse en passant dessus ; une réserve pleine
  ne ramasse pas, la munition reste pour les autres.
- **Le HUD**, au-dessus des cœurs : les quatre réserves (la famille tenue est
  encadrée) et le nom de l'arme, avec « à sec » qui clignote quand sa réserve est
  vide. Masqué en voiture.
- **Le décor se casse sous les armes**, puis revient à l'identique 10 à 15 s après.
  Ne cassent jamais : les appartements, le bar du Hip Hog et son parvis, la borne,
  le bouton, les places des voitures et des motos, le bord de la ville, et tout ce
  qui est sous la surface de l'eau. À la main, rien ne se casse.

---

## 2. Le déroulé

Après le départ de Haven (ou dans un ancien monde sans ville), vous êtes tous
dans un village, avec une **épée plantée dans un socle** au centre. Tant que personne ne la retire, rien ne commence : vous ne pouvez ni
sortir du village ni creuser. C'est le temps de vous organiser.

> Cette retenue ne vaut **qu'en régime Défi**, où l'horloge tourne. En
> **Monde ouvert**, rien ne vous retient : partez explorer avant de tirer la
> Lame si vous voulez, le prologue vous attendra.

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

## 3. La Marée Prismatique

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

## 4. Les six météos

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

## 5. L'équipement

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

## 6. Les ancres

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

### Le Sanctuaire

Chaque ancre est au **sommet d'une pyramide**, dans une place forte : muraille
de 67 blocs de côté avec chemin de ronde et créneaux, quatre tours d'angle, et
un corps de garde au sud fermé par une **herse**.

Chaque porte est une **Porte du Sceau** (celle de Cataclysm, 5 blocs de large
sur 8 de haut). Elle est fermée à l'arrivée. On l'ouvre en tournant la
**manivelle** sur le rempart, à droite de la porte (à défaut, un levier fait
l'affaire). Elle **se referme toute seule quand vous activez l'ancre** — vous
êtes enfermé avec ce qui arrive — et se rouvre quand le siège est fini, gagné
ou perdu.

**Les Sceaux du Tombeau.** Trois sceaux dorment **dans les salles de la
pyramide**, un par niveau : un dans les **caves**, un en haut du **puits
central**, un près de l'**entrée**. Deux lanternes signalent chacun — il faut
trouver la salle, pas le bloc. On entre par le couloir qui s'ouvre au pied de la
face sud.

Si au bout de **sept minutes** aucune ancre n'est activée, les sceaux encore
endormis se laissent entrevoir **vingt secondes à travers la pierre**. Un coup
de main, pas une réponse : ça dit où fouiller, pas quel bloc toucher. **L'ancre du sommet refuse l'arcencium
tant qu'ils ne sont pas tous éveillés** — il faut donc descendre avant de
monter. Un clic droit suffit à en éveiller un, et ils ne se cassent pas à la
pioche.

**Le butin.** Chaque étage de chaque tour a son coffre, et **quatre autres dans
la salle du trésor**, au bout du couloir qui s'ouvre au pied de la face sud de
la pyramide. Le sommet ne porte que l'ancre : on monte pour la tenir, on
descend pour s'équiper. Ce sont des **coffres Lootr** : chaque joueur a son
propre tirage, personne ne se fait devancer. La richesse suit le palier de
l'ancre — la première paie moyennement, la deuxième bien, et la troisième doit
vous armer pour le boss : lingots par vingtaines, matériaux mythiques
d'Apotheosis, lingot de netherite, pièces d'armure d'Arcencium, pommes d'or
enchantées, totems, et **un à deux artefacts**. Les artefacts n'apparaissent
qu'à partir du palier 2.

Des **gardiens** tiennent les lieux avant vous : deux par tour d'angle, d'autres
sur le chemin de ronde et autour de la pyramide. Ils ne quittent pas le
sanctuaire. Comptez-les avant d'entrer.

**Où est l'ancre :** au sommet de la pyramide, sur un parvis à quatre
obélisques. Deux façons de la trouver :

- **la pulsation.** Dès que vous êtes dans l'enceinte, une colonne de lumière
  jaillit de l'ancre toutes les 30 secondes, pendant 10 secondes. Elle se voit
  **à travers les murs** ;
- **la tour d'escalier**, au sud de la pyramide : on y entre par la cour,
  l'escalier en vis monte tout en haut, et une passerelle éclairée rejoint le
  parvis.

Il y a **quatre portes**, une par côté, donc une tombe toujours en face de
l'entrée de la pyramide.

Si tout le monde meurt dans la zone, l'ancre se désactive et l'arcencium est
perdu. Une ancre tenue devient votre **point de réapparition**.

Le panneau en haut à gauche montre en permanence les trois ancres : direction,
distance, et un losange plein quand elle est tenue.

---

## 7. Apotheosis

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

## 8. Les commandes

Toutes commencent par `/arcencium` et demandent le niveau opérateur.

| Commande | Ce qu'elle fait |
|---|---|
| `setup` | Nouvelle partie. Si le monde a une ville : la Lame est replantée, le mode est à revoter, et les joueurs sont ramenés dans leurs appartements. Sans ville, la partie repart au village |
| `weather <nom> [secondes]` | Déclenche une météo tout de suite, sans préavis |
| `weather stop` | Arrête la météo en cours |
| `skip <minutes>` | Avance le chronomètre — pour voir la Marée sans jouer 36 minutes |
| `find` | Donne les coordonnées de la Lame du Serment |
| `sanctuary [1-3]` | Bâtit un Sanctuaire d'Ancre là où vous êtes, au palier demandé (butin plus riche au 3) |
| `anchor` | Vous téléporte à l'ancre du sanctuaire le plus proche et dit si le bloc y est |
| `mode off` | **Éteint le mode** : plus de confinement, ni météo, ni Marée, ni chronomètre. Le monde redevient un Minecraft ordinaire, nos blocs compris — pour explorer et bâtir tranquillement. Les **rituels d'ancre restent jouables** : c'est le bac à sable où l'on essaie un sanctuaire |
| `mode on` | Le rallume |
| `jak ctyport` | Bâtit le **port de Haven** (Jak 3) à partir de vos pieds, vers l'est, le sud et le haut (1227 × 158 × 695 blocs). La zone est **d'abord vidée**, puis le quartier pousse couche par couche : on peut le reposer au même endroit sans restes de l'ancien. L'eau est 57 blocs au-dessus du point de pose. À faire en monde plat |
| `jak stop` | Interrompt la pose en cours |
| `haven build` | Pose la **ville de Haven** (le port de Jak 3) dans sa propre dimension, au niveau de la mer. Dans un monde neuf, c'est fait tout seul à la création ; la commande sert pour un monde existant |
| `haven rebuild` | Remet la zone de la ville à neuf, mer et fond compris, puis la repose : pour essayer une nouvelle version de la ville dans le même monde |
| `haven tp` | Vous emmène dans la rue devant le bar du Hip Hog |
| `haven back` | Vous ramène au village |
| `haven chantier on` / `off` | Mode chantier, pour vous seul : créatif, et la ville redevient modifiable pour aménager les appartements. En chantier, vous ne votez pas et vous ne prenez pas de place dans un appartement. Les autres joueurs restent en mode aventure |
| `haven ouvrir` | Rouvre le lobby de la ville, comme `setup` : Lame replantée, mode à revoter, joueurs ramenés dans leurs appartements |
| `haven skip` | Pour les essais : départ vers le village tout de suite, avec le mode actuel, sans vote. Seulement pendant le lobby |
| `haven salle <n>` (ou `salle <n> tp`) | Vous téléporte dans l'appartement n (1, 2 ou 3), tourné vers la porte |
| `haven salle <n> capture` | Relève votre aménagement de l'appartement n (voir plus bas) |
| `haven salle <n> show` | Résume le relevé (cellules, décors, date, auteur) et dit si un aménagement de cette salle est déjà dans le mod |
| `haven salle <n> reset` | Remet l'appartement tel que la ville le pose, retire ses décors et efface le relevé. L'aménagement déjà dans le mod revient à la prochaine pose |
| `vehicule cara` (ou `carb`, `carc`, et les motos `bikea`, `bikeb`, `bikec`) | Pose une voiture ou une moto volante de Haven devant vous. Elle se monte et se conduit comme celles des appartements (voir la partie 1) |
| `haven invasion etat` | Dit le mode de la ville, le nombre de monstres et d'habitants, et les blocs cassés qui attendent leur retour |
| `haven invasion invasion` (ou `paisible`) | Passe toute la ville en invasion ou en paisible, comme le bouton du QG |
| `haven invasion reconstruire` | Repose tout de suite le décor cassé par les armes |

Dans Haven : mode aventure, rien ne se casse ni ne se pose à la main, pas de faim ni de
combat entre joueurs, seuls les monstres de l'invasion blessent, et l'on ne peut pas sortir
de la ville. Votre mode de jeu vous est rendu en la quittant.
La pose d'essai `jak ctyport` contient elle aussi le mur invisible qui fait le tour du port.

### Aménager un appartement

La ville rejoue votre aménagement à chaque pose, comme pour les sanctuaires.

1. `/arcencium haven chantier on` : créatif, et les protections sont levées pour vous seul.
2. Meublez la salle : blocs, escaliers, portes, lits, coffres remplis, panneaux écrits,
   cadres, tableaux, porte-armures. Vous pouvez aussi changer le mur intérieur, le sol ou
   le plafond.
3. Relevez : la Sonde en main, debout dans la salle, clic droit dans le vide. Ou
   `/arcencium haven salle <n> capture`. Le relevé ne garde que ce que vous avez changé.
   Il est écrit dans `run/arcencium_jak/appartement_<n>.nbt`, avec une version lisible
   `appartement_<n>.txt`. Chaque relevé remplace le précédent.
4. Mettez-le dans le mod : `python tools/jak_zone_apply.py <n>` (`--dry-run` pour
   seulement vérifier). Le script refuse un relevé pris sur une autre version du port,
   écarte l'eau qui coule et les entités qui ne sont pas des décors, et signale les blocs
   d'autres mods absents.
5. Relancez le jeu (ou `/reload`), puis `/arcencium haven rebuild`.

À savoir :
- Ce qui bouge tout seul n'est pas relevé : les portes reviennent fermées, la redstone
  éteinte, les lits libres. Les leviers, les trappes ouvertes, les bougies et feux de
  camp allumés sont gardés.
- Seuls les cadres, cadres lumineux, tableaux et porte-armures sont gardés comme décors.
  Le contenu des coffres est rejoué à chaque pose.
- Si la ville est reposée à partir d'une autre version du port, les salles relevées avant
  sont refusées, avec un message aux opérateurs : il faut les relever à nouveau.
- Il faut être près de la salle pour la relever ou la remettre à zéro.

### Essais automatiques

Sur un serveur d'essai (`run-server`), chacun écrit son rapport puis arrête le serveur :

- `EMERALDWEAPONS_AUTOTEST=vote ./gradlew runServer` : appartements et vote, rapport `run-server/vote_autotest.txt` ;
- `EMERALDWEAPONS_AUTOTEST=salles ./gradlew runServer` : relevé et rejeu des salles, rapport `run-server/salles_autotest.txt` ;
- `EMERALDWEAPONS_AUTOTEST=vehicules ./gradlew runServer` : voitures et motos, rapport `run-server/vehicules_autotest.txt` ;
- `EMERALDWEAPONS_AUTOTEST=invasion ./gradlew runServer` : invasion, décor destructible, bouton du QG et MSPT, rapport `run-server/invasion_autotest.txt` ;
- `EMERALDWEAPONS_AUTOTEST=armes ./gradlew runServer` : Morph Gun (poses, confinement, tir des quatre armes, munitions, décor, MSPT avec quatre tireurs), rapport `run-server/armes_autotest.txt`.

Les chiffres de tir des armes sont dans `jak/gun/GunSpec.java` ; les textures des particules
et la planche de contrôle (`build/jak/gun/planche-tir.png`) sortent de `python tools/gun_particles.py`.

La vitesse des voitures et des motos se règle dans `VehicleSpec.MAX_SPEED_MS`.

Pour arriver devant le bar du Hip Hog juste après la pose, sans bouger avant :
`/tp @s ~375 ~66 ~211` — la porte est à quelques pas.

Les noms de météo : `brume`, `aurore`, `nuit`, `meteores`, `dechirure`, `orage`,
`embellie`. L'autocomplétion les propose toutes.
