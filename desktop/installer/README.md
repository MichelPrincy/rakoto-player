# Installateurs RAKOTO Player

Les deux installateurs utilisent NSIS et le même programme complet Windows x64.

- **Offline** contient l’application, mpv et tous ses composants. Aucun téléchargement à l’installation.
- **Online** contient le petit assistant NSIS, le téléchargement HTTPS INetC et un vérificateur SHA-256 .NET. Il télécharge le programme Offline exact de cette version, vérifie son empreinte puis l’exécute. Il faut donc télécharger la totalité de l’application pendant l’installation. Le lecteur installé est identique.

L’assistant propose le dossier, un raccourci Bureau et le menu Démarrer. Le démarrage automatique et les entrées « Ouvrir avec » sont facultatifs et désactivés par défaut. Les associations par défaut protégées par Windows ne sont pas modifiées. L’installation se fait pour le compte courant, sans demande de droits administrateur.

La désinstallation supprime uniquement les fichiers fournis par le paquet, ses raccourcis et ses clés de registre. Elle conserve les médias, la bibliothèque utilisateur et les fichiers ajoutés par l’utilisateur dans le dossier d’installation.

## Construire

`npm run package`, puis `npm run package:installers`.

Le script réutilise le cache NSIS 3.0.4.1, les plugins nsis-resources 3.4.1 et rcedit 2.6.0 déjà disponibles dans le cache electron-builder local. `NSIS_HOME` peut indiquer un autre dossier NSIS. Le compilateur .NET Framework v4 de Windows et Python/Pillow préparent les ressources. Ces outils ne sont pas embarqués dans le lecteur.

Les URL sont configurées dans `installer/release.json`. Les artefacts, tailles et empreintes réelles sont dans `dist/installateurs/release.json`. `--online-only` régénère le petit assistant à partir du Offline déjà construit et vérifié ; une modification du programme complet exige une construction complète.

## Vérifier

`installer/test-install.ps1 -Mode offline`, puis `-Mode online` après publication. Les tests installent dans un sous-dossier isolé du workspace et utilisent des clés HKCU dédiées `.Test`. Ils vérifient les fonctions du lecteur installé puis la désinstallation et la préservation d’un fichier personnel.

`node tools/verify-web-installer.cjs` vérifie le rejet d’un téléchargement corrompu et d’un HTTP 404, sans exécuter de programme téléchargé.

Les installateurs de cette version de développement ne sont pas signés par un certificat de signature de code. Le contrôle SHA-256 du bootstrap protège l’intégrité du programme complet attendu ; il ne constitue pas une signature d’éditeur Windows.

## Publier les téléchargements

Publier d’abord `site/` avec son binding R2 et son secret serveur `RELEASE_UPLOAD_KEY`. `node tools/publish-release.cjs` attend les paramètres `origin` et `token` sur stdin masqué. Il envoie les fichiers par blocs, puis publie le manifeste en dernier. Aucun secret ne doit être ajouté à un fichier source, une URL ou une commande shell.
