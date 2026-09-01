# Prompt a coller dans Grok ou ChatGPT

But : recuperer les VRAIS bareme et bonus des points de specialiste de NosTale,
pour remplacer les valeurs provisoires de `HeroStat.java`.

A savoir avant de coller : ce qui m'interesse, ce sont des CHIFFRES par point et
par palier, pas une description du systeme. Le prompt est ecrit pour rendre une
reponse difficile a bricoler — s'il ne connait pas, il doit le dire.

---

Tu es documentaliste sur le jeu NosTale (Gameforge). Je veux le bareme exact du
systeme de **points de specialiste** (« points SP », repartis dans la fiche du
personnage), tel qu'on le simule sur https://nosapki.com/fr/simulators/specialists_points

Reponds **uniquement par des tableaux chiffres**. Pas d'introduction, pas de
conclusion.

**1. Les categories.** Liste les categories entre lesquelles on repartit les
points (chez moi : Attaque, Element, Defense, HP/MP — corrige-moi si les noms
officiels different, en donnant le nom francais ET anglais).

**2. Le gain par point.** Pour chaque categorie, un tableau :

| Categorie | Statistique touchee | Gain par point | Unite | Plafond de points |
|---|---|---|---|---|

S'il y a plusieurs statistiques par categorie (par exemple Attaque = degats
ET taux de critique ET degats critiques), fais une ligne par statistique.

**3. Les paliers.** NosTale donne des bonus francs a certains seuils. Pour
chaque categorie :

| Categorie | Seuil (en points) | Bonus obtenu | Valeur |
|---|---|---|---|

Je veux les seuils REELS (tous les combien de points), pas un arrondi.

**4. Le total.** Combien de points un personnage possede-t-il au niveau
maximum, et selon quel bareme par niveau (X points au niveau N) ?

**5. La linearite.** Le gain par point est-il constant, ou decroit-il quand la
categorie monte ? Si le rendement est degressif, donne la table complete des
tranches.

**Contraintes de reponse :**

- Precise la VERSION ou le serveur auquel s'appliquent ces chiffres (les
  valeurs ont change au fil des mises a jour ; dis lesquelles tu donnes).
- Pour chaque tableau, indique ta source (page de wiki, simulateur, patch note).
- **Si tu n'as pas le chiffre exact, ecris « inconnu » dans la case.** Ne
  l'estime pas, ne l'interpole pas, ne le deduis pas d'un autre jeu. Une case
  vide m'est utile ; une case inventee me coute une soiree d'equilibrage.

---

## Ce que ces chiffres remplaceront

`src/main/java/com/emerald/hero/HeroStat.java` porte pour l'instant :

| Voie | Gain par point | Bonus par palier | Palier tous les |
|---|---|---|---|
| ATTAQUE | +0,05 degat | +2,0 degats | 20 points |
| ELEMENT | +0,6 % effets | +4,0 % | 20 points |
| DEFENSE | +0,04 armure | +1,5 armure | 20 points |
| VITALITE | +0,10 PV | +2,0 PV | 20 points |

Plafond par voie : 160 points. Total distribue au niveau 100 : 486 points.

Les valeurs de NosTale ne se transposent pas telles quelles — une armure de
Minecraft n'a pas la meme echelle qu'une defense de NosTale — mais je veux en
reprendre la FORME : le rapport entre le lineaire et le palier, la frequence des
paliers, et le fait que certaines voies donnent une statistique secondaire
(critique, esquive) plutot qu'un simple chiffre. C'est cette forme qui rend la
repartition interessante, et c'est elle que je ne peux pas inventer.
