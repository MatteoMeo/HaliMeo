# HaliMeo — architettura

## App Android
Kotlin + Jetpack Compose per l'interfaccia principale; Room per il diario locale; DataStore per la sessione; WorkManager per tentare la sincronizzazione quando c'è rete. La missione Monaco è mantenuta come asset HTML locale dentro una WebView, quindi non dipende da GitHub Pages.

## Server privato
FastAPI + SQLite su PC. Docker pubblica la porta solo su `127.0.0.1:8000`; un tunnel HTTPS deve essere configurato separatamente. Gli account si creano solo tramite invito monouso, mai tramite registrazione pubblica.

## Offline-first
Le modifiche locali entrano in Room prima della chiamata di rete. In assenza di connessione restano sul dispositivo. La sincronizzazione attuale è una prima implementazione con `updatedAt`; prima di usarla per ricordi importanti va aggiunto un cursore di sincronizzazione persistente, gestione rigorosa dei conflitti, propagazione verificata delle cancellazioni e test multi-dispositivo.

## Dati e sicurezza
- HTTPS obbligatorio per il traffico esterno.
- Password hashate con bcrypt; token JWT con scadenza.
- Codici invito monouso memorizzati sul server solo come hash.
- Nessuna registrazione pubblica.
- Backup server cifrati e conservati anche fuori dal PC.
- Non inserire segreti, chiavi JWT o chiavi private di firma nel repository.

## Limiti dell'implementazione iniziale
La schermata di accesso e l'API login/invito sono predisposte. L'account proprietario si inizializza localmente tramite `server/bootstrap_owner.py`. La sincronizzazione è una base funzionale, ma non ancora certificata per conflitti o uso con dati unici. Il backup nativo dell'intero DB e il flusso completo di aggiornamento APK con verifica Ed25519 sono da completare prima di una release reale.
