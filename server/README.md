# HaliMeo Server

Backend privato HaliMeo basato su **FastAPI + Uvicorn**, esposto all'esterno tramite **Cloudflare Tunnel**. Il processo API viene eseguito direttamente sull'host: non è necessario Docker.

## Avvio e aggiornamento manuale (Windows / PowerShell)

Apri PowerShell nella cartella del repository sul PC che ospita il server:

```powershell
git pull origin feature/ui-ux-refresh
cd server
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Se il processo FastAPI è già in esecuzione, prima arrestalo nella sua finestra con `Ctrl+C`, poi riavvialo con i comandi sopra. Non avviare una seconda istanza sulla stessa porta. Mantieni la finestra/processo attivo: Cloudflare Tunnel deve continuare a inoltrare al servizio locale `http://127.0.0.1:8000` (o alla porta effettivamente configurata nel tunnel).

Se l'ambiente virtuale ha un nome/percorso diverso da `.venv`, attiva quello già usato per il server; non crearne uno nuovo senza motivo. Non cancellare la cartella dati o il database.

## Verifica del routing

Dopo il riavvio controlla prima sul PC:

- `http://127.0.0.1:8000/v1/health` deve restituire `{"status":"ok", ...}`.
- `http://127.0.0.1:8000/docs` deve mostrare **GET /v1/social/feed**.

Poi ripeti la verifica attraverso il dominio pubblico Cloudflare:

- `https://TUO-DOMINIO/v1/health`
- `https://TUO-DOMINIO/docs`

Interpretazione: se la rotta è presente su localhost ma il dominio pubblico restituisce 404, controlla il tunnel Cloudflare e assicurati che punti al processo/porta corretti. Se anche localhost non mostra la rotta, il processo sta caricando una copia vecchia o un modulo Python diverso: verifica la cartella di lavoro e il percorso del repository da cui avvii Uvicorn.

Il feed richiede autenticazione: una richiesta senza token può restituire **401**, che indica che la rotta esiste; un **404** sul percorso esatto indica invece che la richiesta non sta raggiungendo questa route FastAPI (oppure che il path è alterato dal proxy).

## Inviti e account

Non esiste un endpoint di registrazione pubblica. Il primo account proprietario viene creato con `bootstrap_owner.py`; il proprietario autenticato può creare inviti monouso tramite `POST /v1/admin/invites`. Non esporre endpoint di bootstrap pubblici.

## Dati e sicurezza

Il backend usa SQLite e conserva foto e database nella directory indicata da `HALIMEO_DATA_DIR` (il valore predefinito nel codice è `/data`). Prima di cambiare tale variabile o spostare il server, verifica il percorso effettivo dei dati e fai un backup. **Non cancellare il database**: contiene gli account e i ricordi condivisi.

Questa è una base privata in evoluzione, non un servizio pubblico multi-tenant. Prima di un uso più ampio servono rate limiting, throttling login, backup cifrati off-site e ulteriori test di concorrenza.
