# Checklist prima di usare HaliMeo con ricordi reali

- [ ] Compilare debug APK in Android Studio con JDK 17.
- [ ] Impostare l'URL HTTPS reale in `HALIMEO_API_BASE_URL`.
- [ ] Avviare il server, creare owner con `bootstrap_owner.py`, impostare `HALIMEO_OWNER_USER_ID`.
- [ ] Creare e riscattare un invito monouso; testare scadenza e riutilizzo.
- [ ] Aggiungere rate limiting e protezione dai tentativi di login prima dell'esposizione pubblica.
- [ ] Provare due dispositivi: creazione, modifica, cancellazione, offline, riconnessione e conflitti.
- [ ] Implementare cursore sync persistente e risoluzione conflitti verificata.
- [ ] Implementare esportazione/ripristino nativi del diario locale.
- [ ] Implementare verifica Ed25519 del manifest, download e verifica APK, installer Android.
- [ ] Configurare tunnel HTTPS e testare accesso da rete mobile esterna.
- [ ] Configurare backup cifrati, copia off-device e prova di ripristino.
- [ ] Conservare chiave di firma Android e chiave privata update offline con backup.
- [ ] Testare su telefoni reali e controllare privacy, cancellazione account e foto.
