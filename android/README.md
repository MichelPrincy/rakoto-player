# RAKOTO Player Android

Première bêta Android **1.0.0-beta.1**, indépendante de l'application Electron Windows.

## Plateforme et moteur

- Android 6.0 / API 23 minimum ; compilation et cible API 36.
- APK de diffusion ARM64 et ARMv7. Le build de développement ajoute x86_64 pour les tests.
- Java natif et LibVLC **3.7.7** : aucun lecteur WebView ou HTML5.
- Logo RAKOTO fourni par le propriétaire, palette bleue et blanche, touches des Hautes Terres.
- Les bibliothèques ARM64 embarquées ont des segments ELF alignés à 16 Ko.

## Fonctionnalités

Import de fichiers locaux par le sélecteur Android, bibliothèque persistante, recherche, filtres musique/vidéo, favoris et reprise de position. Le fichier est lu depuis son emplacement d'origine ; il n'est pas copié dans la bibliothèque.

Lecture native audio/vidéo : pistes et langues réellement détectées, changement de piste, sous-titres intégrés et externes SRT/ASS/SSA/VTT/SUB/IDX, volume, synchronisation, vitesse, égaliseur natif à dix bandes, répétition et lecture aléatoire. Pour VobSub, sélectionner ensemble les fichiers IDX et SUB de même nom. La compatibilité effective dépend du codec, du fichier et du téléphone.

Plein écran immersif, double toucher pour basculer, commandes masquées après trois secondes et maintenues pendant les interactions. Vidéo Picture-in-Picture sur Android 8+ lorsque l'appareil le permet. Audio en arrière-plan avec MediaSession, commandes de notification, gestion du focus audio et pause lors du débranchement d'un casque.

Le tiroir audio flottant Windows, son visualiseur et ses déformations élastiques ne sont pas inclus dans cette première bêta. Le mini-lecteur audio Android reste dans l'application ; en dehors, les commandes sont celles de la notification. Aucun faux signal audio n'est animé.

## Confidentialité

Pas d'autorisation Internet, microphone ou accès général au stockage. Le sélecteur SAF donne accès uniquement aux fichiers choisis. Bibliothèque, favoris et positions sont conservés dans les préférences privées de l'application. Les sous-titres externes sont copiés dans son cache pour permettre leur ouverture par LibVLC. La désinstallation efface ces données privées et conserve les fichiers médias d'origine.

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
- `Ui.java` : composants, icônes et illustration vectorielle légers.
- `app/src/androidTest/.../PlayerInstrumentation.java` : essais fonctionnels natifs sans capture d'écran.

Les fichiers d'essai synthétiques ne sont pas inclus dans l'APK public. Voir [TESTS.md](TESTS.md) pour les contrôles réellement exécutés et leurs limites.

## Composants et sources

LibVLC est redistribué comme bibliothèque sous LGPL 2.1. Texte de licence et notices dans `app/src/main/assets/`. Les sources exactes des bindings 3.7.7 sont jointes à la release ; les dépôts et instructions de construction des composants natifs sont indiqués dans les notices. La reconstruction et la signature avec une bibliothèque LGPL modifiée sont permises. Le nom et le logo RAKOTO appartiennent à leur titulaire.

RAKOTO est indépendant de l'application VLC de VideoLAN.
