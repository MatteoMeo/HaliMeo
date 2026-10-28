HALIMEO - MODIFICHE

App Android:
- Nella schermata di accesso è stato aggiunto l'indicatore di stato del server.
- Il controllo usa GET /v1/health e si ripete ogni 15 secondi.
- È possibile inserire e salvare l'URL del backend direttamente nell'app.
- Per testare in rete locale usare l'IPv4 del PC, per esempio http://192.168.1.50:8000 (sostituire con il proprio indirizzo).
- PC e telefono devono essere sulla stessa Wi-Fi; Windows Firewall potrebbe dover consentire Python/Uvicorn sulla rete privata.
- Il backend deve restare avviato sul PC.

Backend:
- bootstrap_owner.py ora crea le tabelle mancanti prima di inserire l'account proprietario.

NOTA DI SICUREZZA:
- Questo è un progetto per test locale. L'HTTP in chiaro è abilitato nell'app per consentire il test su LAN; non esporre il server su Internet senza HTTPS.
- Il file server/.env reale non è incluso nell'archivio.

Per ricompilare da Android Studio: aprire la cartella del progetto, attendere la sincronizzazione Gradle e avviare Build > Build APK(s).
