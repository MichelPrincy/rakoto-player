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
