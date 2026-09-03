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

## 6 bis. L'ambiance des meteos *(implementee)*

Le premier essai a rate sur un point unique : on voyait qu'il se passait
quelque chose, on ne le RESSENTAIT pas. Trois regles en sont sorties, valables
pour toute meteo qu'on ajoutera ensuite.

**Une tempete peuple le monde.** Les apparitions naturelles sont plafonnees par
la lumiere et par le nombre de mobs deja charges : sans pression propre, une
tempete ne change rien a ce qu'on croise, et « au final c'est une nuit
normale ». Chaque agressive pose donc la sienne, par apparition d'EVENEMENT
(qui echappe aux regles de lumiere), plafonnee a douze par joueur, au palier de
la phase. Ces monstres portent une etiquette : ce sont eux qui paient.

**Un coup doit toucher le joueur, pas le decor.** Eclat d'ecran et secousse de
camera, avec une force qui decroit sur la distance -- ce qui suffit a situer
l'evenement sans rien afficher. Un eclair a vingt blocs lave l'ecran, le meme a
soixante n'est qu'un frisson.

**Chaque meteo doit avoir sa couleur d'air.** Brouillard et particules propres a
chacune, y compris pour celles qui n'en avaient pas.

| Meteo | Brouillard | Ambiance |
|---|---|---|
| Brume | pastel derivant, 6-56 | motes de prisme |
| Aurore | aucun, volontairement | rubans de GEOMETRIE (voir plus bas) |
| Nuit | bleu nuit, 96 | pluie prismatique, eclairs, eclats a l'horizon |
| Meteores | cendre chaude, 180 | braises qui montent, cendre qui descend |
| Dechirure | violet, 140 | tout monte : poussiere, eclats, bourdonnement |
| Orage | pourpre, 84 | etincelles, souffle grave, tremblement de fond |

**Le repere de RenderLevelStageEvent est relatif a la CAMERA.** Un sommet en
(x, y, z) atterrit a (camX + x, camY + y, camZ + z). Y passer des coordonnees du
monde dessinait l'aurore a peu pres au DOUBLE de la position du joueur -- des
centaines de blocs plus loin, et a l'altitude 62 plutot qu'au-dessus de lui. Il
n'en restait qu'un lisere lointain, ce qui ressemblait a un probleme d'echelle
alors que c'etait un probleme de place. A retenir pour tout rendu a venir.

**L'Aurore est en geometrie, pas en particules.** Une premiere version en posait
trois par tick dans un volume de quatre-vingts blocs de cote, soit une pour
mille metres cubes : invisible par construction. Et meme en multipliant, des
points epars ne font pas un rideau. Neuf rideaux continus sont donc dessines en
melange additif, lumineux en bas et evanescents en haut. C'est le seul effet du
mode qui ne soit pas fait de particules, et c'est voulu.

Ils sont EVENTES, pas paralleles : paralleles, on tombait selon l'orientation
soit sur un ciel raye, soit sur une seule ligne vue dans l'axe. Et l'Aurore
teinte l'air d'un indigo leger -- sans quoi un melange additif se noie dans un
ciel de plein jour -- avec une lueur qui monte du sol et un carillon de fond,
pour qu'elle se remarque meme en regardant ses pieds.

**La pluie de la Nuit est VRAIMENT coloree.** Le moteur la dessine en blanc pur
sans point d'entree pour la teinter : la seule facon d'y arriver est de fournir
notre propre texture, ce qui vaut alors pour toute pluie du monde. Acceptable
parce que le mode COUPE le cycle meteo vanilla a la mise en place -- il ne pleut
plus que quand la Nuit le decide. Ce choix vaut aussi par lui-meme : une averse
tiree par le jeu pendant une Aurore brouillait une meteo qu'on venait
d'annoncer.

**Les eclats de la Dechirure sont a quatre-huit blocs, pas douze-vingt.** Un
bond en apesanteur culmine vers quatre blocs : au-dela, la recompense etait
decorative. La gravite descend a -78 % pour tenir la promesse des « bonds de
cinq blocs », et chaque eclat se signale par un halo et une colonne jusqu'au
sol.

## 6 ter. Apotheosis : paliers et butin *(implemente)*

Apotheosis se joue sur des heures et son pack de quetes ouvre ses paliers un a
un ; une partie en dure une. Tout est donc deverrouille a la main.

**Les paliers de monde.** C'est LE systeme de progression du mod, et il gouverne
l'apparition des Envahisseurs -- ces « boss » qui sont des monstres ordinaires
nommes, rares et equipes. Au palier de depart, Haven, leur chance vaut ZERO :
c'est pourquoi on n'en voyait aucun. Ils s'ouvrent maintenant au rythme des
phases (Frontier des l'Exploration, Pinnacle a l'Assaut) par leurs avancements,
qui sont du vanilla et ne demandent aucune dependance.

**Rien a activer.** Le palier ACTIF vit dans un attachement d'Apotheosis :
accorder l'avancement ouvre la porte mais laisse un CTRL+T que le mode ne doit
pas exiger. Apotheosis est donc une dependance de COMPILATION (`compileOnly`,
prise dans `run/mods`, jamais embarquee dans le jar), et le mode ecrit le
palier lui-meme par `WorldTier.setTier` -- qui pose l'attachement, previent le
client et remplace les augments du palier precedent.

Les avancements restent accordes en plus : sans eux, l'ecran de selection
d'Apotheosis afficherait comme verrouille le palier ou le joueur se trouve
deja. Et le palier ne DESCEND jamais : un joueur qui en a active un plus haut
de lui-meme le garde.

Toutes les citations de classes d'Apotheosis vivent dans
`com.emerald.compat.ApotheosisTiers`, et l'appelant verifie `ModList` avant d'y
toucher : tant qu'il n'y touche pas, la classe n'est pas chargee, et l'absence
du mod ne coute rien.

**Nos propres regles d'apparition** relevent la chance a tous les paliers, Haven
compris, pour qu'il en sorte des le debut ; le delai entre deux Envahisseurs
passe de trois minutes a trente secondes.

**La Chance** monte de deux a dix points selon la phase : c'est le levier
documente pour la rarete du butin.

**Les materiaux** tombent des monstres de tempete (un sur quatre) et de ceux de
la Maree (un sur deux), a une rarete centree sur la phase. Les sigils et vials
suivent, plus rarement. Sans quoi le systeme resterait un decor qu'on n'a jamais
les moyens d'utiliser.

## 6 quater. La Maree est habitee *(implementee)*

Une zone qui se contente de faire mal se contourne : on n'y va pas, et elle ne
raconte rien. Hors du rayon vivable apparaissent donc des monstres du dernier
palier, et de loin en loin (une fois sur quatorze) un **seigneur de passage**,
annonce a cent blocs a la ronde.

Ce ne sont **pas** les trois boss de fin : Ignis, l'Ender Guardian et le Liche
du Crepuscule restent pour le sommet de l'Arc-en-ciel, et les voir avant leur
heure userait l'evenement. Les seigneurs sont pris chez Cataclysm --
Monstruosite de netherite, Harbinger, Coralssus, Maledictus, Remnant ancien,
Prowler, Wadjet -- avec repli sur le Ravageur si le mod manque.

C'est aussi le seul endroit ou les materiaux rares tombent le mieux : la Maree
devient un endroit ou l'on ENTRE, pas seulement une zone qu'on subit.

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

**Glaive, la fureur**

Les quatre repondent aux quatre systemes de l'arme -- la Rage, la Ruee, la
Curee, l'immobilisation -- de sorte qu'aucun ne fasse doublon et que le choix
soit un choix de style et non de puissance.

- **Cran d'Arret** : la Rage ne retombe plus d'un coup, elle perd un cran a la
  fois. Change la NATURE de la retombee et non sa vitesse : le meme budget de
  temps, depense autrement -- de quoi contourner un mur ou changer de cible
  sans repartir de zero.
- **Ruee en Chaine** : un second bond qui touche en ouvre encore un autre,
  **jusqu'a trois**. Le plafond n'est pas une precaution mais la condition pour
  que l'artefact reste un artefact : sans lui, un troupeau de zombies devient
  un moteur de deplacement infini et l'arme n'est plus un corps-a-corps.
- **Onde de Curee** : la Curee porte 1,7 fois plus loin et rend deux fois plus
  de vie. Portee ET plafond montent ensemble a dessein -- elargir seulement le
  cercle serait un gain de degats deguise.
- **Etau de Gangue** : l'immobilisation de la Ruee gagne les ennemis a quatre
  blocs de la cible. Reponse a la seule facon de mourir avec cette arme : etre
  encercle au moment ou l'on bondit.

> Les tables de butin des sanctuaires **lisent desormais l'enumeration Java**
> (`tools/sanctuary_loot.py`). La liste y etait recopiee a la main et avait
> deja pris du retard -- une recompense qui n'existe que dans l'onglet creatif
> n'est pas une recompense.

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

### Le shader du mode

Le mode se joue avec un shader realiste : **Complementary Unbound r5.5.1 +
Euphoria Patches 1.6.4**, pris dans l'instance ATM10 du joueur (c'etait deja
son choix dans son profil principal). Eau realiste, rayons de lumiere,
ombres et eclairage physiques, et surtout la meilleure compatibilite moddee
de sa categorie -- ce qui compte pour un mod qui dessine ses propres
geometries et ses propres brumes. Photon v1.1 (plus lourd, plus strict) reste
l'alternative si l'on veut plus de realisme brut.

Fichiers en place dans l'environnement de dev : `run/shaderpacks/` (le zip de
base et le dossier patche), `run/config/iris.properties` (`enableShaders=true`,
`shaderPack=ComplementaryUnbound_r5.5.1 + EuphoriaPatches_1.6.4`). Dans le
pack livrable, les memes fichiers vont dans `overrides/`.

**Ce que le lancement a appris** : Iris 1.8.8 NeoForge declare Embeddium
incompatible (toutes versions) et **exige Sodium 0.6 pour NeoForge au
demarrage** (`NoClassDefFoundError: net.caffeinemc.mods.sodium...`), bien que
son descripteur ne le declare pas. Sodium NeoForge n'etait nulle part sur la
machine : `sodium-neoforge-0.6.13+mc1.21.1.jar` a ete telecharge depuis
Modrinth avec l'accord du joueur, empreinte SHA-512 verifiee contre celle de
l'API. `run/mods` contient donc Sodium 0.6.13, Iris 1.8.8 et Euphoria
Patcher 1.6.4. Pour le pack livrable base sur ATM10, cela veut dire **Sodium
a la place d'Embeddium** -- a verifier que rien du pack n'exige Embeddium.

### Verifier un lancement sans casser la session du joueur

`runClientWorld` (build.gradle) entre directement dans la sauvegarde `test`
(`--quickPlaySingleplayer`), ce qui force le pack de shaders a compiler ses
programmes -- l'ecran-titre ne le fait pas. Le script de verification qui
l'accompagne obeit a trois regles apprises a la dure :

1. **S'il existe deja une fenetre Minecraft, il ne lance rien.** Le joueur
   teste souvent depuis le meme dossier `run/` au moment ou l'on travaille ;
   un premier script a ferme sa session en pleine Nuit d'Arcencium.
2. **Il ne supprime pas `latest.log`** (le jeu le fait tourner lui-meme) et ne
   croit une ligne du journal que si son heure est posterieure au lancement.
3. **Il ne ferme que son propre arbre de processus** (`taskkill /PID /T`),
   jamais une fenetre par son titre.

**A verifier avec le shader actif** : nos brumes par meteo passent par
`ViewportEvent.ComputeFogColor` / `RenderFog`, que les packs de shaders
recalculent a leur maniere ; et nos geometries en `lightning` / `debugQuads`
(aurore, failles, arcs, fissures) prennent le programme que le pack leur
attribue. Ce qui serait perdu se voit en jeu, pas dans le code.

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
    ├── resourcepacks/   reskin prismatique des boss (optionnel)
    ├── shaderpacks/     Complementary Unbound + Euphoria Patches
    └── config/iris.properties
```

Import via *Creer un profil personnalise → Importer*.

### 13.1 `tools/export_modpack.py` *(ecrit, verifie)*

```
python tools/export_modpack.py            # pack complet
python tools/export_modpack.py --slim     # sans kubejs/assets, ~145 Mo de moins
```

Il lit le `minecraftinstance.json` de l'instance CUSTOM -- **on n'invente aucun
identifiant**, on prend ceux que CurseForge tient a jour -- et separe les mods
en deux : ceux que CurseForge sait retrouver seul (439, nommes dans le
manifeste) et ceux qui n'y sont pas (8, embarques dans `overrides/mods`) :
Distant Horizons 2.4.5, Better Combat, CC:Tweaked, EMF, ETF, Not Enough
Animations, playerAnimator, et **notre jar**, pris dans `build/libs` pour que
le pack porte toujours la derniere compilation.

Les overrides emportent `config` (donc la config DH reglee et `iris.properties`),
`defaultconfigs`, `kubejs`, `resourcepacks` (Fresh Animations), `datapacks`,
`options.txt` (donc les touches de tri corrigees) et **le seul shader retenu**,
Complementary Unbound + Euphoria Patches. Jamais les sauvegardes, les journaux
ni les captures. Le manifeste annonce `recommendedRam: 16384`.

Mesure : 439 mods references, 8 embarques, 80 Mo d'overrides, **65 Mo** de zip
en `--slim`. Sortie dans `dist/`, ignore par git (refabricable).

### 13.2 L'image du profil

Prompt dans `tools/prompts/image_du_mode.md` : paysage voxel, **aucun etre
vivant**, une pyramide a degres coiffee d'une ancre prismatique, l'Arc-en-ciel
derriere, le mur de brume au loin. Le fichier se depose en `tools/pack/icon.png`
(1024x1024) ; l'export le place a la racine du zip. CurseForge ne lit pas
l'icone d'un pack importe : elle se regle a la main sur le profil.

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

## 14 ter. Le Sanctuaire d'Ancre *(bati, pas encore branche)*

Reponse au probleme du 14 bis. L'ancre est au sommet d'une pyramide de six
gradins, dans une place forte batie en blocs du mode :

- **muraille** 67 x 67, epaisse de 2, haute de 8, chemin de ronde a 6, merlons
  un bloc sur deux, meurtrieres vitrees tous les six blocs, deux rampes
  d'acces ;
- **quatre tours d'angle** octogonales et creuses, hautes de 13 -- octogonales
  parce qu'une tour carree se confond avec le coin du mur ;
- **corps de garde** au sud : deux tours qui encadrent une voute, une herse de
  barreaux, et la poulie, la corde et la manivelle de Supplementaries pour en
  montrer le mecanisme ;
- **pyramide** 25 x 25, six gradins de 2, escalier plein sud du seuil au
  sommet -- c'est lui qui repond au probleme d'origine ;
- **garnison** postee a la construction, pas apparue a l'approche : on compte
  les silhouettes avant d'entrer et on decide par ou passer. Attachee au lieu
  par `restrictTo`.

**La herse obeit au jeu**, et c'est pourquoi elle n'utilise pas la poulie de
Supplementaries pour de vrai : elle retombe quand le siege d'ancre commence --
on est enferme avec ce qui arrive -- et se rouvre quand il est fini. Un
mecanisme a redstone n'aurait pas su faire les deux. A la main, on tourne la
manivelle ; le levier vanilla est accepte en repli, faute de quoi un
Supplementaries manquant rendrait la porte inouvrable.

**Pas encore branche sur la generation reelle.** `/arcencium sanctuary` le batit
sur place pour l'examiner. La raison de ne pas l'avoir cable tout de suite est
chiffree : l'emprise fait pres de cent mille blocs, trois sanctuaires en font
trois cent mille, et les poser tous au demarrage du monde coutera plusieurs
secondes de gel. Il faudra les batir paresseusement -- au premier chargement de
leur zone -- avant de remplacer le placement actuel.

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

## 17. Rarete et runes *(les deux faites)*

Emprunte a NosTale, et volontairement dans cet ordre : la rarete d'abord,
puisque c'est elle qui ouvre les runes.

### 17.1 La rarete — FAIT

Huit rangs au-dessus du normal, sur les ARMES comme sur les ARMURES :

| rang | nom | couleur |
|---|---|---|
| 0 | *(normal, aucun mot)* | blanc |
| 1 | Utile | `#A0C8FF` |
| 2 | Bon | `#6FD1FF` |
| 3 | De bonne qualite | `#5CE68A` |
| 4 | Excellent | `#C8F050` |
| 5 | Ancestral | `#FFD24A` |
| 6 | Mysterieux | `#C77DFF` |
| 7 | Legendaire | `#FF9B3D` |
| 8 | Phenomenal | `#FF4D6D` |

Le mot precede le nom, dans la couleur du rang. Les couleurs sont une
LECTURE de NosTale, non des valeurs relevees : a corriger si le joueur
fournit les vraies.

Chiffres : `+0,40` degat par rang pour une arme, `+0,35` armure par rang
pour une piece d'armure. L'ecart doit rester faible -- le mode dure une
heure, et une arme qui double ses degats la termine seule.

La montee se tente a l'etabli de sertissage avec des **Eclats du Destin**,
qui ne se fabriquent pas. Le tirage garde le MEILLEUR de N jets, un par
eclat ; on ne redescend jamais. Voir `GearRarity`.

**Le bareme a ete resserre deux fois, et mesure a chaque fois.** Le premier
donnait une chance sur trente-deux meme avec une pile pleine -- c'est-a-dire
jamais. Je l'ai desserre, et il est devenu trop large : quarante eclats
suffisaient a la moitie des Legendaires. Une piece de rang huit obtenue en vingt
minutes rend inutile tout ce qu'on trouvera ensuite.

Bareme actuel, par lots de trente-deux, memoire des tentatives comprise :

| Eclats depenses | R7+ | Phenomenal |
|---|---|---|
| 40 (~20 min) | 11 % | 3 % |
| 100 (~35 min) | 29 % | 9 % |
| 200 | 54 % | 19 % |
| 400 (la partie entiere) | 87 % | 43 % |

Le Phenomenal reste POSSIBLE pour qui y consacre toute sa partie, et improbable
pour tous les autres. On peut toujours tenter, jamais compter dessus.

### 17.2 Les runes — FAIT

Barème relevé sur NosTale (Gameforge EU) ; le prompt ayant servi est dans
`tools/prompts/nostale_runes.md`.

#### La structure — ce que j'avais faux

Je croyais qu'une rune portait **une** statistique dont le rang multipliait la
valeur. Le vrai système fait tout autrement :

- une rune porte **plusieurs options** ;
- **le rang ne multiplie rien** — il décide du *schéma* : combien d'options, et
  de quels grades ;
- chaque option a une **fourchette de grades**, plancher **et plafond** :
  « Dégâts critiques » n'existe qu'en C, « Attaque augmentée » va de C à A et
  jamais en S, « Dégâts relatifs » n'existe qu'en S. Une case S ne reçoit que ce
  qui a le droit d'y être — c'est ce qui la rend précieuse ;
- chaque option a **ses propres valeurs par grade**, reprises du relevé
  (attaque 95 / 142 / 190, SL 11 / 17…) et ramenées à l'échelle de Minecraft —
  pas un multiplicateur unique, qui aplatissait tous les rapports ;
- chaque option **tire sa valeur** entre un min et un max. **Les minima ne sont
  pas publics** (le relevé les donne tous « inconnu ») : on prend 60 % du
  maximum du grade. C'est la seule invention du catalogue, et elle est signalée
  dans le code pour être remplacée le jour où l'on aura les vrais.

C'est bien meilleur que ce que j'avais posé : deux runes de même rang ne
diffèrent plus par un chiffre mais par leur **composition**. Une Légendaire aux
mauvaises statistiques peut valoir moins qu'une Excellente bien tombée — et
c'est cette incertitude qui donne envie d'en ramasser une de plus.

#### Le schéma par rang

| Rang | Schéma | Rang | Schéma |
|---|---|---|---|
| 1 Utile | C | 5 Ancestral | CBA |
| 2 Bon | CC | 6 Mystérieux | CBAA |
| 3 Bonne qualité | CB | 7 Légendaire | CBAAS |
| 4 Excellent | CBB | 8 Phénoménal | **CBAASS** |

Le rang 8 est **notre ajout** : le relevé donne le même schéma au 7 et au 8
(CBAAS), ce qui rendrait chez nous le Phénoménal strictement inutile. C'est le
seul endroit où l'on s'écarte de la source.

Multiplicateurs de grade : C ×1,00 · B ×1,40 · A ×1,90 · S ×2,60.

#### Les deux familles

| Famille | Support | C | B | A | S |
|---|---|---|---|---|---|
| **Arme** | l'arme tenue **et le casque** | Tranchant, Chance, Fureur, Syncope, Saignée | Cadence, Allonge, **SL Att.**, **SL Élém.**, Curée, Aubaine | Percée, Acharnement, Cerné | **Ravage**, **Cataclysme**, **SL Générale** |
| **Armure** | les 4 pièces | Carapace, Égide | Endurance, Esquive, **SL Déf.**, **SL HP/MP** | Absorption | **Régénération**, **Sauvegarde** |

17 options pour l'arme, 9 pour l'armure. Le rang 8 tire six options
**distinctes**, il en faut donc au moins sept par famille — vérifié.

#### Le casque tient lieu d'arme secondaire

NosTale équipe une arme **principale** et une arme **secondaire**, toutes deux
runables, et toutes deux avec des runes d'**arme**. Nous n'avons pas d'arme
secondaire : c'est le **casque** qui en tient le rôle, et il prend donc une rune
d'arme, tout simplement.

Le casque accepte les **deux** familles, dans deux emplacements distincts : une
rune d'armure parce qu'il est une pièce d'armure, une rune d'arme parce qu'il
tient lieu d'arme secondaire.

> **J'avais inventé une troisième famille, à tort.** Mon objection était qu'une
> rune d'arme posée sur un casque ferait exactement ce qu'elle ferait sur
> l'arme — mais c'est précisément ainsi que NosTale fonctionne, et cela suffit :
> le casque n'a pas besoin d'un rôle propre, il a besoin d'un **second
> emplacement offensif**. Une famille de plus n'ajoutait qu'un catalogue à
> maintenir.

Les effets **conditionnels** (Syncope, Saignée, Curée, Aubaine, Acharnement,
Cerné, Cataclysme) rejoignent donc la famille Arme — et c'est là qu'ils sont
chez NosTale, qui range la syncope et le saignement en grade C et la
régénération par victoire en grade B. Une rune d'arme peut désormais tomber
franchement offensive ou franchement opportuniste, et deux Légendaires ne se
ressemblent plus du tout.

#### Les SL — le pont avec la fiche du Héros

C'est la pièce que j'avais manquée. Chez NosTale, « SL Attaque 17 » ne donne pas
dix-sept points d'attaque mais **dix-sept niveaux dans la voie**. Nos runes font
pareil :

| Option | Famille | Grade min | Effet |
|---|---|---|---|
| SL Attaque | Arme | B | +4 à +13 niveaux en Attaque |
| SL Élément | Arme | B | +4 à +13 niveaux en Élément |
| SL Défense | Armure | B | +4 à +13 niveaux en Défense |
| SL HP/MP | Armure | B | +4 à +13 niveaux en Vitalité |
| **SL Générale** | Arme | **S** | +2 à +4 niveaux dans **les quatre** |

Ces niveaux **ne se paient pas** et s'ajoutent par-dessus l'achat. Ils peuvent
pousser une voie **au-delà du centième** — jusqu'à 120, plafond relevé de
NosTale — ce qu'aucune dépense de points ne permet.

C'est de loin l'option la plus forte du catalogue. Mesuré en points de fiche
économisés :

| Grade | Sur une voie à 0 | à 50 | à 90 |
|---|---|---|---|
| B | ~7 pts | ~28 pts | ~49 pts |
| A | ~9 pts | ~36 pts | ~66 pts |
| S | ~16 pts | ~55 pts | ~76 pts |

Plus la voie est haute, plus la rune vaut cher — parce que là-haut chaque niveau
coûte jusqu'à dix points. **Un joueur qui a déjà tout dépensé a donc encore une
raison de chercher une bonne rune.**

Deux garde-fous : les **paliers restent bloqués à dix** (le dépassement donne du
linéaire, jamais un palier entier, qu'une rune ne doit pas pouvoir offrir), et
la fiche **sépare à l'écran l'acheté de l'offert** — le prix du niveau suivant se
calcule sur ce qu'on a payé, jamais sur le total.

> Le relevé confirme que les effets **conditionnels** existent bien chez NosTale
> — il cite la syncope et le saignement en grade C, la régénération par victoire
> en grade B. La séparation qu'on avait posée avant de le savoir se trouve donc
> validée, jusque dans le détail des grades.

#### Les trois règles

1. **Une seule rune par emplacement.** Graver, c'est choisir. Le casque a deux
   emplacements — un d'armure, un d'arme — parce qu'il tient le rôle de l'arme
   secondaire de NosTale.
2. **Le rang de la rune ne peut pas dépasser celui de la pièce.** Une pièce
   Phénoménale accepte tout, une pièce Utile n'accepte que de l'Utile. C'est ce
   qui relie les deux systèmes : monter une pièce en rareté **ouvre l'accès** aux
   bonnes runes, ce qui vaut bien mieux qu'ajouter des chiffres.
3. **Graver remplace, et l'ancienne rune est perdue.** Même règle que les
   artefacts : on peut changer d'avis, mais cela coûte.

#### L'obtention — la raison de continuer à tuer

Les runes tombent **des monstres, et d'eux seuls**. Les artefacts dorment dans
les coffres, la rareté se monte à l'établi : ni l'un ni l'autre ne récompense le
combat lui-même. Les runes, si.

Le rang est plafonné par les points de vie maximaux de la bête — même mesure que
pour l'expérience du Héros, seule comparable d'un mod à l'autre :

| PV max | Rang maximum |
|---|---|
| < 15 | 2 |
| < 30 | 3 |
| < 60 | 4 |
| < 100 | 5 |
| < 200 | 6 |
| < 400 | 7 |
| ≥ 400 | 8 |

Sous le plafond, le tirage est **uniforme** — et c'est une correction mesurée.
Je prenais d'abord le plus petit de deux tirages : un rang 8 n'apparaissait
alors que dans 4 parties sur 1000, c'est-à-dire jamais. **Un rang qu'on ne voit
jamais n'est pas rare, il est absent.**

**Mesure sur une partie type** (483 monstres, dont trois boss) : 18 runes
ramassées, un rang 7+ dans **6 %** des parties, un rang 8 dans **3 %**.

Tomber sur une Phénoménale *avec* les bonnes options *et* de bons tirages relève
donc d'une chance considérable. On ne construit pas une partie autour, on s'en
souvient.

#### Un seul jet, pas deux

La fiche du Héros et les runes touchent aux mêmes quatre grandeurs — chance de
critique, dégâts critiques, esquive, critiques subis. **Elles se versent dans
les mêmes totaux** et ne tirent pas séparément. Deux systèmes qui tireraient
chacun le leur donneraient deux coups forts par frappe et deux chances
d'esquiver le même coup, et le joueur ne saurait plus ce qu'il possède.

## 18. Le niveau Heros *(fait, bareme NosTale reel)*

Une progression PARALLELE a celle du jeu, plafonnee a cent, qui rend des points
a repartir entre quatre voies. C'est le modele de NosTale, repris pour la meme
raison : un joueur qui choisit ou mettre ses points se souvient de son
personnage, alors qu'une progression automatique ne se remarque pas.

### 18.1 Le bareme

Le total au niveau cent est FIXE A 486 POINTS, et la table y tombe juste :

| Niveaux | Points par montee | Sous-total |
|---|---|---|
| 2 a 25 | 3 | 72 |
| 26 a 50 | 4 | 100 |
| 51 a 75 | 5 | 125 |
| 76 a 99 | 6 | 144 |
| 100 | 45 | 45 |
| | | **486** |

Le gros lot final est delibere : les derniers niveaux sont les plus longs, et
une recompense plate les rendrait ingrats.

### 18.2 L'experience

`needed(level) = 28 + level * 3/5 + level^2 / 500`, soit six mille trois cents
points en tout. **La courbe a ete mesuree, non estimee** : ma premiere version
en demandait soixante-seize mille — six mille quatre cents monstres — parce que
j'avais ecrit « plus rapide que le jeu » sans jamais faire la somme.

La pente est douce (28 points au premier palier, 106 au dernier) plutot
qu'exponentielle : une courbe raide rendait les vingt premiers niveaux gratuits
et les vingt derniers hors d'atteinte.

L'experience vient du COMBAT et des objectifs, jamais du temps qui passe. Une
creature vaut `2 + PV_max / 4`, plafonne a 120 : la valeur suit ce que la
creature coute, non ce qu'elle est, et cette mesure range d'elle-meme un boss
au-dessus d'un zombie.

**Les ancres donnent des NIVEAUX, pas de l'experience** : dix pour la premiere,
douze pour chacune des deux suivantes. Trente-quatre en tout, soit le tiers de
la progression. C'est enorme a dessein — une ancre coute un siege entier, et la
recompenser par de l'experience ordinaire, que le joueur venait de toute facon
d'amasser en la defendant, ne se remarquerait pas.

### 18.3 Les quatre voies — barème réel de NosTale

**LES POINTS N'ACHÈTENT PAS DE LA VALEUR, ILS ACHÈTENT DES NIVEAUX.** C'est la
découverte qui a fait refaire le système. Je croyais que NosTale ajoutait un
gain fixe par point ; le vrai barème (relevé Gameforge EU, post-extension) montre
tout autre chose : une voie monte de 0 à 100, **chaque niveau coûte de plus en
plus cher**, et **chaque niveau rapporte de plus en plus**. Rendement dégressif
par le coût, croissant par la valeur — rien à voir avec une droite.

**Table de coût** (une seule pour les quatre voies ; le vrai barème Attaque
demande 410 points pour 100 niveaux, celle-ci 406, soit 1 % d'écart) :

| Niveaux | Coût | Niveaux | Coût |
|---|---|---|---|
| 0-9 | 1 | 60-79 | 5 |
| 10-19 | 2 | 80-89 | 6 |
| 20-39 | 3 | 90-96 | 7 |
| 40-59 | 4 | 97 / 98 / 99 | 8 / 9 / 10 |

**C'est ce qui fait le choix**, et les chiffres tombent remarquablement bien sur
nos 486 points :

- une voie pleine coûte **406** des 486 points ;
- il reste 80, soit le **niveau 36** dans une deuxième voie ;
- répartir également donne le **niveau 47 partout** ;
- deux voies pleines demanderaient 812 points : **impossible**.

**Gain linéaire** (la valeur d'un niveau monte par tranche de dix, coefficients
1,0 → 2,4, comme l'Attaque NosTale qui passe de +5 à +20) :

| Voie | Par niveau (base) | Total à 100 |
|---|---|---|
| Attaque | 0,05 dégât | +7,4 dégâts |
| Élément | 0,35 % | +51,8 % aux effets du mode |
| Défense | 0,05 armure | +7,4 armure |
| Vitalité | 0,12 PV | +17,8 PV |

**Paliers tous les 10 niveaux** (et non tous les 20 points), avec des
statistiques secondaires — c'est ce qui empêche une voie d'être un curseur :

| Voie | Palier donne | Total à 100 |
|---|---|---|
| Attaque | proba critique, dégâts critiques | 20 % / +100 % (×2,5) |
| Défense | esquive, critiques subis | 15 % / −50 % |
| Élément | résistance aux dégâts indirects | 35 % |
| Vitalité | dégâts et armure | +3,5 / +5,0 |

La Vitalité **déborde sur les deux autres**, comme la voie HP/MP de NosTale dont
les paliers donnent de la puissance d'attaque et de la défense. C'est ce qui
l'empêche d'être la voie qu'on prend faute de mieux.

**Quatre profils mesurés** (simulation, pas estimation) :

| Répartition | Niveaux | Résultat |
|---|---|---|
| Tout Attaque | 100/0/0/0 | +7,4 dég, crit 20 % ×2,50 |
| Tout Vitalité | 0/0/0/100 | +17,8 PV, +3,5 dég, +5,0 arm |
| Équilibré | 47/47/48/48 | +3,4 dég, +4,0 arm, +6,4 PV, +18 % eff |
| Attaque + Défense | 74/0/74/0 | +4,7 dég, +4,7 arm, esq 10 %, crit 14 % |

Aucune des quatre n'en domine une autre, et chacune se joue différemment.

**Les statistiques secondaires n'existent pas dans Minecraft** — le critique
vanilla est purement géométrique et n'obéit à aucune probabilité. Elles sont
appliquées à la main dans `HeroCombat`, sur `LivingIncomingDamageEvent`, seul
endroit qui voie à la fois qui frappe, qui encaisse et le montant avant
réduction. L'ordre y est explicite : **on esquive d'abord, on critique ensuite,
on résiste en dernier.**

### 18.4 L'interface

- **Jauge permanente** en bas a gauche : niveau et pourcentage du niveau
  suivant, plus un lisere violet quand des points attendent. Le coin inferieur
  gauche est le dernier libre — chronometre et ancres en haut a gauche, minimap
  en haut a droite, barres de siege et annonces au centre, Sonde a droite.
- **Fiche complete** sur la touche **H** : les quatre voies, une ligne chacune,
  avec ce que la voie DONNE et non ce qu'elle vaut. Placement par **+1 / +5 /
  +10**, les boutons impossibles etant grises plutot que refuses en silence.
- **Repli en commande** : `/arcencium hero`, `/arcencium hero <voie> <n>`,
  `/arcencium hero reset`, `/arcencium hero xp <n>` pour les essais.

La fiche ne decide de rien : chaque clic est une demande, le serveur revalide
tout et renvoie la fiche entiere — y compris quand rien n'a ete place. Les
points vivent dans les donnees persistantes du joueur, **qui ne se
synchronisent pas** : sans `HeroSyncPayload`, l'ecran serait vide alors que le
serveur sait tout. C'est exactement la panne qu'avait connue la jauge de Rage.

## 19. L'echelle multijoueur *(fait)*

Un siege calibre pour un joueur est une formalite a quatre. Chaque vague gagne
donc **trois quarts de sa taille par joueur supplementaire** :

| Joueurs | Facteur | Vague de 8 |
|---|---|---|
| 1 | 1,00 | 8 |
| 2 | 1,75 | 14 |
| 3 | 2,50 | 20 |
| 4 | 3,25 | 26 |

**Trois quarts et non un entier.** Deux joueurs valent plus que deux fois un
joueur — ils couvrent deux angles, se relevent, concentrent leurs coups — mais
ils partagent aussi un seul jeu d'ancres et une seule heure. Doubler franchement
punirait le fait de jouer ensemble ; ne rien changer le recompenserait.

Plafond de quarante creatures par vague : ce n'est pas un reglage d'equilibre
mais une securite serveur. L'effectif est compte une fois, a l'ouverture du
siege — un joueur qui arrive en cours de vague ne la fait pas gonfler sous ses
pieds, car la jauge de progression compte deja les monstres promis et la voir
reculer serait pire.

Reste a decider : faut-il aussi mettre a l'echelle le cout en Arcencium des
ancres (8 / 16 / 32), ou la duree de l'heure ?


## 20. Le critique des armes *(fait)*

Il manquait. La fiche du Héros et les runes distribuent de la chance de critique
et des dégâts critiques, mais **les armes elles-mêmes n'en avaient aucun** : un
joueur sans point d'Attaque et sans rune ne critiquait jamais, et les deux
systèmes semblaient greffés sur rien.

Chaque arme part donc d'une base, **et cette base monte avec sa rareté**
(+0,7 % de chance et +4 % de dégâts critiques par rang). C'est ce qui donne
enfin à la rareté un effet qu'on **ressent** : jusqu'ici elle n'ajoutait que des
dégâts plats, qu'on ne distingue pas d'une bonne arme ordinaire.

| Arme | Rang 0 | Rang 8 |
|---|---|---|
| Glaive | 7,0 % ×1,62 | 12,6 % ×1,94 |
| Arc | 5,0 % ×1,75 | 10,6 % ×2,07 |
| Lame | 5,0 % ×1,70 | 10,6 % ×2,02 |
| Sceptre | 3,0 % ×1,90 | 8,6 % ×2,22 |

**Les quatre ne sont pas égales, à dessein.** Le Glaive frappe vite et souvent :
beaucoup de chance, peu de dégâts. Le Sceptre frappe rarement et fort :
l'inverse. Le produit reste comparable — au maximum, Glaive ×1,77 et Sceptre
×1,78 de dégâts moyens — mais la **sensation** diffère.

Les trois sources (arme, fiche, runes) se versent dans **un seul total** et
tirent une seule fois. Voir §17.2 et `HeroCombat`.

## 21. Les commandes de test *(fait)*

Le mode a deux systèmes qu'on ne peut pas éprouver en jouant : les runes tombent
dix-huit fois par partie, et le niveau 100 demande cinq cents monstres. Ces
commandes les rendent immédiats.

| Commande | Effet |
|---|---|
| `/arcencium rune weapon <rang> [n]` | donne n runes d'arme de ce rang |
| `/arcencium rune armor <rang> [n]` | idem, famille armure |
| `/arcencium rune drop <pv> <morts>` | simule N morts d'une bête de X PV, rend la distribution des rangs |
| `/arcencium hero level <n>` | offre n niveaux de Héros |
| `/arcencium hero xp <n>` | donne n points d'expérience |
| `/arcencium hero <voie> <n>` | monte une voie de n niveaux |
| `/arcencium hero reset` | rend tous les points |

`rune drop` et `rune <famille>` appellent **la même loi que le jeu**
(`RuneDrops.simulate`, `RuneMark.roll`) et non une copie : une mesure faite au
banc d'essai vaut donc pour la partie. Une simulation qui recalculerait sa
propre loi ne testerait qu'elle-même.


## 22. L'amelioration +1 a +10 *(fait)*

Le troisieme systeme qui touche une piece. Trois systemes, trois questions
differentes -- c'est ce qui les rend compatibles plutot que redondants :

- la **rarete** dit ce que la piece EST, et commande le rang des runes ;
- les **runes** et **artefacts** disent ce qu'elle FAIT ;
- l'**amelioration** dit seulement de combien elle frappe ou protege PLUS.

### 22.1 Le bareme

Releve de NosTale, tel quel :

| Cran | +1 | +2 | +3 | +4 | +5 | +6 | +7 | +8 | +9 | +10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Gain | 10 % | 15 % | 22 % | 32 % | 43 % | 54 % | 65 % | **90 %** | **120 %** | **200 %** |

Sa FORME est ce qui compte : les sept premiers montent doucement, puis le
huitieme saute a 90 % et le dixieme a 200 %. Les trois derniers valent a eux
seuls plus que les sept premiers reunis. Un +7 est une piece correcte qu'on
obtient sans y penser ; un +10 est un evenement.

Le bonus **multiplie** les degats propres de l'arme (`ADD_MULTIPLIED_BASE`), pas
le total du joueur -- sinon un +10 triplerait aussi tout ce que la fiche du
Heros a construit.

### 22.2 L'echelle des materiaux

| Cran vise | Materiau | Quantite |
|---|---|---|
| +1 / +2 / +3 | Fer | 4 / 6 / 9 |
| +4 / +5 / +6 | Or | 4 / 6 / 9 |
| +7 / +8 | Diamant | 4 / 7 |
| +9 | Netherite | 2 |
| +10 | Arcencium | 6 |

Elle fait deux choses d'un geste : elle donne aux metaux vanilla une raison
d'exister passe la cinquieme minute, et elle **borne** la progression -- on ne
monte pas un +9 sans avoir trouve de la netherite, quelle que soit sa chance.

Il faut **en plus** une **Pierre de Forge**, qui ne tombe que des creatures
(12 %). Le metal se ramasse en creusant ; si la pierre aussi, le systeme entier
recompenserait le temps passe plutot que le jeu joue.

### 22.3 Les chances, et ce qu'un echec coute

| Depuis | +0 | +1 | +2 | +3 | +4 | +5 | +6 | +7 | +8 | +9 |
|---|---|---|---|---|---|---|---|---|---|---|
| Reussite | 90 % | 82 % | 74 % | 62 % | 52 % | 44 % | 36 % | 26 % | 18 % | 10 % |

**Un echec ne fait pas redescendre.** Il coute la pierre et le metal, rien de
plus. J'avais commence par faire retomber d'un cran a partir du septieme, pour
la tension ; la mesure a tranche : la marche aleatoire demandait **3 153
pierres et 11 854 diamants** pour un +10 -- c'est-a-dire jamais, dans un mode
d'une heure. *Une tension qu'on n'atteint pas n'est pas une tension.*

**Cout mesure, depuis zero :**

| Cible | Pierres (median) | Materiaux |
|---|---|---|
| +5 | 7 | 24 fer, 18 or |
| +8 | 15 | 24 fer, 38 or, 38 diamants |
| +9 | 21 | + 11 netherite |
| +10 | 29 | + 60 lingots d'Arcencium |

Une partie rapporte une soixantaine de pierres : le +10 est atteignable, et
coute a peu pres tout ce qu'on ramasse.

### 22.4 Le nom

`GearName` compose le nom des DEUX systemes qui y ont droit : `+8 Legendaire
Glaive d'Arcencium`. Il en fallait un seul endroit -- la rarete l'ecrivait deja,
l'amelioration voulait l'ecrire aussi, et chacune aurait efface l'autre.

### 22.5 Commandes de test

| Commande | Effet |
|---|---|
| `/arcencium upgrade <0-10>` | pose directement un cran sur l'objet en main |
| `/arcencium upgrade try <n>` | tente n fois, avec la vraie loi, sans payer |
| `/arcencium upgrade sim <cible>` | simule 1000 montees, rend le cout median |
| `/arcencium upgrade kit` | 64 pierres et 64 de chaque metal |

## 23. A FAIRE — les evenements aleatoires

Des fenetres de quelques minutes, tirees au hasard pendant la partie, qui
augmentent temporairement :

- les **chances d'amelioration** d'un equipement ;
- les **chances de rarete elevee** au tirage des Eclats du Destin ;
- le **rang des runes** que laissent les monstres.

L'interet est d'introduire un rythme : on met de cote ses pierres et ses eclats
en attendant la fenetre, au lieu de les depenser au fil de l'eau. Cela donne
aussi une raison de surveiller l'ecran entre deux sieges.

Restent a decider : la frequence, la duree, l'ampleur du bonus, et si les trois
fenetres sont distinctes ou si une seule les ouvre toutes.


## 24. Les elements *(fait)*

Quatre elements, **deux couples opposes** : Eau ↔ Feu, Lumiere ↔ Obscur. Entre
les deux couples, rien. Une seule question a se poser devant un ennemi -- « suis-je
son contraire ? » -- au lieu d'un tableau de seize cases.

| | contre son contraire | contre lui-meme | ailleurs |
|---|---|---|---|
| Multiplicateur | ×1,60 | ×0,45 | ×1,00 |

### 24.1 Le calcul

Comme chez NosTale, **l'element se calcule sur les degats bruts** :

```
elementaire = brut × puissance × affinite × voie Element × (1 − resistance)
```

Il **s'ajoute** au coup au lieu de le multiplier -- sinon il profiterait du
critique, et le Sceptre, qui n'en a pas, serait puni deux fois.

### 24.2 L'asymetrie qui fait le systeme

**Le joueur CHOISIT le sien, la creature PORTE le sien.** Sans cette asymetrie
il n'y aurait rien a preparer : on ne choisit pas contre quelque chose qui
choisit aussi.

- L'element appartient au **joueur**, pas a l'arme. J'avais commence par le
  faire porter par l'arme : il fallait accorder chaque arme separement, et deux
  verites apparaissaient des qu'on en changeait. C'est la repartition de NosTale
  -- la fee porte l'element, l'arme la force.
- **Chacun recoit un element au lancement**, tire sans remise : a quatre, chacun
  en a un different. Un menu de choix au depart demanderait de decider avant
  d'avoir rien vu du bestiaire ; un tirage impose un point de vue, et c'est en
  decouvrant ce qu'on affronte qu'on apprend s'il faut en changer.
- Pour en changer, il faut **trouver une Pierre elementaire** et s'en servir
  (clic droit, en main). Aucune commande ne le fait -- un raccourci aurait fini
  par etre le seul chemin qu'on emprunte, et la boucle ne serait jamais eprouvee.

Les Pierres tombent des creatures **de leur element** (22 %). C'est la boucle :
pour frapper l'Obscur il faut une Pierre de Lumiere, donc chasser des creatures
de Lumiere -- qu'on combat mal justement parce qu'on n'est pas encore accorde.

### 24.3 Les resistances des creatures — fixes

Chaque creature a un **profil complet de quatre resistances**, pas un chiffre :

| Profil | Eau | Feu | Lumiere | Obscur |
|---|---|---|---|---|
| Eau | 55 % | **0** | 20 % | 20 % |
| Feu | **0** | 55 % | 20 % | 20 % |
| Lumiere | 20 % | 20 % | 55 % | **0** |
| Obscur | 20 % | 20 % | **0** | 55 % |

**Le zero est la porte** : c'est lui qui recompense le joueur qui a prepare le
bon element. Sans lui le systeme ne serait qu'une taxe. Il n'y a donc pas UN bon
element, mais un par famille d'ennemis.

Attribution par les TRAITS et jamais par une liste de noms : immunise au feu →
Feu, aquatique → Eau, mort-vivant ou lanceur de sorts → Obscur. **La Lumiere n'a
aucun representant naturel** -- elle est reservee a ce que le mode place
lui-meme. La croiser doit vouloir dire quelque chose.

### 24.4 Les boss bi-element

Deux elements, affinite calculee sur la **moyenne** des deux couples : contre un
boss Obscur + Feu, une arme de Lumiere obtient 1,6 et 1,0, donc 1,3. Plus 18 %
de resistance, et jamais zero meme devant un contraire.

On moyenne plutot que de prendre le minimum : le minimum rendrait tout boss
insensible au choix d'element, et la mecanique disparaitrait au moment ou elle
compte le plus.

## 25. L'equipement des monstres *(fait)*

Sans lui, tout le reste casse le jeu : le joueur monte sa fiche, sa rarete, ses
runes et ses ameliorations pendant une heure, et si le bestiaire ne bouge pas la
quarantieme minute devient une promenade.

Les monstres utilisent **les memes systemes que le joueur** -- meme rarete, meme
amelioration, memes tables. Retoucher un bareme demain profite au bestiaire le
jour meme.

**Le stade** se lit sur trois sources, dont on prend la plus AVANCEE : temps
ecoule, ancres tenues, palier du siege. Un joueur qui prend trois ancres en vingt
minutes est en avance ; lui envoyer des monstres de vingtieme minute le punirait
de sa vitesse.

| Stade | Echelon | Arme | Armure |
|---|---|---|---|
| 0 % | cuir | 4,0 | 7 |
| 25 % | maille | 6,0 | 12 |
| 50 % | fer, +2 | 7,7 | 15 |
| 75 % | diamant, +3 | 9,7 | 20 |
| 100 % | diamant, +4 | **12,2** | 20 |

**L'ARMURE SATURE, PAS L'ARME**, et c'est la mesure qui l'a dit. Minecraft
plafonne la reduction a vingt points d'armure ; un plastron de diamant complet y
arrive deja. J'avais monte le bestiaire a trente-huit points -- dix-huit de purs
chiffres perdus, pendant que les monstres devenaient injouables a 80 % de
reduction.

L'echelle s'arrete donc au diamant et c'est l'ARME qui porte la difficulte : les
degats montent lineairement et ne plafonnent jamais. Un monstre de fin de partie
n'est pas plus dur a tuer, il est plus **dangereux**.

Ni Legendaire ni Phenomenal sur un monstre : ce sont les deux rangs que le joueur
poursuit. Leur equipement tombe a 1,5 % -- un butin, pas une source.


## 26. L'aura d'amelioration *(fait)*

Ce que l'amelioration MONTRE. Le bareme suit NosTale, tel que le joueur l'a
decrit et que le releve le confirme : un cycle rouge / vert / blanc, joue deux
fois, et c'est l'**ampleur** qui separe les deux tours.

| Cran | Couleur | Ampleur |
|---|---|---|
| +1 a +4 | blanc froid, faible | croissante -- une presence, pas une teinte |
| +5 / +6 / +7 | rouge / vert / blanc | courte |
| +8 / +9 / +10 | rouge / vert / blanc | **longue, constante, large** |

Les crans +1 a +4 n'ont pas de couleur documentee : on ne leur en invente pas.
Tout passe par une seule table (`UpgradeGlow`), lue cote serveur et cote client,
pour que l'arme et l'armure ne divergent jamais.

### 26.1 L'arme : quatre effets, un seul systeme

- **Le halo** *(client)* -- l'arme redessinee en lumiere additive, agrandie,
  deux couches (un corps dense, un voile diffus) et une troisieme tres large au
  +8. C'est ce qui fait la difference entre une aura et un nuage de points : le
  halo epouse la silhouette exacte de la lame. Il **respire**, chaque couche a
  son rythme. Rendu a la premiere personne (le porteur) et a la troisieme (les
  autres), par deux chemins et une seule routine.
- **Il y avait une hélice de particules, retirée.** Elle était fausse par
  construction : le serveur devinait la position de la main, or la main est
  dessinée par le client, animée par les membres et l'objet. Aucun réglage ne
  pouvait rattraper ça. La leçon vaut au-delà : *une particule serveur ne peut
  pas suivre un point qui n'existe que dans le rendu.* Ce qui doit épouser
  l'arme se fait au rendu — c'est le halo, et il le fait bien.
- **L'onde** *(serveur)* -- au +8 et plus, chaque coup porte fait partir un
  anneau de douze particules de la cible vers l'exterieur. C'est le moment
  qu'on retient : le halo finit par se fondre dans le paysage, l'onde n'existe
  qu'un instant.
- Les creatures armees au +5 et plus emettent l'helice : voir un zombie dont
  l'epee brille rouge dit ce qu'on affronte avant le premier coup.

### 26.2 L'armure : une coque autour, jamais dessus

**La première version effaçait l'armure.** Une lueur additive posée sur la
pièce à 55 % : du blanc additif à cette force blanchit tout ce qu'il recouvre,
et un plastron +10 devenait un bloc blanc. La règle qui manquait, dite par le
joueur : *une amélioration ajoute, elle ne remplace jamais l'apparence de base.*

D'où la **coque** : un maillage gonflé bien au-delà de l'armure (déformation
1,75 contre 1,0 pour l'armure extérieure), dessiné à 20 % au plus. Vu de front,
sa face ne teinte la pièce que faiblement — elle reste elle-même, réchauffée de
sa couleur. Vu à la silhouette, on voit ses flancs, hors du corps, contre le
décor : c'est là que la couleur fait un liseré, et c'est ce liseré qu'on lit
comme une aura. Il ne couvre rien puisqu'il est à côté.

- Elle **respire**, chaque pièce à son propre rythme.
- **Les braises** au +8 : de lentes étincelles montent de chaque pièce.

### 26.3 Ce qu'on n'a pas fait, et pourquoi

- **Pas de halo ni de lueur sur les creatures** : elles n'ont que l'helice.
  Greffer les calques sur chaque type de rendu du modpack est un chantier a
  part ; les particules suffisent a les signaler.
- **Le halo derive de quelques centimetres** sur sa couche la plus large : la
  transformation propre a l'objet est hors de portee. Sous le seuil de ce que
  l'oeil distingue sur une lueur diffuse.
- **La rarete n'influence pas encore l'aura.** Le releve dit que R7 ajoutait
  historiquement une couche et que la brillance montait avec la rarete, sans
  decrire comment. A reprendre quand on saura quoi dessiner.

## 27. Les chiffres de dégâts *(fait)*

Un coup qui ne s'affiche pas ne s'évalue pas. Le joueur monte sa fiche, ses
runes, ses améliorations — sans chiffre, il ne sait jamais si le +8 a changé
quelque chose. Le chiffre est la seule preuve tangible de tout ce qu'on a bâti.

- **Le chiffre part du serveur, après armure et résistances** : c'est celui
  qu'on a *infligé*, pas celui qu'on a demandé. Le critique est décidé plus tôt
  et noté sur la victime le temps d'un tick.
- **Le critique se voit avant de se lire** : plus gros, doré avec un liseré
  orange, précédé d'un éclair, et il **bondit** — parabole d'un bloc puis
  retombée — là où un coup ordinaire monte doucement et s'efface. On le
  reconnaît du coin de l'œil, au milieu d'une vague, sans lire le nombre.
- Dessiné face à la caméra, avec la police du jeu, **à travers les murs** : un
  chiffre caché derrière le monstre qui l'a reçu ne servirait à rien.
- Seuls les coups portés par un **joueur** s'affichent.


## 27. Les meteos, refaites *(fait)*

Le reproche etait juste et mesure : les six meteos puisaient dans les memes
particules que les armes, le sceptre et les plantes -- la mote de Prisme, la
tige d'End, la poussiere de redstone -- et tout finissait par se ressembler.

**Regle appliquee : chaque meteo a son vocabulaire, code pour elle, sans rien
emprunter.** Quinze particules nouvelles (`tools/weather_particles.py`,
`client/WeatherParticles.java`), deux rendus en geometrie la ou des points ne
suffisent pas. Mesure apres coup : la seule particule vanilla restante dans la
meteo est `EXPLOSION_EMITTER` a l'impact d'un meteore, gardee parce qu'une
explosion est une explosion.

| Meteo | Vocabulaire |
|---|---|
| **Brume** | *nappes* qui rampent au sol, doublees dans les creux ; *formes fantomatiques* qui se defont quand on approche (opacite = distance) ; souffle etouffe |
| **Aurore** | rideaux en geometrie (inchanges) ; *lucioles de cristal* qui montent des filons d'Arcencium -- l'aurore repond aux veines |
| **Nuit** | *gouttes* fines, chacune d'une couleur d'eclair, qui *se brisent en eclats de cristal* au sol -- presque blancs, un reflet de leur couleur ; l'onde jaune et les cicatrices vertes parlent en eclats aussi (l'anneau colore, juge enfantin, n'existe plus) |
| **Meteores** | *tete* blanche, *braises* qui refroidissent de l'orange au gris le long de la chute, *cendres* qui tombent en se balancant, *onde de choc* a plat a l'impact, grondement lointain ; **secousses** rares avec *poussiere* qui monte du sol, et **fissures reelles** (voir 27.4) |
| **Dechirure** | *eclats de terre* et *brins d'herbe* qui decollent en tournant ; les eclats d'Arcencium sont signales par le sol qui monte vers eux ; **failles en geometrie** |
| **Orage** | ciel vraiment couvert ; *pluie oblique* par **rafales** ; **eclairs de chaleur** qui allument l'horizon, tonnerre qui roule loin ; *etincelles* sur le metal ; **arcs en geometrie** qui courent au sol, **convergent** vers la frappe, eclatent en etoile a l'impact, et courent autour du porteur de Surcharge ; la frappe **monte du sol** |

### 27.1 Les deux rendus en geometrie

- **Les failles** (`RiftRenderer`) : une fente noire dechiquetee bordee d'une
  lueur violette, face a la camera. Deux types de rendu, et il le faut : le
  coeur noir en fondu classique (un noir additif est invisible), le bord en
  additif. La forme est un bruit *seede* sur la position -- stable d'une image a
  l'autre, sinon elle tremblerait comme un defaut d'affichage. Le serveur
  n'envoie plus des nuages de portail mais la **position** des failles
  (`RiftSyncPayload`, toutes les dix ticks).
- **Les arcs** (`StormArcRenderer`) : entierement client. Un arc n'a aucun
  effet de jeu ; le synchroniser ne servirait qu'a le retarder. Chaque client
  tire ses arcs autour de son joueur, en rubans face camera le long d'une ligne
  brisee, un quart de seconde.

### 27.2 Deux techniques a retenir

- **La particule posee a plat** (`FlatParticle`) : les anneaux d'eclatement et
  l'onde de choc ecrivent eux-memes leurs quatre sommets dans le plan du sol.
  Un anneau face camera est un disque qui flotte ; couche, c'est une trace, et
  c'est cela qu'on lit comme un impact.
- **La couleur par l'emetteur** : les textures sont blanches ou grises, la
  teinte vient du code. Une goutte prend la couleur de l'eclair qu'elle annonce
  sans qu'il faille une image par couleur ; une braise refroidit d'elle-meme.

### 27.3 Les secousses et les fissures des Meteores

Toutes les 45 a 80 secondes (jamais dans les vingt premieres), le sol tremble
pour tout le monde : deux secondes et demie, une cloche de secousse plafonnee
a la demi-force, un tonnerre rendu grave, de la poussiere terreuse qui monte
du sol autour de chaque joueur, la pierre qui craque. **Rare a dessein** : une
camera qui tremble gene vite.

Une secousse sur deux ouvre une **fissure : une vraie ouverture dans le sol**,
pas une image. Sa taille est tiree au sort, les grandes bien plus rares :

| Taille | Poids | Largeur | Profondeur | Longueur |
|---|---|---|---|---|
| Craquelure | 70 % | 1 bloc | 2-4 | 4-8 |
| Moyenne | 22 % | 1-2 | 4-7 | 8-14 |
| Grande | 6 % | 2-3 | 8-14 | 14-22 |
| Abime | 2 % | 4-5 | 16-30 | 22-34 |

Ce qui la fait paraitre vraie :
- **une forme de craquelure**, pas de tranchee : une ligne courbee lentement,
  dentelee finement, avec une ou deux *ramifications* en biais (presque
  toujours pour les grandes, rarement pour les petites), et des bouts qui
  finissent en cheveu -- les 18 % extremes ne s'ouvrent jamais ;
- **des parois en gradins** : chaque colonne est creusee selon un profil en V,
  profonde au milieu, a peine entamee au bord ;
- **des gravats** au fond, faits de la matiere otee (pave, pave d'ardoise,
  terre grossiere, gravier), et quelques pierres deplacees sur la bordure ;
- **une annonce** : une fente sombre et fine se dessine et se propage depuis
  le centre, une seconde et demie avant que le sol ne cede -- puis
  l'effondrement court lui aussi du centre aux bouts, avec la poussiere et
  le bruit d'eboulis ;
- **de la chaleur au fond des grandes seulement** : une lueur rouge sourde qui
  respire, des braises qui montent. Une craquelure de deux blocs n'a pas de
  magma.

Garde-fous : la meme liste blanche que les meteores (vanilla, sans obsidienne
ni coffres de mods), jamais l'eau, **jamais sous les pieds d'un joueur** (le
pont qui reste sous lui vaut mieux qu'une chute), jamais a moins de 48 blocs
du village, jamais sous un toit, trois fissures au plus a la fois. Le trou
reste : la tempete a marque la terre, comme les crateres.

La forme (principale et ramifications) est deduite de la position par un bruit
seede, **a l'identique chez le serveur qui creuse et chez le client qui
dessine** (`FissureShape`) : la fente annoncee et le trou coincident, et rien
d'autre que le centre, la direction, la longueur et la largeur ne transite
(`FissureSyncPayload`).

### 27.4 L'Orage refait : la charge qui rampe au sol

Avant : un orage vanilla recolore en violet, sous un ciel intact -- un cercle
d'avertissement, un carillon, un eclair copie du jeu de base. Son identite est
ailleurs : **la Nuit fait tomber des eclairs colores du ciel ; l'Orage fait
ramper la charge au sol.** Cinq choses :

1. **Le ciel se couvre vraiment** : la pluie et le tonnerre du jeu de base sont
   declenches (obscurite, pluie grise, son), et ses eclairs blancs sont bloques
   a la naissance (`EntityJoinLevelEvent`, sauf ceux d'un trident : ils ont
   une cause). Seules nos frappes tombent.
2. **Eclairs de chaleur** : le ciel clignote sans que rien ne tombe (un eclat
   d'ecran doux, ne chez le client), la brume violette s'allume a l'horizon
   avec l'eclat, et le tonnerre roule a soixante-dix blocs, un peu apres.
3. **Rafales** : le vent souffle par a-coups ; la pluie s'epaissit et se couche
   dans la rafale, et on l'entend arriver (les sons de vent de la Brise).
4. **La frappe se renverse** : plus de cercle, plus de carillon. L'air aspire,
   un son grave enfle, et chez les clients les **arcs convergent** vers le
   point, de plus en plus vite, le point gresille ; puis la decharge **monte du
   sol vers le ciel** (l'eclair se revele par le bas, en deux ticks et demi),
   claque comme un trident, et les arcs eclatent en etoile. Les points de
   frappe transitent (`StormStrikePayload`) : c'est un fait de jeu, tous
   doivent le voir au meme endroit.
5. **La Surcharge se voit** : celui qui la porte gresille pendant trente
   secondes, et des arcs lui courent autour du corps.

### 27.5 Ce qui a change apres l'essai

- **Fissures invisibles a l'essai** : le garde-fou du village (48 blocs)
  rejetait tout pres du refuge, et le rythme etait trop lent. Le garde-fou
  depend maintenant de la taille (12 blocs + la longueur maximale : 20 pour
  une craquelure, 46 pour un abime), les secousses viennent toutes les
  35-65 s (la premiere apres 10-25 s), trois sur quatre ouvrent une fissure,
  et les fissures vivent leur vie **quelle que soit la meteo** -- meme sans
  aucune. Commande d'essai : `/arcencium fissure [petite|moyenne|grande|abime]`.
- **Plus de monstres sous les tempetes agressives** : la pression passe de
  1-3 monstres toutes les 3 s a 2-4 toutes les 2 s, plafond par joueur
  12 -> 19.
- **Derniere particule vanilla** : l'arrivee par une faille (portail inverse)
  parle desormais en debris de Dechirure. Il ne reste que l'explosion du
  meteore, gardee parce qu'une explosion est une explosion.

### 27.6 Le voile de ciel : ce que le shader a appris

Deux captures du joueur sous Complementary ont tout dit : une Brume avec « du
brouillard au sol et nulle part ailleurs » sous un ciel bleu, une Pluie de
Meteores sous un beau ciel clair. **Un pack de shaders recalcule le
brouillard et le ciel a sa maniere et ignore `ComputeFogColor` /
`RenderFog`.** Et meme sans shader, le brouillard vanilla ne touche pas le
ciel. Toute l'ambiance par meteo tenait a ces evenements : elle disparaissait.

La reponse : **dessiner le ciel couvert nous-memes** (`SkyVeilRenderer`).
Une coupole autour de la camera, apres le ciel et avant le terrain, en
geometrie ordinaire avec ecriture de profondeur : le terrain, plus proche,
passe devant ; le ciel disparait derriere ; et un shader la traite comme un
objet lointain, pas comme du ciel qu'il repeindrait. Couleur et opacite par
meteo (`WeatherClient.veilFor`), montee avec l'intensite ; la brume laisse un
peu de lumiere au zenith, un ciel de cendres non ; le voile de l'Orage
s'allume avec les eclairs de chaleur.

| Meteo | Voile |
|---|---|
| Brume | pastel derivant, 92 %, plus clair au zenith ; et des nappes DANS L'AIR, pas seulement au sol |
| Aurore | indigo leger, 45 % : les rideaux se lisent meme en plein jour |
| Nuit | indigo profond, 55 % (la nuit vanilla fait le reste) |
| Meteores | rouge-brun de cendres, 88 %, et **l'horloge bascule au crepuscule** (13200) comme la Nuit a minuit : ciel rouge, lumiere basse -- l'apocalypse ne se joue pas a midi |
| Dechirure | violet, 72 % |
| Orage | pourpre sombre, 85 %, qui s'eclaire a chaque eclair de chaleur |

**Ou le dessiner, vu en capture** : juste apres le ciel, sous Iris, la
coupole passait par le programme de CIEL du pack (phase « sky »), qui calcule
sa propre couleur et ignore la notre -- le ciel restait bleu. Dessinee a la
fin du rendu (apres la meteo), a la distance du mur de brouillard de chaque
meteo (Brume 56, Orage 84, Nuit 96, Dechirure 140, Meteores 150, Aurore
210), avec test et ecriture de profondeur, elle est un objet comme un autre :
le terrain plus proche passe devant, le reste disparait dedans. Le voile EST
le mur de brouillard, sous shader comme sans. Et la pluie de la Nuit, noyee par la
pluie grise du pack, passe a vingt-deux gouttes par tick, plus grosses.

**Vu en captures, sous Complementary, dans le monde d'essai** (`runClientWorld`
+ datapack `autotest`, captures F2 automatiques) : la Brume couvre le ciel
d'un pastel qui derive, le sommet de la pyramide s'y fond, des nappes
flottent dans l'air ; les Meteores se jouent sous un ciel rouge-orange de
cendres au crepuscule, silhouettes noires, flocons de cendre -- l'apocalypse
demandee ; la Nuit est une voute indigo zebree de trente gouttes colorees
par tick ; l'Orage un ciel bouche pourpre-gris sous la pluie ; la Dechirure
un ciel lavande, et ses failles en geometrie se voient ; l'Aurore garde ses
rideaux, lisibles en plein jour sur un ciel a peine assombri. La commande
d'essai `/arcencium fissure [taille]` ouvre desormais la fissure DEVANT le
joueur (pour la voir), et chaque ouverture ou refus s'ecrit dans le journal
du serveur avec sa taille et sa raison.

**Le sanctuaire ne se fend pas -- et c'est un piege d'essai.** Une fissure
ouverte sur le parvis du sanctuaire d'essai s'annoncait, chauffait, mais
n'enlevait *zero* bloc : le dallage est en `polished_gangue`, un bloc du mod,
et la meteo ne casse que du vanilla (voir `fragile`). Voulu -- le sanctuaire
est un refuge --, mais cela rend tout essai de fissure au sanctuaire
trompeur. Les essais d'effondrement et de failles se font **dans la nature**
(`verify_client5.sh` deplace le joueur a 60-200 blocs par `spreadplayers`
avant de forcer la meteo). Chaque fissure ecrit dans le journal le bloc de
sa colonne centrale et le nombre de blocs retires : un zero se voit.

**Le premier effondrement en biome naturel a creuse jusqu'au socle.** Une
grande fissure (3 x 16 x 11) a retire 1012 blocs au lieu de deux cents : les
disques de creusement se recouvrent le long de la ligne, et chacun repartait
du NOUVEAU sol, si bien qu'une meme colonne etait creusee six fois, jusqu'a
y = -1. Corrige : chaque colonne memorise sa surface d'origine et la
profondeur deja creusee, et ne descend jamais plus bas que le disque le plus
exigeant qui la touche. Le journal donne le compte de blocs : il doit rester
de l'ordre de la largeur x la longueur x la moitie de la profondeur.

Vu en captures dans la nature : la faille de la Dechirure (fente noire
bordee de rose, debris et brins d'herbe en suspension, « step in to travel »)
se lit parfaitement sous Complementary.

### 27.7 Le mode eteint n'eteint plus la meteo forcee

Le joueur teste en **exploration libre** (mode eteint) : toutes ses sessions
depuis fin aout le montrent. Or le tick serveur de la meteo s'arretait tout
entier avec le mode -- une meteo forcee a la commande s'annoncait, le client
jouait son ambiance, mais rien cote serveur : ni foudre, ni meteore, ni
secousse, ni fissure. C'est pour cela que `/arcencium fissure` « ne
marchait pas ». Desormais, mode eteint = **rien n'est planifie**, mais une
meteo forcee vit entierement, et les fissures aussi.

### 27.8 Une lecon sur le rendu lightning

Le rendu `lightning` (additif) **elimine les faces arriere** ; `debugQuads`
non. Les arcs de l'Orage etaient ecrits dans un seul sens : la moitie d'entre
eux -- ceux qui couraient vers la droite de l'ecran -- etaient invisibles.
Un ruban de lumiere n'a pas de bon cote : on l'ecrit dans les deux sens.

### 27.9 Ce qui reste hors de portee

- **Pas de son custom** : le mod n'a aucun fichier audio. L'ambiance sonore
  superpose des sons vanilla (hauteur, volume, cadence). Une vraie bande son
  demanderait des fichiers qu'il faudrait produire.
- **Le rendu n'a pas ete vu** : tout compile et chaque particule est
  enregistree, texturee et employee (verifie par script), mais les densites, les
  vitesses et les couleurs sont des reglages a l'oeil. `/arcencium weather <id>`
  permet de les eprouver une par une.


## 28. La specialisation du personnage *(en conception, valide en partie)*

Le personnage s'ameliore comme une arme, de **+1 a +20**. De +1 a +15 il
gagne des **ailes de cristal prismatique** qui grandissent a CHAQUE palier ;
de +16 a +20 les ailes ne changent plus, chaque palier ajoute une animation
autour du corps (motes en orbite, anneau au sol, trainee, arcs, onde) --
jamais rien au-dessus de la tete. Chaque palier donne des **points de heros**
a repartir dans les voies existantes : **120 points** au total (3 par palier
de +1 a +5, 5 de +6 a +10, 7 de +11 a +15, 9 de +16 a +20).

**Les ailes** : un eventail de lames de cristal qui part de l'epaule, releve
-- davantage de lames vers le haut, peu sous l'horizontale --, avec une
seconde rangee plus courte derriere a partir de +6 ; a +15 les lames du haut
se rapprochent au-dessus de la tete. Couleurs sobres, base argentee. Deux
apparences (Obscures, Givre) utilisent un second motif, a plumes pendantes.
Ecarte par le joueur : les eventails de fee trop colores, le motif « os et
plumes pendantes » pour la base, toute couronne au-dessus de la tete, et des
ailes qui retombent.

**Le materiau** : un objet existant recolore et renomme (une plume, ou un
eclat), jamais un objet fait de zero. Chances de succes decroissantes,
**jamais de retrogradation**, l'echec consomme le materiau.

**Les apparences** se debloquent a +15 par des objets rares (boss puissants,
coffres rares) et changent totalement les ailes. Bonus valides par le joueur :

| Apparence | Comment on l'obtient | Bonus |
|---|---|---|
| **Prismatiques** | les ailes de base, a +15 | +5 % degats elementaires, +5 % vitesse |
| **Obscures** | boss d'element Obscur | +6 % chance critique, +15 % degats critiques, +5 % vitesse |
| **Rubis** | coffres rares seulement, les plus rares | +10 % attaque, +7 % vitesse |
| **Pierres precieuses** | boss puissants | +4 % attaque, defense, vie et element, +5 % vitesse |
| **Aurore** | coffres de sanctuaire | +7 resistance elementaire, -5 % resistances elementaires de l'ennemi, regeneration lente hors combat, +3 % vitesse |
| **Tempete** | boss tue pendant un Orage | +10 % cadence (tir ou corps a corps), +5 % chance de deferlement, +3 % vitesse |
| **Braise** | boss tue pendant les Meteores | +10 % degats de Feu, brulure sur coup critique, +5 % vitesse |
| **Givre** | boss d'element Eau | +6 % esquive, +8 % defense, givre sur coup critique (ralentit, +15 % degats d'Eau subis), +5 % vitesse |
| **Emeraude** | boss final ou coffre ultime | +12 % points de vie, +7 % defense, +8 % vitesse |

### 28.1 Ce qui est construit *(fait, en attente des textures)*

- **Les donnees** : palier (0-20) et apparence dans les donnees persistantes
  du joueur (`Specialization`), copiees a la mort, envoyees a la connexion, a
  chaque nouveau spectateur et a chaque changement (`WingsSyncPayload`, vers
  tous ceux qui voient le joueur -- les ailes se regardent de l'exterieur).
- **Le rendu** (`WingsLayer`) : deux plans textures attaches au torse,
  derriere les omoplates, ouverts vers l'arriere et l'exterieur, qui battent
  lentement et plus vite en mouvement ; envergure de 12 % a +1 a 1,7 bloc a
  +15 (vu en capture : 1,7 bloc faisait des ailes de la taille de la tete ;
  2,6 blocs, racine un peu plus basse et plus ouverte, donne l'envergure
  voulue). Apparences de lumiere en emissif plein feu, Obscures et Papillon
  eclairees par le monde. Verifie en jeu sous Complementary, de dos et de
  face, pour rubis et prismatiques (`verify_wings.sh`, F5 puis F2). La texture est une aile DROITE, racine a 12 % du
  bord gauche et 78 % du haut ; l'aile gauche est son miroir.
- **Les textures** viennent de ChatGPT (`tools/prompts/ailes_specialisation.md`),
  deposees dans `tools/wings_input/wing_<apparence>.png` et importees par
  `tools/wings_import.py` (512 px, detourage d'un fond blanc ou vert si le PNG
  n'est pas transparent). Trois sont peintes et validees : prismatiques,
  rubis, aurore.
- **Commande d'essai** : `/arcencium ailes <palier> [apparence]`.

Abandonne en route, et pourquoi : sept maquettes dessinees par code (eventails,
plumes, coupoles, gemmes, papillon, dragon) et les seize sprites 32x32 de
Placebo recolores -- aucune n'approchait les ailes peintes que le joueur
voulait ; les prompts ChatGPT y sont arrives du premier coup.

### 28.2 La mecanique *(faite)*

Tranche par le joueur : **la specialisation se garde d'une partie a
l'autre**, et le materiau est **la Plume d'Arcencium**.

- **Ou elle vit** : hors de la sauvegarde du monde, qu'une nouvelle partie
  remplace -- dans `<serveur>/emeraldweapons/specialization.json`, par
  joueur (`SpecializationStore`) : palier, apparence, apparences
  debloquees, echecs. Chargee au demarrage du serveur, ecrite a chaque
  changement.
- **La Plume d'Arcencium** (`ArcenciumFeatherItem`, la plume vanilla
  recoloree en prisme) : clic droit = une tentative du palier suivant. Cout
  et chance par palier vise :

  | Palier | +1..+5 | +6..+10 | +11..+15 | +16 | +17 | +18 | +19 | +20 |
  |---|---|---|---|---|---|---|---|---|
  | plumes | 1,1,2,2,3 | 3,4,4,5,5 | 6,6,7,7,8 | 10 | 12 | 14 | 16 | 18 |
  | chance | 100..80 % | 75..55 % | 50..30 % | 25 % | 22 % | 19 % | 16 % | 13 % |

  Jamais de retrogradation ; l'echec consomme les plumes. Esperance : ~130
  plumes pour +15, ~540 pour +20 -- une progression de compte, sur
  plusieurs parties. Chaque reussite rend ses **points de heros** (3, 5, 7,
  9 par tranche de cinq paliers : 120 en tout) directement dans la cagnotte
  de la fiche.
- **Le butin** : la plume tombe des monstres, 6 % + 22 % x (PV / 200), une
  ou deux sur les gros ; la **Plume d'apparence** tombe des puissants (300 PV
  et plus, 35 %) selon leur element (Obscur -> Obscures, Eau -> Givre) ou la
  meteo (Orage -> Tempete, Meteores -> Braise), sinon parmi Pierres
  precieuses, Emeraude, Papillon, Aurore. Le Rubis ne tombe d'aucun monstre :
  coffres rares seulement (a brancher dans les tables de butin).
- **Les apparences** (`SkinFeatherItem`, la meme plume teintee par l'apparence
  qu'elle porte) : clic droit a +15 ou plus debloque et pose l'apparence ;
  une apparence debloquee se reprend librement. Les bonus (section 28,
  tableau valide) s'appliquent a +15 et au-dela (`SkinBonus`) : attaque,
  defense, vie, vitesse et cadence par modificateurs d'attribut ; critique,
  esquive, resistance, percee, declenchement et element au meme endroit que
  la fiche et les runes (`HeroCombat`) ; brulure et givre sur coup critique ;
  regeneration de l'Aurore hors combat.
- **Commandes d'essai** : `/arcencium ailes <palier> [apparence]`,
  `/arcencium ailes tenter`, `/arcencium ailes plumes <n>`,
  `/arcencium ailes apparence <id>`.
- **Les dix ailes sont peintes** (ChatGPT, prompts de
  `tools/prompts/ailes_specialisation.md`) et importees ; envergure 3,4
  blocs a +15 (le joueur voulait +30 %).

Reste : les animations de +16 a +20, le Rubis dans les coffres, la fiche de
heros qui montre le palier, et la vitesse de tir des arcs (la cadence ne
joue que sur les armes de corps a corps).

## 29. Distant Horizons et les animations *(installes en dev, verifies en jeu)*

Le joueur veut l'horizon lointain pour l'immersion. Ce qui a ete mesure avant
de choisir :

- **ATM10 livre deja Distant Horizons 2.2.1-a** (`DistantHorizons-2.2.1-a-1.21.1-neo-fabric.jar`),
  present dans l'instance CUSTOM avec Iris 1.8.8 et Complementary. Le pack le
  livre **rendu desactive** (`rendererMode = DISABLED`, rayon 256) ; l'instance
  CUSTOM l'a active a la main (rayon 512, le 13 juin 2026).
- **Client ou serveur ?** Le rendu et la generation des LOD sont **cote client**.
  En solo, le serveur integre genere le terrain lointain lui-meme. Sur un serveur
  dedie, la 2.2.1 ne genere rien : le client ne voit que ce qu'il a deja explore.
  A partir de la **2.3**, le meme jar installe **aussi sur le serveur** genere les
  LOD et les envoie aux clients. Pour le mode multijoueur, il faut donc DH >= 2.3
  des deux cotes ; sinon, client seul suffit.
- **Iris 1.8.8 accepte toute DH >= 2.0.4** (chaine dans `DHCompat` : "Iris
  requires DH [2.0.4] or DH API version [1.1.0] or newer"), et se branche par
  reflexion sur l'API. Journal du test : "DH Ready, binding Iris event
  handlers... DH Iris events bound". Complementary r5.5.1 a ses programmes
  `dh_terrain` / `dh_water`. Les DH 3.x (2026) changent le moteur de rendu et
  notent elles-memes qu'Iris ne les suit pas encore : a eviter.
- **Versions Modrinth NeoForge 1.21.1** : 2.2.1-a (sept. 2024, celle du pack),
  2.3.6-b (oct. 2025 : support serveur, "fix neoforge server startup crash"),
  2.4.5-b (dec. 2025 : corrections Iris sur NeoForge), 3.0 a 3.2 (2026).

### 29.1 Les reglages, et pourquoi

Machine : Ryzen 7 5800X (8 coeurs / 16 threads), 128 Go de RAM, RTX 3080 Ti
12 Go, 2560x1440. Ce que DH consomme : **du CPU** (il fait tourner le vrai
worldgen sans structures pour le terrain lointain, puis compresse en LOD) et
**de la VRAM** (les mailles). La RAM n'est pas un levier : les LOD vivent dans
une base SQLite par dimension (`saves/<monde>/data/DistantHorizons.sqlite`).

`run/config/DistantHorizons.toml` (copie de la config CUSTOM, meme version,
memes cles) :

| Cle | Valeur | Pourquoi |
|---|---|---|
| `lodChunkRenderDistanceRadius` | 256 | chaque partie se joue sur un monde neuf : ~200 000 chunks a produire, l'horizon s'etend du proche au loin pendant la premiere demi-heure. 512 ne se remplirait jamais en 60 min ; garder 512 pour l'exploration libre sur un monde qui persiste |
| `verticalQuality` / `horizontalQuality` | HIGH / HIGH | 12 Go de VRAM, large |
| `maxHorizontalResolution`, `transparency` | BLOCK, COMPLETE | detail max, eau et verre transparents |
| `distantGeneratorMode` | FEATURES | arbres et vegetation, sans structures (comme le pack) |
| `numberOfWorldGenerationThreads` / ratio | 8 / 0.8 | la moitie des 16 threads, le jeu garde le reste pour son tick et Sodium |
| `numberOfLodBuilderThreads` / ratio | 4 / 0.5 | |
| propagateur / fichiers | 2 / 0.5 chacun | |
| `enableAutoUpdater` | false | DH ne doit pas se mettre a jour seul et casser la compat Iris |
| `rendererMode` | DEFAULT | le pack le livre DISABLED |

Le rayon et la charge CPU se changent a chaud dans le menu DH (touche du mod),
sans redemarrer.

### 29.2 Le test en jeu

`verify_dh.sh` : entre dans la sauvegarde `test`, monte le joueur a y=140 en
spectateur, attend 150 s, capture. Resultat avec Complementary Unbound : les
reliefs au-dela des 12 chunks vanilla sont bien des LOD, rendus par le shader
(fond de brume coherent). En 2 min 30, 693 sections de niveau 0 ecrites dans la
base.

**Le seul bruit** : 260 erreurs `ServerTickEvent error: NullPointerException
LocalPlayer.blockPosition()` pendant les 13 s ou le client compile le shader a
l'entree du monde (le serveur integre tique deja, `Minecraft.player` est encore
null). Bug de la 2.2.1, sans effet : la generation demarre des que le joueur
existe. L'instance CUSTOM n'en a aucune dans son journal du 13 juin (compilation
du shader plus rapide, fenetre plus courte). La 2.4.5-b devrait l'oter ; a
verifier si le joueur accepte le telechargement.

### 29.3 Animations de combat : rien dans ATM10

Le scan des 443 mods de l'instance CUSTOM ne trouve **aucun mod d'animation de
combat ou de deplacement** : `player-animation-lib` n'est qu'une bibliotheque
(Iron's Spellbooks s'en sert) et `cleanswing` empeche juste de frapper l'herbe
a la place du creeper. Les candidats existants pour NeoForge 1.21.1, tous a
telecharger (Modrinth, verifie le 2 sept. 2026) :

| Mod | Ce que ca change | Cote | Poids |
|---|---|---|---|
| Fresh Animations 1.10.4 (pack de ressources) + Entity Model Features 3.3.3 + Entity Texture Features 7.2.1 | les monstres et villageois bougent de facon realiste (marche, tete, membres) | client seul | 2,3 Mo |
| Not Enough Animations 1.12.4 | le joueur : arc, manger, grimper, ramer, carte, objets tenus | client seul | 1,9 Mo |
| First Person Model 2.7.2 | on voit son corps (et ses ailes) a la premiere personne | client seul | 1,6 Mo |
| Better Combat 2.4.0 (+ playerAnimator 2.0.4) | animations d'attaque par type d'arme, allonge, combos ; nos glaive et sceptre demandent un JSON `weapon_attributes` | les deux | 1,2 Mo |
| ParCool 4.0.0.3 | deplacements : roulade, saut de haie, escalade, ramper, saut mural | les deux | 1,1 Mo |
| Camera Overhaul 2.1.1 | inclinaison et inertie de la camera | client seul | 0,1 Mo |
| Epic Fight 21.17.3.1 | refonte totale du combat, monstres re-animes ; lourd, change tout l'equilibre, nos armes a decrire | les deux | 8,6 Mo |
| Physics Mod 3.0.32 | ragdolls a la mort, tissus | client seul | 61,8 Mo |

### 29.4 Ce qui est installe (2 sept. 2026, accord du joueur)

Telecharges depuis Modrinth, SHA-512 verifiees, dans `run/mods` (et
`run/resourcepacks` pour le pack) :

| Fichier | Role | Cote |
|---|---|---|
| `DistantHorizons-2.4.5-b-1.21.1-fabric-neoforge.jar` | remplace la 2.2.1 du pack | client ; **aussi serveur** pour le multijoueur |
| `entity_model_features-3.3.3-1.21-neoforge.jar` + `entity_texture_features-7.2.1-1.21-neoforge.jar` | moteur des modeles animes (EMF exige ETF >= 7.2.0) | client |
| `FreshAnimations_v1.10.4.zip` | pack de ressources : monstres, villageois, animaux animes | client, **a activer** dans `options.txt` (`resourcePacks`) |
| `notenoughanimations-neoforge-1.12.4-mc1.21.1.jar` | animations du joueur (arc, manger, grimper, ramer, carte) | client |
| `bettercombat-neoforge-2.4.0+1.21.1.jar` + `player-animation-lib-forge-2.0.4+1.21.1.jar` (remplace la 2.0.1) + `cloth-config-15.0.140-neoforge.jar` (deja dans ATM10) | animations d'attaque par arme, allonge, enchainements | les deux |

Ecartes par le joueur : First Person Model, ParCool ; par moi : Epic Fight
(refonte totale, equilibre du mode a refaire), Physics Mod (61 Mo, lourd).

**Better Combat et nos armes** : `data/emeraldweapons/weapon_attributes/` declare
`arcencium_glaive` (`parent: bettercombat:glaive`, deux mains, trois coups) et
`oath_blade` (`bettercombat:sword`). **Le sceptre n'a volontairement pas de
fichier** : son tir part du clic gauche (`ArcenciumScepterClient.onAttackInput`),
et Better Combat ne prend le clic que des objets qu'il connait ; son repli par nom
reconnait `sceptre`, `wand`, `staff`, `rod`, pas `scepter`. L'arc reste vanilla
(le repli ne vise que `two_handed_bow`).

**DH 2.4.5 : la config a change de forme** (`_version = 3`) : les threads sont un
seul reglage `[common.multiThreading] numberOfThreads = 8`, ratio 0.8 ; la
generation est dans `[common.worldGenerator]` (donc valable aussi sur un serveur
dedie) ; une section `[server]` regle l'envoi des LOD aux clients (500 Ko/s par
joueur par defaut). La migration a garde le rayon et l'auto-updater mais a remis
la qualite en MEDIUM : HIGH/HIGH reappliques.

**Mesure** : 8 threads, mode FEATURES, sur le monde `test` : 51 puis 72, 81 et
89 chunks/s en montee de regime. A ce rythme un rayon de 64 chunks est plein en
2 min 30, 128 en 10 min, 256 en 45 a 55 min : le bon reglage pour un monde neuf
de 60 minutes. L'erreur `LocalPlayer.blockPosition()` de la 2.2.1 a disparu.
Reste dans le journal, sans effet sur les LOD : `[Supplementaries] Failed to get
Road Sign Block Entity during generation` quand le worldgen de DH pose un panneau
de route dans sa region sans entites de bloc (une dizaine par session).

**Verification en jeu** (`verify_anim.sh`, `verify_anim2.sh`) : tout charge sans
avertissement, pack actif (`Reloading ResourceManager: ... file/FreshAnimations`),
registre d'armes Better Combat synchronise, un zombie tue au glaive (progres
« Chasseur de monstres »), horizon DH a 256 sous Complementary en capture.

**Pour l'instance CUSTOM** (le modpack joue) : memes fichiers a poser, retirer
`DistantHorizons-2.2.1-a` et `player-animation-lib-forge-2.0.1`, activer le pack
dans `options.txt`, reprendre `run/config/DistantHorizons.toml`. Attention :
CUSTOM tourne avec Embeddium et non Sodium ; Iris y est present mais le journal
du 13 juin ne montre pas ses mixins Sodium appliques.

## 30. La derniere partie : l'Arc-en-ciel, le boss, la fin *(fait, verifie en jeu)*

Ce que le cahier promettait des la premiere page : trois ancres, l'Arc-en-ciel,
le boss a son sommet, la Maree qui referme tout. Le joueur l'a demande en ces
mots : « quand on a detruit les trois sanctuaires, l'apparition de la derniere
zone qu'on avait choisie avec le boss et les monstres, et le message de
victoire et/ou de defaite ».

### 30.1 Ce qui se passe

1. **La troisieme ancre tenue** (`GameManager.resolveAnchor`) appelle
   `Finale.begin`. Le titre « L'Arc-en-ciel se leve » reste cinq secondes ; le
   chat donne la distance et la direction, puis le nom du boss.
2. **L'arene** est la **Prison Givree** de Cataclysm (`cataclysm:frosted_prison`,
   choisie par le joueur), posee par le chemin de `/place structure` :
   `Structure.generate` assemble les pieces (103 a l'essai, emprise
   142 x 139 x 174), puis `StructureStart.placeInChunk` les deverse. **Six chunks
   par tick** : d'un seul coup, la pose figeait le serveur 5,2 s (« Running
   5238ms or 104 ticks behind »). Le boss et les gardes viennent quand le
   dernier chunk est pose. Sans Cataclysm : une butte de deepslate.
3. **Le site** : a 300 blocs du village, sur la bissectrice de deux
   sanctuaires, jamais a moins de 200 d'un sanctuaire ; plein est a defaut.
   La commande d'essai le leve a 120 blocs devant le joueur.
4. **Le boss**, tire au sort parmi ce qui est installe : Ignis, Ender Guardian
   (Cataclysm), Liche (Twilight Forest) ; Wither sinon. Il nait au point le
   plus haut pres du centre de l'arene (« a son sommet »), marque
   `emeraldweapons_final_boss`, persistant.
5. **Les gardes** : le Sculk de Deeper and Darker, reserve a l'arene depuis le
   cahier (snapper, centipede, leech, shattered), plus **un seul Traqueur** en
   sentinelle -- il a sa propre barre de boss, dix Traqueurs auraient couvert
   l'ecran. Dix au lever, puis tant que le boss vit, 2 a 3 de plus pres des
   joueurs presents dans l'arene toutes les 45 s (plafond 10). Sans le mod :
   le repli vanilla du palier 3.
6. **La Maree se recentre sur l'arene** (`PrismaticTide` prend `finale()`
   comme centre) : le village est englouti, le dernier quart d'heure se joue
   dans la prison.
7. **Victoire** : le boss meurt (`LivingDeathEvent` sur l'etiquette). Titre
   VICTOIRE en or cinq secondes, son de defi, trois feux d'artifice par joueur,
   le temps de la partie dans le chat, les gardes se dissipent en ames de
   sculk. **Defaite** : le chrono a zero (`GameTicker`) -> `Finale.defeat` :
   titre DEFAITE violet, souffle du Wither, « la Maree a tout recouvert ».
   Cinq secondes apres, le rappel `/arcencium stop`.

### 30.2 L'Arc-en-ciel dans le ciel

L'arene est a 300 blocs : hors distance de rendu, et une balise ne se voit
pas de si loin. L'Arc-en-ciel est donc **dessine sur la coupole**, comme le
voile de meteo (`RainbowArchRenderer`, AFTER_WEATHER, `debugQuads`) : sept
bandes de teinte, un arc dont l'ouverture depend de la distance -- etroit et
bas de loin, au-dessus de la tete quand on est dessous. Ce n'est pas un objet,
c'est une direction : on marche vers l'arc. Le client le sait par
`GameSyncPayload.finalePos` (septieme champ : `composite` s'arrete a six, codec
ecrit a la main). Le panneau des objectifs montre aussi une ligne « ◈ direction
distance » en couleur tournante, et dit « Victoire » ou « Defaite » a la fin.

### 30.3 Essai

`/arcencium finale [boss]` leve l'arene devant le joueur ; `finale win` et
`finale lose` jouent les deux fins. `verify_finale.sh` (scratchpad) : Prison
posee, Ignis au sommet, chat « The Rainbow rises 120 m away, to the S. / Ignis
guards its summit. », « Victory in 7:22 » puis « Time is up », aucune erreur.
Deux pieges rencontres : l'avertissement **« reglages experimentaux »** bloque le
client des que Twilight Forest ou Deeper and Darker entrent dans `run/mods`
(`confirmedExperimentalSettings` mis a 1 dans `level.dat` de la sauvegarde
test) ; et **tuer le client de test ne sauve pas le monde** -- l'etat de partie
sur disque reste celui du dernier arret propre.

`verify_arch.sh` : l'arc vu de 450 m (etroit, au-dessus de la prison), de 250 m
(plus large) et de 69 m (il passe au-dessus de la tete), avec la ligne
« ◈ N 450m » du panneau. La pose par morceaux a pris 6 s sans figer le
serveur (« 103 pieces sur 108 chunks »).

Reste ouvert : le boss tombe-t-il bien du sommet vers la cour ? (Ignis fait
quatre blocs de large ; a voir en jouant.) Et la Liche n'a pas encore ete vue.

## 31. La Forge d'Arcencium *(faite, a valider en jeu)*

Le joueur voulait « un etabli specifique pour monter le stuff, qui montre
clairement les materiaux necessaires selon le niveau d'amelioration et la
probabilite de reussir ». L'Etabli de Sertissage savait forger, mais en
cachant tout : la pierre dans la case d'artefact, le metal pris dans le sac
sans qu'on sache lequel, le tirage au moment de prendre la piece.

### 31.1 Ce que fait la forge

- **Bloc** `arcencium_forge` (`ArcenciumForgeBlock`) : enclume de fer sombre
  au foyer de braises, textures derivees de l'etabli par recoloration
  (`tools` : aucun, c'est un script PIL d'une fois ; voir la texture). Se
  mine a la pioche de fer, eclaire faiblement. Recette : trois lingots
  d'Arcencium, fer / enclume / fer, trois briques de deepslate.
- **Une seule case** (`ArcenciumForgeMenu`). On y vient l'arme en main : elle
  **monte d'elle-meme** sur la forge a l'ouverture (cote serveur, le client
  recoit le contenu). Le metal et la Pierre de Forge restent dans le sac.
- **L'ecran** (`ArcenciumForgeScreen`, 176 x 240) montre tout : le cran
  actuel et le suivant avec sa chance, les pierres portees, puis
  **l'echelle entiere des dix crans** -- metal (icone), `porte/requis` en vert
  ou rouge, nom du metal, chance -- la ligne du prochain cran en surbrillance
  doree, les crans passes coches. Un bouton **Forger** ; le verdict s'affiche
  sous le bouton (reussi +N, echec, materiaux manquants).
- **Le bouton** passe par `clickMenuButton`, le mecanisme vanilla des menus :
  aucun paquet a ecrire, et le serveur revalide piece, cran, pierre et metal
  avant de tirer. On paie d'abord (pierre puis metal), on tire ensuite --
  la regle de l'etabli, inchangee. Le verdict voyage par `ContainerData`.
- **Les regles ne changent pas** : bareme, chances, materiaux et absence de
  retrogradation sont ceux de `Upgrade` (section 22). L'etabli sait toujours
  forger ; la forge est le chemin clair.

### 31.2 Essai

`/arcencium forge` ouvre la forge sans bloc ; `/arcencium upgrade <n>` pose
un cran sur la piece en main. `verify_forge.sh` (scratchpad) : glaive +3, fer,
or, trois pierres, ouverture, deux clics sur Forger par la souris (position
calculee depuis la fenetre), puis le bloc pose.

**Piege du generateur de donnees** : `runData` echoue avec `run/mods` tel
quel -- ce n'est pas nous, c'est **Lootr**, dont le generateur de compat copie
un `logo.png` inexistant (`LootrCompatDataGenerators.gatherData`) des qu'il
recoit `GatherDataEvent`. Il faut sortir `lootr-neoforge-*.jar` de `run/mods`
le temps de la generation, puis le remettre.

## 32. Le premier vrai essai, et ce qu'il a corrige *(2 sept. 2026)*

Quarante minutes de jeu, **premier sanctuaire toujours pas tenu**. Le joueur a
tout releve d'un coup ; chaque point est mesure ci-dessous avant d'etre corrige.

### 32.1 L'Arcencium etait introuvable — quatre goulots, pas un

C'est la monnaie de TOUT le mode (ancres, equipement, runes, amelioration).
Quatre choses le rendaient rare a la fois :

| Goulot | Avant | Apres |
|---|---|---|
| **Le poids en coffre** | l'entree pesait **1 contre 14** dans son lot : une chance sur quinze qu'elle sorte, quelle que soit la pile promise | poids 6 au sanctuaire, 4 ailleurs (aussi frequent que le meilleur du lot) |
| **La pioche** | `needs_diamond_tool` : on trouve un filon a la dixieme minute sans pouvoir le casser | `needs_iron_tool`, et une pioche de fer dans le kit |
| **Le filon** | 4 veines/chunk, taille 6, de -32 a 48 | **9 veines**, taille 8, de -48 a **64** (les grottes de surface comptent) |
| **Les monstres** | n'en lachaient **aucun** | un sur huit, **un sur trois** sous tempete ou Maree (1-2) |

Quantites en coffre relevees : sanctuaire 1-3 → 3-6 / 4-8 / 5-10 selon le
palier ; cathedrale et citadelle 1-3 → 2-5 et jusqu'a 5-10 au tresor. Meteo :
cratere 1-2 → 3-5, cicatrice 1-2 → 2-4, eclats d'orage 2-3 → 4-6.

**La lecon** : quand une ressource semble rare, regarder le POIDS avant la
quantite. Un lot bien dote qui ne sort jamais ne dote rien.

### 32.2 Quatre-vingt-dix minutes

`GAME_MINUTES` 60 → 90, et les phases gardent leurs proportions :
Montee 18 → **27**, Pression 36 → **54**, Assaut 48 → **72**, Fin 60 → **90**.
La Maree part avec la Pression, donc a 54 minutes.

### 32.3 Le confort : ce qu'on ne doit jamais avoir a fabriquer

Le joueur testait dans l'environnement de DEV, ou aucun mod de confort n'etait
installe : ni JEI (donc **aucun moyen de voir les recettes**), ni sac a dos, ni
poubelle, ni tri. Ils sont tous dans le modpack ; ils sont maintenant dans
`run/mods` aussi (`tools/dev_mods.py jei sophisticatedbackpacks trashslot
invtweaks craftingtweaks inventoryessentials actuallyadditions`).

Le kit de depart (`GameManager.equipStarter`) donne en plus :

- **Sac a dos en netherite** (`sophisticatedbackpacks:netherite_backpack`) : le
  plus grand du pack, tous les emplacements ;
- **Etabli de poche** (`actuallyadditions:crafter_on_a_stick`) ;
- **pioche de fer** Efficacite I et **32 torches**.

Resolus par identifiant (`BuiltInRegistries.ITEM.getOptional`) : sans le mod,
on n'a rien et rien ne casse.

**La poubelle** existait deja : c'est **TrashSlot**, une case sous l'inventaire.
**Le tri au clic du milieu** ne marchait plus parce que TROIS mods se
disputaient le clic : les tris de Sophisticated Backpacks et Sophisticated
Storage, et celui d'InvTweaks. Les deux premiers sont debranches, InvTweaks
garde le clic du milieu (« trier ce qui est sous le curseur ») dans `run/` et
dans l'instance CUSTOM.

### 32.4 Les reperes, enfin trouvables

La Cathedrale et la Citadelle existaient depuis longtemps et **n'apparaissaient
jamais** : trois biomes seulement (marais, marais de paletuviers, foret sombre)
et 96 chunks d'ecart, soit 1536 blocs — la zone de jeu fait 750 de rayon.

- biomes : 3 → **28 terres** (plaines, forets, taigas, savanes, jungles, deserts…) ;
- ecart : cathedrale 96/40 → **40/16** (~640 blocs), citadelle 60/24 → **28/11** ;
- a l'ouverture du jeu, `GameManager.announceLandmarks` annonce la distance et
  la direction de chacune (tags `emeraldweapons:arcencium_cathedral` /
  `arcencium_citadel`, recherche a 6 chunks de rayon).

Ce sont des objectifs **optionnels**, plus faciles qu'un sanctuaire, et le
meilleur rendement en Arcencium du mode.

### 32.5 Les lags

Mesure dans le journal de SA partie (`run/logs`, monde `Try1`, 22h45–23h25) :
**27 « Can't keep up »**, dont deux de 10 et 12 secondes, et des arrets de 2 a
4 secondes toutes les minutes. Entre eux, les lignes des threads de generation
de Distant Horizons.

C'est ma faute : j'avais regle DH sur **8 threads a 0,8** de temps de calcul —
la moitie du processeur, en permanence, pendant que le serveur integre tique.
Ramene a **4 threads a 0,35**, rayon **192** au lieu de 256. L'horizon se
remplit un peu moins vite ; le jeu reste fluide. Config recopiee dans CUSTOM.
Verifie apres coup : **zero** « Can't keep up » sur la session d'essai.

Second levier, valide par le joueur : l'instance CUSTOM allouait **60 Go** de
tas Java. Un tas enorme n'accelere rien -- Minecraft n'en utilise qu'une
fraction -- mais allonge les pauses du ramasse-miettes, qui se voient comme des
saccades. Ramene a **16 Go** (`minecraftinstance.json`, `allocatedMemory`
60000 → 16384 ; l'ancien fichier est garde en `.bak-60go`). A faire **CurseForge
ferme**, sinon l'application reecrit le fichier en quittant.

## 33. Le deuxieme retour du joueur *(3 sept. 2026)*

### 33.1 Le profil importe plantait : c'etait nous

`Mod 'architectury' is not available!` en tete du rapport -- un leurre. Plus
haut dans le journal : **`Failed to register class ArcenciumBowClient with
@EventBusSubscriber`** : la classe ecoute `FMLClientSetupEvent`, un evenement
du **bus de mod**, sans `bus = Bus.MOD`. NeoForge **21.1.193** (le dev) devine le
bus tout seul ; **21.1.174** (celui de CurseForge et de l'instance CUSTOM)
refuse, notre mod ne se construit pas, et le premier mod qui parle a
Architectury tombe sur un chargement casse. Corrige d'un mot ; les neuf autres
abonnes au bus de mod l'ecrivaient deja.

**La lecon** : le dev tourne sur un NeoForge plus recent que le pack. Un
plantage qui n'apparait QUE dans l'instance CurseForge vient de la, avant
toute autre hypothese.

### 33.2 « Le jeu est en anglais »

Le joueur joue en **fr_ch**. Minecraft ne retombe pas sur `fr_fr` pour une
autre variante du francais : il retombe sur l'anglais. `fr_ch.json` et
`fr_ca.json` sont des copies de `fr_fr.json`, et soixante-dix cles qui etaient
restees en anglais dans `fr_fr` (les blocs, quelques raretes) sont traduites.

### 33.3 La fete (`util/Celebration`)

Une reussite ne disait rien : une ligne grise et un son d'enclume. Maintenant,
partout la meme chose -- **titre plein ecran** dans la couleur de l'evenement,
gerbe de particules autour du joueur, deux sons, et un **feu d'artifice** pour
les grandes marches : +8 et au-dela a la forge et a l'etabli, rang 5 et plus
pour la rarete, paliers ronds (+5, +10, +15, +20) pour la specialisation.

### 33.4 L'Autel de Specialisation

Le pendant de la Forge pour le personnage : bloc `specialization_altar`
(forge teintee violet, cristal pale au centre), menu sans case -- les plumes
restent dans le sac -- et bouton **Tenter** qui appelle `Specialization.tryUpgrade`,
la meme routine que la plume en clic droit : une regle, deux portes. L'ecran
montre les **vingt paliers en deux colonnes** (plumes portees / requises,
chance), le prochain surligne. Recette : Arcencium, plumes autour d'un bloc
d'amethyste, deepslate. `/arcencium autel` pour l'essai.

**L'economie**, comme le joueur l'a voulu : « facile parce qu'on en ramasse
beaucoup, pas parce qu'il en faut peu ». Couts ×2,5 (296 plumes de +0 a +20 a
100 %) ; drops de 6 %+22 %·pv/200 a **25 %+40 %·pv/200**, 1 a 3 plumes sur les
puissants. Mesure : ~70 plumes attendues pour +10, ~190 pour +15, ~650 pour +20,
et une partie en rapporte 150 a 180. Le +20 se gagne sur plusieurs parties. La
Pierre de Forge passe de 12 a 20 %, 1 a 3.

### 33.5 Les artefacts, enfin

- **Les notres tombent des monstres** : la table de la section 9.3 n'avait
  jamais ete ecrite. Sous meteo agressive et a ciel ouvert, 4 / 8 / 14 / 22 %
  selon les ancres tenues, double pour les puissants ; les monstres de tempete
  et de Maree y ont droit sans condition. En coffre, le lot « vide » passe de
  96 a 40 : 44 % d'artefact par coffre au lieu de 25.
- **Ceux du modpack** (`compat/ModAccessories`) : Artifacts (49) et Relics (30)
  tires dans le registre par espace de noms, meme chance. Aucune classe des
  deux mods n'est citee.
- **Les reliques arrivent etudiees** (`compat/RelicResearch`) : Relics
  verrouille chaque capacite derriere une enigme d'etoiles a relier. Par
  reflexion sur `IRelicItem.setAbilityResearched`, tout ce que le joueur porte
  est marque etudie une fois par seconde, d'ou que cela vienne.

### 33.6 Les reperes colles au village

`random_spread` ne connait pas le village. Une **zone d'exclusion** de 12
chunks autour de `arcencium_villages` sur les deux ensembles : la cathedrale et
la citadelle se trouvent, mais a une marche.

### 33.7 Les lags au combat

Le journal de sa session ne montre que deux arrets, a la mise en place. Le reste
est cote client, et le client de dev tournait avec le **tas par defaut : 32 Go**
(le quart de la machine), le meme mal que l'instance a 60 Go : de longues pauses
du ramasse-miettes qui se voient quand on frappe. `build.gradle` fixe desormais
**8 a 16 Go** avec G1 regle court pour toutes les configurations de lancement.

Un serveur local n'y changerait rien : le serveur integre tourne deja sur son
propre thread ; le probleme etait la memoire du client.

### 13.3 Le menu principal *(refait)*

Le profil importe affichait le menu d'ATM10 : « Modpack Background (Please
Change Me) », « LOGO TEXT HERE Est. 2019 », un « Custom Button » et un bouton
d'affiliation Akliz. Tout cela vit dans **`packmenu/resources`** de l'instance
-- un pack de ressources ordinaire lu par le mod PackMenu :

| Quoi | Ou |
|---|---|
| Le fond | `assets/packmenu/textures/gui/background.png` (1920x1080) |
| Le logo | `assets/packmenu/textures/gui/logo.png` (300x300, dessine a 100x100) |
| Les boutons | un JSON par bouton dans `assets/packmenu/buttons/` |
| Leurs textes | `assets/packmenu/lang/en_us.json` |
| Le titre, le panorama, la position du logo | `config/packmenu.cfg` |
| **Le nom en bas a gauche** | ailleurs : `config/bcc-common.toml`, `modpackName` |

Fait : fond = une vraie image du mode (le village et l'horizon sous Distant
Horizons, recadree hors de l'interface) ; logo = l'embleme du mode (pyramide a
degres, ancre prismatique, arc-en-ciel, `tools/pack/packmenu/`) ; les deux
boutons de demonstration supprimes ; `modpackName = "Mode Arcencium"`,
version 1.1.0. `tools/export_modpack.py` emporte desormais le dossier
`packmenu`, donc le menu voyage avec le profil.

**A refaire quand le jeu sera ferme** : le fond vient d'une capture 854x480
recadree et agrandie. Une vraie prise en 1920x1080, interface cachee (F1),
demande `overrideWidth`/`overrideHeight` dans `run/options.txt` et un client
libre -- `shoot_menu.sh` (scratchpad) le fait, il refuse tant qu'une partie
tourne.

## 34. Trois bugs de jeu, et la plume *(3 sept. 2026)*

### 34.1 La Brume Prismatique brillait la nuit

Sous le shader, en pleine nuit, la brume devenait des **taches blanches
lumineuses** entre les arbres. Le voile se dessine en geometrie SANS lumiere
(`debugQuads`) : sa couleur est la meme a midi et a minuit. De jour, un pastel
clair passe pour de la brume ; la nuit, il devient une lampe.

`SkyVeilRenderer` multiplie desormais la couleur du voile par la clarte du
ciel : `1 - getStarBrightness()`, avec un plancher a 0,16 pour qu'il reste
lisible. L'Orage garde son eclat d'eclair (`max(clarte, flash)`), sinon la
foudre ne se verrait plus la nuit.

**La lecon** : tout ce qu'on dessine sans lumiere doit suivre le ciel a la main,
sinon il brille la nuit.

### 34.2 Les pretres et les pyromanciens ne se battaient pas

Chez Iron's Spellbooks, **`PriestEntity`, `PyromancerEntity` et
`ApothecaristEntity` heritent de `NeutralWizard`** : ce sont des MARCHANDS. Ils
ne sont pas passifs par accident, ils sont concus pour commercer. Deux des
trois sorciers du palier 2 etaient donc des figurants.

Verifie dans le jar : implementent `Enemy` -- `cultist`, `keeper`,
`cryomancer`, `necromancer`, `archevoker`, `fire_boss`. Le vivier du palier 2
prend maintenant `cultist` et `keeper` a la place du pyromancien et du pretre.
La garnison des sanctuaires, qui tire du palier 2, en profite.

### 34.3 Les coffres des tours n'etaient pas defendus

Le joueur a trouve la faille : entrer dans le sanctuaire, monter la vis d'une
tour, vider quatorze coffres, ressortir -- sans un combat.

La cause tient en une ligne : `restrictTo(centre, 40)`. **Tous** les
defenseurs etaient attaches au CENTRE du sanctuaire, et leur
`MoveTowardsRestrictionGoal` les y ramenait ; ils quittaient donc les tours
pour s'agglutiner dans la cour. Un garde retenu par le centre n'est pas un
garde de tour.

- `SanctuaryGarrison.spawnGuard` attache chacun a SON poste (rayon 12) ;
- `postGuard(level, pos, rayon)` pose un gardien attache de pres ;
- `Sanctuary.towerInterior` en pose **un par palier** avec les deux coffres,
  rayon 5 : il ne descend pas, et on ne peut pas l'attirer dehors ;
- la salle du tresor en recoit **trois**, rayon 6.

### 34.4 La plume ne monte plus rien toute seule

Demande deja faite, mal appliquee : la Plume d'Arcencium montait un palier au
clic droit, sans montrer ni le cout, ni la chance, ni le gain. Elle renvoie
maintenant a l'**Autel de Specialisation** (section 33.4), qui est le seul
chemin -- comme la Forge l'est pour les armes.

### 34.5 L'Aurore servait a rien

« Elle est jolie, mais elle n'apporte rien de bien et rien de mal », et c'est
l'une des deux seules meteos du debut de partie : une fenetre sur cinq gaspillee.

Le cahier lui donnait pourtant un role -- « les veines d'Arcencium proches
scintillent : sous terre, c'est un detecteur, le moment de descendre miner ».
Le code faisait bien quelque chose, mais deux details le rendaient invisible :

1. la sonde cherchait dans un **cube de douze blocs**, donc il fallait deja
   etre sur la veine ;
2. elle posait ses lucioles **sur le filon**, c'est-a-dire DANS la pierre, ou
   personne ne les voit.

Refait : la sonde balaie **quarante blocs** autour du joueur et envoie un **rai
de lumiere du filon jusqu'a six blocs au-dessus du sol**. Depuis la surface, on
voit des colonnes prismatiques sortir de terre, et l'on sait ou creuser. Six au
plus par joueur, une par colonne, rafraichies toutes les trois secondes.

**Le cout tenu** : parcourir 80x64x80 blocs par joueur et par seconde serait
cent mille lectures. On interroge d'abord la **palette de chaque section de
chunk** (`LevelChunkSection.maybeHas`) : une section sans Arcencium repond non
sans qu'on l'ouvre, et il n'en reste qu'une poignee a lire.

Et pour recompenser celui qui creuse, pas seulement celui qui regarde : **un
morceau d'Arcencium brut de plus (1-2) par filon casse pendant l'Aurore**.
Sous-titre refait : « Les veines d'Arcencium percent le sol. Descendez miner. »

### 34.6 Le niveau de Heros se perdait a la mort

Une heure de jeu effacee par une chute. La cause est vieille comme les mods :
**a la mort, Minecraft ne ressuscite pas le joueur, il en construit un autre**
et ne recopie qu'une poignee de choses. Le niveau de Heros, son experience, ses
points places et son element vivent dans `player.getPersistentData()` -- qui
reste sur le cadavre.

`util/PlayerPersistence` ecoute `PlayerEvent.Clone` et recopie le compose
entier, cle par cle, sans ecraser ce que le jeu a deja copie. Tout y passe : la
Rage du Glaive, la Surcharge, les refroidissements -- ils sont tous dates, ce
qui doit expirer expirera seul. Les attributs se remettent d'eux-memes :
`HeroEvents.onTick` reapplique la fiche et la renvoie au client periodiquement.

**La regle, deja notee pour la synchronisation, se double d'une seconde :**
`getPersistentData()` ne traverse ni le RESEAU (il faut un paquet) ni la MORT
(il faut `PlayerEvent.Clone`).

**Pour reparer une partie en cours** : `/arcencium hero level <niveaux>` rend
les niveaux perdus, `/arcencium hero xp <montant>` l'experience.
