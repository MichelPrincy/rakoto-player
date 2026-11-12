# RAKOTO Player Windows 2.3.2

## Régression reproduite et corrigée

Le contrôle de fenêtre native échouait après le clic d’un voisin : le code
vidait toutes les propriétés mpv, y compris `window-id`. Quand mpv réutilise sa
fenêtre, cette propriété ne change pas et l’observateur ne la renvoie pas.
Le lecteur pouvait alors masquer la fenêtre sans pouvoir la retrouver.
La 2.3.2 préserve cet identifiant et relit les propriétés réelles à `file-loaded`,
avant d’annoncer la lecture prête. Les tests vérifient l’identifiant après un
clic normal, en plein écran et après des sélections rapides, ainsi que la durée.
Les 45 contrôles passent depuis les sources après ce correctif.

## Changements

- Liste vidéo : préparation réelle du fichier avant sélection, ouverture mpv confirmée
  par `file-loaded`, protection des sélections rapides et clics dans les deux listes.
- Analyse des autres films en arrière-plan, une tâche à la fois, après chargement
  du premier film. Priorité à la lecture, budget de fond 16 Mio / 3,5 s, pauses
  entre tâches et option de désactivation. Un dépassement reste « différé » et
  n’est pas présenté comme un fichier incompatible ; un clic relance une analyse complète.
- Métadonnées musicales conservées en cache ; contrôle du fichier au moment de
  sa lecture. Pochettes limitées à 384 px et stockées sur disque, hors du catalogue
  en mémoire. Écritures de bibliothèque regroupées, analyseur libéré après 1,5 s,
  tampon mpv borné à 32 Mio en avant et 4 Mio en arrière, mises à jour UI à 5 Hz.
- Enregistrement Windows obligatoire pour les formats pris en charge : Capabilities,
  RegisteredApplications, App Paths, ProgID et SupportedTypes. Aucun changement de
  UserChoice, d’application choisie par défaut ou de clé appartenant à un autre lecteur.
- Bouton Applications par défaut dans Paramètres. Suggestion après plusieurs
  utilisations seulement si un autre exécutable est actuellement choisi ; rappel
  espacé, report possible et option Ne plus proposer. Pas de dialogue forcé en plein écran.

## Contrôles

- 8 tests Node : sessions, persistance uniquement musicale, listes longues,
  enregistrement des formats, fréquence des suggestions, séquentialité et annulation
  de progression d’analyse des anciens dossiers.
- 23 formats/conteneurs de test réellement analysés et lus par mpv : notamment
  MKV multilingue/sous-titré, H.264, HEVC, AVI, WebM, FLAC, MP3, vidéo sans son,
  fichier sans extension. Un fichier endommagé est rejeté.
- 45 contrôles fonctionnels passent dans le programme distribué, dont clics
  de la liste normale et plein écran, analyses de fond, sélections rapides, décodage
  d’image réel, pistes audio/sous-titres, fichiers endommagés et nettoyage des fenêtres.
- Redémarrage du cache musical contrôlé : bibliothèque restaurée, zéro nouvelle
  analyse média. Mesure Electron de travail de processus relevée dans le rapport
  local ; elle ne constitue pas une certification de consommation sur ancien PC.
- Assistant en ligne : charge utile corrompue et téléchargement absent rejetés.
  Parcours complet local de la vraie charge utile : téléchargement, SHA-256,
  installation 2.3.2 et désinstallation passent.

- Installation hors ligne dans un dossier de test isolé : code 0, les mêmes
  45 contrôles passent depuis les fichiers installés ; désinstallation : code 0,
  dossier supprimé. Le lecteur MP4 choisi avant le test reste identique après.

## Taille et compatibilité

- Hors ligne : 141825181 octets ; assistant en ligne : 247807 octets.
- Distribution complète : 453830257 octets non compressés.
- Le paquet reste environ 142 Mo : Electron et mpv constituent l’essentiel du
  poids. Cette version allège les analyses, le démarrage et les tampons, sans
  prétendre avoir supprimé le poids du moteur ou des codecs.
- Windows 10 / 11 x64. Pas de validation sur Windows 7/8, système 32 bits ou
  ancien PC physique. Décodage matériel selon le GPU, repli logiciel conservé.
- Pour réduire fortement la taille à terme, une migration du conteneur Electron
  vers WebView2 système est une piste ; elle exige une nouvelle intégration des
  fenêtres natives et une validation complète, et n’est pas simulée dans cette version.
