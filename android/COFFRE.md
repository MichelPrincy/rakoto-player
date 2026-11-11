# Coffre vidéo — bêta 2

L'utilisateur crée et confirme un code à quatre chiffres au premier usage. L'import **copie** les vidéos dans l'espace privé de l'application et les chiffre. Il ne supprime pas les originaux : ceux-ci restent donc visibles dans leurs dossiers habituels. Cette différence est indiquée avant l'import et dans Profil.

## Stockage

- Clé principale aléatoire de 256 bits, enveloppée par le code (PBKDF2, sel aléatoire, 600 000 itérations) puis par une clé AES du Keystore Android. SHA-256 pour PBKDF2 sur Android 8+, SHA-1 pour la compatibilité Android 6/7.
- Catalogue chiffré AES-GCM, écrit avec AtomicFile. Les noms et durées ne sont pas sauvegardés en clair dans la bibliothèque publique.
- Vidéos stockées sous des identifiants UUID. Chiffrement AES-GCM par blocs de 256 Ko avec sous-clé par fichier, nonces uniques par bloc et données associées authentifiant l'en-tête et la position.
- Import par fichier partiel, synchronisation sur disque, vérification de tous les blocs, puis ajout au catalogue. Les erreurs ne suppriment pas le fichier d'origine.

## Lecture et verrouillage

Android 8+ : un descripteur de fichier proxy fournit à LibVLC les blocs déchiffrés à la demande. Le seek fonctionne sans exporter de vidéo en clair. Le tampon déchiffré est limité à un bloc en cache par lecteur.

Android 6/7 : un fichier temporaire privé est ouvert puis retiré de son répertoire **avant** l'écriture des données déchiffrées. Son espace est libéré à la fermeture du dernier descripteur. Cette compatibilité demande de l'espace temporaire équivalent à la vidéo.

Le passage en arrière-plan verrouille le coffre et arrête le lecteur privé. Le sélecteur de fichiers système dispose d'une courte période pour terminer une sélection d'import. Les fenêtres privées utilisent FLAG_SECURE ; leurs menus sont fermés au verrouillage. La notification et MediaSession affichent un titre générique.

Après cinq codes erronés, un délai persistant et progressif limite les tentatives. Le code se change depuis Profil après déverrouillage. Il n'existe pas de récupération du code dans cette version. La désinstallation efface le coffre et la clé associée ; les mises à jour signées avec la même clé RAKOTO conservent les données.

## Validation

Les tests hôte exécutent le code `VaultFile` livré : lecture complète, seeks traversant les blocs, mauvaise clé, modification des données, troncature et permutation de blocs. Voir `tests/VaultFileTest.java` et `TESTS.md`.

Le fonctionnement complet du Keystore, des permissions, du proxy natif et du verrouillage sur téléphone reste à valider. Ce document décrit l'implémentation ; il ne constitue pas un audit de sécurité indépendant.
