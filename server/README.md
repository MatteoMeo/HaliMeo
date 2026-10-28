# HaliMeo Server

## Avvio locale
1. Installa Docker Desktop o Docker Engine.
2. Copia `.env.example` in `.env`.
3. Imposta `HALIMEO_JWT_SECRET` con un segreto casuale lungo almeno 32 caratteri.
4. Esegui `docker compose up --build -d`.
5. Verifica `http://127.0.0.1:8000/v1/health`.

Il servizio ascolta solo su localhost: usa un tunnel HTTPS per l'accesso esterno. Non esporre direttamente la porta 8000.

## Inviti e account
Non esiste un endpoint di registrazione pubblica. Il primo account proprietario va creato con una procedura amministrativa locale, poi si configura `HALIMEO_OWNER_USER_ID`. Il proprietario autenticato può creare inviti monouso tramite `POST /v1/admin/invites`. Non lasciare endpoint di bootstrap pubblici.

## Prima di usarlo con dati reali
Questa è una base iniziale, non un server già pronto per produzione. Mancano ancora rate limiting, throttling login, migrazioni DB, test di concorrenza, backup cifrati off-site, cursore di sincronizzazione persistente e verifica Ed25519 dei manifest degli aggiornamenti. Il codice attuale assume una sola istanza privata HaliMeo condivisa dai due utenti.
