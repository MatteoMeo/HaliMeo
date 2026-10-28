# Aggiornamenti APK self-hosted

Il server ospita `/v1/updates/latest.json` e APK versionati in `server/data/updates/`. `server/publish_release.py` copia un APK e calcola SHA-256, creando un manifest iniziale.

**Il manifest generato è una bozza, non va pubblicato così com'è.** Prima della produzione:
1. Definire una serializzazione canonica del manifest.
2. Firmare il payload con Ed25519, usando una chiave privata conservata offline.
3. Incorporare nell'app solo la chiave pubblica e verificare la firma prima di fidarsi di URL/versione/hash.
4. Scaricare solo da HTTPS e limitare l'URL all'origine configurata.
5. Verificare SHA-256 e poi avviare l'installer di sistema Android tramite FileProvider.
6. Android mostrerà le conferme richieste; l'app non può garantire un'installazione silenziosa.
7. Mantenere lo stesso applicationId (`it.halimeo.app`) e la stessa chiave Android per tutte le release.

L'endpoint server per manifest/APK è presente. L'interfaccia di aggiornamento nell'app e la verifica crittografica Ed25519 non sono ancora integrate: sono attività obbligatorie prima di distribuire APK ad Alex.
