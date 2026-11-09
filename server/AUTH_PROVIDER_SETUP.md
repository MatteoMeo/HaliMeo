# HaliMeo authentication provider setup

The password + invite registration flow remains available. Social sign-in is implemented server-side at `POST /v1/auth/social/{provider}`; the Android API contract is exposed as `HaliMeoApi.socialLogin`.

## Required server configuration

Set these values in the server's private `.env` (never commit secrets):

- `HALIMEO_GOOGLE_CLIENT_ID`: `609968712989-909cckera569ft2g5ivrp79obp7ij4hn.apps.googleusercontent.com` (OAuth **Web application** client ID; also configured in the Android build).
- `HALIMEO_FACEBOOK_APP_ID`: `2135436027084791` (also configured in the Android build).
- `HALIMEO_FACEBOOK_APP_SECRET`: Facebook app secret. Server-side only; never put it in the APK.

Restart the API after changing environment variables. Google verification uses Google's ID-token verifier and checks issuer, audience and `email_verified`. Facebook verification checks the token with Graph API's `debug_token`, checks that it belongs to the configured app, then retrieves the provider account's ID, name and email.

## API contract

Send JSON to `POST /v1/auth/social/google` or `POST /v1/auth/social/facebook`:

```json
{
  "providerToken": "<ID token for Google, access token for Facebook>",
  "inviteCode": "<required only when creating a new HaliMeo account>",
  "displayName": "<optional display name for a new account>"
}
```

A previously linked provider identity can sign in without another invite. A new identity must redeem an unused, unexpired invite. If the email already belongs to a password account, the server deliberately refuses automatic linking; this avoids account takeover through email collisions. An existing password account can explicitly link a provider after signing in with its password by calling `POST /v1/auth/social/{provider}/link`; the provider's verified email must match the account email. An identity already linked to a different HaliMeo account is rejected.

## Android client status

Google sign-in is now wired with Android Credential Manager and Google ID tokens. The OAuth Web client ID above is configured in the app build. New-account Google registration still requires an invite code and display name; linked Google identities can sign in without a new invite. Classic email/password login and invite registration remain available. Existing password users can explicitly link a Google identity from Impostazioni → Profilo e account → Accesso e sicurezza; the app verifies the token and the backend requires the provider's verified email to match the current HaliMeo account.

Facebook Android sign-in is not yet wired. The Facebook App ID is configured, but the Facebook SDK also needs the app's **Client Token** in Android manifest metadata. The Facebook App Secret remains server-only. Before enabling Facebook, configure the package `it.halimeo.app` (debug builds use `it.halimeo.app.debug`), the release/debug signing fingerprints and the Android OAuth/Login settings in the provider consoles. Do not commit the Facebook App Secret or signing keystore. The social endpoint rejects unconfigured providers with HTTP 503 rather than weakening authentication.

## Database

On startup the API creates the `social_identities` table if it does not exist. Existing users, password hashes, invite records and sessions are not rewritten.
