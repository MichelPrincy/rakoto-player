# RAKOTO Player

Site vitrine de RAKOTO Player en TypeScript, avec une identité bleue et blanche. La première section propose les installations Windows hors ligne / en ligne et l'APK Android.

L'application Android native est dans [`android/`](android/README.md) : Java et LibVLC 3.7.7, Android 6.0+ ARM 32/64 bits, version **1.0.0-beta.2**. Quatre onglets : Audio, Vidéo, Coffre, Profil. Scan MediaStore et dossiers autorisés, playlists et favoris, navigation par dossiers, tri dans les deux sens et coffre chiffré avec code à quatre chiffres. Les originaux des vidéos importées dans le coffre sont conservés. Les tests du chiffrement et des tris sont passés ; les limites de validation Android sont documentées dans [`android/TESTS.md`](android/TESTS.md).

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

L’aperçu local est servi sur `http://127.0.0.1:4173`. Modifier les sources de `src/`, puis exécuter `npm run build` et inclure les changements de `docs/` dans le prochain commit.

## Organisation

- `src/index.html` : structure et textes de la vitrine.
- `src/style.css` : identité visuelle et mise en page responsive.
- `src/client.ts` : téléchargements, menu mobile et aperçu du mini-lecteur.
- `assets/` : logo et illustrations RAKOTO.
- `public/release.json` : versions, tailles, empreintes SHA-256 et URL des installateurs Windows et de l'APK Android.
- `android/` : sources de l'application Android, Gradle Wrapper, notices et tests natifs.
- `docs/` : site prêt à publier sur GitHub Pages.
- `tests/` : contrôle des chemins et des téléchargements dans le client compilé.

## Téléchargements

Les boutons utilisent les installateurs déjà hébergés sur `https://rakoto-player.exquisitecurl1.chatgpt.site`. Le manifeste local permet au site statique de fonctionner sans serveur applicatif ni clé secrète. Les deux installations fournissent la même application Windows ; l’assistant en ligne télécharge et vérifie le programme complet.

L'APK Android complet est hébergé dans les **GitHub Releases** de ce dépôt. Son moteur est inclus pour lire les fichiers locaux hors ligne ; il n'a pas d'autorisation Internet. Cette première bêta ne comprend pas le tiroir audio flottant Windows ni son visualiseur. Le mode flottant vidéo utilise le PiP Android 8+ compatible, l'audio en arrière-plan se commande dans les notifications. L'APK est signé avec la clé de diffusion RAKOTO, conservée en privé et exclue du dépôt.

Pour une nouvelle version, mettre à jour `public/release.json`, puis reconstruire le site. Les URL peuvent aussi pointer vers des fichiers publiés dans GitHub Releases. Les installateurs de la version actuelle sont des versions de développement non signées numériquement.
