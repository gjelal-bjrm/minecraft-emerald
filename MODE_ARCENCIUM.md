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

| Option | Famille | Grades | Effet |
|---|---|---|---|
| SL Attaque | Arme | C → A | **+9-10 / +11-13 / +14-17** niveaux en Attaque |
| SL Élément | Arme | C → A | idem, en Élément |
| SL Défense | Armure | C → A | idem, en Défense |
| SL HP/MP | Armure | C → A | idem, en Vitalité |
| **SL Générale** | Arme | **S** | **+9 à +13** niveaux dans **les quatre** |

Ce sont des **niveaux entiers** : le tirage ne rend jamais « SL Attaque 11,73 ».

**Deux options SL de la même catégorie ne se cumulent pas** — règle donnée par
le joueur : une SL Attaque sur l'arme et une autre sur le casque, deux SL
Générale, on garde **la plus haute** (`Runes.best`). Les deux *catégories*,
elles, s'ajoutent : une SL Générale et une SL Attaque ne sont pas la même
option. Sans cette règle, cinq pièces gravées SL Défense donnaient
quatre-vingts niveaux, soit plus qu'une partie entière de points dépensés.

Ces niveaux **ne se paient pas** et s'ajoutent par-dessus l'achat. Ils peuvent
pousser une voie **au-delà du centième** — jusqu'à 120, plafond relevé de
NosTale — ce qu'aucune dépense de points ne permet.

C'est de loin l'option la plus forte du catalogue. Mesuré en points de fiche
économisés :

| Grade | Sur une voie à 0 | à 50 | à 90 |
|---|---|---|---|
| C (9-10) | ~10 pts | ~40 pts | ~72 pts |
| B (11-13) | ~13 pts | ~52 pts | ~94 pts |
| A (14-17) | ~17 pts | ~68 pts | ~120 pts |
| S — les quatre voies | ~44 pts | ~176 pts | ~312 pts |

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
| Gain | 5 % | 9 % | 13 % | 18 % | 24 % | 31 % | 39 % | **55 %** | **75 %** | **110 %** |

Sa FORME est ce qui compte : les sept premiers montent doucement, puis le
huitieme saute a 55 % et le dixieme a 110 %. Les trois derniers valent a eux
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

### 34.7 Le halo de l'arme restait a la hanche

Depuis Better Combat, l'arme se tient en travers du corps et sa lueur restait
la ou le vanilla met la main. Cause mesuree dans le jar de playerAnimator :
`HeldItemMixin.changeItemLocation` se greffe sur le calque vanilla de l'objet
tenu et applique, juste avant de dessiner, l'os **« rightItem » /
« leftItem »** de l'animation -- echelle, position (en seiziemes), rotations
Z, Y, X (radians). Notre calque de halo rejouait les transformations vanilla et
s'arretait la.

`client/compat/AnimatedHand` lit le meme os **par reflexion** (playerAnimator
n'est pas une dependance) et `UpgradeHandLayer` l'applique apres ses
retournements de bras. Sans playerAnimator, sans animation active, ou sur un
porteur qu'il n'anime pas (monstre), rien ne change.

### 34.8 Les ailes fantomatiques

« Certaines sont tres belles, on voit leur ombre ; la majorite sont vitreuses,
sans ombre. » Les deux « belles » sont Obscures et Papillon : les seules
rendues en MATIERE. Les huit autres etaient en `entityTranslucentEmissive` --
plein feu et translucide, ce qui, par construction, ne projette pas d'ombre
sous un shader et laisse voir a travers.

Desormais **toutes** les ailes se rendent d'abord en decoupe opaque eclairee
par le monde (`entityCutoutNoCull`) : un corps, une ombre. Les apparences de
lumiere recoivent par-dessus une seconde passe emissive translucide (alpha
150, teinte de la peau) qui les fait luire la nuit sans les rendre
fantomatiques.

### 34.9 Deux choses qui ne sont pas a nous

- **Plantage en recherche creative** : `PerkItem.appendHoverText` d'**Ars
  Nouveau 5.8.3** appelle `InputConstants.isKeyDown` (GLFW) depuis le thread
  ou Minecraft construit l'index de recherche des infobulles -- « GL error
  off-thread ». Pas une ligne a nous dans la trace. Mettre Ars Nouveau a jour
  (5.13.1) est risque : sept extensions Ars et six mods en dependent, tous
  epingles par ATM10. **Contournement** : chercher avec JEI (panneau de
  droite), pas avec l'onglet de recherche de l'inventaire creatif.
- **« All The Mods 10 » en bas a gauche** : ce n'est pas BCC (corrige pour
  rien) mais **AllTheTweaks**, qui l'ecrit en dur selon son « pack mode ». Le
  mod ne fournit que cela, Discord et quelques blocs-logos ; rien n'en depend.
  Il sort du profil des que le jeu est ferme (jar verrouille tant qu'il tourne).

## 35. L'armure amelioree, troisieme version : le lisere et la gravure *(3 sept. 2026)*

### 35.1 Pourquoi la deuxieme a echoue

La coque gonflee et translucide de la section 26 « respirait » autour du
corps. En 3D, cela se lit comme du **cellophane colore**, et le blanc pur des
+7 et +10 ecrasait la piece. Le joueur : « pas beau du tout ». L'aura de
NosTale est un sprite 2D ; un maillage gonfle n'en est pas la traduction.

### 35.2 Le lisere (`client/ModRenderTypes.rim`, `UpgradeArmorLayer`)

On ne dessine que le **contour**. La coque est rendue en ne gardant que ses
**faces arriere** : elles sont de l'autre cote du corps, le test de profondeur
les cache partout ou le corps est devant, et il n'en survit qu'un trait au bord
de la silhouette, large comme le debord de la coque. Aucun shader.

**Premiere tentative, ratee** : inverser l'elimination dans l'etat GL
(`glCullFace(GL_FRONT)` dans le creneau de superposition du type de rendu). En
jeu la coque s'est dessinee PLEINE : l'ordre n'a pas survecu au pipeline --
Sodium et Iris tiennent l'etat de rendu a leur compte. **Ce qui marche** : ne
pas toucher au pilote et retourner les faces elles-memes. `ModRenderTypes.flipped`
enveloppe le tampon et rejoue les quatre sommets de chaque quad a l'envers ;
une face avant retournee est une face arriere, et l'elimination ordinaire fait
le reste, quel que soit le moteur.

Trois coques, pour trois epaisseurs (debord sur l'armure exterieure gonflee
d'un) : fine 1,35, moyenne 1,7, large 2,5. Translucide et non additif : le
trait se voit sur la neige comme dans le noir.

### 35.3 La palette, et l'echelle

Le joueur a choisi la palette du mode plutot que le cycle de NosTale :

| Cran | Couleur | Trait | En plus |
|---|---|---|---|
| +1, +2 | blanc froid | fin | -- |
| +3, +4 | blanc froid | moyen | -- |
| +5 / +6 / +7 | or / turquoise / violet | moyen | -- |
| +8 / +9 | or / turquoise | **double** : trait moyen net + voile large | **pulsation** |
| +10 | **prismatique** (la teinte tourne en 6 s) | double | pulsation |

La **pulsation** n'est pas une respiration : une seule onde part des pieds et
gagne la tete en 2,2 s (`UpgradeGlow.pulse`), chaque piece s'allume a son
passage. C'est la vague de la lame, portee au corps.

`item/UpgradeGlow` reste la table unique : le halo de la lame, ses particules
et l'armure suivent la meme palette -- une lame +10 tourne aussi.

### 35.4 La gravure (armure d'Arcencium seulement)

Les **jointures des plaques**, relevees dans la texture de l'armure elle-meme
(`tools/armor_engraving.py` : saut de luminance entre texels voisins, bord
exterieur des pieces), en blanc sur fond transparent. `ArcenciumArmorLayer`
les rend apres les fissures, emissives et translucides, dans la couleur du
cran : faibles de +1 a +4, franches de +5 a +7, pleines et au passage de l'onde
a partir de +8. Une amelioration ajoute ; la piece reste dessous.

Les autres armures du modpack n'ont pas de gravure -- il faudrait un calque par
texture -- et gardent le lisere seul.

## 36. Le debut de partie, deuxieme lot *(4 sept. 2026)*

### 36.1 L'atelier du village (`game/Workshop`)

Les trois stations -- Forge d'Arcencium, etabli de sertissage, Autel de
Specialisation -- sont posees a la mise en place, a **douze blocs plein est
de la Lame**, sur une dalle de briques de gangue avec deux lanternes. Le joueur
l'a voulu pour donner une raison de REVENIR au village : sans lui, les stations
se fabriquent (Arcencium, plumes, amethyste) et personne ne les a avant la
deuxieme moitie de partie.

### 36.2 L'equipement vanilla (`item/GearEligibility`)

Toutes les **epees** et toutes les **armures** de l'espace de noms `minecraft`
passent par les trois systemes, avec des plafonds :

| | Pieces du mode | Pieces vanilla |
|---|---|---|
| Amelioration | +10 | **+7** -- puis « Trop faible pour aller au-dela » |
| Rarete | Phenomenal (8) | **Ancestral (5)** -- l'eclat ne fait rien, rien n'est consomme |
| Runes | rang ≤ rarete | rang ≤ rarete, donc **5 au plus** |

Les pieces des autres mods ne passent pas : on ne connait ni leur force ni
leur equilibre. Le bonus de rarete et d'amelioration s'appliquait deja a tout
ce qui porte des degats ou de l'armure (`RarityStats`) ; seules les portes
etaient fermees.

### 36.3 Les runes, comme le joueur les voulait

Le rang donnait un **schema fixe** : une Legendaire avait toujours cinq
options et toujours un S. Maintenant :

- le **nombre d'options est tire** entre le schema moins deux (une au moins)
  et le schema entier -- une Legendaire en a 3, 4 ou 5 ; une Phenomenale 4 a 6 ;
- les **grades sont tires sans remise dans le vivier du rang** : « CBAAS »
  n'est plus une suite imposee mais cinq lettres dont on en prend `count`. Le
  S n'est plus garanti, il est possible ;
- chaque option tire sa valeur entre **68 %** du maximum et le maximum (les
  degats critiques : +39 a +57 %, les chiffres du joueur) ;
- **les runes tombent facilement** : 9 % de base (3 % avant), +11 % sur les
  betes coriaces ;
- **le rang suit l'heure** : Exploration ≤ 3, Montee ≤ 5, tout ouvert des la
  Pression -- combine au plafond par points de vie ; sous le plafond un rang
  pese `plafond - rang + 2`, et les boss tirent deux fois en gardant le
  meilleur. **Mesure** (483 monstres, trois boss, 3 000 parties simulees) :
  63 runes par partie, un rang 7+ dans 25 % des parties, un rang 8 dans 2 %.
  Avec le 8 reserve a l'Assaut et un poids en `+1`, il ne sortait dans AUCUNE
  partie : un rang qu'on ne voit jamais n'est pas rare, il est absent.

### 36.4 Les ailes portent (`client/WingsFlightClient`, `specialization/WingsFlight`)

En l'air, touche Saut tenue : des +5 la chute se freine, des +10 on plane
(une poussee vers le regard), a +15 presque une elytre, a **+20 le double
saut** (le meme que les Bottes d'Eclair). Le plane se calcule sur le CLIENT --
le mouvement d'un joueur est pilote par son client -- et le serveur efface les
degats de chute a proportion (67 % pardonnes a +5, tout a +15). On ne monte
jamais : un plane descend toujours, c'est ce qui le laisse honnete.

### 36.5 Les chiffres de degats, enfin visibles

Ils existaient (`DamagePopClient`) mais etaient dessines DANS le monde, a
l'etape « apres les particules » -- que le pipeline d'Iris ne rend pas. Ils
sont maintenant projetes sur l'**interface** : on retient les matrices de la
camera a chaque image, on projette la position du coup a l'ecran, on ecrit
le chiffre la. Le HUD est dessine apres le shader, quel que soit le shader.
Le critique reste plus gros, dore, precede d'un eclair, et il bondit.

### 36.6 Les Ailes du Souverain Astral

Trois propositions dans `tools/prompts/ailes_souverain_astral.md` (Couronne
d'Astres, Firmament Brise, Aurore Souveraine) : apparence, bonus, animation
ajoutee par le mod, prompt. Le joueur choisit ; l'apparence, son bonus et son
animation viendront ensuite.

## 37. La Traque, les hauts rangs de rune, la courbe d'amelioration *(4 sept. 2026)*

### 37.1 « Il n'y a pas assez de monstres »

Le joueur cherchait des monstres a farmer et n'en trouvait pas. La cause n'est
pas un reglage mais une consequence : **le mode se joue a midi**
(`WorldSetup` fixe l'heure a 1000, la meteo la retient), or Minecraft ne fait
apparaitre d'hostiles a ciel ouvert que dans le noir. Le bestiaire vivait dans
les grottes pendant que la partie se joue en surface -- et la partie DEMANDE de
farmer : runes, plumes, Arcencium.

`game/Prowl` ajoute une **pression**, sans toucher aux regles d'apparition du
jeu. Toutes les cinq secondes, on compte les hostiles autour de chaque joueur ;
s'il en manque, on en pose un ou deux **derriere lui**, sur un anneau de 22 a
38 blocs. Le nombre vise suit la phase :

| Phase | Hostiles vises dans 40 blocs | Vivier |
|---|---|---|
| Prologue | 0 *(le siege suffit)* | -- |
| Exploration | 10 | palier 1 |
| Montee | 14 | palier 2 |
| Pression | 18 | palier 3 |
| Assaut, Fin | 22 | palier 3 |

Trois garde-fous : la **zone sure du village** (rien n'apparait dans
quarante-huit blocs, mais la passe continue -- les monstres se posent AUTOUR) ;
les traques **ne sont pas persistants**, ils s'effacent d'eux-memes et rien ne
s'accumule ; et un **casque force** sur la tete, sans quoi la moitie du vivier
vanilla brule au soleil -- on aurait peuple le monde de torches.

### 37.1 ter Le compte se fait a l'etage du joueur

Premiere mesure de la version corrigee : **zero pose en une minute**. La cause
etait le volume du compte -- un cube de RADIUS de demi-cote, soit cent
quatre-vingt-douze blocs d'arete, qui ramassait **tous les monstres des grottes
sous les pieds du joueur**. Huit y sont toujours ; la cible etait donc toujours
atteinte, et la Traque ne posait jamais rien.

On ne compte plus que sur vingt blocs de hauteur : c'est bien d'un PAYSAGE
qu'il s'agit, pas d'une colonne jusqu'au socle du monde.

### 37.1 bis On peuple, on ne chasse pas *(corrige apres essai)*

La premiere version posait les monstres a **vingt-deux blocs** et leur
**donnait le joueur pour cible**. Le joueur : « je me fais harceler non-stop,
ce n'est pas le but ; c'est a moi d'aller vers eux, pas a eux d'etre a cote de
moi ». Il a raison, et l'erreur etait de conception : j'avais ecrit une
PRESSION la ou il fallait une POPULATION.

| | Avant | Maintenant |
|---|---|---|
| Anneau d'apparition | 22-38 blocs | **48-96 blocs** |
| Cible designee | le joueur | **aucune** |
| Rayon du compte | 40 blocs | **96 blocs** |
| Vises (Exploration → Assaut) | 10-22 | **8-20** |
| Village | la passe s'arretait | **zone sure de 48 blocs, la passe continue** |

Le chiffre vise decrit desormais une **densite de paysage** : sur un disque de
quatre-vingt-seize blocs, huit a vingt silhouettes, c'est un monde habite qu'on
traverse -- et non une meute au coude. Et comme aucun d'eux ne recoit d'ordre,
ils ne viennent que si l'on s'approche.

### 37.2 Les rangs 7 et 8 de rune existent enfin

Le joueur : « l'objectif c'est quand meme d'avoir la possibilite d'obtenir des
runes rang 7 et 8, juste moins frequemment ». Or ils sortaient dans 2 % des
parties quoi qu'on fasse du tirage -- et j'avais d'abord cherche la correction
du mauvais cote.

**Le goulot n'etait pas le tirage, c'etait le plafond par points de vie** :
il fallait 400 PV pour ouvrir le rang 8, et seuls les trois boss d'une partie
y arrivaient. La table descend donc d'un cran -- une bete a 200 PV (monstre de
siege tardif, seigneur de Maree) peut donner du rang 8 :

| PV max | < 15 | < 30 | < 60 | < 100 | < 200 | ≥ 200 |
|---|---|---|---|---|---|---|
| Rang maximum | 3 | 4 | 5 | 6 | 7 | 8 |

**Mesure**, avec la Traque qui double le bestiaire croise : 116 runes par
partie, un rang 7 ou plus dans **76 %** des parties, un rang 8 dans **34 %**.
Par rune : rang 1 une fois sur trois, rang 7 une fois sur cent, rang 8 une fois
sur deux cent cinquante. Possibles, et bien plus rares que les autres.

### 37.3 Le +10 ne double plus les degats

« Deux cents pour cent au plus dix, c'est beaucoup trop » -- et c'est juste :
une arme qui triple ses degats termine seule une partie de quatre-vingt-dix
minutes, et la rarete, les runes et la fiche du Heros deviennent decoratives.

Le plafond descend a **+110 %**, la FORME est conservee -- c'est elle qui compte :

| Cran | +1 | +2 | +3 | +4 | +5 | +6 | +7 | +8 | +9 | +10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Avant | 10 | 15 | 22 | 32 | 43 | 54 | 65 | 90 | 120 | 200 |
| **Maintenant** | 5 | 9 | 13 | 18 | 24 | 31 | 39 | **55** | **75** | **110** |

Les trois derniers crans valent toujours a eux seuls plus que les sept premiers
reunis (240 contre 139) : c'est bien a partir du +8 qu'on gagne gros. Et
l'equipement vanilla, plafonne a +7, s'arrete a +39 % -- l'ecart avec les armes
du mode se creuse au bon endroit.

## 38. Voir loin : ce qui etouffait Distant Horizons *(4 sept. 2026)*

« Dans certaines videos on voit vraiment tres loin, et dans mon mode pas tant
que ca. » Trois causes, mesurees -- et **deux ne venaient pas de DH**.

### 38.1 Notre propre brouillard (la cause principale)

`WeatherClient.onRenderFog` fixait le plan lointain a **56 a 210 blocs** selon
la meteo. DH en affiche **4 096**. On coupait donc a **2 a 7 %** de ce qu'il
sait montrer -- et comme une meteo du mode tourne presque en permanence apres
l'Exploration, on ne voyait quasiment jamais loin. Ce n'etait pas DH, c'etait
nous.

Une seule meteo est vraiment un brouillard, et son nom le dit : la **Brume
Prismatique** (72 blocs, c'est tout son propos). Les autres teintent le ciel et
assombrissent ; elles n'ont aucune raison de fermer l'horizon :

| Meteo | Avant | Maintenant |
|---|---|---|
| Brume Prismatique | 56 | **72** *(c'est un brouillard)* |
| Orage Prismatique | 84 | **420** |
| Nuit d'Arcencium | 96 | **520** *(il fait noir, ce n'est pas du brouillard)* |
| Dechirure | 140 | **620** |
| Meteores | 180 | **780** |
| Aurore | 210 | **1 400** |

### 38.2 Le brouillard propre a DH

`farFogStart` valait **0,4** : DH commencait a voiler des 40 % de sa portee,
en exponentiel carre a densite 2,5. La moitie lointaine s'effacait toute
seule. Et le brouillard de HAUTEUR, a densite **20** sous y=80, effacait les
plaines basses -- exactement les « structures mal chargees au loin ».

Profil pose (`tools/dh_profile.py`, applique aux trois configurations) :
`farFogStart` 0,75 · `farFogDensity` 1,0 · `heightFogDensity` 3,0 ·
`heightFogEnd` 0,9.

### 38.3 La generation etait bridee -- par moi

Quatre fils a 35 % du temps : reglage pris le soir ou l'on cherchait la cause
des lags. **Cette cause etait le tas de 32 Go**, corrige depuis (§33.7). Le
brida est donc reste sans raison, et le relief lointain mettait un temps fou a
se construire.

Sur un Ryzen 5800X (seize fils) : **8 fils a 70 %**, limite de requetes 20 →
50, portee 192 → **256 chunks (4 096 blocs)**, qualite verticale et horizontale
en EXTREME.

### 38.4 Ce que le profil ne peut pas faire

DH **n'affiche que ce qu'il a deja genere**. Un monde neuf montre peu, quel que
soit le reglage ; les videos ou l'on voit a perte de vue sont tournees sur des
mondes ou DH a tourne des heures, ou pre-generes. Deux remedes possibles, si le
rendu ne suffit toujours pas :

- laisser une partie tourner une fois : la generation continue en fond ;
- pre-generer avec Chunky autour du village avant de jouer.


## 39. Les Ailes de Pierres Precieuses : belles en image, ternes en jeu *(4 sept. 2026)*

Le joueur les a repeintes et les trouve « trop elevees et trop serrees » une
fois portees. **J'ai verifie avant de conclure**, et ma premiere lecture etait
fausse : le cadrage (boite du dessin dans la toile) et la repartition de la
matiere sont IDENTIQUES a celles du Givre et des Prismatiques, qui rendent tres
bien -- centre de masse a (0,47 ; 0,50) contre (0,51 ; 0,51), meme part de
matiere dans le tiers haut. Ce n'est donc ni un decalage, ni un centrage.

**LA CAUSE ETAIT L'ANCRAGE, et le joueur l'a tenu bon contre deux de mes
diagnostics.** Le calque posait TOUTES les ailes sur le meme point de leur
toile -- (0,12 ; 0,78) ecrit en dur. Or chaque peinture place sa racine ou elle
veut. Releve (centre de masse des 8 % de matiere les plus proches du coin
bas-gauche) :

| Aile | racine (u ; v) |
|---|---|
| Obscures | (0,12 ; 0,85) |
| Prismatiques, Rubis, Aurore | (0,14-0,15 ; 0,85-0,87) |
| Givre, Braise, Tempete, Emeraude | (0,16-0,17 ; 0,83-0,85) |
| Papillon | (0,21 ; 0,79) |
| **Pierres Precieuses** | **(0,21 ; 0,76)** |

Toutes etaient donc dessinees un peu trop haut, et les Pierres Precieuses --
racine la plus haute ET la plus rentree -- l'etaient le plus : « trop elevees
et trop serrees », mot pour mot. `WingSkin` porte desormais la racine de
chaque apparence et le calque s'en sert.

**Les deux diagnostics precedents etaient faux**, et il faut le noter parce
qu'ils sont instructifs. J'ai d'abord accuse les branches separees : elles sont
voulues, et le Givre a exactement les memes en rendant tres bien. La mesure a
tranche autrement. En relevant sur les dix ailes l'**etalement de teinte**
(0 = une seule couleur, 1 = arc-en-ciel) et la **saturation moyenne** :

| Aile | Etalement | Saturation |
|---|---|---|
| Givre, Rubis, Tempete, Braise | 0,01 - 0,02 | 0,47 - 0,80 |
| Obscures, Papillon | 0,06 - 0,09 | 0,20 - 0,69 |
| Emeraude, Aurore | 0,16 - 0,17 | 0,44 - 0,71 |
| Prismatiques | 0,33 | **0,16** *(pastel)* |
| **Pierres Precieuses** | **0,56** | **0,71** |

Le jeu d'ailes suit donc une regle que personne n'avait formulee : **soit une
famille de teintes, meme tres saturee ; soit beaucoup de teintes, mais pales**.
Les Pierres Precieuses enfreignent les deux a la fois, et a un bloc de large
l'oeil ne peut plus GROUPER les plumes en un objet -- chacune devient une tache,
et l'ensemble se lit comme des confettis.

**L'epreuve qui tranche** : reduire l'image a 64 pixels, ce que le jeu en fait.
Le Givre et les Prismatiques restent des ailes ; les Pierres Precieuses
deviennent un gribouillis. **Corollaire** : la variete d'une aile a l'autre gagne a
passer par le PATRON plutot que par la couleur.

Ces deux relevés restent vrais et utiles -- ils disent comment une texture se
LIT une fois reduite -- mais ils n'expliquaient pas la plainte du joueur, qui
portait sur le PLACEMENT. Il l'a maintenu deux fois ; il avait raison.

**Le patron retenu** : le **Vitrail** (brief complet dans
`tools/prompts/ailes_pierres_precieuses.md`). De larges panneaux de verre
colore tenus par un plombage noir epais, en deux branches, avec une racine d'or
massive. Le plombage est ce qui manquait : des lignes sombres epaisses
survivent a la reduction et GROUPENT la forme, ce qui autorise enfin des
couleurs nombreuses et franches.

En attendant une repeinture (prompt dans `tools/prompts/ailes_pierres_precieuses.md`) :

- `WingSkin` porte desormais un **ecartement** et une **envergure** par
  apparence. Les Pierres Precieuses recoivent +14 degres et +15 % : les deux
  branches se separent et chaque pierre redevient lisible. Toutes les autres
  gardent zero -- une aile bien composee n'en a pas besoin ;
- `specialization/WingGems` : **les pierres tombent des ailes**, demande du
  joueur. Une gemme toutes les trois tiques s'egrene et s'efface, dans six
  couleurs franches. Aucune texture peinte : on donne un bloc de gemme a la
  particule de poussiere qui tombe, et elle en prend la couleur -- emeraude,
  redstone, lapis, or, amethyste, diamant. Cote serveur, donc visible par
  toute l'equipe.

## 40. Les Ailes du Souverain Astral *(4 sept. 2026)*

Le joueur a choisi le patron A -- **la Couronne d'Astres** -- et son bonus, puis
a peint les deux images : le **Vitrail** pour les Pierres Precieuses, et
l'astrale. Nuit profonde piquetee d'etoiles, nervures d'or, nebuleuse violette
et turquoise au coeur.

### 40.1 L'import : detourer un fond NOIR

`tools/wings_import.py` savait detourer le blanc et le vert. Les deux images
arrivent sur fond **noir opaque**, et un simple seuil de luminance aurait troue
l'aile astrale de part en part -- elle est elle-meme d'un bleu presque noir.

On procede donc **par region** : le fond est la zone sombre CONNEXE qui touche
le bord de l'image (remplissage par diffusion depuis les quatre cotes). Tout ce
qui est sombre mais ENFERME dans le dessin -- l'ombre entre deux plumes, le
creux d'une gemme -- reste opaque. Les bords recoivent un degre d'un pixel pris
de la luminance, pour que la decoupe ne soit pas crenelee.

Les textures passent aussi de 512 a **1024** : ces peintures ont du detail a
garder.

### 40.2 Ce que les deux nouvelles valent, mesure

L'epreuve des 64 pixels, sur fond de ciel et sur fond de nuit :

| Aile | Racine (u ; v) | Lisible reduite |
|---|---|---|
| Pierres Precieuses (vitrail) | (0,12 ; 0,80) | **oui** -- l'or groupe les panneaux, les couleurs se lisent en bandes ordonnees |
| Souverain Astral | (0,18 ; 0,87) | **oui** -- silhouette sombre franche, nebuleuse et nervures d'or au coeur |

Le vitrail regle ce qui clochait : le plombage dore fait exactement le travail
attendu, et les sept teintes ne se lisent plus comme des confettis.

L'astrale est rendue en emissif mais **retenue a 0,72** : plein feu, ses etoiles
se seraient lavees dans la lumiere au lieu de s'allumer.

### 40.3 Le bonus : la Constellation

+12 % de chance de critique et +20 % de degats critiques -- au-dessus de toutes
les autres apparences -- et surtout :

> **Chaque critique allume une etoile** (jusqu'a cinq). A cinq, le coup suivant
> est un **critique garanti** qui frappe **tout ce qui entoure la cible**
> (rayon 3, 60 % des degats) et eteint les etoiles.

Les etoiles s'eteignent seules apres **huit secondes** sans critique : sans
cela on les accumulerait sur des poules avant d'entrer au sanctuaire, et la
recompense ne recompenserait plus le combat. Chaque etoile monte d'un ton --
on ENTEND la constellation se remplir.

C'est ce qui distingue ces ailes d'un sac de pourcentages : elles demandent de
tenir le rythme, et elles rendent un moment plutot qu'un chiffre.

### 40.4 L'obtention

La Plume du Souverain Astral tombe **du boss final, et de lui seul**, a la
condition que les **trois sanctuaires** soient tombes -- une victoire obtenue
en courant droit a l'arene ne la donne pas. **Une chance sur trois** : il faut
gagner plusieurs fois, ce qui convient a une apparence qui se garde entre les
parties. Elle est explicitement hors du vivier des plumes de monstre.

### 40.5 Les animations : deux versions refusees, puis la regle

**Premiere version** : la poussiere qui tombe d'un bloc pour les pierres du
Vitrail -- un grain de deux pixels, chute en une demi-seconde. Invisible ; le
joueur ne l'a jamais vue.

**Deuxieme version** : des gemmes-objets qui tombaient de l'aile toutes les
quatre tiques, et pour le Souverain Astral des etoiles blanches (END_ROD)
partout -- aux pointes, en anneau autour des epaules, en trainee de plane, en
couronne. Verdict du joueur : les pierres « font pleurer les ailes », et les
etoiles sont « trop visibles, ca gache la vue et empeche de contempler les
ailes ». Il a demande « des choses plus discretes et plus petites ».

**La regle qui en sort, a garder pour tout effet d'aile futur** : un effet
d'aile est un ACCENT, jamais un spectacle. L'aile est le spectacle. Ce qu'on
ajoute doit se lire du coin de l'oeil et disparaitre quand on regarde en face.

Ce qui reste, et rien de plus :

| Aile | Effet | Cadence |
|---|---|---|
| Vitrail | un **glint** : une seule mote prismatique (5 a 10 centiemes de bloc, fondu en entree et en sortie) posee SUR l'aile, sans vitesse -- une facette qui accroche la lumiere. Rien ne tombe. | une toutes les 2,5 s |
| Souverain Astral | le **scintillement est dans la lueur de la texture** (`WingsLayer` : deux ondes emissives qui ne battent jamais a l'unisson) et le battement plus lent et plus ample ; aucune particule au repos | continu, rendu client |
| Souverain Astral | la **Constellation**, parce qu'elle est une information de jeu : une mote par etoile allumee, qui tourne lentement au-dessus de la tete | une par etoile toutes les 20 tiques |
| Souverain Astral | l'etoile gagnee : une mote ; la frappe pleine : douze motes autour de la cible | a l'evenement |

Plus d'anneau, plus de trainee, plus de couronne.

---

## 41. Deux facons de jouer, et une refonte des coffres *(4 sept. 2026)*

### 41.1 Le regime du monde : Defi ou Monde ouvert

Le mode n'avait qu'une facon de se jouer : quatre-vingt-dix minutes, la Maree
qui referme la carte, une victoire ou une defaite. C'est une bonne course --
mais tout ce qu'on a bati autour (meteo, runes, ameliorations, specialisation,
ailes) demande plus de temps qu'une course n'en laisse.

**Le regime appartient au MONDE** (`GameState.Mode`, sauvegarde en NBT), pas au
joueur ni au serveur : deux mondes peuvent se jouer differemment, et un monde ne
change pas de nature entre deux connexions.

**Comment on choisit** — trois portes, une seule verite :

1. **une carte dans le chat a la connexion**, tant que la question n'a pas de
   reponse : un titre, une ligne par regime, deux boutons cliquables ;
2. **`/arcencium partie defi`** et **`/arcencium partie libre`** -- c'est
   exactement ce qu'appellent les boutons ;
3. **la Lame du Serment refuse de venir** tant que personne n'a tranche
   (`ModeChoice.ready`). Un defaut silencieux aurait suffi, mais lancer par
   megarde un chronometre de quatre-vingt-dix minutes sur un monde qu'on
   voulait habiter est le genre d'erreur qu'on ne peut plus defaire.

Le choix se verrouille au retrait de la Lame ; `/arcencium stop` rouvre le lobby
et donc le choix.

### 41.2 Ce que change le Monde ouvert

| | Defi | Monde ouvert |
|---|---|---|
| Chronometre | 90 min, defaite au bout | aucun |
| Maree Prismatique | de la minute 54 a la fin | jamais |
| Phases (meteo, equipement des monstres) | l'horloge | **les ancres tenues** : 0 → Exploration, 1 → Montee, 2 → Pression, 3 ou arene levee → Assaut |
| Difficulte de fond (`MobGear.stage`) | le temps ecoule | **le cycle** : +1/3 par tour de piste |
| Boss abattu | ecran de victoire, fin | **le cycle recommence** |
| Meteo, prologue, sanctuaires, sieges, butin | identiques | identiques |

**Le cycle** (`GameManager.raiseNextCycle`, vingt secondes apres la mort du
boss) : trois nouveaux sanctuaires se dressent ailleurs -- la rosace tourne de
37 degres par cycle et s'ecarte de 60 blocs jusqu'a trois cycles. Les anciens
**restent debout** : ce sont des ruines qu'on a prises, et la carte se couvre
peu a peu de sanctuaires morts. L'interface remplace le compte a rebours par
**« Cycle N »**.

### 41.3 La meteo s'annonce par un presage, plus par son nom

On lisait « Orage Prismatique dans 12 s » : une fiche technique, et plus rien a
decouvrir. Le preavis dure maintenant **dix a quinze secondes tirees au sort**
(une duree constante se compte), il part **une seule fois**, et chaque meteo a
**sa phrase**, qui decrit un signe et jamais la chose :

| Meteo | Presage |
|---|---|
| Brume | « L'air s'epaissit ; au loin, les couleurs se brouillent. » |
| Aurore | « Le ciel palit, comme s'il retenait sa lumiere. » |
| Nuit | « Le jour recule. Quelque chose attend qu'il s'eteigne. » |
| Meteores | « Une odeur de pierre chaude descend du ciel. » |
| Dechirure | « Quelque part l'air se fend, et le bruit arrive en retard. » |
| Orage | « Les cheveux se dressent ; la lumiere gresille. » |

Le panneau de l'interface ne dit plus que **« ⚠ Presage »**, dans la couleur de
la meteo -- seul indice offert a qui la connait. Le nom arrive avec la meteo
elle-meme, en plein ecran.

### 41.4 Le personnage appartient au MONDE

`SpecializationStore` ecrivait dans le dossier du serveur, qui en solo est le
dossier du jeu : **un seul personnage pour tous les mondes de la machine**. On
remettait a zero dans un monde d'essai et le personnage du vrai monde
disparaissait avec.

Le fichier vit desormais dans `<monde>/emeraldweapons/specialization.json`.
Chaque monde a son niveau d'ailes et ses apparences, comme il a deja son niveau
de Heros (donnees du joueur) et son equipement.

L'ancien fichier global n'est **ni lu ni efface** : il est signale a la
connexion, et **`/arcencium personnage importer`** le reprend dans le monde ou
l'on est. Le recopier tout seul aurait donne le meme personnage maxe a tous les
mondes -- exactement ce qu'on venait de corriger.

### 41.5 Les coffres : cent cinquante-six devient vingt

**La mesure d'abord.** Un sanctuaire comptait quatre tours d'angle de sept
etages et huit tourelles de porte de six, a **deux coffres par etage**, plus
les quatre du tresor : **156 coffres**. On sortait du premier sanctuaire avec de
quoi finir la partie, et les deux suivants n'avaient plus rien a donner.

Trois coupes, sans qu'un seul bloc du monument change :

- les **tourelles de porte** n'ont plus de coffre : ce sont des postes de garde,
  leurs gardiens restent ;
- **un** coffre par etage au lieu de deux ;
- **un etage sur deux**, en commencant par le premier -- le sommet reste paye,
  la tour ayant un nombre impair d'etages.

**Vingt coffres par sanctuaire.** Le gardien, lui, reste a *chaque* etage : on
ne monte pas plus facilement, on monte pour moins de coffres et chacun compte.

### 41.6 Le butin suit l'echelle d'amelioration

Un coffre ne donne pas ce qui est **cher**, il donne ce qui **sert
maintenant**. On trouvait neuf diamants par coffre des le premier sanctuaire,
alors que le diamant n'entre dans l'amelioration qu'a **+7** -- une fortune
inutile, la pire des recompenses.

| | Palier 1 | Palier 2 | Palier 3 |
|---|---|---|---|
| Ce qu'il finance | +1 a +6, la specialisation | +7, +8 | +9, +10, le boss |
| Fer / Or | 75 / 45 | 15 blocs / 57 | — |
| Diamant | **5** | 69 | 86 |
| Netherite | — | 9 eclats | 23 lingots |
| Lingot d'Arcencium | — | — | 41 |
| Plumes d'Arcencium | 20 | 38 | 71 |
| Eclats du Destin | 20 | 29 | 39 |
| Artefacts | 4 (1 coffre sur 5) | 7 (1 sur 3) | 10 (1 sur 2) |
| Notre equipement | — | — | ~2 pieces (1 coffre sur 13) |

*(moyennes par sanctuaire, 200 tirages, `scratchpad/sim_loot.py`)*

Trois decisions dans ce tableau :

- **les fioles d'experience disparaissent des trois paliers.** L'experience du
  mode se gagne en tuant, le niveau de Heros ne les lit meme pas, et par vingt
  coffres elles rendaient l'enchantement gratuit ;
- **les artefacts ne sont plus garantis.** Ils l'etaient aux paliers deux et
  trois : cinquante artefacts par partie, pour six emplacements -- on ne
  choisissait plus, on rangeait. Et **dix artefacts majeurs** (Filtre de Brume,
  Jambieres de Maree, Plaque de Gangue, Bottes de Retour, Eclat Final, Drain de
  Cristal, Fleche Fourchue, Ruee en Chaine, Coque Prismatique, Repere d'Echo)
  sont **ecartes du palier un** et **trois fois plus probables au palier
  trois** ;
- **notre equipement** quitte le tirage ordinaire du palier trois -- ou les
  quatre pieces d'armure tombaient a quatre lancers, ce qui vidait la Forge de
  son sens -- pour un tirage a part : une chance sur treize, soit une piece ou
  deux par sanctuaire, jamais la panoplie.

---

## 42. L'Aurore se dit, et le menu ne se perd plus *(4 sept. 2026)*

### 42.1 L'Aurore parle a qui a des veines sous les pieds

Rappel du concept (section 6) : l'Aurore est **la fenetre de la mine**. Les
veines d'Arcencium a moins de **40 blocs** envoient un **rai de lumiere** du
filon jusqu'au-dessus du sol -- six colonnes au plus, rafraichies toutes les
trois secondes -- et **chaque filon casse pendant l'Aurore rend 1 a 2 morceaux
d'Arcencium brut de plus**.

Rien de tout cela ne se DISAIT. Le sous-titre « Descendez miner » passe en trois
secondes, sous un titre, une fois ; les colonnes de lumiere ne veulent rien dire
pour qui ignore leur regle ; et le supplement de minerai tombe au milieu du
reste du filon, indiscernable. Deux messages, jamais plus d'un chacun par
Aurore et par joueur :

- quand des veines chantent **effectivement pres de lui** : « L'Aurore fait
  chanter *n* veine(s) d'Arcencium -- la plus proche a *d* blocs, vers le
  *cardinal*. Suivez les colonnes de lumiere : le filon rend le double tant
  qu'elle dure. » Un conseil qui compte les veines qu'on a sous les pieds est
  une information ; un conseil qu'on repete est un bruit ;
- a son **premier filon casse**, sur la barre d'action : « L'Aurore double la
  veine ».

### 42.2 Le menu du mode revenait a celui d'ATM10

Le menu (fond peint, embleme, boutons) vivait **uniquement** dans
`packmenu/resources/` de l'instance CurseForge, pose a la main une fois. Le
depot n'en gardait qu'une copie morte dans `tools/pack/packmenu/`, que rien ne
reinstallait.

**Mesure** : les deux instances portaient l'image d'ATM10 **du 8 juin**
(2560x1440), pas la notre (1920x1080) -- et `export_modpack.py`, qui recopie le
dossier de l'instance, l'emportait telle quelle. Le profil importe ouvrait donc
sur le menu d'ATM10, fond « Please Change Me » et bouton Akliz compris. Il a
suffi que CurseForge repare une instance pour tout perdre en silence.

**Le depot fait desormais foi.** `tools/menu_arcencium.py` repose
`tools/pack/packmenu/assets` par-dessus l'instance, et `export_modpack.py`
applique le meme calque au zip apres avoir copie le dossier de l'instance : une
instance reparee redevient juste au passage suivant, et le zip ne peut plus
mentir. Le bouton Akliz, qu'un calque ne peut pas supprimer, est livre en
version invisible (0x0, hors ecran).

### 42.3 Le damier violet : un jar reecrit sous un jeu ouvert

**Ce n'etait pas une perte, c'etait une lecture a moitie faite.** A 18h40 j'ai
recopie le jar dans les instances pendant que le client demarrait (18h38).
Minecraft lisait le fichier au moment ou il etait reecrit :

    java.util.zip.ZipException: invalid distance too far back

sur CHAQUE modele et CHAQUE structure du mod -- d'ou le damier violet et noir
sur tout notre contenu, onglet creatif et village compris. Le jar sur le disque
etait juste (`testzip()` sain, 1458 entrees) ; un redemarrage suffisait.

La regle « ne jamais toucher a la session du joueur » etait ecrite pour les
LANCEMENTS ; elle vaut autant pour l'ECRITURE. `tools/deploy_jar.py` la rend
mecanique : il lit les lignes de commande des `java`/`javaw` en cours, **refuse
toute instance dont un jeu tourne**, et verifie le zip apres copie.

### 42.4 La contradiction de l'Aurore : le diamant d'abord

Le minerai d'Arcencium se mine **a la pioche de diamant**. Une meteo qui montre
les veines d'Arcencium et paie double dessus ne servait donc a rien tant qu'on
n'avait pas de diamant -- c'est-a-dire pendant la phase d'Exploration, la seule
ou l'Aurore tombe. On donnait une clef a qui possedait deja la serrure.

- **la sonde cherche aussi le diamant** (`#c:ores/diamond`, donc les variantes
  moddees aussi) : meme rai de lumiere, meme portee de 40 blocs ;
- **un filon de diamant casse pendant l'Aurore rend 1 a 2 diamants de plus**, et
  **une fois sur deux un Arcencium brut avec**.

La veine d'Arcencium qu'on voit briller a quarante blocs devient atteignable
dans la meme fenetre : la meteo ne montre plus une porte fermee, elle donne la
clef et la porte.

---

## 43. Le gel de fin de siege *(4 sept. 2026)*

### 43.1 La mesure

Journal du joueur, instance « Mode Arcencium », 4 septembre :

```
18:54:11  Can't keep up! Running 3506ms or 70 ticks behind
18:56:25  Can't keep up! Running 6996ms or 139 ticks behind
18:56:45  Can't keep up! Running 5800ms or 116 ticks behind
18:58:12  Can't keep up! Running 7104ms or 142 ticks behind
```

Quatre gels de 3,5 a 7,1 secondes, tous juste apres la defense du village --
« je ne peux ni manger ni rien faire, je ne peux meme pas ouvrir l'inventaire ».
Ce sont les TROIS SANCTUAIRES. Le fil serveur est unique : tant qu'il batit, il
ne repond plus, et le client attend ses reponses (l'inventaire est une requete
serveur ; manger aussi).

**Pourquoi c'est si lourd**, en arithmetique et non en intuition : l'emprise
deblayee fait `2*(HALF + TOWER_RADIUS) + 1 = 211` colonnes de cote, soit
**44 521 colonnes**, chacune lue sur une cinquantaine de blocs vers le haut et
treize vers le bas -- plus de **deux millions de lectures**, avant la moindre
pose.

### 43.2 Le chantier s'etale

`Sanctuary.Job` : le corps de `build()` devient une **file d'etapes**, consommee
sur un **budget de douze millisecondes par tique** (sur les cinquante d'une
tique serveur), un seul chantier a la fois. Le deblaiement est coupe en bandes
de deux colonnes -- cent six etapes a lui seul.

**L'ordre est intouche.** Il porte des annees de corrections : la cour avant la
pyramide, le couloir avant l'ascension, le calque avant-dernier, la garnison en
dernier quand plus un bloc ne bouge. Seul le MOMENT change. `build()` existe
toujours et vide la file d'un coup : c'est la voie des commandes et des essais.

Les trois sanctuaires mettent alors une minute ou deux a se dresser, a 450
blocs de la, pendant qu'on s'equipe au village. Personne ne les regarde monter.

**Et l'on mesure desormais** : chaque chantier ecrit dans le journal son temps
total de fil serveur ET **sa pire etape**. Si une etape unique -- une tour, la
pyramide -- depasse encore la tique, le chiffre le dira et on la coupera a son
tour. C'est ce qui manquait pour trancher sans deviner.

### 43.3 Un coffre rase ne doit pas vomir son contenu

Dans le meme journal, dix fois :

```
Failed to create block entity minecraft:barrel ... got Block{minecraft:air}
    at BarrelBlock.onRemove -> Containers.dropContentsOnDestroy
    at Sanctuary.set -> Sanctuary.clearSite
```

Le deblaiement passe sur des villages et des structures. Chaque coffre et
chaque tonneau appelait son `onRemove`, qui **fait tomber tout son inventaire
au sol** : des centaines d'entites-objets a nourrir, et une entite de bloc
recreee sur de l'air. On retire l'entite de bloc AVANT de poser l'air : plus
rien a laisser tomber, plus rien a recreer.

### 43.4 Un serveur dedie n'aurait rien change a CE gel

La question s'est posee -- Docker, beaucoup de RAM. La machine a **128 Go et un
Ryzen 7 5800X** : la RAM n'a jamais ete la contrainte, et un gel de tique ne
s'achete pas en giga-octets. Le travail est **mono-fil** ; un serveur dedie
l'aurait fait exactement au meme rythme, en deconnectant le client au lieu de
le figer.

Ce qu'un serveur dedie apporte reellement : deux JVM au lieu d'une, donc les
pauses de ramasse-miettes du client ne s'ajoutent plus a celles du serveur, et
l'on peut regler les deux separement. C'est un gain sur le lag DE FOND, pas sur
les pics de generation. A garder pour quand les pics auront disparu.

En attendant, un reglage concret : le client tourne en `-Xms256m -Xmx16384m`.
Un tas qui part de 256 Mo pour monter a 16 Go se redimensionne sans arret. Un
tas FIXE de 10 a 12 Go (`-Xms10240m -Xmx10240m`) supprime ces redimensionnements
-- et au-dela de 12 Go, les pauses de ramasse-miettes s'allongent au lieu de
raccourcir.

---

## 44. L'Aurore guide vraiment *(4 sept. 2026)*

### 44.1 Pourquoi elle ne guidait rien

Verdict du joueur apres dix minutes de minage sous une Aurore : « je n'etais
pas du tout guide, c'est moi qui l'ai trouve par hasard ». Deux causes, toutes
deux de notre cote, et aucune n'etait une question de dosage :

1. **une particule se cache derriere la pierre.** Le rai montait du filon
   jusqu'au-dessus du sol ; sous terre, il traverse vingt blocs de roche, et
   les particules sont dessinees avec le test de profondeur. Or c'est SOUS
   TERRE qu'on a besoin d'etre guide -- en surface il n'y a rien a miner ;
2. **`sendParticles` ne quitte pas trente-deux blocs.** Le serveur ne l'envoie
   qu'aux joueurs a moins de 32 blocs, alors que la sonde en cherchait 40 : les
   filons lointains -- les seuls qu'on n'aurait pas trouves seul -- etaient
   precisement ceux qui n'affichaient rien.

### 44.2 Trois canaux, dont aucun ne se cache

| Canal | Ce qu'il donne | Ou il vit |
|---|---|---|
| **Le panneau** (`VeinHudClient`) | fleche **relative au regard**, distance, profondeur (▲/▼), trois filons | HUD -- insensible a la roche ET aux shaders |
| **Les jalons** | une silhouette lumineuse **a travers les murs**, sur chaque filon | lueur d'entite (porte-armure marqueur, invisible) |
| **Le carillon** | la hauteur du son monte quand on approche | audio -- marche les yeux sur la paroi |

La fleche est **relative** et non cardinale : « sud-ouest » demande de savoir ou
est le sud, « en haut a droite » se suit sans reflechir. Le signe de l'angle
compte -- l'angle croit vers l'est puis le sud, c'est-a-dire vers la GAUCHE du
joueur ; sans ce moins, la boussole envoie exactement a l'oppose.

Les jalons sont des porte-armures **invisibles et luisants** : le rendu ne
dessine alors que leur contour, et le contour de lueur traverse les murs -- rien
d'autre dans le jeu ne le fait pour un BLOC. `setSmall` et `setMarker` etant
prives, on les pose par la sauvegarde (`saveWithoutId` puis `load`) ; sans
« Marker », le jalon garde sa boite de collision et l'on s'y cognerait en
percant la paroi, sur un obstacle invisible. Ils sont balayes a chaque
rafraichissement, a la fin de l'Aurore et au debut de toute meteo.

**La portee passe de 40 a 64 blocs** : a quarante, il faut deja etre presque
dessus, et une galerie ordinaire en fait cent.

### 44.3 Ce que l'Aurore donne au corps

« Elle n'aide pas du tout a miner. » Desormais, tant qu'elle dure :

- **Hate II** -- la pioche ;
- **Vitesse I** -- la galerie ;
- **la faim descend deux fois moins vite** : on reprend la moitie de
  l'epuisement a chaque passage. Pas une pause -- ce serait une invulnerabilite
  deguisee -- mais de quoi tenir une longue descente sans remonter manger ;
- **le diamant rend 1 a 2 de plus**, et une fois sur deux un Arcencium brut ;
- **le minerai d'Arcencium rend le double**.

Une fenetre de minage doit se sentir dans les mains, pas seulement se lire dans
le ciel.

### 44.4 Le reglage de la machine

`tools/java_args.py`. Le client tournait en `-Xmx16384m -Xms256m` : un tas qui
part de 256 Mo pour monter a 16 Go se redimensionne sans cesse, et au-dela de
douze gigaoctets les pauses de ramasse-miettes s'ALLONGENT -- le collecteur a
plus de memoire a parcourir, pas moins de travail. La machine a 128 Go et un
Ryzen 7 5800X : la RAM n'a jamais ete la contrainte, le reglage l'etait.

Tas **fixe de 10 Go** et jeu d'options G1 eprouve sur Minecraft moddé : des
pauses courtes et regulieres plutot que rares et longues, ce qu'il faut a un jeu
qui dessine soixante images par seconde. Le script refuse toute instance dont le
jeu tourne -- CurseForge reecrit `minecraftinstance.json` a la fermeture et
effacerait le reglage.

---

## 45. Reprendre, mettre en pause, et la mesure du chantier *(4 sept. 2026)*

### 45.1 Une partie reprise reprend vraiment

**Le chronometre se gardait deja tout seul** : il se compte en tics de MONDE,
qui ne courent pas pendant que le jeu est ferme. On revient a la seconde exacte
ou l'on est parti, et cela n'a jamais demande une ligne de code.

Deux choses, en revanche, ne vivaient qu'en memoire vive :

- **le siege du village.** Quitter pendant le prologue laissait la partie en
  PROLOGUE sans aucune vague : la lame retiree, le joueur confine au village, et
  plus rien qui puisse arriver. Une partie perdue pour de bon, et pas par le
  jeu. Il **repart depuis sa premiere vague** -- on ne sait pas ou il en etait,
  et redemander trois vagues vaut mieux que rendre la partie impossible ;
- **les sanctuaires en attente.** Quitter pendant qu'ils se dressent laissait
  des ancres annoncees a l'interface et RIEN sur le terrain. Au chargement, on
  regarde chaque ancre : si son bloc n'est pas la, le chantier retourne dans la
  file. Le palier se lit desormais sur le RANG de l'ancre et non sur ce qui
  reste a batir, sans quoi le seul sanctuaire manquant aurait recu le palier
  trois.

`GameManager.resume`, appele par `WorldSetup` au demarrage du serveur.

### 45.2 La pause

`/arcencium pause` et `/arcencium reprendre`. On ne peut pas arreter l'horloge
du monde -- elle fait pousser le ble et tourner les fours -- alors on note
l'instant ou l'on s'arrete, et au retour **on decale le depart d'autant**. Tout
ce qui se lit sur le temps ecoule suit sans le savoir : les phases, la Maree, la
defaite. **La meteo ne tire plus non plus** : revenir dans une Nuit d'Arcencium
qu'on n'a pas vue arriver serait exactement la punition qu'on cherchait a
eviter. L'etat est SAUVEGARDE : une pause survit a la fermeture du jeu, ce qui
est meme le cas le plus courant.

L'interface affiche « ⏸ 89:58 » en bleu : un compte a rebours arrete sans rien
qui l'explique se lit comme un jeu bloque.

### 45.3 Le chantier, mesure puis coupe en deux

On ne devine plus quelle etape coute : chaque chantier journalise son temps
total **et le nom de sa pire etape**. Trois passages, trois mesures :

| Etat | Total | Pire etape |
|---|---|---|
| avant | 5 500 ms **en une tique** | -- |
| file d'etapes | 5 551 ms etales | **pyramide, 1 148 ms** |
| pyramide par quadrants | 5 698 ms | **muraille, 546 ms** |
| muraille en tranches de 24 | 6 082 ms | **pyramide q1, 519 ms** |

Le pic est passe de **7 secondes a un demi**, et ce demi-seconde est une pose de
structure indivisible (`template`) : on s'arrete la. Six secondes de fil serveur
etalees sur vingt-cinq, a 450 blocs de la, pendant qu'on s'equipe au village.

### 45.4 Deux fautes trouvees par la mesure

**Le balayage qui tuait le serveur.** `sweepMarks` effacait les jalons de
l'Aurore en parcourant `level.getEntities().getAll()`. Retirer une entite
pendant qu'on parcourt la vue TROUE ses sections, et la vue rend alors des
nulls : `Cannot invoke Entity.getTags() because entity is null`, serveur a
terre en une minute. On releve d'abord, on efface ensuite. **La meme faute
dormait dans `Finale.dissolveGuards`** -- elle ne s'etait jamais reveillee
parce qu'elle ne tourne qu'une fois par partie.

**La boussole qui mentait.** J'avais raisonne que le vecteur « a droite » du
joueur etait `(cos yaw, sin yaw)` et corrige le signe en consequence. C'est le
vecteur A GAUCHE : lacet zero regarde le sud, et le sud a l'est sur sa gauche.
La capture d'essai l'a montre d'un coup d'oeil -- le filon pose a l'est
s'affichait a droite. Verifie ensuite dans l'autre sens : le filon droit devant
affiche ↑, celui a l'est affiche ←. Une boussole se relit sur une image, pas
sur un raisonnement.

### 45.5 Un filon par VEINE, pas par colonne

Le panneau affichait « Diamant 63m ▲5 » trois fois de suite : un filon naturel
de diamant tient sur deux ou trois colonnes, et la sonde en gardait un par
colonne. Trois lignes identiques ne guident vers rien de plus qu'une seule. Huit
blocs d'ecart minimum entre deux lignes, et les trois designent trois endroits.

---

## 46. La Battue et l'Heure Doree *(4 sept. 2026)*

### 46.1 Pourquoi la Brume est morte

Deux causes, mesurees et non supposees.

**Le rendu.** `config/DistantHorizons.toml` porte `enableVanillaFog = false` : DH
coupe le brouillard du jeu et dessine le sien sur 4 096 blocs. Notre mur a 72
blocs etait donc neutralise, et l'on voyait le terrain lointain A TRAVERS la
brume, sous un ciel reste bleu. « On voit parfois derriere et parfois on ne voit
pas » : c'etait exact, et ce n'etait pas rattrapable sans piloter la config de
DH meteo par meteo.

**Le jeu.** Son seul effet etait `FOLLOW_RANGE -70 %` sur les hostiles a 48
blocs -- c'est-a-dire rien de perceptible. Elle ne donnait rien et prenait la
vue.

### 46.2 LA BATTUE : la fenetre de combat

Plus un gramme de brouillard : ni mur, ni coupole, ni particules. L'horizon est
degage, et il n'y a plus rien qui puisse mal se dessiner.

| | |
|---|---|
| Ce qu'on voit | **tout ce qui vit a 64 blocs se detoure**, a travers les murs (lueur d'entite -- la meme technique que les jalons de l'Aurore) |
| Ce qu'on gagne | detection ennemie **-70 %** ; **critique garanti** sur une creature qui ne vous a pas pris pour cible ; **vivier double** ; **+50 %** de plumes, pierres de forge, cristaux, runes et experience de Heros |
| Duree | 2 minutes fixes |
| Son | une nappe tres basse toutes les huit secondes |

Elle repond a l'Aurore : **l'Aurore envoie miner, la Battue envoie combattre.**
Le vivier double se peuple dans l'ANNEAU de 48 a 96 blocs -- on ne pousse
toujours rien vers le joueur, « c'est a moi d'aller vers eux ».

Le critique d'embuscade est distinct du critique force de la Constellation :
seul ce dernier declenche la Nova astrale. Les confondre aurait donne
l'explosion a chaque creature surprise, sans aile ni etoile.

### 46.3 Le noir et blanc : construit, verifie, retire

L'ambiance « vieux film » a ete faite et elle FONCTIONNAIT, y compris sous Iris
+ Complementary : le jeu embarque le programme `color_convolve` (la vision du
Creeper) avec un uniforme `Saturation` ; a zero, chaque pixel devient sa
luminance. Un seul fichier JSON, aucun GLSL. La saturation etant un uniforme,
elle se pilotait en continu -- fondu de trois secondes, et des a-coups de
couleur rendus par les coups portes et recus.

Le joueur l'a essayee et tranchee : « au debut c'etait sympa, mais a la longue
c'est vraiment genant ». Retiree entierement. **La lecon vaut d'etre gardee :
un post-traitement du jeu passe sous Iris, et ce qui est dessine APRES la chaine
garde ses couleurs.**

### 46.4 L'HEURE DOREE : la fenetre de l'atelier

Les autres meteos poussent DEHORS. Celle-ci fait RENTRER.

- **son ciel ne nous coute rien** : on ne peint pas un voile, on DEPLACE
  L'HORLOGE a 11 800 -- juste avant le coucher. Le soleil devient rasant et
  dore, et c'est le jeu (ou le pack de shaders) qui le rend. L'horloge est
  rendue a la fin, comme pour la Nuit d'Arcencium ;
- **+15 points de reussite** a la Forge. Points fixes et non multiplicateur :
  sur un +9 a 10 % cela fait 25 %, sur un +1 a 95 % cela ne change rien. La
  fenetre vaut pour les paris qu'on n'ose pas ;
- **l'Etabli ne prend plus l'Eclat du Destin** sur une tentative de rarete.
  L'Eclat est la seule monnaie qu'on ne puisse pas produire : le rendre, c'est
  offrir une tentative entiere. Seul l'Eclat est rendu -- une rune ou un
  artefact serti reste consomme ;
- **l'interface dit le chiffre DU MOMENT** : l'ecran de la Forge et l'infobulle
  interrogent la meteo du CLIENT, sans quoi ils afficheraient dix pour cent
  pendant que le serveur en roule vingt-cinq. Une interface qui ment sur un
  pari est pire qu'une interface muette ;
- deux minutes trente.

Les trois douces se partagent donc les trois verbes du mode : **Aurore = miner,
Battue = combattre, Heure Doree = forger.**

---

## 47. Les grottes prennent vie -- le plan, et l'etape 1 *(5 sept. 2026)*

### 47.1 Le plan arrete avec le joueur

Six etapes, chacune verifiee en jeu avant la suivante :

1. **L'ambiance de la Battue** (aube, corbeaux, cor, hurlements, tambour) et le
   **blanc / rouge** -- *faite*.
2. **La Proie** (cerf ou sanglier du Twilight Forest, tire au sort) et **la
   Serie** (butin x1,5 / x2 / x3 sur les kills enchaines, affichee).
3. **La Resonance** (la pioche comme sonar : les minerais du meme type
   repondent a 4 / 8 / 12 blocs selon l'outil) et **les Percees** (la roche
   qui s'ouvre en couloir, escalier, cheminee-bassin ou salle, 1 sur 45,
   sous y = 48, en exposant les filons qu'elle croise).
4. **Les Poches** (1 minerai casse sur 12 s'ouvre sur une geode, une cache, un
   filon riche -- ou le Vide, verrouille tant que moins de deux sanctuaires
   sont tenus).
5. **Les Puits et les Brumes d'Aurore** : pendant l'Aurore, des colonnes de
   lumiere (blocs traversables, chute lente, remontee) se levent dans les
   grottes, et des paires de brumes etoilees relient deux points -- on s'y
   dissout et l'on traverse la roche jusqu'a l'autre. Annonce a 45 s et 15 s
   de la fin, extinction bloc par bloc, chute lente a qui est encore dedans.
6. **Les Echos** (Souffle, Secousse, Chant, Yeux), toutes les 3 a 5 minutes
   sous terre.

Perimetre : partout sous terre, sauf les 48 blocs du village et l'emprise des
sanctuaires. Rien a fabriquer, rien a poser : ce sont les grottes qui vivent.

### 47.2 Etape 1 : ce qu'on entend et voit de la Battue

Tout ce qui ne peut PAS mal se dessiner -- l'horloge, le son, la lueur
d'entite, des creatures vivantes -- et rien d'autre (`weather/BattueScene`).

| | |
|---|---|
| **L'aube** | horloge a 23 000 : lune encore la, premiere lueur a l'est, lumiere bleue -- l'oppose de l'Heure Doree |
| **Les corbeaux** | 5 a 8 `twilightforest:raven`, tenus en cercle a 14 blocs au-dessus du joueur (leur IA les poserait en dix secondes), rayon et hauteur qui ondulent ; ils partent avec la meteo |
| **Le cor** | la corne de chevre du jeu, variantes « call » et « seek » ; a 70 blocs au debut, puis 50, puis 30 -- la chasse se rapproche |
| **Les hurlements** | des loups a 40-70 blocs, toutes les 15 a 25 s, direction au sort |
| **Le tambour** | une grosse caisse au joueur quand un hostile qui ne l'a pas vu est a moins de 16 blocs ; tempo de 30 a 8 tiques selon la distance ; se tait des qu'il est repere |

**Blanc / rouge / or** : la couleur d'une lueur d'entite est celle de son
EQUIPE de scoreboard. Trois equipes (`arc_battue_white/red/gold`), chaque
creature y est rangee chaque seconde selon son etat -- cible sur un joueur ou
non, Proie ou non -- et toutes sont videes a la fin. Gratuit, et fiable sous
tout pack de shaders.

Verifie en jeu : captures de l'aube aux corbeaux, des contours blancs, du
zombie passe au rouge en prenant le joueur pour cible ; `blanc present`,
`rouge present`, `equipes videes`, `corbeaux partis`.

### 47.3 Etape 2 : la Proie et la Serie

**La Proie** (`weather/BattueHunt`). Une seule par Battue, tiree au sort entre
`twilightforest:deer` et `twilightforest:boar` -- deux betes terrestres, ni
nageuses ni volantes. Grossie a 1,6 par l'attribut `generic.scale` du jeu (pas
de retexture), 60 PV, nommee (*Cerf Noir, Seize-Cors, Cerf de Prisme, Grand
Brocard / Solitaire, Vieille Bete, Ecorche, Ragot*), lueur OR par l'equipe de
scoreboard. Ses buts d'IA sont remplaces : flotter, FUIR les joueurs a 24
blocs, errer, regarder. Sa vitesse est calee sur le sprint du joueur --
l'attribut vaut 0,10, et le but de fuite le multiplie par 1,45 pour le cerf
(0,145 : on ne le rattrape pas en ligne droite) et 1,3 pour le sanglier (a
egalite : on le rattrape en coupant). Le sanglier accule CHARGE : six de degats
et un recul toutes les quatre secondes, puis il refuit avec un eclair de
vitesse. Une laisse de 120 blocs la ramene vers son point d'apparition ; elle
brame toutes les vingt secondes ; sa position est sur la boussole (« Proie 72m
▼36 », en or). L'abattre sonne l'hallali (la corne « call » du jeu) et rend une
**rune au plafond de la phase**, 3 a 5 plumes et 60 d'experience de Heros. La
rater ne coute rien : « La Proie s'est echappee ».

**La Serie.** Deux kills a moins de huit secondes ouvrent une serie : x1,5 de
butin, x2 a cinq, x3 a dix, sur TOUT ce que la Battue rend deja (plumes,
pierres, cristaux, runes, experience). Elle tombe si l'on s'arrete. Le
serveur ne parle qu'aux kills (`StreakPayload` : compte, multiplicateur, tic
du dernier kill) ; le client (`StreakHudClient`) dessine la barre qui se vide
depuis ce tic, le « ×2 ! » au centre a chaque palier, et « Serie perdue » une
seconde quand elle tombe. Le gestionnaire de kill passe en priorite HAUTE pour
que le kill qui ouvre un palier en profite lui-meme.

**Les chances au-dela de un** : une serie a x3 porte la chance d'une plume a
plus de cent pour cent ; plafonner serait mentir. La partie entiere est due, la
fraction se joue (`RuneDrops.rolls`).

**La boussole sert a tout** : `VeinSyncPayload` porte desormais deux bits par
entree -- Arcencium, diamant, Proie, et la Brume d'Aurore a venir.

Verifie en jeu : « Battue : la Proie « Seize-Cors » (cerf) », « ↙ Proie 72m
▼36 », « SERIE ×1,5 4 kill(s) » puis « ×2 5 kill(s) », « Hallali ! Seize-Cors
est tombe. », Proie partie a l'arret.

### 47.4 Etape 3 : la Resonance et les Percees

Le paquet `mine` porte desormais le sous-sol, avec ses regles communes
(`Underground`) : SOUS y = 48 seulement, jamais a moins de 48 blocs du village
ni de 110 d'une ancre, et l'on ne touche qu'a la ROCHE NATURELLE -- un bloc
pose, un coffre, un minerai arretent tout. Le minerai n'est jamais detruit, il
est decouvert.

**La Resonance** (`mine/Resonance`) : casser un minerai fait repondre les
minerais DU MEME TYPE caches dans la roche autour -- un carillon a leur endroit,
plus haut quand ils sont proches, et un jalon lumineux de deux secondes et
demie a travers la pierre (`mine/Jalons`, la technique des filons de l'Aurore
sortie pour servir a tous : un porte-armure marqueur invisible et luisant qui
porte sa propre date de mort). Six echos au plus, et jamais un filon deja a
l'air libre. La portee suit l'outil -- bois et or 2, pierre 3, fer 4, diamant
8, netherite et au-dela 12 -- et double pendant l'Aurore. Toujours actif,
jamais en creatif.

**Les Percees** (`mine/Breakthrough`) : en cassant de la PIERRE sous y = 48,
une chance sur quarante-cinq que la roche cede. Un craquement, un grondement,
« La roche cede. » sur la barre d'action, et le passage s'ouvre BLOC PAR BLOC
-- une tranche par tique, avec le bruit et les eclats du bloc (evenement 2001)
pour que la fissure se voie courir. Quatre formes :

| Forme | Emprise | Ce qu'elle fait |
|---|---|---|
| Couloir (40 %) | 1x2, 6 a 14 blocs droit devant | avancer vite |
| Escalier (30 %) | descend d'un bloc par bloc, 5 a 10, avec un palier | descendre sans sauter |
| Cheminee (15 %) | gorge 2x2 de 6 a 12 blocs, chambre 3x3x3 au sol D'EAU, puis un couloir qui repart | descendre d'un coup, sans se faire mal |
| Salle (15 %) | 5x4x5 | respirer, poser une base |

L'emprise ENTIERE est verifiee avant le premier bloc : de la roche, de l'air
ou du minerai, rien d'autre, et pas une goutte de fluide a son contact --
sinon pas de percee du tout. Vingt secondes de repit entre deux. A la fin, la
faille plante DEUX A QUATRE MINERAIS dans les parois qui bordent ce qu'elle a
ouvert (ardoise sous zero, diamant seulement sous seize, Arcencium une fois sur
quatre) : « ca casse des filons ».

Commandes d'essai : `/arcencium percee <couloir|escalier|cheminee|salle>` et
`/arcencium resonance`.

Verifie en jeu : echos presents ; couloir ouvert avec un minerai en paroi ;
escalier descendant avec deux cuivres exposes ; cheminee « gorge ouverte » et
« eau a 12 » (le fond de la chambre, par les blocs) ; salle carree.

Piege d'essai a retenir : `execute as @p run tp @s ~ ~ ~` SANS `at @s` prend
la position de la source de la fonction -- le spawn du monde -- et
`/fill` refuse au-dela de 32 768 blocs, en silence dans une fonction.

### 47.5 Etape 4 : les Poches

`mine/Pockets`. En cassant un minerai sous y = 48, une chance sur douze (une
sur six pendant l'Aurore) qu'il s'ouvre : un craquement, un echo creux, et
derriere lui -- dans la direction du regard, quantifiee -- une cavite qu'on n'a
pas creusee : une gorge d'un bloc, puis un blob de 3x2x3 aux coins rognes, qui
s'ouvre du plus pres au plus loin par le chantier des Percees (memes tranches,
memes garde-fous : de la roche, de l'air ou du minerai, et rien a moins d'un
bloc d'un fluide, sinon rien). Au fond :

| | | |
|---|---|---|
| **Geode** | 50 % | 45 % des parois en amethyste, des cristaux qui poussent vers l'interieur (ils eclairent : c'est la seule poche qu'on VOIT s'ouvrir), et au sol 2-3 pierres d'element et 1-2 plumes |
| **Cache** | 30 % | un coffre, eparpille : fer et or, ou or et diamant sous zero, des torches, une rune une fois sur trois |
| **Filon riche** | 15 % | 6 a 10 blocs d'Arcencium dans les parois |
| **Le Vide** | 5 % | 5x4x5, NOIR, et une elite de la garnison des sanctuaires attachee a la cavite -- elle paie double |

**Le Vide est verrouille** tant que moins de deux sanctuaires sont tenus
(`GameState.anchorsActive() < 2`) : avant, la poche redevient une geode.

**Une Percee sur six debouche sur une Poche**, au bout de la faille.

Aucun message : le trou parle de lui-meme. Le journal, lui, dit tout
(« Poche GEODE ouverte derriere ... vers south (19 blocs) », ou pourquoi elle a
ete refusee).

Commande d'essai : `/arcencium poche <geode|cache|filon|vide>`.

Verifie en jeu : geode aux parois d'amethyste et Pierres de Lumiere au sol ;
coffre de la cache exactement au fond (par le bloc) ; filon de six Arcencium en
paroi ; Vide verrouille sans deux sanctuaires (aucune elite).

Deuxieme piege d'essai, meme famille que le premier : `execute as @p run
execute if entity @e[distance=..12]` sans `at @s` mesure la distance depuis le
spawn du monde.

### 47.6 Etape 5 : les Puits et les Brumes d'Aurore

`mine/AuroreCaves`, avec deux blocs sans objet ni recette (`AuroreLightBlock`,
`StarMistBlock` : incassables, sans butin, sans collision, lumineux, rendus en
translucide par `render_type` dans le modele -- textures procedurales, car ce
sont des effets de lumiere et non des pieces d'identite).

**Au debut de l'Aurore**, autour de chaque joueur (rayon 64, 400 colonnes
sondees au hasard, jamais dans un chunk qu'il faudrait charger, jamais sous le
ciel, jamais hors du perimetre d'`Underground`) :

- **2 a 4 Puits** la ou il y a au moins six blocs d'air au-dessus d'un sol
  naturel -- les plus grands vides d'abord, jamais deux a moins de douze blocs.
  La colonne MONTE bloc par bloc (un par tique, une note qui grimpe). Dedans on
  monte (0,22/tique), on descend accroupi, la chute est remise a zero a chaque
  tique. Le mouvement est pose des deux cotes, comme une colonne de bulles ;
- **1 a 2 paires de Brumes** : deux lieux a 30-120 blocs, en preferant le plus
  grand ecart de hauteur (c'est une remontee). Six blocs chacune (une croix et
  deux de haut). Entrer dans l'une declenche le voyage : un porteur invisible
  (porte-armure marqueur, `noPhysics`) suit une courbe de Bezier a travers la
  roche en 70 tiques, le joueur le chevauche (re-attache a chaque tique s'il
  descend), invulnerable, dans une trainee de motes ; a l'arrivee il est pose
  sur l'ancre jumelle. Le depart ne se fait qu'en ENTRANT (touche cette tique
  et pas la precedente), et jamais dans les trois secondes qui suivent une
  arrivee : on ne rebondit pas.
- La brume la plus proche est sur la boussole (`KIND_MIST`, « Brume 34m ▼12 »).

**La fin, annoncee** : a 45 s et a 15 s de la fin (`WeatherManager.
remainingTicks`), « Les puits d'Aurore s'eteignent dans N secondes ». A la
fin, les colonnes s'eteignent DE HAUT EN BAS, un bloc par tique ; qui est
encore dedans recoit dix secondes de chute lente ; les brumes se dispersent ;
un voyage en cours va jusqu'au bout.

Commandes d'essai : `/arcencium grottes ouvrir|fermer`.

Verifie en jeu : « 2 puits et 1 paire(s) de brumes leves », l'une a y = 16 et
l'autre a y = 0 ; le voyage capture en plein vol DANS la roche (x -405, y 7)
puis a l'arrivee exacte (-410, 0, -401) ; le puits qui porte le joueur de y = 0
a 7 puis 13 ; les deux avertissements ; « puits eteint » et « brume dispersee »
par les blocs apres la fin.

Troisieme piege d'essai : SendKeys reserve `{ } ( ) + ^ % ~` -- une commande
tapee au chat qui en contient est tronquee, et un chat reste ouvert apres un
echec (Echap avant `t`).

### 47.7 Etape 6 : les Echos

`mine/Echoes`. Jusqu'ici rien n'arrivait dans une galerie. Tant qu'un joueur
est SOUS TERRE (sous y = 50, sans ciel au-dessus, dans le perimetre
d'`Underground` : pas les 48 blocs du village, pas l'emprise des sanctuaires),
un echo lui arrive toutes les 3 a 5 minutes -- le premier au bout de 2 a 3,
pour que la premiere descente en rencontre un. Le compte ne tourne que sous
terre : remonter le fige, redescendre le reprend. Jamais deux fois le meme
d'affilee. Chacun est annonce d'une ligne courte, en italique, comme un
presage :

- **Le Souffle** (30 %) -- « Un souffle passe. » Un souffle de Breeze, et les
  torches a 12 blocs (murales, d'ame comprises) tombent : l'objet est au sol,
  recuperable. Le noir revient, et ce qui vient avec. S'il n'y avait rien a
  souffler, la paroi se fend a la place : pas d'echo vide.
- **La Secousse** (30 %) -- « La paroi se fend. » La secousse des Meteores
  (`WeatherPulsePayload`, 55 de tremblement, sans flash), un craquement de
  debris antiques, et 2 a 4 minerais apparaissent sur les parois exposees a
  8 blocs (le tirage par profondeur des Percees, `Breakthrough.oreFor`, avec
  l'Arcencium dedans), chacun detoure huit secondes.
- **Le Chant** (28 %) -- « Quelque chose chante dans la pierre. » Une cache
  est plantee dans la roche PLEINE a 15-25 blocs (six faces de roche autour),
  et un carillon d'amethyste la fait entendre pendant 90 s : plus fort et plus
  aigu a mesure qu'on approche, toutes les deux secondes de loin, chaque
  seconde a moins de douze. Pas de boussole : c'est l'oreille qui guide.
  A moins de trois blocs, « Le chant s'arrete : la pierre s'ouvre » -- la
  Poche-cache s'ouvre (gorge, blob, coffre garni selon la profondeur, rune une
  fois sur trois), et le bloc devant la gorge se fend. Trop tard : « Le chant
  s'est tu. »
- **Les Yeux** (12 %, rare) -- « Quelque chose vous regarde. » Un battement de
  coeur de Warden, et trois hostiles du vivier des sieges (`SiegeRoster.
  forTier`, palier = ancres tenues + 1, equipes par `MobGear`) se levent dans
  le noir a 16-24 blocs, la ou l'on peut se tenir (deux d'air sur du plein),
  DEJA DETOURES pendant dix secondes, et vous ont pris pour cible. Le combat
  qui vient a vous.

Commandes d'essai : `/arcencium echo souffle|secousse|chant|yeux`.

Verifie en jeu (galerie taillee de 57 x 4 x 57 sous vingt-huit blocs de
roche, chaque echo force par la commande) : le Souffle fait tomber les quatre
torches posees (bloc absent, objets au sol) ; la Secousse pose 3 minerais sur
569 parois candidates ; le Chant plante sa cache a 24 blocs, et le joueur
teleporte a deux blocs l'ouvre (« Poche CACHE ouverte derriere -418 22 -417
vers east », le bloc cible est de l'air, la ligne « la pierre s'ouvre » au
chat) ; les Yeux levent trois hostiles marques (« Quantite : 3 »), a cible.

Quatrieme piege d'essai : quand le script tourne en arriere-plan, la fenetre
du jeu garde le focus entre deux commandes, donc `Echap` OUVRE le menu pause
au lieu de le fermer -- une commande sur deux se perdait. Desormais
`pauseOnLostFocus:false` dans `run/options.txt`, et le chat tape `t`,
`Ctrl+A`, la commande : plus d'Echap nulle part.

## 48. L'Aurore, revue sur mesure *(6 sept. 2026)*

« J'ai mine pendant deux minutes en vertical, presque aucune fissure, et j'ai
fini par decouvrir DE L'ARCENCIUM alors que je n'avais toujours pas de
diamant. » Mesure dans le journal de la session (9 h 58) et dans les reglages,
avant de toucher a quoi que ce soit :

- **L'Arcencium etait douze fois plus abondant que le diamant** : neuf veines
  de huit par chunk entre -48 et 64 (pic a y = 8), contre 7 + 2 + 4 tentatives
  de diamant de taille 4 a 8, la moitie jetees a l'air libre, entre -144 et 16
  (pic a -64). A y = 16-20 on tombe sur l'Arcencium avant le diamant,
  mathematiquement.
- **La boussole menait a une porte fermee** : le filon le plus proche, toutes
  sortes confondues -- donc presque toujours de l'Arcencium, inminable sans
  diamant.
- **« 0 puits et 0 brume »** : l'Aurore a ete annoncee au joueur en surface
  (y = 99) et le sondage des grottes se faisait autour de SA hauteur (plafonne a
  47) : une plage vide.
- Les Percees n'etaient pas en cause : sous y = 48 seulement, une sur 45, vingt
  secondes de repit ; parti de y = 99 il avait casse ~30 blocs eligibles, soit
  0,7 percee attendue. Elles restent telles quelles (le joueur n'a pas retenu
  cette proposition-la).

Quatre changements, acceptes sur proposition :

1. **L'Arcencium, plus rare et plus profond que le diamant**
   (`ModWorldGenProvider`) : deux veines de cinq par chunk entre -64 et 0, la
   moitie de celles exposees a l'air jetees, comme le diamant. On rencontre le
   diamant d'abord, l'Arcencium ensuite, et l'Aurore le double. Ce qui manque
   au compte vient des Poches, des Percees, des Echos et des coffres.
2. **Les Eclats d'Arcencium** (`mine/ArcenciumShards`, objet
   `arcencium_shard`, recette 4 eclats = 1 brut) : le filon reste un bloc a
   pioche de diamant pour le brut, l'experience et la Fortune ; mais une pioche
   d'un cran en dessous n'en repart plus les mains vides : un ou deux eclats
   (plus un ou deux pendant l'Aurore), et un mot une fois sur la barre
   d'action. Le fer est le chemin lent, le diamant le chemin plein ; aucun n'est
   un mur. La texture est decoupee dans celle du brut (l'image du joueur), rien
   d'invente.
3. **La boussole sait ce qu'on peut miner** (`WeatherEffects.prioritise`,
   `hasArcenciumPick`) : sans pioche capable de tirer le brut quelque part sur
   soi, le diamant passe devant (a rang egal, le plus proche) ; avec, l'
   Arcencium d'abord. Le message compte ce qu'on montre : « 6 filon(s) : 6 de
   diamant, 0 d'Arcencium -- le plus proche a 70 blocs », suivi de « Sans pioche
   en diamant sur vous, l'Aurore mene au diamant d'abord : c'est lui, la clef »
   ou de « Pioche en diamant sur vous : l'Aurore mene a l'Arcencium d'abord ».
   Le sondage descend desormais du plafond d'Underground jusqu'a la roche-mere
   (l'Arcencium vit sous zero, et l'appel se recoit en surface), et il est
   ETALE : trois chunks par tique, les plus proches d'abord, 81 chunks en 26
   tiques, un resultat garde dix secondes ou seize blocs de marche. D'un coup,
   il coutait 100 a 170 ms.
4. **Les Puits et les Brumes se levent SOUS le joueur** (`AuroreCaves`) :
   sondage fixe de y = 47 a -60 sous sa position, quelle que soit sa hauteur.
   Et « qui descend trouve » : un joueur autour de qui rien ne s'est leve
   (en surface sans grotte dessous, ou parti miner ailleurs) recoit ses puits
   et ses brumes en passant sous terre, une fois, jamais dans la derniere
   minute (`descents`, reessai toutes les dix secondes).

Verifie en jeu, en quatre passages : la pioche en fer casse le filon et laisse
UN eclat en poche, pas de brut ; la pioche en diamant laisse le brut, pas
d'eclat ; l'Aurore lancee avec le joueur a y = 75 leve « 2 puits et 1 paire »
a y = -1 et -26 sous lui ; la boussole affiche « Diamant 18m ▼46 » sans pioche
et « Arcencium 18m ▼31 » avec ; le joueur envoye dans le Nether pendant le
debut (« 0 puits ») recoit « a la descente de Dev, 2 puits et 1 paire » huit
secondes apres son retour sous terre ; le sondage fait « 81 chunks en 26
tiques ».

Pieges d'essai, cinquieme serie : `runData` plante dans le GatherDataEvent de
`lootr` (il cherche `../logo.png`) avant d'ecrire quoi que ce soit -- les deux
JSON generes de l'Arcencium ont ete ecrits a la main, identiques a ce que le
provider produirait, et le provider reste la source. Une vraie casse de bloc se
fait a la souris (`mouse_event` bouton gauche tenu, P/Invoke) : SendKeys ne
tient pas un clic. Et `execute if items entity @s inventory.*` ne regarde PAS
la barre d'outils : c'est `hotbar.*` qui recoit ce qu'on ramasse.

## 49. La Premiere Forge, et l'atelier qu'on voit *(6 sept. 2026)*

`game/FirstForge`, appele a la victoire du prologue (`GameManager.openTheGame`).

Le prologue enseignait a se battre et rien d'autre. On en sortait avec trois
sanctuaires a prendre et aucune raison de toucher aux trois etablis -- que le
joueur, faute d'avoir essaye une fois, ne cherchait meme plus : « j'ai du mal a
les trouver dans le village ». Chaque defenseur repart donc avec DE QUOI FAIRE
LES TROIS GESTES DU MODE, une fois chacun :

- **huit lingots de fer** -- le cout de deux ameliorations au premier cran, lu
  dans `Upgrade.cost(1)` et non recopie : une piece defensive, puis l'arme ;
- **trois plumes d'Arcencium** -- `Specialization.COST[1]`, le premier palier
  d'ailes, garanti (cent pour cent de reussite a ce cran) ;
- **trois lignes** qui disent a quoi sert chaque etabli, et ou il est.

On donne la MATIERE, jamais le resultat : le geste reste a faire, et c'est lui
qu'on veut enseigner. Une piece deja amelioree n'aurait rien appris.

**L'atelier signale.** Les trois etablis sont detoures d'or pendant dix
minutes, et le message donne la distance, la direction et les coordonnees.

Deux corrections en chemin, l'une et l'autre trouvees par la capture :

1. **Le jalon marqueur ne se voyait pas.** `Jalons.place` pose un porte-armure
   MARQUEUR : sans corps, son contour tient en un pixel. Sur un filon, le bloc
   colore fait le travail ; sur un etabli, on ne voyait rien (premiere capture a
   l'appui). `Jalons.glow` pose desormais un `block_display` qui porte la forme
   du bloc, agrandi d'un centieme, avec `glow_color_override` pour la couleur :
   le contour exact du bloc, a travers les murs, sans collision.
2. **L'atelier ne se souvenait pas de lui-meme.** On cherchait les trois blocs
   autour de la Lame, et dans un monde d'essai remis en place plusieurs fois la
   recherche tombait sur les etablis d'un atelier precedent (six blocs plus bas).
   `Workshop.place` ecrit desormais son centre dans `GameState` (`Workshop` en
   NBT), et la recherche part de la ; le balayage autour du village reste la
   porte de secours des parties commencees avant ce changement.

Verifie en jeu : huit lingots et trois plumes comptes dans le sac, trois
contours poses, et les etablis trouves EXACTEMENT la ou l'atelier venait d'etre
pose (-659, 279, 269 / 271 / 273 pour un centre en -659, 279, 271). Les deux
captures montrent les trois contours dores a quatorze blocs a decouvert, puis a
travers un mur de pierre dresse entre le joueur et l'atelier.

Voir aussi `RUNES.md`, ecrit le meme jour : le releve complet des options de
runes, leurs fourchettes par grade et ce qu'elles font vraiment.

## 50. Les runes, revues avec le joueur *(6 sept. 2026)*

Le releve `RUNES.md` a ete relu par le joueur ; ce qu'il en a dit, et ce qui
en a ete fait :

- **« Une rune peut avoir de 1 a 10 options : CC/BB/AAA/SSS. »** Mon schema
  s'arretait a six ; il etait faux. `RuneMark.PATTERN` monte desormais vers ce
  plein : C, CC, CCB, CCBB, CCBBA, CCBBAAA, CCBBAAASS, CCBBAAASSS. Mesure sur
  133 runes tirees en jeu : rang 1 = 1 option, rang 8 = 8 a 10, jamais plus de
  deux C, deux B, trois A, trois S, jamais un doublon.
- **Renommages** (langues seules, les identifiants ne bougent pas) : Chance
  devient **Precision** ; les « SL » deviennent **PC** (PC Attaque, PC Element,
  PC Defense, PC PV/PM, PC Generale) ; Sauvegarde devient **Bastion**.
- **Cerne retiree** (« bizarre sur une arme ») : elle reste dans l'enum pour
  que les runes deja tirees se relisent, tous ses maxima a zero, et
  `Rune.of` ne la tire plus. **Execution** la remplace : degats majores sur
  une cible sous 30 % de vie -- le miroir de l'Acharnement, qui joue sur SA
  propre vie.
- **Trois options d'armure ajoutees**, C a A, en points fixes : **Garde**
  (melee), **Pavois** (distance, `IS_PROJECTILE`), **Sceau** (magie,
  `neoforge:is_magic`). Et **Riposte**, S, qui renvoie une part des degats :
  il fallait une troisieme option S d'armure pour remplir « SSS ».
- **Corrections acceptees** : Aubaine affiche sa vraie chance (41-60 %) ;
  Percee ignore reellement une part de l'armure (`CombatRules` avant/apres) ;
  Egide progresse de C a B (3,0 / 3,8 / 4,7) ; Bastion reduit TOUS les degats
  subis, comme son texte le dit. Le tout dans `RuneEvents.onIncoming`, dans
  l'ordre esquive, fixes, pour cent, armure.
- **Raretes** : famille B, « la lumiere » -- Limpide, Diaphane, Splendide,
  Magnifique, Solaire, Celeste, Feerique, Prismatique. Huit mots identiques au
  masculin et au feminin : le mot precede le nom de l'objet, et « Excellente
  Glaive » ne pouvait pas s'accorder.

**L'element, en sursis.** Le joueur songe a retirer le systeme entier :
resistances a attribuer a chaque monstre, calculs de degats, pierres de
changement, bonus d'ailes specifiques. Mesure de l'empreinte : huit classes
dans `element/` (969 lignes), la voie Element de la fiche, les pierres
d'element (`ELEMENT_STONE`), deux ailes (Braise, Eau) et le tooltip d'arme.
Rien n'est retire tant qu'il n'a pas tranche ; le plan est dans la reponse du
jour.

**Les monstres suivent-ils la courbe ?** Oui, et c'est mesure dans `MobGear` :
cinq echelons du cuir a la netherite, amelioration jusqu'a +3 pour l'armure et
+6 pour l'arme, rarete jusqu'au rang 4 et 6, tires sur le STADE de la partie --
le plus avance du temps ecoule, des ancres tenues et du palier du siege.

**La commande qui fait apparaitre des runes.** Elle existait deja sous son nom
anglais ; elle parle desormais francais et dit ce qu'elle a donne :

    /arcencium rune arme|armure <rarete 1-8> [combien 1-64]
    /arcencium rune weapon|armor <rarete 1-8> [combien 1-64]   (les deux marchent)

La rarete est celle de la piece : 1 = Limpide, 8 = Prismatique. Le message
rappelle la rareté et le schema de cases (« 5 rune(s) -- Rune d'arme de rarete
Prismatique (cases CCBBAAASSS) »), et chaque tirage part au journal, option par
option, ce qui rend le banc d'essai possible.

Verifie en jeu : cinq runes d'arme de rang 8 dans la barre, douze runes
d'armure de rang 3 reparties en neuf plus trois, le nom anglais toujours
accepte. Au-dela de trente-six, le sac est plein : `Inventory.add` rend faux et
JETAIT la pile en silence -- les runes en trop tombent maintenant aux pieds.
Mesure : 9 dans la barre, 27 dans le sac, et des objets au sol.

Cinquieme piege d'essai : ne jamais taper `+=` dans une commande envoyee par
SendKeys -- le plus y signifie MAJ, et la commande part tronquee. On compte les
deux zones separement. Et un objet lache par un joueur qui VOLE sort du rayon
en deux secondes : le compter avec `distance=..8` ne trouve rien.

## 51. L'etabli qui avalait les runes *(6 sept. 2026)*

Rapport du joueur : « une epee en fer de rarete 4, une rune de rarete 2 : ca ne
m'a pas dit que ca n'a pas marche, la rune a disparu et je ne la voyais pas sur
l'arme. »

**La cause, lue dans `SocketBenchMenu.socket`** : l'apercu d'amelioration
passait AVANT la gravure, et il se declenchait des que le joueur avait dans le
sac le metal du cran suivant -- quoi qu'il y ait dans l'autre case. La piece
sortait telle quelle, la rune posee etait consommee a la prise, et rien n'etait
grave. La Premiere Forge, qui donne justement huit lingots de fer, rendait le
bogue certain pour tout le monde.

Trois corrections :

1. **La rune d'abord**, et l'apercu d'amelioration seulement quand c'est une
   Pierre de forge qui est posee.
2. **Un refus se dit.** `Runes.refuse` rendait deja la raison, l'etabli ne la
   montrait pas. Barre d'action, en rouge : « Cette rune ne se grave pas sur
   cette piece : une rune d'arme va sur une arme ou un casque... » ou « Rune
   Solaire : trop haute pour une piece Magnifique. Montez d'abord la rarete de
   la piece. » Et le journal le note.
3. **Fermer l'etabli rend ce qui est pose**, meme ouvert par commande : l'acces
   NULL rendait `access.execute` muet, et ce qui etait pose disparaissait.

`/arcencium etabli` ouvre l'ecran sans le bloc ; `/arcencium etabli essai` joue
l'etabli reel (piece en main, objet en main gauche) et ecrit au journal ce qui
en sort : c'est le banc d'essai.

Verifie en jeu, dans les conditions du rapport (epee en fer de rarete 4, huit
lingots dans le sac) : la rune d'arme de rang 2 est gravee, les huit lingots
intacts, la rune consommee, « Rune gravee : Rune d'arme Diaphane » ; une rune
d'armure est refusee (`family`) et rendue au sac ; une rune d'arme de rang 6 est
refusee (`rank`) et rendue.

Deux pannes d'environnement sur le chemin, notees pour la prochaine fois :
`build/moddev/artifacts/neoforge-21.1.193.jar` corrompu (« invalid distance
too far back » a l'analyse des mods) -- on le supprime, Gradle le refait ; et
Distant Horizons qui ne trouve plus `sqlScripts/scriptList.txt` dans son propre
jar une fois sur trois au demarrage -- le jar est sain, on relance.

## 52. L'amelioration qui se voit *(6 sept. 2026)*

« Entre le +1 et le +7, je ne les remarque vraiment pas beaucoup. Entre le +8
et le +10, j'aime bien, mais ca ne se voit pas assez, surtout sur notre epee. »
Trois propositions acceptees, sur toutes les armes, et calibrees par captures
(le harnais `shoot_auras.sh` : neuf armes en barre, une capture par cran a la
troisieme puis a la premiere personne, de nuit).

**Mesure de depart** : le +1 etait un contour blanc a 40 % d'intensite,
dessine a 1,07 fois l'arme avec 0,42 d'alpha -- un voile a peine plus fort
qu'un enchantement ; la couche large du +8 etait a 0,09 d'alpha, invisible par
construction. Sur la capture « avant », +1 et +4 ne se distinguent pas d'une
arme nue.

1. **Le halo recalibre** (`UpgradeGlow`, `UpgradeHaloRenderer`) : 75 % des le
   +1, 100 % au +4 ; contour a 1,12 et 0,45 d'alpha, seconde couche a 1,30
   des le +5, couche large a 1,50 et 0,18 au +8. Un premier essai a 0,80
   d'alpha blanchissait la lame entiere et tuait tout le reste : la mesure a
   ramene a 0,45.
2. **La jauge sur la lame** (`Notched`). J'avais promis des encoches, une par
   cran, qu'on compte : la mesure les a refusees -- une epee tient sur seize
   pixels, sa lame sur onze, sept traits n'y tiennent pas et l'on ne voyait
   qu'une lame blanchie. A la place, la lame se REMPLIT DEPUIS LA POINTE, un
   septieme par cran, d'un bleu froid de +1 a +4 (blanche, la jauge se
   confondait avec le fer), puis de la couleur du palier, avec un trait sombre
   a la frontiere. Au +8 la lame entiere prend la couleur -- or, turquoise,
   puis la teinte tournante du prismatique -- et une lumiere blanche la balaie
   de la garde a la pointe. Meme technique que la vague des raretes : chaque
   face du modele est teintee d'apres sa hauteur, ce qui vaut pour toutes les
   armes sans une texture de plus.
3. **Les etincelles**, posees PAR LE RENDU. Le serveur ne sait pas ou est la
   main (§ UpgradeAuraEvents) ; le rendu tient la matrice de l'objet. A la
   premiere personne, un point pris au hasard le long de la lame passe par la
   pose puis par la rotation de la camera (mesure a lacet 90 : la rotation
   DIRECTE pose le point devant et a droite, l'inverse derriere). A la
   troisieme personne la pose de la couche est reecrite par les mods
   d'animation et par le rendu du corps en vue subjective -- le point retombait
   a trois ou quatre metres quelle que soit la rotation -- on prend alors la
   main dans le repere du corps (`yBodyRot`). 0,12 etincelle par cran et par
   tique, la couleur du palier ; au +8, six de plus le long de la lame a chaque
   coup : la trainee.

Captures « apres », premiere personne : +1 pointe bleue et contour, +4 lame
bleue aux quatre septiemes, +7 violette entiere, +8 or avec ses etincelles,
+10 blanc tournant ; le coup au +10 laisse un nuage d'etincelles de la couleur
de l'instant, sur l'epee de fer comme sur l'epee d'emeraude. Troisieme
personne : les trois auras se lisent a quatre metres.

## 53. La boussole de l'Aurore, qui vise enfin *(6 sept. 2026)*

« Une fois arrive a l'etage du diamant, ca ne me disait pas dans quelle
direction il se trouve. J'ai mine dans toutes les directions et je ne l'ai pas
trouve. » Capture a l'appui : le panneau annoncait « Diamant 23m » avec une
fleche vers la gauche. Deux causes, mesurees toutes les deux.

**1. La fleche etait trop grossiere.** Huit secteurs, donc quarante-cinq
degres par case : au bout d'un tunnel de vingt-trois blocs, on pouvait passer
a NEUF BLOCS ET DEMI a cote du filon. Elle passe a seize secteurs (quatre et
demi), avec deux nouveautes : « « » et « » » disent de quel cote corriger
quand on est entre deux fleches, et une CIBLE ◎ s'affiche sous six degres --
le signal qu'on peut creuser tout droit, a deux blocs pres.

**2. « Les silhouettes brillent a travers la roche » etait faux.** Le jalon
etait un porte-armure marqueur : sans corps, son contour tient en un pixel
(meme defaut que les etablis de l'atelier, §51). Corrige d'abord en
`block_display` -- et la mesure a montre que cela ne suffisait pas : UNE LUEUR
D'ENTITE NE SE DESSINE QUE SI L'ENTITE EST RENDUE, et Sodium ne rend pas ce qui
se trouve dans une section de terrain masquee. Trois captures au meme endroit :
a vingt-trois blocs dans un massif plein, un fantome a peine visible ; a quatre
blocs dans la pierre, RIEN ; les deux memes blocs otes, le contour cyan eclate.
Un filon enterre est precisement le cas ou la lueur ne marche pas.

**Le repere a l'ecran** (`client/VeinMarkerClient`) : on projette soi-meme la
position du filon dans l'interface, apres tout le rendu du monde. Ni la roche,
ni Sodium, ni les shaders d'Iris n'ont leur mot a dire -- c'est le seul canal
dont on soit maitre de bout en bout. Un losange creux, de la couleur de la
sorte, a la place EXACTE du filon ; trois au plus, comme le panneau ; rien
quand le filon est hors champ, la fleche s'en charge (coller des losanges aux
bords encombrait l'ecran de six marques immobiles, capture a l'appui).

Verifie en jeu, un diamant plante a vingt-trois blocs plein est dans un massif
de pierre : plein est, le losange est au centre et le panneau affiche ◎ ; a
trente degres, le losange glisse et la fleche devient « ‹ » ; dos au filon,
plus de losange et la fleche pointe en bas ; a quatre blocs dans la pierre, le
losange tient toujours. Le journal confirme le jalon serveur sur le bon bloc
(-377, 20, -400).

Les jalons `block_display` restent : ils servent quand le filon donne sur une
grotte ouverte. Ils ne sont plus reposes que si la liste change -- les recreer
toutes les deux secondes faisait clignoter la lueur -- et le message d'annonce
ne promet plus ce qu'ils ne tiennent pas.

## 54. Les grottes qui suivent, la Chambre d'Aurore, le Rappel *(6 sept. 2026)*

« Je tombe tres rarement sur les portails etoiles, et je ne suis pas tombe
non plus sur les colonnes. » Mesure dans le journal du joueur : son Aurore
avait bien leve 3 puits et 1 paire -- entre 80 et 115 blocs SOUS la surface
ou il se tenait, une seule fois, et la brume la plus proche de sa galerie
n'est jamais montee sur le panneau (quatrieme ligne d'un panneau qui en
montre trois). Trois causes : trop peu, leves une fois autour du point de
depart, invisibles.

**A. Plus, et qui suivent** (`AuroreCaves`). 4 a 6 puits et 2 a 3 paires par
levee ; une grotte de 4 blocs d'air suffit (6 avant). `servedAt` retient OU
la levee a eu lieu : a 48 blocs de la, une nouvelle levee. Et le panneau
reserve leurs lignes a la brume et au puits les plus proches, apres les
trois filons (`VeinHudClient.order`) ; le paquet passe a trois bits par
entree (`VeinSyncPayload.put`), avec le genre Puits.

**B. La Chambre d'Aurore** (`mine/AuroreChamber`), l'idee du joueur. En
cassant de la roche sous y = 48 pendant l'Aurore, une chance sur 40, trente
secondes de repit : une chambre de 5 x 4 x 5 s'ouvre devant soi, bloc par bloc
(la mecanique des Percees), et une brume se leve au centre. Sa jumelle se
leve dans l'ordre : contre le filon que la boussole designe (une poche de 3 x
3 x 3 creusee contre le minerai, qui reste en place), sinon dans la plus grande
grotte du sondage, sinon au jour. Le message dit ou elle mene.
`WeatherEffects.guidedVein` retient le filon designe a chaque joueur ;
`AuroreCaves.placePair` pose une paire a la demande. `/arcencium chambre`
l'ouvre sans casser quarante pierres.

**C. Le Rappel** (`AuroreCaves.placeRecall`, bloc `recall_mist`, la brume
teinte or). A la fin de l'Aurore, chaque joueur sous terre voit une brume de
rappel se lever a ses pieds, 45 s. Y entrer, c'est le voyage a travers la
roche, droit vers le premier bloc a ciel ouvert au-dessus (`Heightmap
MOTION_BLOCKING_NO_LEAVES`). Un seul sens ; le depart reste possible apres
la fin de l'Aurore tant qu'une brume de rappel existe.

Verifie en jeu : premiere levee « 4 puits et 2 paires » ; la chambre ouverte
en (-396, 20, -400) avec sa brume au centre, la jumelle en (-379, 19, -400)
contre le diamant plante en (-377, 20, -400) qui reste intact, « ... contre un
filon de Diamant, a 17 blocs » ; entre dans la brume, le joueur arrive dans la
poche ; la brume de rappel posee a ses pieds, et le joueur remonte au jour ;
une seconde Aurore, puis 60 blocs plus loin sous terre : « a la descente de
Dev, 4 puits et 3 paires ». Le panneau montre les lignes Brume et Puits.

## 55. L'audit : quarante-cinq agents, et ce qu'ils ont trouve *(9 sept. 2026)*

Le joueur a rendu une liste apres son essai : l'Aurore trop courte et jamais
premiere, l'Heure Doree sans moyen de rentrer, la rarete introuvable apres le
village, les lags qui « bloquent totalement le jeu », et « gros bug : il n'y a
plus aucun coffre dans les tours des sanctuaires ». Plutot que de traiter les
points un par un, huit enqueteurs ont fouille le mode en parallele, chaque
trouvaille a ete confiee a un contradicteur charge de la refuter, et la
synthese n'a garde que ce qui a survecu. Plusieurs affirmations du dossier
sont mortes la : l'equipement des monstres, le rythme des phases et le
perimetre de minage ont ete laisses intacts, faute de preuve.

### A. Les coffres : la cause, et pourquoi personne ne l'avait vue

`chestY = y + base + storey + 1` visait le plancher de l'etage AU-DESSUS.
Le coffre de l'etage 2 se posait exactement la ou passe la vis de l'etage 3 --
elle l'ecrasait -- et le dernier tombait un bloc au-dessus du parapet, sur la
terrasse. Quatre coffres sur vingt disparaissaient a chaque sanctuaire.

Le compte rendu ne pouvait pas le dire : il comptait les POSES. Il relit
maintenant chaque emplacement et annonce combien ont survecu. Les quatre
coffres d'une tour tournent aussi sur les quatre coins de leur salle
(`corner`) au lieu de s'empiler dans la meme colonne.

Et les huit TOURELLES DE PORTE paient a leur tour, un coffre chacune, au
rez-de-chaussee. Elles n'avaient rien : « elles gardent, elles ne paient pas ».
Le joueur les voulait servies, et il a raison -- on traverse ces tours a chaque
entree sans jamais les fouiller. Mais pas au regime des tours d'angle : trois
coffres chacune auraient porte le sanctuaire de vingt a quarante-quatre, et
c'eut ete un autre jeu. Vingt-huit, donc, et la fouille d'une porte vaut le
detour sans valoir une tour.

### B. L'etabli : quatre defauts, tous verifies au journal

- **La duplication.** Un clic-maj sur la case de resultat rendait la piece ET
  la gardait, sans limite. `quickMoveStack` s'arrete desormais sur cette case.
- **Le double de.** `onTake` s'executait aussi chez le client, avec une autre
  graine : « Excellent » a 09:50:23.238, « Legendaire » douze millisecondes
  plus tard, pour une seule prise. Une garde de cote, et le de est unique.
- **L'imprimante a Eclats.** L'Heure Doree remboursait l'Eclat a CHAQUE prise :
  cinq minutes de fenetre suffisaient a monter au Phenomenal. Une tentative
  offerte par fenetre, retenue par joueur (`goldenUsed`).
- **La pile detruite.** Une pile entiere de Pierres de Forge partait par
  tentative ; on n'en consomme plus qu'une, et la mise en Eclats est plafonnee
  a seize par prise.

### C. Le vrai probleme de l'amelioration n'etait pas au village

`RuneDrops` posait la porte des runes AVANT le cristal elementaire et la
Pierre de Forge : rien ne tombait sans rune. Un zombie laisse une rune une
fois sur dix, donc une Pierre une fois sur cinquante. Une tentative coute une
Pierre, une partie en demande une quarantaine : il aurait fallu deux mille
morts en quatre-vingt-dix minutes. Les deux boucles passent devant la porte.
Les taux ecrits (20 % et 22 %) ne bougent pas -- ils s'appliquent enfin.

Et la defense du village donne desormais six Eclats du Destin, ce qui n'est
pas un chiffre rond : c'est `PITY_PER_DRAW`, donc six essais d'un coup. Le
message annonce la chance reelle d'atteindre Splendide. La pitie, elle, se
CONSOMME maintenant : sans cela, deposer cent trente Eclats un par un donnait
onze fois plus de jets que la meme depense en une prise.

### D. Les lags : trois secondes et demie, dans une seule etape

Mesure : 542 a 7 372 ms de fil serveur par sanctuaire, pire etape 3 474 ms.
La cause n'etait pas le nombre de blocs mais la GENERATION du terrain -- un
sanctuaire se dresse a quatre cent cinquante blocs du village, sur du sol que
personne n'a jamais charge, et la premiere bande de deblaiement en reclamait
quatorze chunks d'un coup. Le budget de douze millisecondes par tique n'y
pouvait rien : il compte apres l'etape, et une etape ne se coupe pas.

Quatre corrections :

1. **Le prechargement par paliers.** Demander un chunk fini en entraine une
   vingtaine derriere lui. On monte donc toute la zone d'un etage a la fois
   (structures, biomes, relief, decors, fini) : la cascade est bornee a un
   etage.
2. **Le deblaiement a une colonne** par etape au lieu de deux, et **le
   rhabillage en bandes de huit** au lieu d'un seul bloc de 211 x 211.
3. **La sonde du sommet** partait du plafond du monde et redescendait a vide :
   deux millions de lectures d'air par chantier. Elle part six blocs au-dessus
   du parapet.
4. **Le balayage des jalons** parcourait toutes les entites du monde toutes
   les dix tiques, du debut a la fin de la partie, pour des reperes qui vivent
   quelques minutes. Un compteur, et il ne tourne plus a vide.

Il restait le pire, et le banc d'essai l'a trouve tout seul.

### D bis. Le sanctuaire qui se rebatissait a chaque connexion

Le journal du monde d'essai, a chaque ouverture, sans exception :

    Partie reprise : 3 sanctuaire(s) a rebatir
    Pyramide de Cataclysm non posee a y=322 : repli sur la notre
    Ancre non posee en BlockPos{x=-670, y=324, z=716} : minecraft:void_air
    Sanctuaire palier 1 : 20 coffres poses, 0 survivants

Trois sanctuaires refaits a CHAQUE lancement -- huit, quatre et quatre
secondes de fil serveur, plus le pic de generation -- et refaits pour rien,
puisqu'ils se dressaient au-dessus du plafond du monde, ou le jeu ne rend que
du vide : ni ancre, ni coffre.

La mecanique. La reprise decidait « ce site est inacheve » en regardant si le
bloc d'ancre est la. Or l'ancre coiffe le FAITE de la pyramide, et la position
retenue devient celle du faite. Que la pose echoue une fois -- ou qu'un joueur
casse l'ancre, ou simplement qu'il l'active -- et l'ouverture suivante rebatit
un sanctuaire entier en prenant cet ancien faite pour sol, quarante blocs plus
haut. Puis la suivante, quarante blocs plus haut encore. Le monde d'essai en
etait a 322, 335 et 342.

`GameState` retient donc ce qu'il a BATI (`markBuilt`, sauvegarde sous
`Built`) au lieu de le deduire du terrain, et la reprise ne remet en file que
les sites qui n'ont jamais porte leur monument. Un garde-fou refuse en plus de
batir au-dela du plafond du monde. C'est probablement la moitie des « lags qui
reviennent souvent » : ils revenaient a chaque connexion.

Verifie en deux ouvertures d'affilee, avec une fermeture PROPRE entre les deux :

    [ouverture 1] Partie reprise : 3 sanctuaire(s) a rebatir
                  Sanctuaire demande a y=324, ramene a 270 : le plafond est a 320
                  Sanctuaire retenu comme bati : demande y=324, dresse y=312
                  (trois fois, 28 coffres poses et 28 survivants chacun)
    [ouverture 2] « a rebatir » : 0     sanctuaires batis : 0

Le journal de bord du monde porte desormais les six positions, le sol vise et
le faite obtenu. Il a fallu trois essais pour le mesurer : le banc TUAIT le
client, si bien que le monde ne se sauvegardait jamais et que la deuxieme
ouverture relisait un etat vieux d'une heure. On envoie maintenant la fermeture
de fenetre, celle du joueur, et l'on attend que le processus rende la main.

### E. L'Aurore premiere, et cinq minutes

`firstDrawn` force l'Aurore au premier tirage : c'est la seule meteo qui
APPREND quelque chose, et elle donne le diamant sans lequel rien ne
s'ameliore. Elle passe de deux-quatre minutes a cinq fixes -- de la surface a
y = 12 il y a quatre-vingts blocs, soit une minute et demie a la pioche de fer
sous Hate II, avant meme de chercher un filon. L'ecart avant la premiere
meteo est fixe a soixante secondes : sur un serveur neuf il valait dix
secondes, pendant que les sanctuaires se dressaient encore.

La Hate II et la Vitesse s'affichent enfin comme effets : quarante pour cent
de vitesse de minage, et rien a l'ecran ne le nommait.

### F. L'Heure Doree : les Portes, et l'horloge retenue

La fenetre recompense de s'asseoir a l'atelier, et l'atelier est au village --
que le joueur avait quitte. `weather/GoldenGate` ouvre donc une Porte doree a
neuf a quinze blocs de chaque joueur et une seconde a l'atelier ; y entrer
tient du pas de cote, et la porte du village renvoie chacun a la sienne. La
boussole les designe (genre 5 du paquet). A la fin de la fenetre elles tiennent
QUARANTE-CINQ SECONDES de plus, avec le compte a rebours en barre d'action.

L'horloge, elle, etait posee une fois a 11 800 et laissait courir le temps :
sur cinq minutes on finissait a 17 800, soit soixante-dix secondes de lumiere
doree puis trois minutes et demie de nuit noire. Elle avance six fois moins
vite, de 11 800 a 12 800. La Battue recoit la meme retenue, de 23 000 a
23 800 : son aube de chasse disparaissait au bout de cinquante secondes.

### G. La Battue : le tambour bat pendant la poursuite

Le tambour ne sonnait que pour les hostiles qui ne vous avaient pas vu :
pendant la poursuite -- le coeur de la Battue -- il se taisait. La Proie y
entre, avec un rayon de vingt-quatre blocs puisqu'elle se stabilise a sept
blocs devant. Et son nom s'affiche : `Visibility.NEVER` valait pour les trois
equipes, si bien que « Seize-Cors » etait nomme et invisible.

### H. Distant Horizons : les trous, pas la portee

« Parfois les choses tres loin sont mal chargees et on voit l'interieur. » La
portee avait ete reglee en septembre (section 38) ; ce sont deux autres cles.
`upsampleLowerDetailLodsToFillHoles` etait faux : au passage d'un niveau de
detail au suivant, le maillage grossier ne recouvre pas exactement le fin et
la difference reste vide -- on voit au travers. `overdrawPrevention` valait
zero : les LOD se dessinaient par-dessus les vrais chunks, deux surfaces au
meme endroit, celle qui gagne changeant avec l'angle. Les deux sont passees
dans `tools/dh_profile.py`, qui les pose dans les trois configurations.

Les fils de generation ne bougent PAS : les avoir brides en cours de route
traitait des lags qui venaient du chantier des sanctuaires, corrige a la
source ici meme.

### I. Ce qui a ete verifie, et comment

Trois passages du banc d'essai, journal purge avant chacun.

| ce qu'on mesure | avant | apres |
| --- | --- | --- |
| coffres survivants par sanctuaire | 16 sur 20 | 28 sur 28 |
| pire etape du chantier | 3 474 ms | 552 a 1 013 ms |
| sanctuaires rebatis a chaque connexion | 3 | 0 |
| horloge de l'Heure Doree sur 100 s | vers la nuit | 11 823 -> 12 176 |
| horloge de la Battue sur 45 s | vers le plein jour | 23 046 -> 23 365 |

La premiere meteo tiree a bien ete l'Aurore, avec « 4 puits d'Aurore, 2 paires
de brumes etoilees » et six filons de diamant montres. Une Porte doree s'est
ouverte a treize blocs, a annonce « les Portes tiennent encore 45 secondes » a
la fin de la fenetre, puis s'est dissipee.

Les trois sanctuaires du cycle suivant, sur du sol jamais visite et par le
chemin normal du jeu (la file d'etapes, pas la commande), ont rendu « 20
coffres poses, 20 survivants » chacun, avec 552 a 1 013 ms pour la pire etape ;
puis « 28 poses, 28 survivants » une fois les tourelles de porte servies.

Le banc des butins, enfin : cinquante zombies tues par le joueur, inventaire
vide au depart.

| ce qui tombe | attendu | ramasse |
| --- | --- | --- |
| Pierres de Forge | ~10 | 12 |
| cristaux elementaires | ~11 | 18 |
| runes | ~5 | 3 |
| plumes d'Arcencium | ~14 | 16 |

Avant la correction, la Pierre et le cristal etaient enfermes derriere les
trois jets de runes : au plus trois monstres sur cinquante pouvaient en
laisser.

Le banc lui-meme a coute trois essais, et les deux premiers etaient faux --
c'est la lecon utile. Une fonction de datapack ne renvoie PAS le resultat de
ses commandes a la conversation : `data get` et `scoreboard players get` n'y
ecrivent rien, il faut un `tellraw`. Et cinquante zombies convoques sur le
meme bloc s'ecrasent les uns les autres (`maxEntityCramming`, vingt-quatre) :
ils mouraient par ecrasement avant le coup du joueur, et l'evenement de butin
ne passait que deux fois sur cinquante. Une sonde posee dans `RuneDrops` l'a
dit en une ligne, la ou trois heures de raisonnement auraient tourne en rond.

## 56. Cinq minutes pour toutes les meteos *(9 sept. 2026)*

« Deux minutes c'est bien trop court. » Le joueur l'avait dit de l'Aurore ; il
le dit maintenant des autres, et la raison est la meme pour toutes. Une meteo
du mode n'est pas un decor qui passe, c'est une FENETRE : on la voit tomber, on
decide ce qu'on en fait, on s'y rend, on le fait. Les trois premieres etapes
mangeaient les deux minutes et il ne restait rien pour la quatrieme.

Toutes les meteos tirees durent donc cinq minutes, et FIXES : une duree tiree
entre deux et quatre minutes ne s'annonce pas et ne se planifie pas.

| meteo | avant | apres |
| --- | --- | --- |
| Aurore, Heure Doree | 5 min | inchange |
| Battue | 2 min | 5 min |
| Nuit | 2 min 30 a 4 min | 5 min |
| Meteores, Dechirure, Orage | 2 min a 3 min 20 | 5 min |
| Embellie | 1 min a 1 min 30 | inchange |

L'Embellie ne bouge pas : ce n'est pas une meteo qu'on tire, c'est l'accalmie
qui suit chaque tempete agressive, et cinq minutes de rien ne sont pas un
cadeau.

**Les horloges suivent.** Quatre meteos forcent l'heure du jour, et les
allonger sans les retenir aurait refait la faute de l'Heure Doree qui
finissait de nuit :

- la **Nuit** posait minuit et laissait courir : sur cinq minutes elle se
  terminait a 24 000, c'est-a-dire AU LEVER DU SOLEIL. Un huitieme de vitesse,
  minuit a 18 750, il fait noir du debut a la fin ;
- les **Meteores** posaient le crepuscule et finissaient a 19 200, en pleine
  nuit noire, alors que tout leur interet est le ciel bas et rouge. Un sixieme
  de vitesse, 13 200 a 14 200 : le jour tombe pendant qu'elles tombent ;
- la **Battue** avait deja sa retenue, calee sur deux minutes ; la pente passe
  du tiers au huitieme pour couvrir les cinq ;
- l'**Heure Doree** etait deja calee sur cinq minutes.

**Ce que cela change au rythme, et ce que cela ne change pas.** L'ecart entre
deux tirages n'a pas bouge : deux a quatre minutes en Exploration et en
Montee, une minute et demie a trois en Pression, vingt a quarante secondes
pendant l'Assaut. En Exploration, la meteo occupait donc deux minutes sur
cinq ; elle en occupe cinq sur huit. Et comme aucune des agressives ne monte
en puissance avec le temps -- leurs cadences sont plates -- cinq minutes
d'Orage font deux fois et demie la foudre de deux minutes, sans courbe. Si le
mode parait trop charge a l'essai, c'est l'ecart entre tirages qu'il faudra
allonger, pas la duree qu'il faudra reprendre.

## 57. Le sous-sol sous le village, le rappel qui attend, l'atelier elargi *(9 sept. 2026)*

### A. « J'ai mine dix minutes, il ne s'est rien passe »

Ni chambre, ni percee, ni poche, ni echo, sur deux Aurores entieres. Le
journal de partie du joueur a donne la reponse en une passe : il minait SOUS
LE VILLAGE.

Les deux zones de paix d'`Underground` etaient des CYLINDRES sans fond --
quarante-huit blocs autour du village, cent dix autour de chaque ancre, du
plafond du sous-sol jusqu'au fond du monde. Or ce qu'un joueur fait pendant
l'Aurore, c'est creuser vers le bas depuis la ou il se tient, et la ou il se
tient c'est le village, avec l'atelier et les trois etablis.

Sur treize endroits releves dans son journal, entre y = -8 et y = -37, ONZE
tombaient dans les quarante-huit blocs du village : de treize a quarante-quatre
blocs du centre.

| endroit mine | distance au village | avant | apres |
| --- | --- | --- | --- |
| (-312, 232) | 13 | bloque | libre |
| (-327, 202) | 23 | bloque | libre |
| (-343, 193) | 38 | bloque | libre |
| (-347, 183) | 48 | libre | libre |

La zone de paix a maintenant un fond : sous y = 24, plus aucune emprise de
surface ne protege quoi que ce soit. Le village est a y = 71 et le sous-sol
commence a 48 : il reste quarante-sept blocs de roche entre le plus haut
evenement possible et le plancher du village.

Et la regle se lit en jeu. `/arcencium what` ajoute une ligne :

    sous-sol a vos pieds (y=-20) : AUTORISE | village a 2 blocs | plafond y=48
    sous-sol a vos pieds (y=30)  : interdit | village a 2 blocs | plafond y=48

C'est exactement la question qu'on ne pouvait pas poser, et qui a coute deux
Aurores au joueur.

### B. Le Rappel etait une trappe, il devient une porte

« Quand la meteo se termine, ca me fait remonter directement. » La brume se
levait « a ses pieds » au sens propre : sur son bloc et sur celui de sa tete.
Le joueur etait donc DEDANS des la premiere tique, et le depart partait tout
seul. La detection de bord existait pourtant -- il faut ne pas toucher a la
tique precedente -- mais le bloc n'existait pas a la tique precedente.

La brume s'ouvre desormais A COTE, jamais sur lui, et le joueur est declare
« touchait deja » a la pose : meme colle a elle, rien ne part tant qu'il n'y
entre pas de lui-meme.

Et elle taille s'il le faut, ce que le joueur demandait. Trois passes : les
quatre voisins de plain-pied, puis les quatre diagonales, puis une alcove d'un
bloc sur deux ouverte dans la premiere paroi de ROCHE NATURELLE venue. Jamais
un bloc pose, jamais un minerai, jamais un coffre.

Verifie dans une poche d'un seul bloc, quatre parois de deepslate :

    BILAN RAPPEL y=-20 | a cote e1 o0 s0 n0 | SUR LUI=0

L'alcove a ete taillee a l'est, la brume s'y est posee, et le joueur est reste
a y = -20.

### C. L'atelier a une rangee d'en face

Le mode se joue dans un modpack, et le joueur passait son temps a chercher une
enclume. Reparer, retirer un enchantement, tailler une gemme : ce sont les
gestes qui ENTOURENT nos trois etablis, et les envoyer chercher ailleurs casse
la boucle qui ramene au village.

La dalle passe de 7 x 5 a 10 x 9, avec quatre lanternes au lieu de deux. En
face de nos trois stations, a quatre blocs, sept postes empruntes :

| poste | ce qu'il sert |
| --- | --- |
| enclume | reparer, renommer, combiner les enchantements |
| meule | retirer un enchantement, reparer sans livre |
| table de forge | monter au netherite |
| etabli | tout le reste, et le sertissage des gemmes d'Apotheosis |
| table de taille | tailler et fusionner les gemmes |
| table de recuperation | demonter une piece pour reprendre ce qu'elle porte |
| table de reforge simple | refaire les affixes d'une piece |

Les trois d'Apotheosis sont demandes au REGISTRE et poses seulement s'ils
existent : sans lui, la dalle a trois places vides et le mode tourne. Le
journal le dit alors, une ligne par poste absent.

Verifie en jeu : les sept postes repondent « POSTE OK » a leur position.

### D. Le pack partait de la mauvaise instance

`export_modpack.py` lisait « All the Mods 10 - CUSTOM ». Ce profil n'est pas le
notre : c'est une copie d'ATM10 dans laquelle le mode avait ete glisse a
l'epoque ou il n'etait qu'un mod, et son `minecraftinstance.json` porte
`installedModpack: All the Mods 10 - ATM10`. Le zip heritait de cette
filiation, avec la liste d'add-ons et les reglages d'un autre pack.

Il part maintenant de « Mode Arcencium » : aucun modpack de base, 438 add-ons
CurseForge, et la meme liste de mods a quatre fichiers pres -- trois jars
desactives qui trainaient dans l'autre profil, et AllTheTweaks, qu'on ne
voulait deja pas.

## 58. Le systeme s'ouvre a tout l'equipement *(9 sept. 2026)*

### A. Pourquoi une epee de PIERRE passait deja

Le joueur s'est etonne : « j'avais une epee en pierre avec les statistiques
d'Apotheosis, j'ai pu la runer ET l'ameliorer ». Les deux sont vrais et ne se
contredisent pas. Un affixe d'Apotheosis est une DONNEE posee sur l'objet, pas
un objet neuf : l'epee reste `minecraft:stone_sword`, donc vanilla, donc
admise par la regle d'alors -- « l'espace de noms minecraft, et rien d'autre ».

Ce que cette regle fermait, c'etait le reste du modpack. Quatre cent quarante
mods, et pas une seule de leurs armes ne pouvait entrer a l'etabli.

### B. Deux familles, deux plafonds, et c'est tout

`GearEligibility` ne demande plus « d'ou vient cet objet » mais « est-il des
notres » :

| | Forge | rarete | runes |
| --- | --- | --- | --- |
| l'equipement du mode | +10 | Prismatique | rang 7 |
| tout le reste, vanilla comme modde | +7 | Solaire | rang 5 |

Un seul plafond pour ce qui n'est pas de nous, et non un troisieme palier pour
les mods : on ne connait ni la force ni l'equilibre de quatre cents mods, et
pretendre les classer serait inventer. Le plafond garde intacte la seule chose
qui compte, l'equipement du mode reste ce qu'on cherche parce qu'il est le seul
a aller au bout.

Ce qui compte comme arme se lit aux ETIQUETTES communes -- `c:tools/melee_weapon`,
`c:tools/ranged_weapon`, `c:tools/bow`, `c:tools/crossbow`, `c:tools/spear`,
`c:tools/mace` -- que NeoForge remplit pour le jeu, que les mods serieux
remplissent pour eux, et que les scripts d'unification du modpack completent.
`SwordItem` sert de filet. Une armure se lit a `Equipable` et a son
emplacement, l'armure de cheval exclue. Une pile de plus d'un objet n'est
jamais un equipement : c'est le garde-fou contre une etiquette trop large.

Verifie en jeu, `/arcencium what` en main :

    twilightforest:ironwood_sword     | WEAPON  | plafond +7,  Solaire
    minecraft:netherite_sword         | WEAPON  | plafond +7,  Solaire
    minecraft:diamond_chestplate      | ARMOR   | plafond +7,  Solaire
    emeraldweapons:arcencium_glaive   | WEAPON  | plafond +10, Prismatique
    emeraldweapons:arcencium_chestplate | ARMOR | plafond +10, Prismatique
    minecraft:diamond_pickaxe         | refusee par les etablis
    minecraft:stick                   | refusee par les etablis

### C. Nos armes et les gemmes d'Apotheosis

Apotheosis range chaque objet dans une CATEGORIE, et c'est elle qui decide
quelles gemmes s'y sertissent. Lecture faite dans son code : `bow` teste
`BowItem`/`CrossbowItem` ; les quatre categories d'armure testent `Equipable`
et l'emplacement ; `melee_weapon` teste les MODIFICATEURS D'ATTRIBUT de
l'objet, c'est-a-dire s'il donne des degats d'attaque en main principale.

| notre piece | categorie | avant |
| --- | --- | --- |
| Epee d'emeraude, Lame du Serment | melee_weapon | deja bonne |
| Glaive d'Arcencium | melee_weapon | deja bonne |
| Arc d'Arcencium | bow | deja bonne |
| les quatre armures | helmet, chestplate, leggings, boots | deja bonnes |
| **Sceptre d'Arcencium** | **none** | **aucune gemme** |

Le Sceptre est declare `new Item.Properties().durability(900)` : il ne donne
aucun modificateur d'attaque, donc Apotheosis le rangeait dans `none` et
aucune gemme ne s'y posait. On ne touche pas a ses statistiques pour autant --
la Concorde n'est pas une arme de melee et n'a pas a le devenir. On passe par
le point d'extension prevu, la carte de donnees
`apotheosis:loot_category_overrides`, qui force sa categorie sans rien changer
d'autre. Les deux entrees d'origine d'Apotheosis y sont recopiees, pour que
notre fichier ne puisse pas les effacer s'il remplacait au lieu de fusionner.

Le mode expose aussi enfin ses etiquettes communes -- `c:tools/melee_weapon`,
`c:tools/ranged_weapon`, `c:tools/bow`, `c:armors` -- ce qu'il aurait du faire
depuis le debut : elles ne servent pas qu'a nous, elles rendent notre
equipement visible a tout mod du pack qui lit les etiquettes.

Verifie en jeu : les cinq etiquettes repondent presentes sur l'epee, le glaive,
le sceptre, l'arc et le plastron ; la carte de donnees se charge sans un
reproche.

## 59. Rendre les armes fabricables, et faire pousser le prisme *(9 sept. 2026)*

« Tout notre systeme repose sur le fait d'avoir des armes de notre mode.
Peut-etre qu'on devrait revoir les tables de craft. » Releve fait, et il y avait
de quoi.

### A. Ce que coutait l'equipement complet

| arme | Arcencium | emeraudes | prisme | autre |
| --- | --- | --- | --- | --- |
| Epee d'emeraude | 3 | 2 | 2 branches | **2 epees en netherite** |
| Glaive | 4 | 9 (un bloc) | 1 branche, 1 fibre | rien |
| Sceptre | 3 | 1 | 1 branche | rien |
| Arc | 3 | 2 | 1 branche | 1 arc |

Plus l'armure : 5, 7, 7 et 4 lingots. **Trente-sept lingots** pour l'ensemble,
quatorze emeraudes, et deux epees en netherite.

Trois murs, et le premier etait enorme. DEUX EPEES EN NETHERITE, c'est huit
debris antiques, deux modeles de forge et l'or qui va avec : hors de portee en
quatre-vingt-dix minutes, sur l'arme qui donne son nom au mode. Le BOIS DE
PRISME n'avait aucune generation dans le monde -- l'arbre ne poussait que d'un
plant -- donc les branches et les fibres ne venaient que du village d'Arcencium
ou des coffres de sanctuaire : sans passage par l'un des deux, aucune arme
fabricable, quel que soit le minerai ramasse. Et le MINERAI rendait un brut par
bloc, a deux filons de cinq par chunk sous y = 0.

### B. Les quatre allegements

1. **L'epee d'emeraude prend une epee de diamant**, plus deux de netherite.
2. **Le glaive prend trois emeraudes**, plus un bloc entier. Le total des
   quatre armes tombe de quatorze emeraudes a huit.
3. **Le minerai rend deux bruts.** Tout l'equipement coute moitie moins de
   pioche, sans qu'une seule recette bouge.
4. **L'armure suit une regle enfin tenue** : la silhouette du jeu, un lingot
   remplace par la fibre de prisme. Elle ne l'etait que pour le plastron ; le
   casque, les jambieres et les bottes coutaient le plein tarif PLUS une fibre.
   Vingt lingots au lieu de vingt-trois.

En pioche, l'ensemble passe de trente-sept blocs de minerai a dix-sept.

### C. L'Arbre de Prisme pousse, et il est beau

Le joueur voulait « un bel arbre, de differentes tailles, avec des formes
majestueuses ». Une seule silhouette repetee ne fait pas un bosquet, elle fait
un decor : il y en a donc trois.

| taille | charpente | ce qu'elle donne |
| --- | --- | --- |
| petit | tronc courbe, feuillage en nuage | le buisson qui remplit les bords |
| moyen | celle du grand chene : tronc qui se divise, houppier rond | six a treize blocs |
| grand | celle du cerisier : tronc court, branches horizontales, rideaux de feuilles | celui qu'on voit de loin |

Un tirage les melange -- un sur dix est grand, un tiers est moyen, le reste est
petit -- et le plant tire sa taille comme le monde : planter un prisme et voir
sortir un grand est la moitie du plaisir.

Le bosquet se pose un chunk sur six, sur le sol, la ou un plant de prisme
survivrait, dans les douze biomes qui portent deja le village d'Arcencium :
l'arbre appartient au meme paysage, et l'etendre a tout l'overworld en ferait
une banalite.

Et la Premiere Forge donne desormais quatre branches et deux fibres, de quoi
monter une arme et une piece d'armure : la porte ne reste jamais fermee.

Verifie en jeu : les trois tailles se posent, cinq, dix et cinq blocs de tronc
dans la colonne centrale, et un feuillage de 169 blocs pour le moyen contre 432
pour le grand.

### D. Le generateur de donnees remarche

Il plantait depuis des mois : la generation partait de `run/`, ou vivent les
quatre cent quarante mods du modpack, et Moonlight y appelle
`Minecraft.getInstance()` pendant `GatherDataEvent` -- il n'y a pas de client en
generation. On ecrivait donc les fichiers generes A LA MAIN, ce qui avait laisse
passer au moins une divergence : le minerai etait a pioche de diamant dans le
JSON livre et a pioche de fer dans le generateur. Une regeneration l'aurait
silencieusement rouvert au fer, et avec lui toute la mecanique des Eclats.

Une ligne dans `build.gradle` -- `gameDirectory = project.file('run-data')` --
donne a la generation son propre dossier, vide de mods. Elle remarche, et les
quatre cents fichiers generes portent enfin ce que le generateur dit vraiment.

## 60. Le plantage « charge_pad », et les lags qui ne venaient pas d'ou l'on croyait *(9 sept. 2026)*

« Il y a toujours beaucoup trop de lags ! Ca arrive en combat, en minant, apres
une mission. C'est injouable ! » -- et un plantage, « Exception ticking world :
Cannot set property charge_pad ... as it does not exist in Block{minecraft:air} ».

### A. Ce que le journal de la partie disait vraiment

Quatorze retards en dix-neuf minutes, de 2 a 19 secondes. Correles un par un
avec nos propres lignes de journal :

| heure | retard | ce que notre mod faisait |
| --- | --- | --- |
| 15:47 a 16:02 | 12 retards de 2 a 6,6 s | rien : aucune ligne de notre mod dans les 25 s d'avant |
| 16:03:17 | 18,8 s | le sanctuaire 1 se termine a 16:03:18 (27,6 s de fil serveur) |
| 16:03:45 | 13,3 s | le sanctuaire 2 commence, sur sol vierge |
| 16:04:09 | plantage | pendant le chantier du sanctuaire 2 |

Deux choses distinctes, donc. Le chantier, qui coute encore vingt-sept
secondes chez le joueur contre trois a huit en dev. Et un BRUIT DE FOND d'un
retard par minute, sans rapport avec ce que le mode fait, present dans chaque
session depuis le 4 septembre :

| session | monde | minutes | retards | > 5 s | fils DH |
| --- | --- | --- | --- | --- | --- |
| 4 sept. 18:52 | T1 | 18 | 13 | 5 | 8 a 0,7 |
| 6 sept. 13:58 | Test commandes | 60 | 94 | 18 | 8 a 0,7 |
| 7 sept. 20:25 | Nouveau monde | 41 | 32 | 10 | 8 a 0,7 |
| 9 sept. 10:57 | T4 | 46 | 36 | 7 | **4 a 0,4** |
| 9 sept. 15:44 | T5 | 19 | 14 | 6 | 8 a 0,7 |

Le nombre de fils de Distant Horizons n'est PAS la variable decisive : meme
taux avec quatre fils qu'avec huit. C'etait pourtant le reglage qu'on avait
touche dans les deux sens.

### B. Le profil du joueur tournait sans aucun reglage JVM

La ligne de commande du client, dans le rapport de plantage :

    -Xmx16384m -Xms256m

Rien d'autre. Pas de G1 regle, pas de tas fixe. `tools/java_args.py` existe
exactement pour cela, et sa propre notice explique qu'un tas qui part de 256 Mo
pour monter a 16 Go passe son temps a se redimensionner, chaque agrandissement
etant une pause. Il n'avait ete applique qu'a « All the Mods 10 - CUSTOM » ; le
profil « Mode Arcencium », cree le 4 septembre, n'en avait jamais herite. Le
client de dev, lui, a les bons reglages depuis le debut -- et n'a jamais
montre ce bruit de fond.

Pose le 9 septembre : dix gigaoctets fixes et le jeu d'options G1. C'est la
premiere chose a verifier apres la prochaine partie : si le bruit de fond
tombe, c'etait lui.

### C. Le dev ne reproduit pas le profil, et c'est pour cela qu'on ne voyait rien

`run/mods` contient 43 jars. Le profil en a 446. Un chantier de sanctuaire y
prend trois a huit secondes de fil serveur ; chez le joueur, vingt-sept. Tout
ce qu'on a mesure « en jeu » l'a ete dans un monde dix fois plus leger que le
sien. `tools/dev_mods.py` lit desormais le profil « Mode Arcencium » ; un vrai
A/B des lags devra se faire avec les 446 jars.

### D. Le plantage : un chunk fini et deblaye dans la meme tique

La station de charge de PneumaticCraft n'est pas de nous : elle vient d'une
structure d'un autre mod, generee dans un chunk que le chantier venait de
FINIR. En finissant un chunk, le jeu inscrit ses entites de bloc fraiches pour
leur `onLoad` a la tique suivante. Si, dans la MEME tique, une etape de
deblaiement remplace leur bloc par de l'air, NeoForge appelle `onLoad` quand
meme -- il ne verifie pas `isRemoved` -- et la station tente de poser une
propriete sur de l'air. Avec un budget de douze millisecondes, un
prechargement et un deblaiement tenaient souvent dans la meme tique.

Les etapes de prechargement au palier FULL CLOSENT desormais leur tique
(`Step.closesTick`) : les entites se chargent, puis seulement on deblaie.
Cent quatre-vingt-seize tiques de plus par sanctuaire, dix secondes que
personne ne voit. Et en garde-fou, `removeErroringBlockEntities = true` dans
le profil : une entite de bloc qui plante est retiree au lieu d'emporter le
monde.

Le monde T5 ne portait aucune entite PneumaticCraft dans ses regions : elle
n'a vecu que le temps d'une tique, ce qui confirme le scenario.

### E. Le chantier ne genere plus rien sur le fil serveur : il demande

Le premier correctif fermait la tique apres chaque chunk fini. C'etait juste,
mais chaque `getChunk(..., true)` BLOQUAIT encore le fil serveur le temps de la
generation, quel que soit le palier demande : le chunk se fabrique sur les fils
de travail, mais le fil serveur attend la reponse. Chez le joueur, avec quatre
cent quarante mods de generation, cela faisait vingt-sept secondes de fil
serveur par sanctuaire.

Un TICKET fait exactement ce que fait un joueur qui marche : il dit au systeme
de chunks « je veux celui-la fini », et le systeme le fabrique en arriere-plan,
sur ses fils, sur autant de tiques qu'il faut. Le fil serveur n'y touche que
pour la promotion finale. Le chantier pose donc un ticket par chunk du site
(`TicketType arcencium_chantier`, niveau 33 : fini, mais ni entites ni blocs
qui tiquent, et qui expire seul apres deux minutes), puis une etape d'ATTENTE
se represente a chaque tique tant qu'un chunk manque -- en fermant sa tique a
chaque fois, ce qui garde la protection contre le plantage « charge_pad ». Le
compte rendu dit combien de tiques on a attendu. Les tickets sont rendus a la
fin.

Mesure en dev, trois sanctuaires sur sol vierge par le chemin normal du jeu :

| | avant (getChunk bloquant) | apres (tickets) |
| --- | --- | --- |
| fil serveur par sanctuaire | 9 000 a 9 900 ms | 1 329 a 2 408 ms |
| pire etape | prechargement, 1 700 ms | pyramide, 343 ms |
| « Can't keep up » pendant les trois chantiers | 3 | 0 |
| chunks attendus en arriere-plan | -- | 229, 47 et 34 tiques |

Le prechargement a disparu du classement des pires etapes : il ne coute plus
rien au fil serveur. Ce qui reste est de la pose de blocs, deja en bandes.

## 61. Les tombeaux muets, et les reglages JVM que CurseForge efface *(12 sept. 2026)*

### A. « Je n'arrive pas a activer les tombeaux, rien ne se passe »

Le registre des sceaux (`SanctuarySeals`) etait VOLATIL -- « cela se rebatit,
cela ne se sauve pas ». C'etait vrai tant que la reprise rebatissait tout ;
c'est faux depuis qu'elle ne rebatit plus ce qui existe (§55 D bis). Un
sanctuaire bati la veille n'avait donc plus un seul sceau inscrit : le clic sur
un tombeau cherchait sa position dans une liste vide, ne trouvait rien, et se
taisait. Et un joueur qui avait eveille ses cinq sceaux la veille se serait vu
refuser l'ancre au retour, sans pouvoir recliquer des sceaux deja allumes.

Les sceaux sont maintenant ECRITS dans le journal de bord a la pose
(`GameState.setSeals`, etiquette `Vaults`) et RELUS a la reprise
(`SanctuarySeals.restore`). Et l'etat eveille/endormi ne vit plus en memoire :
il se lit sur le bloc, qui porte `lit=true` et survit a tout. Chaque consultation
-- le clic, l'ancre, la commande, le rappel periodique -- relit d'abord les blocs
charges (`refresh`).

Verifie en deux ouvertures avec fermeture propre entre les deux :

    [ouverture 1]  Sceaux : ... endormi x5   puis   5 sceau(x) eveille(s)
    [ouverture 2]  Sceaux : ... eveille x5   sans rien toucher

Et l'inverse : batis sans eveiller, recharge, « endormi x5 », eveille, « eveille x5 ».

Au passage, `SanctuaryMist.nearestAnchor` -- volatil lui aussi -- se rabat sur
les ancres du journal de bord quand sa liste est vide : les commandes d'essai
repondaient « aucun sanctuaire connu de cette session » devant un sanctuaire
bien reel.

### B. CurseForge a efface les reglages JVM

Les reglages poses le 9 septembre (§60 B) n'ont jamais tourne : a la fermeture
du jeu, CurseForge a reecrit `minecraftinstance.json` et remis `override:
false, memoire 4096, args : aucun`. Le fichier n'est pas la source de verite de
CurseForge, il en est une copie. La session du 11 septembre a donc joue avec
`-Xmx16384m -Xms256m` et rien d'autre, comme avant : trente retards en
cinquante-quatre minutes, de deux a neuf secondes, un par minute pendant la
Battue, sans lien avec nos lignes de journal, Distant Horizons generant sans
interruption pendant cinquante-deux des cinquante-trois minutes.

La seule facon durable est de saisir les arguments dans l'application
CurseForge elle-meme (profil, Reglages, Java). La chaine est celle de
`tools/java_args.py`, tas fixe de dix gigaoctets et G1 regle.

## 62. Accelerer la partie, et le Carnet qui l'apprend *(12 sept. 2026)*

« Il me reste quarante minutes et je n'ai toujours pas fait le premier
sanctuaire. Heros 18, specialisation +9, une epee en diamant +5, rarete 2 ou
4. Monter tous les equipements a +5 serait impossible. » Et le joueur tranche :
on n'allonge pas la partie, on l'accelere. Il a raison : un mode plus long
avec les memes taux resterait un mode ou l'on manque de tout.

### A. Le compte, avec ses chiffres

A soixante-quatorze minutes de jeu, sans avoir pu toucher une ancre (bug des
tombeaux, §61). Ce que coutait vraiment un +5 sur cinq pieces, avec les
chances de reussite de la Forge :

| cran | chance | tentatives en moyenne | metal par tentative |
| --- | --- | --- | --- |
| +1 | 90 % | 1,1 | 4 fer |
| +2 | 82 % | 1,2 | 6 fer |
| +3 | 74 % | 1,4 | 9 fer |
| +4 | 62 % | 1,6 | 4 or |
| +5 | 52 % | 1,9 | 6 or |

Soit vingt-quatre fer et dix-huit or par piece, **cent vingt fer et
quatre-vingt-dix or** pour cinq pieces -- parce que chaque echec emportait le
metal avec la Pierre. Le diamant et l'Arcencium tombent a l'Aurore ; le fer et
l'or ne tombaient nulle part de special. « J'etais rapidement bloque parce qu'il
me manquait du fer et de l'or » : c'est exactement ce que dit le tableau.

### B. Les quatre leviers

1. **Un echec a la Forge ne coute plus que la Pierre** (`Upgrade.refund`). On
   paie toujours d'abord -- un tirage ne doit jamais preceder un paiement qui
   pourrait echouer -- mais le metal revient dans le sac sur un rate. Le +5
   sur cinq pieces tombe a soixante fer et quarante or, et c'est le cout de la
   reussite, pas celui de la malchance.
2. **Le fer et l'or dans les recompenses.** La defense du village donne
   vingt-quatre fer et douze or (huit fer avant) ; chaque Proie de Battue lache
   une cache de douze a seize fer et six a huit or. Le metal tombe la ou le
   joueur revient de toute facon.
3. **L'experience Heros une fois et demie** : un zombie vaut dix, non sept.
   La courbe comptait sur les trente-quatre niveaux des trois ancres ; meme
   corrigees, elles arrivent tard, et le mode doit se finir en quatre-vingt-dix
   minutes.
4. **L'Eclat du Destin deux fois plus souvent** : un monstre sur six au combat
   ordinaire, un sur deux sous la Maree ou l'orage. La rarete a 2-4 apres une
   heure ne suivait pas le rythme des armes.

### C. Le Carnet

« Je ne sais pas du tout comment fabriquer les armes et je n'ai aucun moyen de
le savoir. Un joueur ne saura jamais comment ca fonctionne, surtout pour runer
l'arme ou monter sa rarete. » Le manuel existe (GUIDE.html), mais personne ne
lit trente pages en pleine partie.

Le Carnet (`quest/Quests`) dit UNE chose a la fois, au moment ou elle sert, et
la coche quand c'est fait. Dix etapes dans l'ordre naturel d'une partie :

| # | etape | ce qu'on verifie | ce qu'elle paie |
| --- | --- | --- | --- |
| 1 | Le premier Arcencium | un brut, un lingot ou quatre eclats dans le sac | 2 Eclats du Destin |
| 2 | Trois lingots | trois lingots | 3 Pierres de Forge |
| 3 | Le bois de Prisme | une branche ou une fibre | 4 emeraudes |
| 4 | Votre premiere arme | une arme du mode portee ou dans le sac | 6 Pierres, 8 fer |
| 5 | La Forge : +1 | une piece a +1 | 3 Eclats |
| 6 | Une rune gravee | une piece qui porte une rune | 3 plumes |
| 7 | Monter la rarete | une piece de rang 2 ou plus | 4 plumes |
| 8 | La specialisation | +1 a l'Autel | 2 Eclats, 6 or |
| 9 | Eveiller un sceau | un sceau eveille, n'importe ou | 8 lingots d'Arcencium |
| 10 | Tenir une ancre | une ancre active | 12 Pierres de Forge |

Chaque etape se verifie sur ce que le joueur PORTE ou A FAIT, toutes les deux
secondes -- jamais sur un clic dans une interface, qu'on rate ou qu'on ne fait
pas dans l'ordre. L'etape 4 dessine les quatre recettes en lettres dans le
chat, avec la legende. La memoire vit dans les donnees persistantes du joueur :
elle survit a la mort et a la session, et le Carnet se rouvre a la bonne page
a la connexion. Une ligne en bas de l'ecran, au-dessus de la fiche du Heros,
dit l'etape en cours ; `/arcencium quete` la relit avec sa recette.

Les recompenses sont petites et tournees vers l'etape suivante : le Carnet ne
remplace pas le jeu, il l'ouvre. La neuvieme paie huit lingots, soit le prix
exact de la premiere ancre.

### D. Le lag : ce qu'on sait, ce qu'on ne peut pas mesurer ici

La session du 11 septembre : trente retards en cinquante-quatre minutes, de
deux a neuf secondes, sans lien avec nos lignes de journal ; un par minute
pendant la Battue, un toutes les deux minutes le reste du temps ; Distant
Horizons a genere pendant cinquante-deux des cinquante-trois minutes. Et les
reglages JVM du 9 septembre n'ont jamais tourne : CurseForge les a effaces
(§61 B).

Le dev a tente de rejouer le profil entier -- les 446 jars installes par
`tools/dev_mods.py` -- pour comparer, sur le meme tour du monde, la generation
lointaine allumee et eteinte. Trois lancements, trois murs : Sodium contre
Embeddium (ecarte), un banc orphelin d'une session precedente qui a ferme le
client suivant, puis « Mod 'architectury' is not available! » au demarrage,
l'ordre de construction des mods en userdev appelant Architectury avant qu'il
soit pret. Ce n'est pas un defaut du profil, c'est le banc qui ne sait pas
porter quatre cent quarante-six mods. Le jeu leger est remis.

La mesure fidele se fera donc SUR LE PROFIL, en deux parties courtes et
comparables : d'abord avec les arguments JVM saisis dans l'application
CurseForge, puis, si les retards restent, avec `enableDistantGeneration =
false` dans `config/DistantHorizons.toml` -- la vue au loin ne montre alors
que le terrain deja visite, mais plus rien ne se genere en arriere-plan.
C'est la seule variable qu'on n'a pas encore isolee, et c'est celle que le
journal designe : elle tourne sans relache, dans chaque session, depuis le
4 septembre.

### E. Ce que le banc a verifie *(12 sept. 2026)*

Deux lancements du jeu leger (`verify_carnet.sh`), le Carnet remis a zero, ce
qu'il attend donne etape par etape par commande, puis cinquante zombies tues
d'un coup par le joueur.

- **Le premier lancement n'a pas demarre.** Le Carnet est un abonne
  d'evenements : sa classe se charge a la construction du mod, avant
  l'enregistrement des objets, et sa liste d'etapes construisait les piles de
  recompense avec `ModItems.FATE_SHARD.get()` -- « Trying to access unbound
  value ». Les recompenses sont devenues des fournisseurs, resolus au moment
  de payer.
- **Neuf etapes sur dix cochees dans l'ordre**, chacune dite puis franchie
  dans le chat, avec sa recompense. La neuvieme s'est cochee parce que le
  monde d'essai gardait un sceau eveille du banc des tombeaux (§61) : c'est
  bien l'etat du bloc qui fait foi, pas une memoire volatile. La dixieme
  demande une ancre active et n'a pas ete jouee au banc ; elle lit le meme
  compteur que la Finale.
- **Les recompenses disaient « + 0 × Air »** : on lisait le nom et le compte
  APRES avoir range la pile, et `add` vide ce qu'on lui tend. Lus avant,
  desormais.
- **Les taux.** Cinquante zombies : 6 puis 7 Eclats du Destin (un sur six
  attendu, soit 8 ; ~4 avant) ; seize niveaux Heros annonces, Heros 18 a la
  fin (un zombie vaut 11 points, 8 avant).
- Une lecon de banc : SendKeys reserve `{ } [ ]`, donc un `/give` avec
  composants ne passe pas par le chat simule. Les commandes a accolades vont
  dans une fonction du datapack, et le chat n'appelle que `/function`.
