# HaliMeo authentication provider setup

HaliMeo supports classic email/password accounts and Google/Facebook sign-in. Every new account is created first in a **pending activation** state; the user must then redeem one unused, unexpired invitation before any protected app feature becomes available.

## Required server configuration

Set these values in the server's private `.env` (never commit secrets):

- `HALIMEO_GOOGLE_CLIENT_ID`: `609968712989-909cckera569ft2g5ivrp79obp7ij4hn.apps.googleusercontent.com` (OAuth **Web application** client ID; also configured in the Android build).
- `HALIMEO_FACEBOOK_APP_ID`: `2135436027084791` (also configured in the Android build).
- `HALIMEO_FACEBOOK_APP_SECRET`: Facebook app secret. Server-side only; never put it in the APK.

Restart the API after changing environment variables. Google verification uses Google's ID-token verifier and checks issuer, audience and `email_verified`. Facebook verification checks the token with Graph API's `debug_token`, checks that it belongs to the configured app, then retrieves the provider account's ID, name and email.

## Registration and activation

- Classic registration: `POST /v1/auth/register` with `email`, `password`, and `displayName`. The server creates a pending account and returns a session token with `activated: false`.
- Social registration/sign-in: `POST /v1/auth/social/google` or `POST /v1/auth/social/facebook`. A new provider identity creates a pending account and returns `activated: false`; an already-linked active identity signs in normally, without another invitation. A previously created pending social account remains pending when it signs in again.
- Activation: `POST /v1/auth/redeem-invite` with `Authorization: Bearer <pending-session-token>` and JSON `{"inviteCode":"..." }`. The server atomically claims the unused invitation and activates that account.
- A pending account's token is accepted only by the activation endpoint. All diary, profile, sync, invite-management and other protected endpoints reject it until activation succeeds.

Invitations are single-use and expire. Creating the account does not consume an invitation; the invitation is consumed only after successful redemption.

## Account linking and duplicate protection

A previously linked provider identity can sign in without another invite. If the email already belongs to a password account, the server deliberately refuses automatic linking; this avoids account takeover through email collisions. An existing password account can explicitly link a provider after signing in with its password by calling `POST /v1/auth/social/{provider}/link`; the provider's verified email must match the account email. An identity already linked to a different HaliMeo account is rejected.

## Account-bound profile and memories

Profile nickname, description and profile photo belong to the authenticated user record. The Android app keeps local profile-photo selections in preferences scoped to that user ID; logging out ends the profile session, and signing in with another account loads that account's profile instead.

Diary entries keep their original `owner_id`. Only the creator may edit or delete an entry or change/remove its photo. The API enforces this on sync and photo endpoints, not only in the Android interface. Existing user records are migrated as already active; existing diary owners and invite records are preserved.

## Android client status

Google sign-in and explicit account linking use Android Credential Manager and Google ID tokens. The app first offers any Google account on the device and falls back to the explicit **Sign in with Google** credential flow if Credential Manager reports no credential. Register the app's signing-certificate SHA-1 fingerprint in Google Cloud Console as an Android OAuth client for package `it.halimeo.app.debug` (the package ID used by the Alpha APK). The workflow prints this SHA-1 fingerprint without exposing the private keystore. Keep the Web application client ID configured in the app and server.

Facebook login and explicit account linking use the Facebook Android SDK. In Meta Developers → Facebook Login → Settings, register package `it.halimeo.app.debug`, enable **Client OAuth Login** and **Web OAuth Login** as applicable, and add the Android Key Hash printed by the Alpha workflow. The workflow prints the Base64 SHA-1 Android key hash derived from the persistent Alpha signing certificate. The Facebook Client Token is in Android manifest metadata; the Facebook App Secret remains server-only. Never commit the App Secret or signing keystore. The Facebook app must also be in the appropriate Live/development mode and the testing account must have a role if the app is not Live.

For local builds signed with a different certificate, register that certificate's key hash separately. The published Alpha APK is re-signed with the persistent Alpha key, so use the hash printed by the workflow for the APK distributed through Alpha releases.

## Database

On startup the API creates the `social_identities` table if it does not exist and adds the activation/profile columns to existing `users` tables with safe defaults. Existing users remain active; existing password hashes, invite records, social identities and sessions are not rewritten.
