# RAKOTO Player Windows 2.3.0

Lecteur local Windows 10/11 x64, avec interface Vue/Electron et décodage natif mpv.
L’identité Lamba et les thèmes clair/sombre sont conservés. Les animations
décoratives, la déformation de fenêtre et le moteur de ressort ont été retirés.

## Vidéos : une session temporaire

Ouvrir un film depuis l’Explorateur, le menu Ouvrir ou un glisser-déposer démarre
le fichier choisi. La liste contient les vidéos du même dossier, triées par nom
avec tri numérique naturel (2 avant 10). Les sous-dossiers ne sont pas parcourus.
Les autres vidéos sont listées sans analyse lourde ; leur contenu est analysé
quand elles sont lancées. L’extension sert au repérage du dossier, pas à garantir
la compatibilité. Un fichier non reconnu affiche une erreur réelle.

La liste vidéo, l’historique, la position et les préférences par film ne sont
pas sauvegardés. Les fichiers eux-mêmes restent à leur emplacement. Une nouvelle
ouverture remplace la session précédente. L’ancienne bibliothèque est migrée :
les références vidéo sont retirées, la musique et ses playlists sont conservées.
Les anciens chemins sont analysés une fois pour préserver aussi les fichiers
audio seuls dans un conteneur vidéo ; les démarrages suivants ne chargent que
les chemins musicaux sauvegardés.

En fenêtre normale, les commandes sont fixes en bas. Le bouton Liste ou F6 ouvre
le dossier à droite, sans masquer le film. En plein écran Electron, seule la
vidéo reste visible après trois secondes d’inactivité ; un mouvement révèle les
commandes. Le bord droit révèle la liste temporaire, qui se referme en revenant
sur l’image. Menus, curseurs et navigation clavier suspendent le masquage pendant
l’interaction. Quitter le plein écran conserve la zone vidéo.

## Musique et contrôles

La bibliothèque musicale, les favoris et les playlists restent sauvegardés dans
le dossier de données de l’application. Les réglages de répétition/aléatoire
vidéo sont temporaires et indépendants de ceux de la musique. Le mini-lecteur
musical, l’égaliseur dix bandes, les paroles LRC, les thèmes et le volume restent
disponibles. Les pistes audio, sous-titres intégrés/externes, leur synchronisation,
les chapitres et les options du moteur natif restent accessibles par clic droit.

- Ctrl+O : ouvrir des fichiers ; Ctrl+M : mini-lecteur musical / vidéo flottante.
- F6 : liste du dossier ou file musicale.
- Espace : lecture/pause ; flèches gauche/droite : ±5 secondes.
- Flèches haut/bas : volume ; M : muet.
- F ou Entrée, double-clic sur la vidéo : plein écran.
- Échap : fermer la liste plein écran, puis quitter le plein écran.

## Allègement

Pas de boucle d’élasticité ni de déformation au déplacement/redimensionnement.
Pas d’analyse systématique des films voisins. Le visualiseur ne crée pas de
second décodage vidéo. Les grandes listes utilisent un rendu de lignes borné.
Le graphe Web Audio est suspendu pendant la lecture native. Le moteur et les
fenêtres vidéo sont libérés après l’arrêt ; le worker d’analyse est également
libéré après quelques secondes d’inactivité.

Le socle Electron et mpv représente l’essentiel du poids : cette version allège
surtout le travail en arrière-plan et l’usage mémoire. La distribution complète
reste d’environ 454 Mo non compressés ; les codecs n’ont pas été retirés.

## Développement et distribution

`npm ci`, puis `npm start`. `node tools/compile.cjs` compile les modèles Vue.
`node tools/package.cjs` produit `dist/RAKOTO Player` ;
`node tools/build-installers.cjs` construit les installateurs hors ligne/en ligne.
Les sources publiques n’incluent pas les binaires natifs : leur provenance exacte
et leurs licences figurent dans `native/README.md` et les notices du paquet.

`node --test tools/test-video-session.cjs` vérifie la logique des sessions et de
la migration. `electron . --verify-session` vérifie les fonctions réelles avec
les médias synthétiques de `tools/media-work/fixtures`, dans un profil temporaire
isolé. Les résultats de cette version sont dans `WINDOWS-2.3.0-TESTS.md`.

Référence consultée : [historique officiel PotPlayer](https://t1.daumcdn.net/potplayer/PotPlayer/v4/Update2/UpdateEng.html).
Le comportement de session et de liste latérale correspond à la demande du
propriétaire ; RAKOTO conserve son moteur et son interface propres.
