# Nouvelle illustration transparente du hero — 12 novembre 2026

- Image fournie : « Jeunes Malgaches, musique et technologie.png » (1536 × 1024).
- Exports WebP avec transparence préservée : 371 380 octets (bureau) et
  119 124 octets (mobile). Aucun fond ou voile coloré ajouté à l’illustration.
  À la demande du propriétaire, seul le bas se fond par un masque alpha CSS
  (de 82 % à 100 % de la hauteur), sans modifier le fichier transparent.
- Cadrage adapté au format 3:2 et suppression de l’ancien effet de feu et de
  son moteur WebGL. Les textes restent identiques, hors descriptions alt FR/MG.
- TypeScript, compilation et 8 tests Node passent. Les 36 contrôles Chromium
  passent à nouveau (360 à 1440 px, FR/MG, menu, liens, audio et ressources).
- Captures bureau/mobile examinées : transparence et boutons lisibles.
  Le mobile est émulé ; aucun test sur téléphone physique n’est revendiqué.

---

## Historique du design précédent

# Design du package « feu de camp » — 12 novembre 2026

- Direction du ZIP appliquée dans la landing page existante : crème, terre rouge,
  vert forêt, titres Fraunces auto-hébergés et frises géométriques.
- Illustration WebP fournie par le propriétaire (171 930 octets), utilisée dans
  le hero. Seuls les textes `hero.*` FR/MG changent : comparaison des autres
  traductions avec le commit publié `02e7531` strictement identique.
- Animation locale WebGL : déformation masquée des flammes, braises, 24 dessins/s
  au maximum. Les personnages et le reste de l’image restent fixes. Aucun nouveau
  fichier vidéo, bibliothèque d’animation, morceau ou appel externe à une police.
- 8 contrôles spécifiques passent dans Chromium : rendu WebGL, changement réel
  des pixels des flammes, stabilité des personnages, arrêt hors écran, mode léger,
  réduction des mouvements, repli sans WebGL et absence d’erreurs console.
- Les 36 contrôles du site passent à nouveau : FR/MG, 5 largeurs (360–1440 px),
  menu mobile, ancres, captures, audio volontaire, téléchargements et contacts.
- TypeScript, compilation et les 8 tests Node passent. Les 5 fichiers de
  téléchargement répondent en HTTP 200 avec les tailles officielles.
- Vérification visuelle des captures bureau, mobile FR/MG, fonctionnalités,
  section du logiciel et téléchargement. Les compositions Windows restent de
  vraies captures ; les aperçus conceptuels mobiles gardent leurs légendes.
- Limitations : tests mobiles émulés dans Chromium, sans téléphone physique,
  Safari ni Firefox. L’effet anime une illustration ; ce n’est pas une vidéo
  de feu filmé. Il se désactive selon les préférences de mouvement et en mode léger.

---

# Refonte immersive — 12 novembre 2026

## Contrôles réalisés avant publication

- TypeScript : `npm run check`, sans erreur.
- Compilation statique : `npm run build`, pour GitHub Pages sous `/rakoto-player/`.
- Huit tests Node : ressources et chemins, vrais téléchargements, choix des APK,
  clés FR/MG complètes, ancres, H1 unique, métadonnées, données structurées,
  sitemap, robots et budgets gzip du JavaScript/CSS.
- Trente-six contrôles dans Chromium fourni par Electron : largeurs 1440, 1024,
  768, 390 et 360 px, images chargées, menu mobile, ancres, FR/MG, persistance,
  mode léger, préférence de réduction des mouvements, captures agrandies,
  FAQ, liens Android par architecture, contacts et absence d’erreurs navigateur.
- Son : activation explicite, visualiseur alimenté par le signal synthétisé,
  coupure, absence de lecture automatique après rechargement et après choix
  « son activé » mémorisé. Le changement réel du canvas est contrôlé pendant
  la lecture. Aucun microphone ni flux audio externe n’est utilisé.
- Boutons de partage et de copie : sorties contrôlées dans le navigateur,
  sans envoyer de message et sans écraser le presse-papiers du propriétaire.
- Les cinq installateurs/APK officiels répondent en HTTP 200 aux requêtes HEAD,
  avec les tailles exactes déclarées dans le manifeste.
- Captures visuelles : introduction bureau, mobile FR/MG et scène multiplateforme.

Les deux dictionnaires ont été relus pour garder un registre cohérent. Les
emprunts techniques dans le malagasy sont intentionnels. Aucune relecture
par un traducteur tiers n’est revendiquée.

## Après publication

Les mêmes 36 contrôles navigateur passent sur l’URL publique
https://michelprincy.github.io/rakoto-player/, avec zéro erreur navigateur.
Les images et captures se chargent après changement de largeur et les liens
de téléchargement restent actifs. Le sitemap XML, robots.txt et l’image de
partage répondent en HTTP 200 avec leurs types de contenu attendus.

## Limites

Les tailles mobiles ont été émulées dans Chromium. Aucun téléphone physique,
Safari/iOS ou Firefox n’a été utilisé dans cette session. Aucun score Lighthouse
n’est revendiqué. Ces contrôles ne constituent pas une nouvelle validation des
binaires Windows/Android : seuls le site et ses liens sont concernés.

Les captures Windows proviennent du véritable logiciel avec des données de
démonstration. Android et iOS sont des compositions conceptuelles explicitement
signalées ; Android bêta est publié, iOS n’a pas de version téléchargeable.

## Reproduire

`npm ci`, `npm run check`, `npm run build`, `npm test`, puis `npm run preview`.
Pour les contrôles navigateur, utiliser un runtime Electron existant avec
`scripts/test-browser.cjs` ; les résultats et profils isolés vont dans
`build-output/browser-audit/`, ignoré par Git. La variable `RAKOTO_SITE_URL`
permet de contrôler la même page après publication. `node scripts/check-downloads.mjs`
contrôle les cinq fichiers officiels par HEAD et taille, sans les télécharger.
