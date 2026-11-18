# 筆 Fude — app Android

Palestra quotidiana di giapponese (Lingua giapponese 2.2 / 3, Ca' Foscari): scrittura dei kanji a mano, letture, kanji simili, forme verbali, grammatica e traduzione.

L'app è una WebView a schermo intero che carica `app/src/main/assets/www/index.html`. I progressi restano sul telefono.

## Scaricare l'app
Link fisso all'ultima versione (repository pubblico):
https://github.com/ziliofrancesco2-cloud/fude-app/releases/latest/download/Fude.apk

Ogni nuova versione si installa sopra la precedente senza perdere i dati, perché l'APK è sempre firmato con la stessa chiave (segreti `FUDE_KEYSTORE_B64` e `FUDE_KS_PASS` del repository). L'app controlla da sola se c'è una versione nuova.

## Ottenere l'APK
1. Carica questa cartella in un repository GitHub (anche privato).
2. Il workflow **Compila APK** parte da solo a ogni push su `main` (o da *Actions → Compila APK → Run workflow*).
3. A fine build apri l'esecuzione e scarica l'artefatto **Fude-apk** (uno zip con dentro `Fude.apk`).
4. Sul telefono apri `Fude.apk` e consenti l'installazione da fonti sconosciute.

## Compilare in locale
Con Android Studio: *Open* sulla cartella, poi *Build → Build APK(s)*.

## Aggiornare i contenuti
Sostituisci `app/src/main/assets/www/index.html` e alza `versionCode` in `app/build.gradle`.

## Funzioni native
- Promemoria giornaliero (notifica) se l'obiettivo del giorno non è raggiunto.
- Sintesi vocale giapponese per il dettato e gli esempi audio.
- Salvataggio cloud con account email (Supabase, progetto `fude`).
