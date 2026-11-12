# Référencement et langues

La page de présentation reste unique : https://michelprincy.github.io/rakoto-player/.
Le HTML initial est complet en français, avec un seul H1, titres H2/H3,
description, canonique, Open Graph, Twitter Card et données SoftwareApplication.
Les versions et téléchargements du balisage sont issus de public/release.json.
Aucun avis, nombre d'utilisateurs ou score n'est inventé. Le balisage décrit
le logiciel ; il ne garantit pas un résultat enrichi dans Google.

Le français cible naturellement lecteur audio et vidéo Windows, lecteur
multimédia gratuit, MKV/MP4, lecture hors ligne et alternative à VLC.
Les informations techniques restent précises : Windows 10/11 x64,
Android ARM 6.0+ en bêta ; iOS annoncé, sans lien App Store.

Le sélecteur FR/MG traduit le contenu, les textes accessibles, le titre et les
métadonnées. Le choix est conservé localement et partageable avec ?lang=mg.
La canonique reste l'URL française car GitHub Pages renvoie le même HTML
initial pour les paramètres de requête. Aucun hreflang trompeur n'est ajouté.
La langue du navigateur n'impose pas une redirection. Le malagasy utilise
volontairement des termes techniques courants et un registre oral.

Pour la recherche anglophone, les intentions à préparer sont Windows media
player, free video player for Windows 11, offline MKV player et VLC alternative.
Une future version anglaise doit traduire tout le même composant de page,
avec HTML initial localisé, title/description anglais et hreflang réciproques.
Elle ne doit pas être annoncée comme disponible avant cette implémentation.
Les métadonnées françaises et malagasy actuelles ne sont pas mélangées avec
des listes de mots-clés anglais.

src/sitemap.xml et src/robots.txt sont recopiés à chaque compilation.
Sur GitHub Pages, le robots.txt de ce projet est sous /rakoto-player/ ; seuls
les propriétaires de la racine du domaine contrôlent son robots.txt effectif.
La page n'ajoute aucun noindex et peut être découverte via son sitemap et
les liens publics. La soumission du sitemap et une demande d'indexation se
font dans Search Console avec le compte du propriétaire. Le déploiement
du site ne remplace pas ces opérations ni une garantie de positionnement.
