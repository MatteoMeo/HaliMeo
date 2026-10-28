# HaliMeo — correzione indicatore server

Questa versione corregge l'inizializzazione di ApiClient e l'indicatore Online/Offline.
Il controllo ora si riavvia quando salvi l'indirizzo del server, invece di basarsi sul testo digitato prima del salvataggio.

## Ricompilare
1. Estrai lo ZIP in una cartella nuova.
2. Apri la cartella `HaliMeo` in Android Studio.
3. Attendi la sincronizzazione Gradle.
4. Seleziona **Build > Build APK(s)**.
5. Installa l'APK aggiornato sul telefono.

L'URL salvato nell'app deve essere quello raggiungibile dal telefono. Il controllo usa `GET /v1/health` e si ripete ogni 15 secondi.

## Accesso fuori dalla stessa Wi-Fi
Questa correzione non rende il server pubblico. Per usarlo da reti diverse serve pubblicare il backend su un host raggiungibile via HTTPS, oppure configurare un tunnel sicuro. Non esporre direttamente la porta 8000 su Internet senza protezioni.
