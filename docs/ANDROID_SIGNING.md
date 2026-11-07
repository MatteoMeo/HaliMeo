# Firma Android permanente di HaliMeo

La firma di tutte le build Alpha e Stabile usa un unico keystore di rilascio permanente.

- Alias: `halimeo-alpha`
- Tipo: JKS
- Impronta certificato SHA-256: `11:6C:6D:81:73:C2:06:96:D4:A0:D1:3D:01:E2:A4:69:AE:E0:68:F2:6D:3E:DE:8A:6D:D7:7E:FE:1C:57:3D`

## GitHub Actions Secrets richiesti

Configurare in **Settings → Secrets and variables → Actions → New repository secret**:

1. `HALIMEO_SIGNING_KEYSTORE_BASE64`: contenuto Base64 completo del file `halimeo-alpha-release.jks`.
2. `HALIMEO_SIGNING_STORE_PASSWORD`: password del keystore.
3. `HALIMEO_SIGNING_KEY_PASSWORD`: password della chiave.

Il workflow controlla l'impronta del certificato prima di compilare e pubblicare. Se i secret mancano o la chiave non corrisponde, la build si interrompe senza generare un certificato sostitutivo.

## Regole

- Non committare mai il keystore, il Base64 o le password.
- Conservare almeno due copie offline del file JKS e della password, in luoghi sicuri e separati.
- Non rigenerare la chiave per le build successive.
- Il passaggio dalla firma Alpha 350 a questa nuova identità richiede una reinstallazione una tantum dell'app su ogni dispositivo. Tutti gli aggiornamenti successivi saranno compatibili con questa firma.
