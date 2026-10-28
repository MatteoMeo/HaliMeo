# HaliMeo — Android + server privato

Progetto sorgente iniziale per HaliMeo: app Kotlin/Jetpack Compose, diario locale Room, accesso con account individuali creati tramite invito, API FastAPI su PC e missione Monaco v6.1 inclusa come HTML offline.

## Struttura
- `app/`: progetto Android Studio.
- `app/src/main/assets/missione-monaco.html`: esperienza originale, foto inclusa.
- `server/`: API FastAPI + SQLite, Docker Compose, bootstrap owner, inviti e hosting release APK.
- `docs/`: architettura, flusso update e checklist.

## Build Android
1. Apri questa cartella in Android Studio con JDK 17.
2. In `gradle.properties` aggiungi `HALIMEO_API_BASE_URL=https://il-tuo-host.example/` (slash finale consigliato).
3. Sincronizza Gradle e seleziona Build > Build APK(s).

## Server
Vedi `server/README.md`. Il server si lega a localhost; configura un tunnel HTTPS verso `127.0.0.1:8000`. Non aprire direttamente la porta del router.

## Importante
Questo è codice sorgente iniziale, non un APK compilato e collaudato. In questo ambiente non è stata eseguita una build Android né un test su dispositivi fisici. L'updater è predisposto lato server ma l'installazione automatica controllata dall'app e la verifica Ed25519 devono essere completate prima di distribuire release reali. La sincronizzazione richiede ulteriori test di conflitto e cursore persistente prima di usarla come unica copia dei ricordi.
