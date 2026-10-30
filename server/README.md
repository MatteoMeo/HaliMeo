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

## Aggiornare il server dopo le modifiche social

Le modifiche al backend non vengono installate automaticamente dalla build APK. Se l'app registra `GET /v1/social/feed` con risposta **404**, il processo FastAPI in esecuzione sta quasi certamente usando un'immagine/codice precedente oppure il tunnel reverse-proxy punta a un servizio diverso.

Dalla cartella del repository sul PC che ospita il server, aggiorna il branch usato per il deploy e ricrea il container:

```bash
git pull origin feature/ui-ux-refresh
cd server
docker compose up --build -d
docker compose logs --tail=100 -f halimeo-api
```

Verifica poi `/v1/health` e `/docs`: nella documentazione OpenAPI deve comparire `GET /v1/social/feed`. Se la rotta compare su `http://127.0.0.1:8000/docs` ma il dominio pubblico restituisce ancora 404, controlla che il tunnel/reverse-proxy inoltri allo stesso servizio sulla porta 8000. **Non cancellare `server/data`**: contiene il database e gli account privati.

## Prima di usarlo con dati reali
Questa è una base iniziale, non un server già pronto per produzione. Mancano ancora rate limiting, throttling login, migrazioni DB, test di concorrenza, backup cifrati off-site, cursore di sincronizzazione persistente e verifica Ed25519 dei manifest degli aggiornamenti. Il codice attuale assume una sola istanza privata HaliMeo condivisa dai due utenti.
