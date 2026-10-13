# Sudoku

Un sudoku pour Android, style « Galet » : grille du jour avec calendrier en pastilles, trophées du mois et de l'année (or, argent, bronze), 4 niveaux, mode crayon, 3 erreurs maximum.

## Installer sur le téléphone

Télécharge la dernière version depuis le téléphone :
https://github.com/Tototo44/sudoku/releases/latest/download/Sudoku.apk

Ouvre le fichier, puis accepte l'installation. Une nouvelle version s'installe par-dessus l'ancienne sans effacer les trophées.

## Comment c'est construit

À chaque modification, GitHub vérifie les règles du jeu (tests), construit l'APK et le publie dans « Releases ».

- `engine/` : fabrication des grilles, règles de la partie, calendrier et trophées (testés dans `app/src/test`).
- Les écrans : accueil, jeu, calendrier, trophées.
- Police : Nunito (licence SIL Open Font License).
