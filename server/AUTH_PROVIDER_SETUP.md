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

Google sign-in and explicit account linking use Android Credential Manager and Google ID tokens. The app first offers any Google account on the device and falls back to the explicit **Sign in with Google** credential flow if Credential Manager reports no credential. Register the app's signing-certificate SHA-1 fingerprint in Google Cloud Console as an Android OAuth client for package `it.halimeo.app.debug` (the package ID used by the Alpha APK). The workflow prints this SHA-1 fingerprint without exposing the private keystore. Keep the Web application client ID configured in the app and server. New-account Google registration still requires an invite; linked identities can sign in without another invite. Classic email/password login remains available.

Facebook login and explicit account linking use the Facebook Android SDK. In Meta Developers → Facebook Login → Settings, register package `it.halimeo.app.debug`, enable **Client OAuth Login** and **Web OAuth Login** as applicable, and add the Android Key Hash printed by the Alpha workflow. The workflow prints the Base64 SHA-1 Android key hash derived from the persistent Alpha signing certificate. The Facebook Client Token is in Android manifest metadata; the Facebook App Secret remains server-only. Never commit the App Secret or signing keystore. The Facebook app must also be in the appropriate Live/development mode and the testing account must have a role if the app is not Live.

For local builds signed with a different certificate, register that certificate's key hash separately. The published Alpha APK is re-signed with the persistent Alpha key, so use the hash printed by the workflow for the APK distributed through Alpha releases.

## Database

On startup the API creates the `social_identities` table if it does not exist. Existing users, password hashes, invite records and sessions are not rewritten.
