# Moteurs natifs de RAKOTO

- **mpv** est un lecteur distinct piloté par un canal IPC Windows local. Le binaire fourni provient du build Windows x64 Shinchiro du 8 novembre 2026, révision mpv 55d3feb45c. L’archive utilisée s’appelle mpv-x86_64-20261108-git-55d3feb45c.7z et son SHA-256 est 465faafbf90527421ccc304870f02e59f3f47d3361a6f526ef95f40f8f2c2459. [Archive du mainteneur](https://sourceforge.net/projects/mpv-player-windows/files/64bit/) · [Sources mpv](https://github.com/mpv-player/mpv/tree/55d3feb45c) · [Configuration de compilation](https://github.com/shinchiro/mpv-winbuild-cmake)
- **MediaInfo** (mediainfo.js 0.3.8, MediaInfoLib 26.05) analyse le contenu réel des médias dans un worker Node. Son module WASM remplace FFprobe dans la distribution portable depuis la version 2.2.2. La notice BSD est fournie dans `resources/app/node_modules/mediainfo.js/LICENSE.txt`. [Sources du module](https://github.com/buzz/mediainfo.js) · [MediaInfoLib](https://github.com/MediaArea/MediaInfoLib)
- **FFprobe** est conservé uniquement dans les outils de développement pour les comparaisons. Il provient de @ffprobe-installer/win32-x64 5.1.0, build Gyan du 13 février 2023, révision FFmpeg 2296078397. Il n’est plus embarqué dans le portable. [Paquet d’origine](https://www.npmjs.com/package/@ffprobe-installer/win32-x64/v/5.1.0) · [Sources FFmpeg](https://github.com/FFmpeg/FFmpeg/tree/2296078397)

Les projets mpv et FFmpeg publient leurs conditions de licence avec leurs sources. Les fichiers .exe du dossier portable sont les binaires d’origine.

## Licences du paquet local

Le lecteur mpv fourni est le build GPL, avec les notices et les sources de sa révision liées ci-dessus. Le FFprobe de développement annonce GPL version 3 ou ultérieure dans sa sortie `-L`. Les notices des bibliothèques Electron, MediaInfo et npm sont également fournies dans le paquet.

Une diffusion publique du binaire requiert de satisfaire les conditions des licences des builds exacts, notamment la mise à disposition des sources correspondantes et des notices applicables à l’ensemble des bibliothèques compilées. Ce paquet est préparé pour l’installation locale. [Licence et attribution mpv](https://github.com/mpv-player/mpv/blob/55d3feb45c/Copyright) · [Licences FFmpeg](https://github.com/FFmpeg/FFmpeg/blob/2296078397/LICENSE.md)
