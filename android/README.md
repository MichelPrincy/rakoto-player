# RAKOTO Player Android

Bêta Android **1.0.0-beta.4**, indépendante de l'application Electron Windows. Mise à jour avec la même signature et conservation des données de la première bêta.

## Plateforme et moteur

- Android 6.0 / API 23 minimum ; compilation et cible API 36.
- APK de diffusion ARM64 et ARMv7. Le build de développement ajoute x86_64 pour les tests.
- Interface Kotlin 2.3.21 avec les vues Android natives ; services Java existants et LibVLC **3.7.7** conservés. Aucun WebView, Compose, Flutter ni runtime JavaScript.
- Logo vectoriel extrait de la maquette, identité Lamba de la maquette : crème, terre rouge, or et vert. Thèmes clair, sombre et système.
- Les bibliothèques ARM64 embarquées ont des segments ELF alignés à 16 Ko.

## Fonctionnalités

Quatre onglets en bas : **Musique, Vidéos, Coffre, Réglages**.

- **Audio** : scan de toutes les entrées audio accessibles de MediaStore, y compris enregistrements, notifications et sonneries, sans filtre `IS_MUSIC`, de durée ou de taille par défaut. Un filtre de durée facultatif masque des éléments sans supprimer le catalogue. Mémoire partagée et volumes SD indexés ; ajout de dossiers par SAF pour les contenus non indexés. Recherche, albums, artistes, dossiers, playlists persistantes, favoris et reprise de lecture. Lecteur musical natif complet et file de lecture.
- **Vidéo** : liste ou grille, dossiers hiérarchiques comme dans un explorateur, vue de tous les fichiers, playlists et favoris. Tri par nom, taille, durée et date d'ajout, dans les deux sens. Les durées non détectées sont indiquées comme inconnues.
- **Coffre** : clavier PIN confirmé, vidéos `.rpv` et images `.rpi` chiffrées. Import multiple, filtres, tri, image privée avec zoom/EXIF et vidéo native. Le déplacement retire les originaux uniquement après écriture durable, authentification complète et comparaison SHA-256 ; refus ou accès insuffisant restent « À terminer ». Restauration dans la galerie avec le nom et l’extension d’origine, puis retrait du coffre. Les anciennes copies peuvent être associées à leur original avec preuve d’identité des octets.
- **Réglages** : nom du profil local, permissions, dossiers scannés, durée minimale facultative, thèmes, lecture en arrière-plan et vitesse vidéo par défaut. Aucun compte en ligne.

Le catalogue utilise SQLite et n'a plus le plafond de 2 000 fichiers. Les fichiers publics sont lus à leur emplacement d'origine. Les scans et traitements du coffre se font hors du thread de l'interface.

Lecture native audio/vidéo : pistes et langues réellement détectées, changement de piste, sous-titres intégrés et externes SRT/ASS/SSA/VTT/SUB/IDX, volume, synchronisation, vitesse, égaliseur natif à dix bandes, répétition et lecture aléatoire. Pour VobSub, sélectionner ensemble les fichiers IDX et SUB de même nom. La compatibilité effective dépend du codec, du fichier et du téléphone.

Barre vidéo fixe et compacte sous l’image ; plein écran immersif, double toucher pour basculer, commandes masquées après trois secondes en plein écran et maintenues pendant les interactions. Retour arrête la vidéo, y compris depuis le plein écran, sans arrêter la musique quand on quitte son écran de lecture. Les titres ne sont jamais remplacés par une adresse `fd://`. Vidéo Picture-in-Picture sur Android 8+ lorsque l'appareil le permet. Audio en arrière-plan avec MediaSession, commandes de notification, gestion du focus audio et pause lors du débranchement d'un casque.

Le tiroir audio flottant Windows, son visualiseur et ses déformations élastiques ne sont pas inclus. Le mini-lecteur audio Android reste dans l'application ; en dehors, les commandes sont celles de la notification. Aucun faux signal audio n'est animé.

## Confidentialité

Pas d'autorisation Internet, microphone ou `MANAGE_EXTERNAL_STORAGE`. `READ_MEDIA_IMAGES` est ajouté pour le coffre photo ; `WRITE_EXTERNAL_STORAGE` est limité à Android 6–9 pour les déplacements et exports sur ces versions. Le scan demande `READ_MEDIA_AUDIO` / `READ_MEDIA_VIDEO` sur Android 13+, ou `READ_EXTERNAL_STORAGE` jusqu'à Android 12. Android 14+ peut limiter l'accès aux seules vidéos sélectionnées ; ce statut est affiché dans Réglages. Les dossiers privés d'autres applications ne sont pas contournés. Le sélecteur SAF donne accès aux dossiers ou fichiers choisis.

Le coffre utilise AES-256-GCM par blocs authentifiés de 256 Ko. Sa clé est enveloppée avec une clé dérivée du code par PBKDF2 et une clé Android Keystore non exportable. Noms et métadonnées du catalogue sont également chiffrés. Les essais sont limités avec temporisation persistante après cinq échecs. Le coffre se verrouille en arrière-plan et ferme la lecture privée ; les notifications ne révèlent pas les noms des vidéos. Voir [COFFRE.md](COFFRE.md).

Les sauvegardes et transferts automatiques des données privées sont désactivés. La désinstallation efface le coffre ; restaurer ses fichiers avant de désinstaller. Les sous-titres externes du lecteur public utilisent le cache privé de l'application.

## Construire

Prérequis : JDK 17, SDK Android 36, Build Tools 35.0.0/36.0.0, accès Maven pour récupérer les dépendances lors de la première compilation. Définir `ANDROID_HOME` ou un `local.properties` privé. Gradle est épinglé à 8.13 et AGP à 8.13.2.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Le build de diffusion nécessite votre clé PKCS12, alias `rakoto`. Fournir ses informations par variables d'environnement, jamais dans les sources :

```powershell
$env:RAKOTO_KEYSTORE_FILE = 'C:\chemin-prive\rakoto-release.p12'
# Fournir RAKOTO_KEYSTORE_PASSWORD dans l'environnement du processus.
.\gradlew.bat :app:assembleRelease :app:lintRelease -PsplitApks
```

Sur le poste d'origine, `tools/build-android.ps1` conserve la clé dans `android/signing/` et protège son mot de passe avec DPAPI pour l'utilisateur Windows. **Sauvegarder cette clé et son mot de passe de façon privée** pour pouvoir livrer des mises à jour installables sur la même application. Ils sont exclus du dépôt et de l'APK. Une reconstruction signée par une autre clé demande la désinstallation de l'ancienne application.

## Allègement

Le moteur LibVLC est créé au premier besoin de lecture, ou pour rejoindre un service déjà actif. Les pochettes et miniatures sont chargées à la demande par deux tâches au maximum, avec une file bornée et un cache de 2 Mio sur les appareils low-RAM, 8 Mio autrement. Les listes réutilisent leurs lignes. Aucun faux visualiseur ne tourne en boucle. La timeline est inactive sans média et arrêtée en arrière-plan.

La diffusion produit trois APK signés de même version : ARM64 seul, ARMv7 seul et universel ARM32+64. Les architectures x86 de développement ne sont pas incluses dans les APK publics. La réduction de taille vient du choix d’architecture, pas d’un changement des codecs. Mesurer la RAM et la fluidité sur les téléphones cibles avant de les annoncer comme validées.

Tailles signées de la bêta 4 :

- universal : 44,94 Mo (44936153 octets).
- armeabi-v7a : 20,79 Mo (20791332 octets).
- arm64-v8a : 24,49 Mo (24485527 octets).

## Architecture

- `PlaybackService.java` : unique instance LibVLC, décodage, pistes, égaliseur, audio focus, MediaSession et service de lecture.
- `MainActivity.java` : bibliothèque, surface vidéo, commandes, plein écran et PiP ; connexion au service par Binder privé.
- `MediaEntry.java` / `Library.java` : métadonnées des URI et persistance locale.
- `BrowserScreen.java` : quatre onglets, dossiers, tri, favoris, playlists et profil.
- `MediaScanner.java` : MediaStore et parcours des dossiers autorisés via SAF.
- `Vault.java` / `VaultFile.java` / `VaultProxy.java` : code, clés, catalogue, chiffrement authentifié, vérification des sources et lecture privée.
- `VaultTransfers.java` / `VerifiedRemoval.java` : consentements Android, suppression après preuve, journal des opérations et reprise des déplacements.
- `VaultGallery.java` / `PrivateImageViewer.java` / `VaultCandidates.java` : restauration réelle en galerie, image privée et sélection image/vidéo.
- `MediaNames.java` / `ShuffleCycle.java` : titres stables et aléatoire sans répétition.
- `StartupSplash.kt` : ouverture native avec le logo vectoriel exact de la maquette.
- `GasySkin.kt` / `GasyTheme.kt` : écrans Kotlin natifs, navigation, menus, thèmes et clavier du coffre.
- `MusicPlayerView.kt` : lecteur audio natif raccordé au même service.
- `MediaArtLoader.kt` : miniatures réelles à la demande et cache borné.
- `Ui.java` : icônes vectorielles et composants partagés.
- `app/src/androidTest/.../PlayerInstrumentation.java` : essais fonctionnels natifs sans capture d'écran.

Les fichiers d'essai synthétiques ne sont pas inclus dans l'APK public. Voir [TESTS.md](TESTS.md) pour les contrôles réellement exécutés et leurs limites.

## Composants et sources

LibVLC est redistribué comme bibliothèque sous LGPL 2.1. Texte de licence et notices dans `app/src/main/assets/`. Les sources exactes des bindings 3.7.7 sont jointes à la release ; les dépôts et instructions de construction des composants natifs sont indiqués dans les notices. La reconstruction et la signature avec une bibliothèque LGPL modifiée sont permises. Le nom et le logo RAKOTO appartiennent à leur titulaire.

RAKOTO est indépendant de l'application VLC de VideoLAN.
