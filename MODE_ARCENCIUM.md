# Mode Arcencium — cahier de conception et d'implémentation

Document de travail. Il recense **tout** ce qui a ete decide pour le mode de jeu,
et sert de liste de taches. Rien ici n'est encore code sauf mention explicite.

Etat au 2026-08-25. Branche `feat/arcencium-bow`.

---

## 1. Le mode en une phrase

Un mode roguelite jouable en solo ou en multijoueur, sur un monde genere a neuf
a chaque partie, ou une equipe dispose de **60 minutes** pour activer trois
ancres, faire apparaitre l'Arc-en-ciel, et tuer le boss a son sommet — avant que
la Maree Prismatique ne referme la zone de jeu.

Principes directeurs, valides au fil de la discussion :

- **La meteo doit ouvrir une facon de jouer, pas seulement taxer la facon en cours.**
- **Trois activites complementaires** (mine, bois, tempete) dont aucune ne peut etre negligee.
- **La strategie prime sur l'equipement** : les monstres viennent en escouades avec des roles.
- **La vitesse se paie en difficulte** : se separer va plus vite mais affronte les paliers durs a effectif reduit.

---

## 2. Deroule d'une partie

| Phase | Minutes | Contenu |
|---|---|---|
| **Prologue** | avant le chrono | Defense du village. Le chrono ne demarre qu'a la fin. |
| **Exploration** | 0-18 | Brume Prismatique, Aurore. Mine, bois, premier equipement. |
| **Montee** | 18-36 | + Nuit d'Arcencium. Premiere fenetre a artefacts. |
| **Pression** | 36-48 | + Meteores, Tornade, Orage. La Maree commence a monter. |
| **Assaut** | 48-60 | Orage permanent. Arc-en-ciel et boss. |

- Monde **genere a neuf** a chaque partie.
- Zone de jeu : **rayon 750 blocs** (bordure de monde). Ancres a ~450 du centre, a 120 deg.
- Mort d'un joueur : **reapparition + perte de l'equipement au sol**, comme en vanilla.
- Points de reapparition : le village, puis **chaque ancre activee**.

### Conditions de fin

- **Victoire** : le boss du sommet de l'Arc-en-ciel est tue.
- **Defaite** : la Maree Prismatique referme entierement la zone a la fin du temps.

---

## 3. Prologue — « La Nuit des Corrompus »

Tous les joueurs apparaissent **au meme endroit**, sur la place du village.

- Equipement de depart : **armure de fer complete + epee de fer + bouclier**,
  avec Protection I et Tranchant I. Rien de plus.

### 3.1 La Lame du Serment — le declencheur

**Mise en place automatique.** Aucune commande n'est necessaire. Au premier
chargement du monde, le mod cherche **le village d'Arcencium** (tag
`emeraldweapons:arcencium_village`, rayon 160 chunks) : le mode doit commencer
dans son propre decor. A defaut, il se rabat sur un village ordinaire (tag
`minecraft:village`, rayon 96 chunks), puis sur le point d'apparition.

Le village d'Arcencium apparait desormais dans **21 biomes** au lieu des quatre
taigas d'origine, ce qui garantit qu'il en existe un a portee dans presque tous
les mondes. Il rejoint aussi le tag vanilla des villages, si bien que les cartes
et boussoles d'exploration le trouvent comme n'importe quel autre.

Le mod y plante la lame, y fixe le point d'apparition, chasse les monstres
alentour et met le jour.

Chercher un village plutot qu'en improviser un : il existe forcement dans un
biome habitable, avec ses maisons et sa lumiere -- deux choses qu'on ne saurait
pas improviser aussi bien, et dont l'absence transforme le prologue en survie
dans le noir.

La lame est dessinee en **trois dimensions par un renderer dedie**, qui rend
l'objet lui-meme avec sa texture animee. Un modele de bloc ordinaire n'en
donnerait qu'une decalcomanie.

Au centre du village, **une epee de notre mode est plantee dans le sol**,
entouree de nombreux villageois. C'est l'appat : elle attire les joueurs vers
la place avant que quoi que ce soit ne commence.

**Tant que la lame n'est pas retiree, la partie n'a pas commence :**
- les joueurs **ne peuvent pas sortir du village** (barriere invisible ~40 blocs,
  le joueur est repousse avec un message),
- ils **ne peuvent pas casser de bloc** (`BlockEvent.BreakEvent` annule),
- aucun chronometre, aucune ancre, aucun monstre.

**Retirer la lame declenche tout.** C'est une action volontaire, donc personne
ne peut rater l'annonce : un joueur qui rejoint en retard trouve la partie
encore en attente, ou deja lancee mais avec les autres au meme endroit.

**Les armes preteees.** Celui qui tire la lame porte l'epee ; chaque autre
defenseur recoit au hasard l'**Arc d'Arcencium** ou le **Sceptre d'Arcencium**,
L'arc porte **Infinite**, plutot qu'une reserve qui s'epuiserait au milieu du
siege ; quelques fleches l'accompagnent, l'enchantement en exigeant une en
poche. La composition d'equipe existe donc des le prologue, et chacun voit ce
que le mode reserve.

**Les golems de fer sont ecartes** le temps du siege, et rendus au village des
qu'il est tenu : ils abattaient les vagues a la place des joueurs, qui n'avaient
plus qu'a regarder.

Tout est repris a la fin du siege. Un marqueur distingue ces armes preteees de
celles qu'un joueur aurait fabriquees, qui ne doivent jamais etre effacees, et
leur infobulle annonce le pret pour que la reprise ne passe pas pour une perte.

**La lame elle-meme** : une Epee d'Emeraude ceremonielle, aux cristaux de
Fureur **eteints**. Elle montre des la premiere minute a quoi ressemble
l'equipement du mode, sans court-circuiter la progression.
- Seul **le joueur qui la retire** la porte.
- S'il meurt pendant le siege, elle tombe au sol et reste jouable : un autre
  peut la ramasser.
- **A la mort du dernier monstre, elle se dissout** en particules prismatiques
  -- et c'est de cette dissolution que naissent les trois faisceaux des ancres.
  La lame ceremonielle *devient* les trois ancres.

**Acte** : au retrait, toute l'equipe recoit un buff court **« le Serment vous
lie »** -- le moment est collectif, pas reserve au porteur.

**Acte** : si personne ne retire la lame, un **rappel a l'ecran apparait au bout
de 60 secondes**, puis se repete.

### 3.2 Le siege du village

**Difficulte.** Le prologue n'emploie QUE des monstres vanilla faibles -- zombie,
squelette, husk. Les factions du modpack sont taillees pour du jeu tres avance
et massacraient des defenseurs en armure de fer ; elles restent reservees aux
sieges d'ancre, comme le prevoyait deja le cahier.

Trois vagues de 3, 5 et 6, **mises a l'echelle du nombre de joueurs presents** :
une vague calibree pour quatre est infaisable en solo.

Le **Serment** (Force I, Resistance I, Regeneration I) est renouvele pendant TOUT
le siege, et non quarante secondes : c'est lui qui rend le village tenable.

L'equipement de depart est conserve apres la victoire -- le retirer punirait
d'avoir gagne. Seule la Lame du Serment se dissout.

**Condition de defaite : plus aucun villageois vivant.** Pas la mort d'un joueur,
qui reapparait et revient -- sa chute ne doit pas condamner l'equipe. Tant qu'un
villageois tient debout, la defense continue.

Si aucun joueur n'est present, le siege **se suspend** au lieu d'echouer.

Six villageois au minimum sont reposes avant chaque tentative, et le village est
repeuple apres un echec : sa condition de defaite etant justement leur absence,
sans repeuplement la mission serait perdue pour toujours.

- Le village est attaque. Les monstres **entrent dans le village** et attaquent
  joueurs et villageois.
- Les monstres sont **attaches au village** (rayon 26 blocs) et surtout ils y
  **convergent** : la laisse empeche de partir mais ne dit pas ou aller, et sans
  cela ils erraient dans tout le village.
  Trois moyens : un but de deplacement vers le point d'attache, en priorite
  faible pour qu'il cede des qu'une cible apparait ; la traque des villageois,
  eux-memes retenus au centre ; et la **luminescence**, qui les rend visibles a
  travers les murs -- le compteur de la barre correspond ainsi toujours a
  quelque chose qu'on peut trouver.
- **Compteur visible** : barre de boss segmentee, « CORROMPUS RESTANTS · N ».
- Message clair a l'ecran : defendre le village.

A la mort du dernier monstre :
1. Quelques secondes de calme.
2. **La Lame du Serment se dissout.**
3. Trois faisceaux de lumiere jaillissent a l'horizon, aux positions des ancres.
4. Titre plein ecran.
5. **Le chronometre apparait et demarre.** Il reste visible toute la partie.

---

## 4. Les ancres

### Cout et paliers

Le palier depend du **rang d'activation**, pas de l'ancre choisie. Il est verrouille
au moment ou le rituel demarre, sur la formule :

> palier = (nombre d'ancres deja actives) + (nombre de sieges deja en cours) + 1

| Palier | Cout | Siege |
|---|---|---|
| 1 | 8 lingots d'Arcencium | 3 vagues, ~2 min |
| 2 | 16 lingots | 4 vagues avec elites, ~3 min |
| 3 | 32 lingots | 5 vagues + mini-boss, ~4 min |

Cela couvre le cas multijoueur ou trois groupes lancent trois rituels en parallele :
ils affrontent les paliers 1, 2 et 3 dans l'ordre de depose de l'Arcencium.

### Echec

Si **tous les joueurs de la zone meurent** : l'ancre se desactive, **l'Arcencium est perdu**,
le compteur redescend. Une nouvelle tentative recalcule son palier au demarrage
(pas de punition qui s'empile).

> A regler a l'essai : la perte totale de l'Arcencium est peut-etre trop punitive.

### Recompense — « l'Echo de la Victoire »

Tous les joueurs ayant inflige des degats pendant le siege recoivent :
- une grosse dotation d'XP,
- un buff de **3 minutes** : Force, Regeneration, Vitesse, de niveau proportionnel au palier.

L'ancre devient un **point de reapparition**.

---

## 5. Les monstres

### Spawn classique

**Le systeme de spawn vanilla reste totalement intact.** Les factions n'apparaissent
que dans les sieges d'ancre et dans nos structures.

### Les six factions

Une faction est tiree au sort par ancre, **jamais deux fois la meme dans une partie**
(120 combinaisons). Chaque faction possede deja sa structure d'escouade.

| Faction | Mod | Composition |
|---|---|---|
| **La Cour Noyee** | Cataclysm | `deepling`, `deepling_angler` *(archer)*, `deepling_brute` *(garde)*, `deepling_priest` *(soigneur)*, `deepling_warlock` *(mage)*, `coral_golem` *(elite)* |
| **La Legion Draugr** | Cataclysm | `draugr`, `koboleton` *(archer)*, `elite_draugr`, `royal_draugr` *(elite)*, `kobolediator` *(elite)* |
| **Le Cercle Arcanique** | Iron's Spellbooks | `cultist`, `pyromancer`, `cryomancer`, `necromancer`, `priest` *(soigneur)*, `archevoker` *(elite)* |
| **Les Oublies** | Undergarden | `rotling`, `rotwalker`, `rotbeast`, `nargoyle` *(volant)*, `forgotten_guardian` *(elite)* |
| **La Horde Gobeline** | Twilight Forest | `kobold`, `blockchain_goblin`, `lower_goblin_knight`, `helmet_crab`, `armored_giant` *(elite)* |
| **Le Sculk** | Deeper Darker | `sculk_snapper`, `sculk_centipede`, `sculk_leech`, `stalker`, `shattered` *(elite)* |

Le Sculk est reserve a l'arene finale.

**Melange vanilla obligatoire** : squelettes en archers, evokers dans le Cercle,
pillards partout. Les factions doivent rester ancrees dans un Minecraft reconnaissable.

### Les elites, par role

| Role | Candidats |
|---|---|
| **Brise-ligne** | `ignited_berserker`, `netherite_ministrosity`, `armored_giant`, `minotaur`, `troll` |
| **Sentinelle** | `coral_golem`, `forgotten_guardian`, `ender_golem`, `citadel_keeper` |
| **Traqueur** | `the_prowler`, `stalker`, `endermaptera`, `nightfall_spider` |
| **Meneur** | `archevoker`, `royal_draugr`, `knight_phantom`, `deepling_warlock`, `death_tome` |

Le **Meneur** buffe ou soigne autour de lui : c'est la cible que designe le Repere d'Echo.

### Le boss final

**Un des trois, tire au hasard a chaque partie :**

- **Ignis** *(Cataclysm)* — titan de feu, plusieurs phases.
- **Ender Guardian** *(Cataclysm)* — teleportations, rayons, invocations. Theme celeste.
- **Twilight Lich** *(Twilight Forest)* — trois phases : bouclier reflechissant, sbires, corps-a-corps.

Le Warden est **ecarte** : concu pour qu'on le fuie, il s'enterre et tue en un coup.
Mauvais boss d'arene.

> Optionnel : reskin prismatique des trois boss via un pack de ressources livre avec le modpack.

---

## 6. La meteo *(implementee)*

**Globale** : elle touche toute la zone en meme temps. **Progressive** : le
tirage suit la phase -- Exploration {Brume, Aurore}, Montee +Nuit, Pression
tout, Assaut {Meteores, Dechirure, Orage} avec des pauses tres courtes (c'est
l'« orage permanent »). Jamais deux fois la meme de suite.

Regles communes, toutes implementees :
- **Preavis de 15 s** (titre + compte a rebours) avant toute meteo tiree au sort.
- Un abri en **materiaux du mod est toujours sur** : les meteores ne brisent
  jamais un bloc de notre espace de noms.
- Duree **2 a 4 minutes** ; toute agressive finie naturellement est suivie de
  **l'Embellie** (60-90 s), pendant laquelle aucune apparition naturelle de
  monstre (les sieges, en EVENT, continuent).
- Le **Filtre de Brume** immunise aux degats de toutes les meteos agressives ;
  la Surcharge de l'Orage lui reste acquise -- s'exposer aux frappes devient un
  style de jeu.

### Brume Prismatique *(douce)*
Brouillard dense a teinte derivante, vue reduite a ~56 blocs -- et la vue des
monstres reduite d'autant (portee de detection -70 %). La fenetre pour
traverser ou contourner sans se battre.

### Aurore *(douce)*
Rubans colores hauts dans le ciel. Les plantes prismatiques brillent aussi de
jour, et les **veines d'Arcencium proches scintillent et carillonnent** : sous
terre, c'est un detecteur. Le moment de descendre miner.

### Nuit d'Arcencium *(charniere, agressive)*
La nuit tombe en plein jour (horloge deplacee puis rendue), **pluie et
tonnerre**, et des **eclairs d'Arcencium** : de vrais eclairs, dont la couleur
annonce l'effet -- on apprend a lire le ciel.

| Couleur | A l'impact |
|---|---|
| **Rouge** | met le feu |
| **Bleu** | gele l'eau en glace, frigorifie et ralentit |
| **Jaune** | **onde electrique** au ras du sol (rayon 10), frappe monstres ET joueurs -- un eclair sur cinq au plus, jamais deux ondes a la fois |
| **Rose** | pose la **Marque Prismatique** |
| **Vert** | laisse une **cicatrice luisante**, minable 30 s -> arcencium brut |

Le repli naturel est la grotte -- mais c'est dehors que tombent les artefacts.

### Pluie de Meteores d'Arcencium *(agressive)*
Cercle d'avertissement ~3 s avant chaque impact. Les impacts blessent, brisent
les blocs fragiles vanilla, **livrent de l'arcencium brut** et percent parfois
jusqu'aux grottes -- un raccourci vers le minage.

### Dechirure Prismatique *(agressive -- remplace la tornade)*
Le lieu se defait, en trois symptomes qui se justifient l'un l'autre :
- **l'apesanteur** : gravite -65 % pour tout le monde, bonds de cinq blocs,
  chutes amorties pendant la tempete ;
- **les eclats en suspension** : des grappes d'arcancium flottent a 12-20
  blocs du sol, atteignables SEULEMENT pendant l'apesanteur -- c'est ce qui la
  justifie ; les eclats non cueillis retombent a la fin ;
- **les failles** : y entrer depose pres d'une **ancre non tenue** (a defaut,
  un point lointain). On sait qu'on arrive quelque part d'utile, pas lequel,
  ni avec qui.
Le danger : tout s'arrete d'un coup. Etre en l'air a cet instant coute cher.

### Orage Prismatique *(agressive)*
Frappes annoncees par une pulsation au sol (~2,5 s), 10 degats dans un rayon
de 3,5 -- et la **Surcharge** (Force II, Vitesse II, 30 s) a tout joueur dans le
rayon. La seule meteo ou l'on cherche a etre touche.

### La Maree Prismatique *(implementee)*
A partir de la **36e minute**, le rayon vivable descend de **750 a 120 blocs**
(60e minute), centre sur le village. Dehors on **survit, mais mal** : une
corrosion magique graduee par la profondeur (~1 coeur pres du bord, jusqu'a 4
loin dedans, toutes les 2 s) et la Faiblesse. Sortir de deux blocs reste
anodin ; s'enfoncer de deux cents devient une expedition. Les **Jambieres de
Maree** annulent tout. Barre de boss violette avec le rayon courant, mur de
brume prismatique visible pres du bord.

### Commandes d'essai
`/arcencium weather <brume|aurore|nuit|meteores|dechirure|orage|embellie> [secondes]`,
`/arcencium weather stop`, `/arcencium skip <minutes>` (avance l'horloge :
phases et Maree).

### Destruction de decor
Les meteores appliquent la regle de resistance : blocs vanilla fragiles
destructibles, **materiaux du mod intouchables**.

---

## 7. Les artefacts

### 7.1 Notre systeme — un artefact par emplacement

**Convention de nommage** : uniquement des objets et des mecanismes. Rien qui
evoque un etre vivant, une benediction, une malediction ou une divinite. Un
artefact est une piece qu'on sertit, pas une faveur qu'on recoit.

**Six emplacements** : casque, plastron, jambieres, bottes, epee, arc.
Quatre artefacts possibles par emplacement, tous a effet **comportemental**
(pas de simples bonus de statistiques : c'est ce qui nous distingue des gemmes d'Apotheosis).

**Casque, la perception**
- **Lentille du Prisme** : voit ancres, coffres et artefacts a travers les murs, a 40 blocs.
- **Filtre de Brume** : immunise aux degats des meteos agressives.
- **Repere d'Echo** : voir 7.2.
- **Lentille d'Aurore** : vision nocturne, monstres luisants dans le noir.

**Plastron, la survie**
- **Plaque de Gangue** : le coup fatal laisse a 1 PV (recharge 3 min).
- **Coque Prismatique** : absorbe les degats, libere une onde de choc une fois pleine.
- **Reservoir de Prisme** : regeneration lente permanente, doublee hors combat.
- **Plastron de Resonance** : +5 % de degats par coup recu, jusqu'a +50 %.

**Jambieres, le controle**
- **Lest de Gangue** : immunite au recul, insensible a la tornade.
- **Jambieres de Maree** : la Maree Prismatique ne ronge plus, permet de rester dans la zone qui se ferme.
- **Champ de Cristal** : ralentit les ennemis a moins de 4 blocs.
- **Renfort de Siege** : +40 % d'armure pendant un siege d'ancre.

**Bottes, le deplacement**
- **Semelle de Prisme** : vitesse +20 %.
- **Bottes d'Eclair** : double saut.
- **Semelle Vaporeuse** : marche sur l'eau et la lave.
- **Bottes de Retour** : teleportation a l'ancre active la plus proche (recharge 2 min).

**Epee, le corps-a-corps**
- **Regulateur de Lame** : la Fureur Cristalline monte deux fois plus vite.
- **Lame de Chaine** : les coups touchent aussi les ennemis adjacents.
- **Drain de Cristal** : 15 % de vol de vie.
- **Eclat Final** : tuer un ennemi declenche une explosion prismatique.

**Arc, la distance**
- **Tension Rapide** : charge complete en deux fois moins de temps.
- **Fleche Fourchue** : le tir a pleine tension part en trois fleches.
- **Marque Prolongee** : la Marque Prismatique dure trois fois plus longtemps.
- **Fleche Tracante** : les fleches inflechissent leur course vers la cible.

### 7.2 Le Repere d'Echo (regle particuliere)

- **Actif uniquement pendant les sieges d'ancre.**
- **Une fois par siege** (equivaut au cooldown de 15 min evoque, mais lisible sans minuteur invisible).
- Au debut du siege, il **designe un elite** parmi les assaillants (un Meneur).
- Le tuer avant la fin de la vague donne **un artefact garanti**, beaucoup d'XP,
  et un **buff pour toute l'equipe**. Le laisser filer ne donne rien.

### 7.3 Sertissage

- Se fait a **l'Etabli de Sertissage** (section 8.4).
- **Amovible, mais l'artefact retire est detruit.** *(recommandation, a confirmer)*

### 7.4 Les artefacts du modpack

**Artifacts** (49 objets) et **Relics** (30 objets) sont des accessoires Curios :
ils occupent des emplacements **separes** des notres et s'y ajoutent.

- **On garde tout, y compris les objets comiques** (decision utilisateur).
- Relics monte de niveau a l'usage, avec un arbre de capacites : excellente courbe
  de progression pour une partie de 45 minutes.

Un personnage complet = **6 sertissages + accessoires Curios**.

---

## 8. Equipement et fabrication

### 8.1 A creer

| Objet | Etat |
|---|---|
| Epee d'Emeraude | **existe** |
| Arcencium Bow | **existe** |
| **Heaume d'Arcencium** (armure 3) | **fait** |
| **Plastron d'Arcencium** (armure 9) | **fait** |
| **Jambieres d'Arcencium** (armure 7) | **fait** |
| **Greves d'Arcencium** (armure 3) | **fait** |

Statistiques **legerement au-dessus de la netherite**, sur tous les tableaux :
protection 22 contre 20, tenacite 3,5 contre 3,0, resistance au recul 0,15
contre 0,10, durabilite facteur 45 contre 37, enchantement 22 contre 15.
| **Sceptre d'Arcencium** | **fait**, voir 8.3 |
| **Coffre d'Arcencium** simple + double | **fait** |
| **Etabli de Sertissage** | **fait** |
| **Lame du Serment** (ceremonielle, voir 3.1) | a faire |

**Bonus de set complet, « Resonance Prismatique »** : la Fureur Cristalline ne
retombe plus a zero quand elle expire, elle redescend d'un cran.

### 8.2 Derives de l'Arbre de Prisme

Aucune piece d'Arcencium n'est fabricable sans passer par l'arbre.

- **Branche de Prisme** : le manche. Epee, arc, sceptre.
- **Fibre de Prisme** : la doublure. Casque, plastron, jambieres, bottes.

### 8.3 Le Sceptre d'Arcencium

Troisieme piece de la triade : l'epee est la **Fureur**, l'arc la **Tension**,
le sceptre la **Concorde**.

**Design** : reprend l'epee a la lettre. Hampe sombre, couronne doree ailee
reprenant la garde, cristal prismatique en levitation dans la couronne.
Les cinq cristaux de Fureur deviennent un **anneau de cinq eclats** autour de la
couronne, qui **s'allument un par un pour afficher le rechargement**
(le cooldown se lit sur l'objet, sans interface).

**Clic gauche** : trait prismatique lent.
- 2,5 degats sur un ennemi (epee ~7, arc jusqu'a 6). **Volontairement plus faible.**
- 1 coeur rendu a un allie touche.
- Anti-abus : un meme allie ne peut etre soigne qu'une fois toutes les **1,5 s**.

**Clic droit, l'Onde de Concorde** (rayon 8, recharge 25 s)
- Repousse les monstres.
- Allies : **Regeneration II 8 s** et **+8 % d'armure 15 s**.

### 8.4 L'Etabli de Sertissage

**Seul moyen d'installer un artefact dans une piece d'equipement.**
Fabrique en **Planches Cristallisees + Arcencium**.

C'est la piece qui verrouille la complementarite : les artefacts arraches a la
tempete ne servent a rien sans avoir abattu des Arbres de Prisme.

---

## 9. Economie

### 9.1 Les trois activites complementaires

| Source | Donne | Ne donne pas |
|---|---|---|
| **Grottes** | l'**Arcencium**, monnaie des ancres | aucun artefact |
| **Tempetes** | les **artefacts**, l'equipement enchante | pas d'Arcencium en quantite |
| **Bois** | l'**Etabli de Sertissage** | rien de combattif |

Liens secondaires du bois : les abris (les meteos rendent le bati utile), et les
**Arbres de Prisme qui ne poussent pas partout** (bosquets, pres des villages).

### 9.2 Ou trouver l'Arcencium

| Source | Rendement | Cout |
|---|---|---|
| Filons en grotte | 1-3 lingots / veine | pioche en diamant, exploration. Sur mais lent. |
| Cathedrale, dernier etage | 3-8 lingots + equipement enchante | monter 250 blocs. Dangereux, tres rentable. |
| Crateres de meteorites | 1-2 fragments | sortir sous la tempete. |
| Vaisseau, coffres au tresor | 3-7 lingots | le trouver et y monter. |

**A faire : reduire la frequence de generation du minerai** (actuellement 4 veines/chunk, trop genereux).

### 9.3 Butin en tempete

Un monstre lache un artefact **si et seulement si** :
1. une meteo agressive est active, **et**
2. il meurt **a ciel ouvert** : verification `level.canSeeSky(pos)`, le meme test
   que la pluie vanilla. Sous un arbre, sous un toit, en grotte : rien.

| Ancres actives | Artefact | Equipement enchante | Niveau du butin |
|---|---|---|---|
| 0 | 4 % | 10 % | fer, ench. I |
| 1 | 8 % | 15 % | fer/diamant, ench. I-II |
| 2 | 14 % | 20 % | diamant, ench. II-III |
| 3 | 22 % | 25 % | diamant/arcencium, ench. III-IV |

Les **elites lachent toujours** : 40 % artefact, 60 % equipement.

Le niveau de l'equipement est fourni par les **affixes d'Apotheosis**, branches sur
le nombre d'ancres activees. Rien a ecrire.

### 9.4 Experience

- **x3** sur tous les gains d'XP dans le mode.
- **x5** sur les monstres tues sous une tempete, a ciel ouvert.
- Le minerai d'Arcencium en donne davantage.
- Grosse dotation a chaque siege reussi (l'Echo de la Victoire).

Les **couts d'enchantement ne sont pas touches** : l'acceleration de l'XP suffit,
et ne casse pas la compatibilite avec le modpack.

---

## 10. Le vaisseau, la Racine de Prisme

Probleme : le vaisseau flotte dans le ciel, inatteignable, et hors budget-temps.

**Solution retenue (option A)** : le vaisseau est retenu au sol par une **immense
racine de cristal** descendant jusqu'a la terre.
- Visible de tres loin : donne une raison de lever les yeux des la premiere minute.
- **Escaladable** (~1 a 2 min de montee).
- **Gardee a sa base.**

**Recompense** pour qu'il vaille le detour : un **artefact garanti** plus une reserve
d'Arcencium suffisante pour **payer une ancre entiere**. C'est un troisieme chemin,
un raccourci risque qui court-circuite le minage.

---

## 11. L'interface

Exigence explicite de l'utilisateur : « une interface un minimum travaillee qui ne
soit pas tres moche ».

| Element | Rendu |
|---|---|
| **Chronometre** | Haut a GAUCHE (le centre est pris par Jade, les barres de boss et les titres), cadre prismatique dessine a la main, nom de la phase en dessous. Couleur du vert au rouge a mesure que le temps s'epuise. Visible toute la partie. |
| **Compteur de monstres** | Barre de boss segmentee (natif, fourni par Gateways). |
| **Maree Prismatique** | Barre de boss. |
| **Annonces majeures** | Titres plein ecran natifs. |
| **Les trois ancres** | En permanence sous le chronometre : distance et direction cardinale, vertes une fois tenues. Un titre passe, une position doit rester consultable. |

Maquette du chronometre a produire **avant** de coder l'interface.

---

## 12. Les mods tiers utilises

| Mod | Role dans notre mode |
|---|---|
| **Gateways to Eternity** | **Le moteur des sieges.** Vagues, composition, modificateurs d'attributs par vague, recompenses, temps limite, barre de boss avec compteur, laisse anti-fuite. Entierement pilotable en JSON. |
| **Apotheosis** | Les **affixes** : equipement a prefixes aleatoires, six rangs de rarete. Branche sur le nombre d'ancres. |
| **Apothic Spawners** | Peuplement de la cathedrale (plus tard). |
| **Lootr** | Coffres a contenu **par joueur**. Supprime la course au coffre en multijoueur. |
| **Artifacts** + **Relics** | 79 accessoires Curios, en plus de nos sertissages. |
| **Cataclysm, Iron's Spellbooks, Undergarden, Twilight Forest, Deeper Darker** | Les six factions et les elites. |
| **Waystones, JourneyMap** | Confort. Deja presents dans l'instance. |
| **Curios** | API des emplacements d'accessoires. |

### Le modpack dans l'environnement de developpement

`tools/dev_mods.py` copie des mods du modpack vers `run/mods/` en resolvant leurs
dependances transitivement -- un jar seul refuse de demarrer si l'une manque.

```
python tools/dev_mods.py gateways apotheosis cataclysm irons_spellbooks curios jade
```

Regle de travail : **quand le projet a besoin d'une fonctionnalite, on prend ce
qui existe dans le modpack plutot que d'ecrire une alternative maison.**

Deja installes : Gateways, Apotheosis (+ Attributes, Enchanting, Spawners),
Cataclysm (+ LionfishAPI), Iron's Spellbooks, Curios, GeckoLib, Placebo, Jade,
Tombstone, JourneyMap, Waystones, Balm.

**Tombstone** change la penalite de mort pour le mieux : une tombe avec une cle
plutot que des objets au sol, qui disparaissent au bout de cinq minutes -- fatal
sur une partie de 45 minutes.

**Apotheosis fait deja les escouades** : ses `apothic_elites` acceptent des
`supporting_entities`, soit un meneur avec ses gardes -- exactement la structure
prevue en 5.2, et c'est ce meneur que designera le Repere d'Echo. Ses
`apothic_invaders` fourniront les elites a equipement et butin dedies.

### Dependances souples

Verifier au demarrage quelles entites existent reellement
(`BuiltInRegistries.ENTITY_TYPE.containsKey`). Les factions absentes sont retirees
du tirage, avec un **repli vanilla** (pillards, squelettes, evokers) si aucune n'est
disponible. Le mod reste jouable seul et devient meilleur dans le modpack.

---

## 13. Le modpack livrable

Objectif : un pack importable dans CurseForge contenant tous les mods necessaires.

L'instance `All the Mods 10 - CUSTOM` contient un `minecraftinstance.json` avec les
**446 identifiants projet/fichier CurseForge**, ce qui permet de generer un
`manifest.json` valide automatiquement.

```
EmeraldWeapons-Pack.zip
├── manifest.json        mods references par ID CurseForge
├── modlist.html
└── overrides/
    ├── mods/            notre jar (absent de CurseForge)
    ├── config/          reglages Gateways, Apotheosis, Lootr...
    ├── defaultconfigs/
    └── resourcepacks/   reskin prismatique des boss (optionnel)
```

Import via *Creer un profil personnalise → Importer*.

A ecrire : `tools/export_modpack.py`.

---

## 14. Ordre d'implementation

### Etape 1 — Les fondations *(faite)*

L'armure est **derivee de la netherite vanilla** : noircie, puis gravee d'un
reseau de fissures ramifiees qui brillent en arc-en-ciel. Les icones
d'inventaire s'animent par .mcmeta (12 images) ; l'armure PORTEE s'anime via un
calque de rendu maison (`ArcenciumArmorLayer`), les calques d'armure ne passant
pas par un atlas. Genere par `tools/armor_textures.py`.

- [x] **Branche de Prisme** et **Fibre de Prisme** (items + recettes + textures)
- [x] **Armure d'Arcencium** : 4 pieces, materiau d'armure, textures d'objet, texture de calque porte
- [x] Recettes des 4 pieces (Arcencium + Fibre)
- [x] **Bonus de set « Resonance Prismatique »**
- [x] Ajouter Branche/Fibre aux recettes existantes de l'epee et de l'arc
- [x] Onglet creatif, tags, datagen, langue FR/EN

### Etape 2 — Le Sceptre d'Arcencium *(faite)*

Variante **S2** (couronne ouverte). Clic gauche : trait prismatique (2,5 degats,
ou 1 coeur rendu a un allie, plafonne a un soin par allie et par 1,5 s).
Clic droit : Onde de Concorde. Les cinq eclats du bandeau affichent le
rechargement via le predicat de modele `emeraldweapons:charge`.

- [x] Maquette de la texture **a valider avant le code**
- [x] Projectile prismatique (degats 2,5 / soin 1 coeur, cooldown 1,5 s par allie)
- [x] Onde de Concorde (rayon 8, repousse, Regeneration II 8 s, +8 % armure 15 s, recharge 25 s)
- [x] Anneau de 5 eclats qui s'allument selon le rechargement
- [x] Recette (Arcencium + Branche de Prisme)

### Etape 3 — Le coffre et l'etabli

Le coffre est pose. Sa texture vient de la planche de matiere plaquee sur le
gabarit vanilla (`tools/chest_textures.py`), loquet dore conserve pour qu'il
reste lisible. `Sheets.chooseMaterial` ne connaissant que les coffres vanilla
et n'etant pas surchargeable, `ArcenciumChestRenderer` reprend le rendu de
ChestRenderer avec nos materiaux. Aucun fichier d'atlas n'a ete necessaire :
la source du sheet vanilla balaie `entity/chest` de tous les namespaces.

- [x] **Coffre d'Arcencium** simple et double : modele, block entity, texture
- [x] **Etabli de Sertissage** : bloc, interface, logique de sertissage/retrait

### Etape 4 — Le systeme d'artefacts

Les 24 artefacts existent et fonctionnent. Un SEUL objet les porte tous :
l'artefact est un composant de donnees et le modele choisit sa texture par le
predicat `emeraldweapons:variant`. Chaque texture part d'une silhouette vanilla
noircie, teintee et sertie d'or.

Deux artefacts restent inertes tant que le mode de jeu n'existe pas :
**Filtre de Brume** (pas de meteo) et **Jambieres de Maree** (pas de Maree).
Deux autres ont une version generale en attendant : **Renfort de Siege** se
declenche des que trois ennemis pressent le porteur, et **Bottes de Retour**
vise le point de reapparition -- qui SERA l'ancre, celles-ci faisant office de
points de reapparition dans le mode.

Le sertissage n'accepte que notre equipement : armure d'Arcencium, Epee
d'Emeraude, Arc et Sceptre. Aucun equipement vanilla ni d'un autre mod.

- [x] Composant de donnees « artefact serti » sur l'equipement
- [x] Les 24 artefacts, par emplacement
- [ ] Regle particuliere du Diademe d'Echo
- [ ] Tables de butin (coffres + tempete)

### Etape 5 — La boucle de jeu minimale *(en cours)*

L'ossature est posee : `GameState` (SavedData attachee au surmonde, donc une
partie survit a un arret du serveur), `GamePhase` avec ses cinq paliers, un
diffuseur d'etat une fois par seconde, le chronometre a l'ecran, et la commande
`/arcencium start|stop|status` pour eprouver la boucle sans rejouer le prologue.

Le temps est compte en **ticks de monde** et non en horloge reelle : la partie
se fige avec le serveur au lieu d'expirer pendant qu'il est eteint.

- [ ] Preregle de monde « Arcencium », bordure a 750
- [x] **Lame du Serment** plantee au centre, villageois autour
- [x] Confinement avant declenchement (sortie bloquee, minage bloque)
- [x] Retrait de la lame = declencheur de partie
- [x] Buff d'equipe « le Serment vous lie » au retrait
- [x] Rappel a l'ecran apres 60 s sans retrait
- [x] Dissolution de la lame a la fin du siege -> apparition des ancres
- [x] Prologue au village (Gateways)
- [x] Les 3 ancres : placement, faisceau, rituel, palier par rang d'activation
- [x] Sieges via Gateways, 6 factions tirees au sort
- [x] Ancres comme points de reapparition
- [x] Maree Prismatique
- [ ] Arc-en-ciel, arene, boss tire parmi les 3
- [ ] Conditions de victoire et de defaite
- [x] Interface : chronometre (titres et barres a venir)

### Etape 6 — Meteo et economie

- [x] Les 6 meteos, globales, avec progression par ancres
- [x] Preavis de 15 s, Embellie, abris surs
- [x] Resistance des materiaux a la destruction
- [ ] Butin de tempete (`canSeeSky`), multiplicateurs d'XP
- [ ] Reduction de la frequence du minerai d'Arcencium

### Etape 7 — Structures et finitions

- [ ] Racine de Prisme sous le vaisseau + tresor
- [ ] Villages hostiles (pillards vanilla + villageois corrompus)
- [ ] Peuplement de la cathedrale (Apothic Spawners)
- [ ] `tools/export_modpack.py` et le pack CurseForge

---

## 14 bis. A FAIRE EN PRIORITE — le placement des ancres

**Constat.** Les ancres sont posees a 450 blocs du village, sur trois directions
a 120 degres, en cherchant un sol degage dans un rayon de 16 blocs. Rien ne
garantit qu'elles tombent dans une region ACCESSIBLE : elles peuvent echouer au
fond d'un ravin, sur un pic, en pleine mer, ou dans un biome infranchissable a
pied. Une ancre qu'on ne peut pas atteindre bloque toute la partie.

**Direction retenue.** Ne plus poser un bloc nu sur le terrain, mais **au sommet
d'une construction** -- pyramide, temple, socle a degres -- qui garantit a la
fois la visibilite de loin et un chemin pour monter. Les marches font l'acces,
la hauteur fait le repere.

**Candidats reperes dans le modpack** (structures deja taillees pour cela) :

| Mod | Structure | Interet |
|---|---|---|
| L_Ender's Cataclysm | `cataclysm:cursed_pyramid` | pyramide franche, sommet plat |
| AllTheModium | `allthemodium:ancient_pyramid` | tres grande, escaliers exterieurs |
| Explorify | `explorify:badlands_pyramid`, `desert_shrine` | petites, faciles a reskiner |
| Structory Towers | `structory_towers:ancient_temple`, `sacred_relic_temple` | soignees, echelle moyenne |
| Yung's | `betterdeserttemples:desert_temple`, `betterjungletemples:jungle_temple` | acces amenages, tres lisibles |
| Dungeons Arise | `dungeons_arise:abandoned_temple`, `infested_temple` | les plus spectaculaires |

**Pistes de travail.**
1. Extraire une de ces structures au format .nbt (`tools/nbt_structure.py` sait
   deja lire les jars du modpack) et la reskiner a la palette d'Arcencium
   (`tools/reskin.py`).
2. La poser sous chaque ancre, l'ancre au sommet, via le `CenteredTemplateStructure`
   ecrit pour la cathedrale -- il pose deja un template entier sans jigsaw.
3. Valider l'accessibilite : pas d'ancre en mer, pas d'ancre dans un ravin, et
   un chemin praticable depuis le sol.
4. A defaut de structure, batir un socle a degres a la main, comme celui de la
   Lame du Serment mais plus haut.

---

## 15. Questions ouvertes

1. **Artefact amovible ou definitif ?** Recommandation : amovible, mais l'artefact retire est detruit.
2. **La perte totale de l'Arcencium** en cas d'echec de siege est-elle trop punitive ?
3. **Maquette du chronometre** et **maquette du sceptre** a valider avant codage.
4. Duree exacte de la partie (45 ou 50 min) et rayon (750) : **a valider en jouant**.

---

## 16. Decisions actees (ne pas rediscuter)

- Le palier d'ancre depend du **rang d'activation**, pas de l'ancre.
- La meteo est **globale**.
- Les artefacts ne tombent qu'**a ciel ouvert**, sous une tempete.
- Le **Warden est ecarte** comme boss final ; tirage entre Ignis, Ender Guardian, Twilight Lich.
- **Les objets comiques d'Artifacts sont conserves.**
- Le **spawn vanilla reste intact**.
- Les sieges utilisent **Gateways**, pas un systeme maison.
- Le mode devient un **modpack livrable**.
- La partie **ne demarre pas au spawn** mais au **retrait de la Lame du Serment**,
  pour qu'aucun joueur ne rate l'annonce.
- Au retrait : **buff d'equipe « le Serment vous lie »**, et **rappel a l'ecran
  apres 60 s** si la lame reste plantee.
