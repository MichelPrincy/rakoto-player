# RAKOTO Player

Site vitrine de RAKOTO Player en TypeScript, avec une identité bleue et blanche et deux téléchargements Windows dès la première section : installation hors ligne et installation en ligne.

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
- `public/release.json` : version, tailles, empreintes SHA-256 et URL des installateurs.
- `docs/` : site prêt à publier sur GitHub Pages.
- `tests/` : contrôle des chemins et des téléchargements dans le client compilé.

## Téléchargements

Les boutons utilisent les installateurs déjà hébergés sur `https://rakoto-player.exquisitecurl1.chatgpt.site`. Le manifeste local permet au site statique de fonctionner sans serveur applicatif ni clé secrète. Les deux installations fournissent la même application Windows ; l’assistant en ligne télécharge et vérifie le programme complet.

Pour une nouvelle version, mettre à jour `public/release.json`, puis reconstruire le site. Les URL peuvent aussi pointer vers des fichiers publiés dans GitHub Releases. Les installateurs de la version actuelle sont des versions de développement non signées numériquement.
