# HaliMeo authentication provider setup

The password + invite registration flow remains available. Social sign-in is implemented server-side at `POST /v1/auth/social/{provider}`; the Android API contract is exposed as `HaliMeoApi.socialLogin`.

## Required server configuration

Set these values in the server's private `.env` (never commit secrets):

- `HALIMEO_GOOGLE_CLIENT_ID`: the OAuth **Web application** client ID used as the audience for Google ID tokens.
- `HALIMEO_FACEBOOK_APP_ID`: Facebook app ID.
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

## Important client setup still required

The server and Retrofit contracts (social login and authenticated linking) are in place, but provider-specific Android SDK sign-in buttons are not yet wired. They must not be enabled until the app's Google OAuth client ID, Facebook app ID, package/signing fingerprints and provider console settings are configured. These values are deployment-specific and are intentionally not guessed or committed. The social endpoint rejects unconfigured providers with HTTP 503 rather than silently weakening authentication.

## Database

On startup the API creates the `social_identities` table if it does not exist. Existing users, password hashes, invite records and sessions are not rewritten.
