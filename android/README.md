# RAKOTO Player Android

Bêta Android **1.0.0-beta.2**, indépendante de l'application Electron Windows. Mise à jour avec la même signature et conservation des données de la première bêta.

## Plateforme et moteur

- Android 6.0 / API 23 minimum ; compilation et cible API 36.
- APK de diffusion ARM64 et ARMv7. Le build de développement ajoute x86_64 pour les tests.
- Java natif et LibVLC **3.7.7** : aucun lecteur WebView ou HTML5.
- Logo RAKOTO fourni par le propriétaire, palette bleue et blanche, touches des Hautes Terres.
- Les bibliothèques ARM64 embarquées ont des segments ELF alignés à 16 Ko.

## Fonctionnalités

Quatre onglets flottants en bas : **Audio, Vidéo, Coffre, Profil**.

- **Audio** : scan de toutes les entrées audio accessibles de MediaStore, y compris enregistrements, notifications et sonneries, sans filtre `IS_MUSIC`, de durée ou de taille. Mémoire partagée et volumes SD indexés ; ajout de dossiers par SAF pour les contenus non indexés. Recherche, playlists persistantes, favoris et reprise de lecture.
- **Vidéo** : dossiers hiérarchiques comme dans un explorateur, vue de tous les fichiers, playlists et favoris. Tri par nom, taille, durée et date d'ajout, dans les deux sens. Les durées non détectées sont indiquées comme inconnues.
- **Coffre** : création et confirmation d'un code à quatre chiffres, copies vidéo chiffrées, import, lecture avec navigation, tri, suppression de la copie privée et changement du code. Les fichiers d'origine restent à leur place et ne sont jamais supprimés automatiquement.
- **Profil** : nom local, permissions, dossiers scannés et réglages du lecteur. Aucun compte en ligne.

Le catalogue utilise SQLite et n'a plus le plafond de 2 000 fichiers. Les fichiers publics sont lus à leur emplacement d'origine. Les scans et traitements du coffre se font hors du thread de l'interface.

Lecture native audio/vidéo : pistes et langues réellement détectées, changement de piste, sous-titres intégrés et externes SRT/ASS/SSA/VTT/SUB/IDX, volume, synchronisation, vitesse, égaliseur natif à dix bandes, répétition et lecture aléatoire. Pour VobSub, sélectionner ensemble les fichiers IDX et SUB de même nom. La compatibilité effective dépend du codec, du fichier et du téléphone.

Plein écran immersif, double toucher pour basculer, commandes masquées après trois secondes et maintenues pendant les interactions. Vidéo Picture-in-Picture sur Android 8+ lorsque l'appareil le permet. Audio en arrière-plan avec MediaSession, commandes de notification, gestion du focus audio et pause lors du débranchement d'un casque.

Le tiroir audio flottant Windows, son visualiseur et ses déformations élastiques ne sont pas inclus. Le mini-lecteur audio Android reste dans l'application ; en dehors, les commandes sont celles de la notification. Aucun faux signal audio n'est animé.

## Confidentialité

Pas d'autorisation Internet, microphone ou `MANAGE_EXTERNAL_STORAGE`. Le scan demande `READ_MEDIA_AUDIO` / `READ_MEDIA_VIDEO` sur Android 13+, ou `READ_EXTERNAL_STORAGE` jusqu'à Android 12. Android 14+ peut limiter l'accès aux seules vidéos sélectionnées ; ce statut est affiché dans Profil. Les dossiers privés d'autres applications ne sont pas contournés. Le sélecteur SAF donne accès aux dossiers ou fichiers choisis.

Le coffre utilise AES-256-GCM par blocs authentifiés de 256 Ko. Sa clé est enveloppée avec une clé dérivée du code par PBKDF2 et une clé Android Keystore non exportable. Noms et métadonnées du catalogue sont également chiffrés. Les essais sont limités avec temporisation persistante après cinq échecs. Le coffre se verrouille en arrière-plan et ferme la lecture privée ; les notifications ne révèlent pas les noms des vidéos. Voir [COFFRE.md](COFFRE.md).

Les sauvegardes et transferts automatiques des données privées sont désactivés. La désinstallation efface le coffre ; conserver les originaux. Les sous-titres externes du lecteur public utilisent le cache privé de l'application.

## Construire

Prérequis : JDK 17, SDK Android 36, Build Tools 35.0.0/36.0.0, accès Maven pour récupérer les dépendances lors de la première compilation. Définir `ANDROID_HOME` ou un `local.properties` privé. Gradle est épinglé à 8.13 et AGP à 8.13.2.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Le build de diffusion nécessite votre clé PKCS12, alias `rakoto`. Fournir ses informations par variables d'environnement, jamais dans les sources :

```powershell
$env:RAKOTO_KEYSTORE_FILE = 'C:\chemin-prive\rakoto-release.p12'
# Fournir RAKOTO_KEYSTORE_PASSWORD dans l'environnement du processus.
.\gradlew.bat :app:assembleRelease :app:lintRelease
```

Sur le poste d'origine, `tools/build-android.ps1` conserve la clé dans `android/signing/` et protège son mot de passe avec DPAPI pour l'utilisateur Windows. **Sauvegarder cette clé et son mot de passe de façon privée** pour pouvoir livrer des mises à jour installables sur la même application. Ils sont exclus du dépôt et de l'APK. Une reconstruction signée par une autre clé demande la désinstallation de l'ancienne application.

## Architecture

- `PlaybackService.java` : unique instance LibVLC, décodage, pistes, égaliseur, audio focus, MediaSession et service de lecture.
- `MainActivity.java` : bibliothèque, surface vidéo, commandes, plein écran et PiP ; connexion au service par Binder privé.
- `MediaEntry.java` / `Library.java` : métadonnées des URI et persistance locale.
- `BrowserScreen.java` : quatre onglets, dossiers, tri, favoris, playlists et profil.
- `MediaScanner.java` : MediaStore et parcours des dossiers autorisés via SAF.
- `Vault.java` / `VaultFile.java` / `VaultProxy.java` : code, clés, copies chiffrées et lecture privée avec seek.
- `Ui.java` : composants, icônes et illustration vectorielle légers.
- `app/src/androidTest/.../PlayerInstrumentation.java` : essais fonctionnels natifs sans capture d'écran.

Les fichiers d'essai synthétiques ne sont pas inclus dans l'APK public. Voir [TESTS.md](TESTS.md) pour les contrôles réellement exécutés et leurs limites.

## Composants et sources

LibVLC est redistribué comme bibliothèque sous LGPL 2.1. Texte de licence et notices dans `app/src/main/assets/`. Les sources exactes des bindings 3.7.7 sont jointes à la release ; les dépôts et instructions de construction des composants natifs sont indiqués dans les notices. La reconstruction et la signature avec une bibliothèque LGPL modifiée sont permises. Le nom et le logo RAKOTO appartiennent à leur titulaire.

RAKOTO est indépendant de l'application VLC de VideoLAN.
