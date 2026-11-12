# Contrôles Windows 2.3.0 — 12 novembre 2026

Quatre tests de logique réussis : dossier courant et tri naturel, sélection du
fichier demandé, dossier inaccessible, migration musicale sans références vidéo,
et fenêtre de rendu bornée sur 10 000 entrées avec recherche accentuée.

Trente-cinq contrôles fonctionnels réussis depuis les sources, puis les mêmes
trente-cinq depuis le dossier Windows distribué :

- Quatre films triés, fichier cliqué effectivement lu, audio et sous-dossiers exclus.
- Une seule analyse média au départ ; voisin HEVC analysé lors de sa sélection.
- Playlist et file musicales conservées pendant la session vidéo.
- Pas de contrôleur flottant en mode normal ; panneau à droite sans recouvrement.
- Image réellement décodée par mpv (capture du moteur 320 × 180), pas seulement audio.
- Changement de piste audio et de sous-titres sans rechargement du film.
- Plein écran natif, masquage après trois secondes, bord droit, F6 et Échap.
- Maintien des commandes pendant l’interaction, conservation de la zone vidéo
  au retour normal et disparition du contrôleur flottant hors plein écran.
- Aucune référence vidéo dans le JSON sauvegardé ; playlist musicale conservée.
- Fichier corrompu et ouverture directe invalide : erreur visible, ancienne lecture arrêtée.
- Moteur et fenêtres vidéo libérés après arrêt.

Les tests utilisent un profil isolé et des fichiers synthétiques MKV multilingues,
MP4 H.264/HEVC et MP3. Ils n’accèdent pas à la bibliothèque personnelle du propriétaire.
Les contrôles d’interface sont automatisés sans revue graphique manuelle.
Ce résultat ne certifie pas tous les médias, GPU, pilotes ou machines de 8 Go.
Aucun gain chiffré de RAM/FPS n’est revendiqué. L’accélération et le repli logiciel
existants du moteur restent conservés.

Les installateurs sont construits avec NSIS ; les tests du dossier distribué ne
remplacent pas un essai manuel sur chaque ordinateur cible. La distribution non
compressée mesure 453 807 377 octets, dont 249 831 936 pour Electron et 121 631 744
pour mpv. Le changement vise surtout l’activité et la rétention mémoire.
