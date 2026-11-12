# RAKOTO Player

RAKOTO est l’évolution d’AZUR Player en application Windows 10/11 avec Vue 3 et Electron. Il lit des fichiers locaux et stocke la bibliothèque, les playlists, les favoris, l’historique et les réglages dans le dossier de données de l’application.

## Thème Lamba 2.2.4

La version ordinateur reprend la maquette fournie : crème, terre rouge, or et vert forêt, zone de lecture sombre, bibliothèque à droite et logo vectoriel original. Les pages de bibliothèque, playlists, favoris, égaliseur et paramètres restent accessibles depuis le bouton Bibliothèque. Les thèmes clair et nuit ainsi que les fenêtres flottantes conservent les mêmes commandes de lecture. Cette publication a été compilée sans session de vérification, à la demande du propriétaire.

Les sources publiées n’incluent pas les binaires natifs. Leur provenance et leurs révisions sont décrites dans `native/README.md` ; le moteur `native/mpv.exe` est nécessaire à la lecture native. Exécuter `npm ci` pour installer les dépendances et compiler les modèles Vue, puis `npm start`. Pour produire les ressources Windows du logo : `powershell -NoProfile -STA -File tools/render-desktop-mark.ps1`, puis `python installer/artwork.py` avec Pillow installé. Le packaging et les installateurs utilisent les outils décrits dans `installer/README.md`.

## Lancer

Si le dossier portable est présent, double-cliquez sur **dist\RAKOTO Player\RAKOTO Player.exe**. Gardez les fichiers du dossier portable ensemble. Pour lancer depuis les sources, double-cliquez sur **Lancer RAKOTO Player.cmd**. Le premier démarrage demande Node.js 22.12 ou ultérieur et peut télécharger les dépendances npm et le runtime Electron officiel. Une fois les dépendances disponibles, la lecture locale fonctionne hors connexion.

Depuis PowerShell dans ce dossier, vous pouvez aussi lancer `npm.cmd ci` puis `npm.cmd start`. Pour lancer la maquette interactive avec une bibliothèque de démonstration, utilisez `npm.cmd run demo`. Pour préparer une distribution Windows x64 dans `dist`, lancez `npm.cmd run package` après l’installation du runtime. Distribuez le dossier complet.

## Bibliothèque et lecture

- **Importer des médias** ou glisser-déposer ajoute des fichiers à la bibliothèque. RAKOTO lit les métadonnées et pochettes disponibles. Les suggestions, albums, catégories et vidéos de l’accueil proviennent de votre collection.
- Créez des playlists et ajoutez leurs médias avec les cases de sélection. Dans la file d’attente, glissez un média pour le réordonner. Le lecteur propose lecture aléatoire, répétition, volume et vitesse.
- L’égaliseur audio traite le signal avec dix filtres ajustables. Choisissez un préréglage, ajustez le renfort des graves et activez le fondu entre deux pistes musicales depuis la page Égaliseur.
- Un fichier **LRC** ou **SRT** du même nom que le média est chargé automatiquement. Les menus Paroles et Sous-titres permettent aussi de choisir un fichier.
- Dans la vue vidéo, utilisez le plein écran, le cadrage cinéma, l’image dans l’image ou la fenêtre vidéo flottante. Le choix d’une piste audio apparaît quand le moteur expose plusieurs pistes. Pour le choix de résolution, importez plusieurs versions du même titre, par exemple `Film [720p].mp4` et `Film [1080p].mp4`. La lecture reprend à la position courante lors du changement de version.
- La compatibilité des vidéos et pistes intégrées dépend du moteur natif mpv. Les formats audio simples utilisent Web Audio pour préserver le fondu et la visualisation.

## Fenêtres flottantes

Le bouton du mini-lecteur ou **Ctrl+M** masque la fenêtre principale et ouvre une fenêtre Windows indépendante, au-dessus des autres applications. La même session de lecture alimente les fenêtres via IPC. Le bouton de réduction du mini-lecteur ouvre le mode pilule. Le bouton d’agrandissement revient au mini-lecteur ou à la bibliothèque. Faites glisser la surface du flottant pour le déplacer. Les bords proches s’alignent sur l’écran et chaque mode mémorise sa position. Les paramètres permettent de régler l’opacité.

**Espace** lit ou met en pause, les **flèches gauche/droite** déplacent la lecture de cinq secondes, **F** bascule le plein écran, **Échap** revient à la vue principale et **Ctrl+O** importe des fichiers.

## Démonstration et vérification

`npm.cmd run verify` vérifie le lecteur et les échanges entre fenêtres sur des médias de test générés. Définissez `RAKOTO_CAPTURE` pour choisir un dossier de sorties. `node tools/preview.cjs <dossier>` génère les mêmes scènes de présentation dans Edge en mode sans fenêtre. `python tools/presentation.py <dossier>` assemble la planche 4K à partir des captures. La scène du Bureau Windows est reconstituée pour la présentation.


## Lecteur avancé 2.1

Le clic droit dans la vidéo ouvre les menus Audio, Sous-titres, Vidéo, Lecture et Paramètres. Le bouton **Options média** ouvre le même menu dans la fenêtre normale et dans les modes flottants. Le moteur vidéo mpv peut afficher et changer les pistes MKV intégrées pendant la lecture. MediaInfo lit le contenu réel et renseigne la fenêtre **Informations média**, y compris les chapitres quand ils sont présents. La valeur « Non renseigné » indique que l’analyse ne fournit pas le champ demandé.

Les sous-titres externes SRT, ASS, SSA, VTT, SUB et IDX passent par mpv. Les sous-titres VobSub requièrent la paire IDX + SUB associée. Les réglages de police et de couleur concernent les sous-titres textuels. Les styles ASS/SSA d’origine sont conservés par défaut. La normalisation, le retard audio et les réglages de canaux sont disponibles pendant la lecture native. Pour un fichier audio compatible avec les deux moteurs, choisissez **Audio → Moteur audio**. Web Audio conserve le fondu et la visualisation. mpv donne accès à la normalisation, au retard audio et aux réglages de canaux.

En plein écran vidéo, le déplacement du pointeur fait apparaître la barre flottante. Elle se masque après trois secondes d’inactivité avec le pointeur. Un menu ouvert ou une manipulation du curseur suspend ce délai. Double-cliquez sur la vidéo pour basculer le plein écran et utilisez Échap pour en sortir. Les marqueurs de chapitres et le temps survolé sont disponibles. Les miniatures de survol ne sont pas générées. La molette contrôle le volume si l’option est activée dans le menu Paramètres.

Les binaires Windows fournis figurent dans `native` dans les sources et dans `resources\app\native` dans le dossier portable. Le décodage matériel est demandé automatiquement lorsque mpv et le pilote le permettent. La lecture de fichiers locaux ne nécessite aucune connexion.

### Tests fonctionnels

Pour régénérer les fichiers synthétiques, exécutez `node tools/make-media-fixtures.cjs <chemin-vers-ffmpeg.exe> <dossier-des-fixtures>`. Puis exécutez `node tools/verify-native.cjs <dossier-des-fixtures>`. Pour le test Electron sans capture visuelle, définissez `RAKOTO_FIXTURES` vers le même dossier et lancez `npm.cmd run verify:advanced`.



### Correction d’affichage Windows 2.1.1

La vidéo mpv utilise une fenêtre native dédiée avec présentation Direct3D 11 compatible. Si un pilote pose encore problème, ouvrez **Clic droit → Vidéo → Décodage vidéo → Logiciel**. Ce réglage est mémorisé. Le logo de l’application est généré à partir de `logo/logo.png`.

### Correction de la présentation vidéo 2.1.2

Sous Windows, mpv présente désormais l’image dans sa propre fenêtre native. RAKOTO l’aligne sous ses commandes et déplace les deux ensemble en mode normal, plein écran et vidéo flottante.

## Édition organique 2.2

**Betsileo Nature** associe les bleus de RAKOTO à un paysage original de brumes et de terrasses. Les paramètres proposent aussi Océan, Forêt, Minimaliste et Nuit. Le ressort anime le tiroir pendant son déplacement et les éléments de l’en-tête principal. Les réglages **Discrètes** et **Désactivées** réduisent les effets. Les préférences d’accessibilité Windows sont prises en compte.

Réduisez la fenêtre pour afficher le tiroir compact. Cliquez sur la pochette ou sur **Déployer** pour afficher le titre, l’artiste, le volume et la progression. Dans les paramètres, vous pouvez retirer le premier plan ou désactiver le visualiseur. Ses cinq barres proviennent de Web Audio ou d’une branche d’analyse du signal déjà décodé par mpv. En mode vidéo, RAKOTO peut prendre une miniature locale ponctuelle pour la pochette.

Cette version de développement se lance depuis le dossier portable. Pour la reconstruire, exécutez `npm.cmd run package`.

### Corrections et allègement 2.2.2

Le tiroir et la barre de titre se déplacent par capture du pointeur. Le ressort reçoit le mouvement pendant le geste, avec retour au repos au relâchement. Les boutons et curseurs conservent leurs interactions. La double activation de la barre de titre agrandit ou restaure la fenêtre. Les modes Discrètes, Désactivées et la réduction des animations Windows restent respectés.

Les fenêtres flottantes transparentes sont créées à leur taille finale et remplacées seulement après leur premier rendu. L’ancienne fenêtre est détruite après restauration ou déploiement, pour éviter une surface blanche résiduelle sous Windows.

L’analyse réelle utilise désormais MediaInfo WASM à la place du binaire FFprobe dans le portable. Un seul worker, isolé du fil principal, analyse les fichiers par blocs et libère son tas WASM après quinze secondes d’inactivité. mpv, ses codecs et les solutions de rendu logiciel restent fournis. Le portable garde les ressources système françaises et anglaises ; cela ne limite pas les langues audio et sous-titres des médias. Les dépendances de compilation et l’outil CLI MediaInfo sont exclus du paquet.

Les commandes de reconfiguration du décodeur et des sorties disposent d’un délai distinct, plus long, pour éviter un faux échec de bascule matériel/logiciel lorsque la machine est chargée.

Le projet reste une version de développement. Le portable peut être lancé directement ; son archive ZIP doit être extraite entièrement avant lancement. Electron conserve un poids important dans le dossier déployé.

### Surface unique 2.2.3

Le contour décoratif indépendant de la fenêtre principale a été supprimé : il formait une seconde bordure en mouvement sur une fenêtre fixe. La fenêtre principale garde sa géométrie native, avec une petite inertie de son en-tête. Dans le tiroir transparent, le fond, la bordure, la pochette et les commandes appartiennent désormais au même élément animé. Le déplacement est limité à quelques pixels et l’étirement à 1,8 % pour préserver la lisibilité. Le réglage d’opacité agit sur le fond sans atténuer les commandes. Il ne s’agit pas d’une déformation physique du rectangle Windows.

## Installation Windows et site vitrine

Les programmes **Offline** et **Online** sont produits dans `dist/installateurs`. Le premier contient le lecteur complet ; le second télécharge ce même programme et vérifie son empreinte avant de l’ouvrir. Les deux installent le même lecteur. L’assistant propose le dossier, les raccourcis et des options facultatives. Une désinstallation est enregistrée dans Windows. Voir `installer/README.md` pour la construction et les tests.

La vitrine publique TypeScript est dans `site/`. Elle présente le lecteur et les deux téléchargements dès la première section. Le manifeste du service fournit les tailles et empreintes réellement construites. Le stockage des installateurs est séparé du code du site.
