# RAKOTO Player

Landing page unique de RAKOTO Player en TypeScript : univers bleu nuit, image fournie par le propriétaire, captures Windows réelles et défilement narratif. Interface française et malagasy, ambiance sonore originale sur activation volontaire, téléchargements Windows hors ligne / en ligne et APK Android. iOS est annoncé sans lien App Store. Aucun framework d’animation ni service musical externe n’a été ajouté.

L'application Android native est dans [`android/`](android/README.md) : interface Kotlin native, services Java et LibVLC 3.7.7, Android 6.0+ ARM 32/64 bits, version **1.0.0-beta.4**. Quatre onglets : Musique, Vidéos, Coffre, Réglages. Identité Lamba crème/terre rouge, thèmes clair et sombre, albums/artistes, miniatures réelles, liste/grille vidéo, scan MediaStore et dossiers autorisés, playlists, favoris et coffre chiffré avec clavier PIN. Le moteur est chargé à la demande. Trois APK : ARM64 allégé, ARMv7 allégé et universel. Le coffre déplace les vidéos et images après authentification complète et comparaison SHA-256 ; Android peut demander un consentement. Les retraits refusés restent à terminer. La restauration restitue les extensions et la visibilité en galerie. Les commandes vidéo sont fixes et Retour arrête la vidéo. Le titre sélectionné reste stable, sans adresse fd://. Les tests du chiffrement et des tris sont passés ; les limites de validation Android sont documentées dans [`android/TESTS.md`](android/TESTS.md).

## Publier depuis ce dépôt avec GitHub Pages

Le site est déjà compilé dans **`docs/`**. Dans le dépôt GitHub :

1. Ouvrir **Settings → Pages**.
2. Dans **Build and deployment**, choisir **Deploy from a branch**.
3. Choisir la branche **main**, le dossier **/docs**, puis **Save**.

GitHub publiera le site à l’adresse `https://michelprincy.github.io/rakoto-player/`. Cette adresse devient disponible après activation de Pages et réussite du déploiement GitHub. Les chemins des images et scripts sont compatibles avec ce sous-dossier.

## Modifier le site

```sh
npm ci
npm run check
npm run build
npm test
npm run preview
```

L’aperçu local est servi sur `http://127.0.0.1:4173/rakoto-player/` et à la racine. Modifier les sources de `src/`, puis exécuter `npm run build` et inclure les changements de `docs/` dans le prochain commit.

## Organisation

- `src/index.html` : document et métadonnées de la vitrine.
- `src/page.mjs` : composition sémantique unique, rendue en HTML à la compilation.
- `src/content.ts` : dictionnaires FR/MG complets, persistance et métadonnées localisées.
- `src/experience.ts` : entrées narratives, parallaxe bornée, mode léger, synthèse et analyse audio réelles.
- `src/style.css` : identité visuelle et mise en page responsive.
- `src/client.ts` : téléchargements, langue, menu mobile, captures agrandies et partage volontaire.
- `assets/` : logo et illustrations RAKOTO.
- `public/release.json` : versions, tailles, empreintes SHA-256 et URL des installateurs Windows et de l'APK Android.
- `android/` : sources de l'application Android, Gradle Wrapper, notices et tests natifs.
- `docs/` : site prêt à publier sur GitHub Pages.
- `tests/` : chemins, téléchargements, traductions, ancres, SEO, ressources et budgets de transfert.
- `SEO.md` : stratégie française, préparation anglaise et limites d’indexation de la traduction côté client.

Le choix FR/MG et l’intensité des effets sont mémorisés. Même si le visiteur avait activé le son, une nouvelle visite reste silencieuse jusqu’à un nouveau clic. Les effets sont réduits avec `prefers-reduced-motion`, l’économie de données ou un appareil signalant peu de ressources. Les animations hors écran et le son dans un onglet masqué sont suspendus.

Les captures Windows affichent des données de démonstration. Les appareils Android et iOS sont des aperçus conceptuels identifiés comme tels ; ils ne sont pas présentés comme des captures d’applications. Les crédits audio et visuels sont dans `assets/AUDIO-NOTICE.txt` et dans le footer.

## Téléchargements

Les boutons utilisent les installateurs déjà hébergés sur `https://rakoto-player.exquisitecurl1.chatgpt.site`. Le manifeste local permet au site statique de fonctionner sans serveur applicatif ni clé secrète. Les deux installations fournissent la même application Windows ; l’assistant en ligne télécharge et vérifie le programme complet.

Les APK Android signés sont publiés dans les **GitHub Releases** de ce dépôt et servis par un miroir avec reprise et vérification SHA-256. Le site propose une version allégée par architecture ; sans indication fiable, il utilise la version universelle. Son moteur est inclus pour lire les fichiers locaux hors ligne ; il n'a pas d'autorisation Internet. La bêta Android ne comprend pas le tiroir audio flottant Windows ni son visualiseur. Le mode flottant vidéo utilise le PiP Android 8+ compatible, l'audio en arrière-plan se commande dans les notifications. L'APK est signé avec la clé de diffusion RAKOTO, conservée en privé et exclue du dépôt.

Pour une nouvelle version, mettre à jour `public/release.json`, puis reconstruire le site. Les URL peuvent aussi pointer vers des fichiers publiés dans GitHub Releases. Les installateurs de la version actuelle sont des versions de développement non signées numériquement.
