# Validation de la bêta Android 4

## Contrôles exécutés sur Windows

- Compilation de l'APK de développement et du paquet de tests natifs avec JDK 17 / Gradle 8.13 / AGP 8.13.2.
- Analyse statique Android Lint sans erreur bloquante.
- Compilation de l’interface Kotlin native avec les services Java conservés.
- Vérification des trois APK de diffusion : universel ARM, ARM64 seul, ARMv7 seul ; même clé de signature et code de version 4.
- Compilation de diffusion optimisée et signée, vérification par `apksigner` et `zipalign`.
- Inspection des ABI de diffusion : ARM64 et ARMv7, sans les bibliothèques x86 de l'émulateur.
- Vérification des segments ELF ARM64 alignés à 16 Ko, notices LGPL présentes.
- Vérification du manifeste : pas d’autorisation Internet, microphone ou MANAGE_EXTERNAL_STORAGE ; écriture du stockage limitée à Android 6–9 et lecture des images pour le coffre photo.
- Tests hôte **exécutés et passés** sur le code `VaultFile` livré : chiffrement, relecture complète d'un fichier synthétique de plus de 8 Mo, seek inter-blocs, nonces distincts, mauvaise clé, fichier altéré, tronqué et blocs permutés.
- Tests hôte **exécutés et passés** sur `Library.sort` : noms, taille supérieure à 4 Go, durées, dates, ordres inversés et durées inconnues en fin de liste.

Les permissions de lecture des médias sont désormais déclarées pour le scan. Elles ne donnent pas un accès général aux dossiers protégés d'Android.

Reproduire les tests hôte avec JDK 17 et `ANDROID_HOME` :

```powershell
.\tests\run-host.ps1
```

## Contrôles ajoutés dans la bêta 4

- Tests hôte passés de restauration authentifiée exacte, extensions `.rpi`/`.rpv`, export corrompu rejeté et export interrompu.
- Tests hôte passés des noms `fd://`, titres numériques, métadonnées de secours et conservation du titre sélectionné.
- Tests hôte passés de l’aléatoire sans répétition, fin réelle de file, historique précédent/suivant, répétition et file vide.
- Tests de la barrière utilisée par le déplacement : échec de vérification = aucune suppression ; suppression refusée = aucune finalisation ; ordre vérification → retrait → validation.
- `VaultInstrumentationCases` crée uniquement un coffre et une image synthétiques isolés. Elle teste Keystore, PNG chiffré, descripteur privé, source modifiée conservée et export réel en galerie sur Android 10+. Cette suite est compilée, mais son exécution sur appareil reste à faire.
- La suite native vérifie aussi le retour qui arrête réellement la vidéo. Aucun fichier personnel ni coffre utilisateur n’est réinitialisé par ces tests.

## Limite de validation à cette date

Aucun téléphone physique n'est connecté au poste. L'émulateur Android 11 a été installé et plusieurs démarrages sans interface ont été essayés, mais il reste hors ligne sur cette configuration Windows dépourvue d'accélération d'émulation opérationnelle. Aucun pilote système n'a été installé pour contourner cette limite.

**Les tests de décodage et d'interaction Android ci-dessous sont compilés mais n'ont pas été exécutés avec succès sur ce poste.** La lecture, les permissions complètes/partielles, le scan d'une vraie carte SD, les playlists, le Keystore, le proxy du coffre et son verrouillage doivent encore être validés sur un appareil Android réel. Aucun test visuel ni aucune capture n'ont été effectués.

La nouvelle interface Kotlin, le clavier PIN, les images, les consentements de suppression Android 10/11+, le refus de ces consentements, la reprise après interruption et l’export en galerie doivent être exercés sur téléphone. La barre vidéo fixe et son masquage en plein écran sont à vérifier fonctionnellement sans capture. Aucune consommation RAM ni accélération mesurée sur appareil n’est annoncée. La séparation des ABI réduit effectivement les octets téléchargés ; elle ne constitue pas une mesure de fluidité.

## Suite native prête à exécuter

La classe `PlayerInstrumentation` utilise les statistiques réelles de LibVLC, y compris `decodedVideo` et `displayedPictures` : un simple état « lecture » ou la présence de son ne suffisent pas pour valider une vidéo.

Elle couvre MKV simple, MKV à quatre langues et trois sous-titres, MP4 H.264 / HEVC, AVI, WebM, FLAC, MP3, PCM, média sans audio, contenu sans extension, MOV, M4V, 3GP, WMV, FLV, MPEG, TS / M2TS, AAC, Ogg, M4A, Opus et fichier corrompu. Elle vérifie aussi changement de pistes, sous-titre externe, synchronisation, vitesse, égaliseur, plein écran, PiP lorsque disponible et commandes de notification.

Les fichiers synthétiques sont générés avec Node.js et FFmpeg dans `app/src/androidTest/assets/fixtures/` ; ils restent exclus du dépôt et du paquet de diffusion. Le générateur crée uniquement des mires, des tonalités et du texte, sans aucun contenu personnel.

```powershell
node tests/generate-fixtures.cjs C:\chemin\vers\ffmpeg.exe
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.rakotoplayer.android.test/com.rakotoplayer.android.PlayerInstrumentation
```

Ne pas présenter cette suite comme « passée » avant son exécution effective sur un appareil ou émulateur opérationnel.
