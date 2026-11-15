# 筆 Fude — app Android

Palestra quotidiana di giapponese (Lingua giapponese 2.2 / 3, Ca' Foscari): scrittura dei kanji a mano, letture, kanji simili, forme verbali, grammatica e traduzione.

L'app è una WebView a schermo intero che carica `app/src/main/assets/www/index.html`. I progressi restano sul telefono.

## Ottenere l'APK
1. Carica questa cartella in un repository GitHub (anche privato).
2. Il workflow **Compila APK** parte da solo a ogni push su `main` (o da *Actions → Compila APK → Run workflow*).
3. A fine build apri l'esecuzione e scarica l'artefatto **Fude-apk** (uno zip con dentro `Fude.apk`).
4. Sul telefono apri `Fude.apk` e consenti l'installazione da fonti sconosciute.

## Compilare in locale
Con Android Studio: *Open* sulla cartella, poi *Build → Build APK(s)*.

## Aggiornare i contenuti
Sostituisci `app/src/main/assets/www/index.html` e alza `versionCode` in `app/build.gradle`.

## Play Store
L'APK è firmato con la chiave di debug: va bene per installarlo sui tuoi dispositivi. Per pubblicarlo serve una chiave di firma tua e un bundle `.aab`.
