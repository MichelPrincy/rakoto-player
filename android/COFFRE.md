# Coffre vidéo et photo — bêta 4

Le coffre utilise un code PIN confirmé à quatre chiffres et un chiffrement réel. Les vidéos sont stockées en `.rpv`, les images en `.rpi`, sous des identifiants UUID dans le dossier privé de RAKOTO. Le contenu chiffré n'est pas indexé par la galerie. Renommer un fichier seul ne constitue pas ce chiffrement.

## Déplacement vers le coffre

1. Chiffrer les octets du fichier choisi dans un partiel privé, par blocs de 256 Ko. Calculer le SHA-256 pendant cette lecture.
2. Relire et authentifier tous les blocs. Synchroniser les données sur disque, enregistrer le fichier final et le catalogue chiffré, puis synchroniser le répertoire.
3. Avant le retrait de l'original, authentifier à nouveau le fichier privé et vérifier que la source a toujours exactement la taille et le SHA-256 attendus.
4. Retirer l'original avec le fournisseur Android autorisé. Android 11+ utilise le consentement MediaStore lorsque la suppression directe est refusée ; Android 10 utilise la demande récupérable ; Android 6–9 peut demander l'écriture du stockage partagé. Les documents SAF nécessitent un fournisseur qui autorise leur suppression.
5. Marquer le transfert terminé après confirmation du retrait. Si le consentement est refusé ou si le fichier change, le contenu privé reste protégé et le transfert est indiqué « À terminer ». L'action « Terminer le déplacement » permet de reprendre.

Les URI et empreintes de sources sont dans le catalogue chiffré. Le journal privé conserve des identifiants opaques de reprise. Les restrictions des fournisseurs Android ne sont pas contournées. Une réimportation peut conserver une ancienne version privée si la source a changé.

## Anciennes copies

Les fichiers `.vlt` des bêtas précédentes restent compatibles et sont renommés vers `.rpv` / `.rpi` à l'ouverture du coffre. Ils ne contiennent pas d'association fiable à l'ancien original. « Associer et retirer l'original » fait choisir ce fichier et compare les octets aux données authentifiées du coffre avant d'autoriser son déplacement. Une simple égalité de nom ne suffit pas.

## Restauration dans la galerie

« Restaurer dans la galerie » authentifie et déchiffre le fichier, restitue son nom et son extension d'origine dans `Pictures/RAKOTO Player` ou `Movies/RAKOTO Player`, puis retire l'entrée du coffre après réussite. Les conflits de noms ne doivent pas écraser un fichier existant.

Android 10+ : la nouvelle entrée MediaStore reste `IS_PENDING` jusqu'à écriture complète et synchronisation. Un échec supprime la sortie incomplète. Le journal permet le nettoyage des exports interrompus au lancement suivant.

Android 6–9 : écriture d'un temporaire caché, synchronisation puis publication et indexation par le scanner Android. Une autorisation de stockage peut être nécessaire. Les temporaires identifiés et bornés au dossier RAKOTO sont nettoyés au démarrage suivant.

Le verrouillage ou l'interruption d'un export empêche sa finalisation. Un échec conserve le contenu privé. Une interruption après publication peut laisser à la fois le fichier public complet et sa copie privée, afin de préserver les données.

## Clés, lecture et verrouillage

Clé principale aléatoire de 256 bits, enveloppée par PBKDF2 avec sel aléatoire et 600 000 itérations puis par une clé AES du Keystore Android. SHA-256 pour PBKDF2 sur Android 8+, SHA-1 sur Android 6/7. Catalogue AES-GCM via AtomicFile ; sous-clé par fichier, nonces et données associées authentifiant les blocs et leurs positions.

Android 8+ lit les blocs déchiffrés à la demande via un descripteur proxy. Android 6/7 utilise un temporaire privé retiré de son répertoire avant l'écriture en clair ; cette compatibilité requiert un espace temporaire équivalent au média. Les images sont décodées depuis le descripteur avec une taille bornée, orientation EXIF et zoom. Les formats dépendent du décodeur Android ; une image non décodable peut toujours être restaurée.

Le coffre se verrouille à la sortie de l'application, ferme ses fenêtres et arrête la vidéo privée. Les sélecteurs et consentements système disposent d'un délai limité. Les fenêtres privées utilisent `FLAG_SECURE`. Notification et MediaSession conservent un titre générique.

Les tentatives de PIN sont limitées avec temporisation persistante. Il n'existe pas de récupération du code. **La désinstallation efface le coffre : restaurer ses fichiers avant de désinstaller.** Une mise à jour signée par la même clé RAKOTO conserve les données.

## Validation

Les tests hôte exécutent le lecteur chiffré livré : lecture, seek, export, altération, troncature, permutation de blocs, interruption et barrière de suppression. La suite native synthétique est compilée ; les consentements MediaStore, le Keystore, les images et les reprises après interruption restent à exercer sur un appareil réel. Voir `TESTS.md`. Aucun test visuel ni aucune capture n'ont été effectués.
