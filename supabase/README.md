# Supabase setup (native Android app)

## 1. Create the project
- Go to https://supabase.com/dashboard → **New project**.
- Pick a name (e.g. `financetracker-native`), a strong DB password, and any region.
- Wait for provisioning (~1–2 min).

## 2. Apply the schema
- Open the project → **SQL Editor** → **New query**.
- Paste the entire contents of `001_schema.sql` and click **Run**.
- Run the verification queries at the bottom of the file to confirm tables, RLS, and the signup trigger.
- **Existing v1 deployments** (schema created before the offline sync phase): run
  `002_sync_columns.sql` instead — it adds `updated_at` columns, the update
  triggers, and the `profiles` table without dropping your data.

## 3. Connect the app
- Project **Settings → API**:
  - `Project URL` → `SUPABASE_URL`
  - `anon public` key → `SUPABASE_ANON_KEY`
- The anon key is NOT a secret (it is protected by Row Level Security — it can only
  touch rows where `auth.uid() = user_id`). It gets compiled into the APK either
  way, so the real hygiene rule is: **don't commit it to git**.
  → Put both values in `local.properties` (already gitignored) or in your
  user-level `~/.gradle/gradle.properties`, NOT the project's `gradle.properties`:
  ```properties
  supabaseUrl=https://YOUR-PROJECT-REF.supabase.co
  supabaseAnonKey=YOUR-ANON-KEY
  ```
- Rebuild the app. Empty values = guest-only mode (everything keeps working offline).
- Never put the `service_role` key or the database password anywhere in the app.

## 4. Google sign-in
- Supabase: **Authentication → Providers → Google** → enable, add your Google Cloud OAuth client ID + secret.
- Google Cloud Console → **Credentials → OAuth Client ID → Android** (one client can hold
  several SHA-1s). The app package is **`com.joeabouserhal.financetracker`** — if the
  client was created with the old template package (`com.example.financetracker`), edit or
  recreate it. Register BOTH signing fingerprints:

  - **Debug builds** — SHA-1 of the debug keystore:
    ```
    keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
    ```
  - **Release builds** — SHA-1 of `keystore/finance-tracker-release.jks`
    (alias `finance-tracker`, passwords in `local.properties`):
    ```
    keytool -list -v -keystore keystore/finance-tracker-release.jks -alias finance-tracker
    ```
    Current release SHA-1: `bb21ce9bedb97200bb9824140ff402eea80f8f57`

  If the console's Android client doesn't offer adding a second fingerprint,
  **create a second OAuth Client ID → Android** with the same package name and
  the release SHA-1 — Google allows multiple Android clients per package (one
  per signing key). Do NOT remove the existing fingerprint unless that
  keystore no longer exists.
- Put the **Web client ID** (the OAuth client Supabase uses) in `local.properties`:
  ```properties
  googleServerClientId=YOUR-WEB-CLIENT-ID.apps.googleusercontent.com
  ```
- Symptom check: "Google sign-in couldn't find a usable account" (while the device has a
  Google account) almost always means the package/SHA-1 above is not registered in Google
  Cloud Console — not that the device lacks an account.

## 5. Two-device smoke test checklist (final release)
Install `app/build/outputs/apk/release/app-release.apk` on two devices (release build = R8-shrunk + signed).

1. **Sign-in pull** — Device A: sign in (Google or email). Dashboard should show the seeded USD/Cash account and 12 categories within seconds. Settings → SYNC shows ONLINE pill, 0 pending, a LAST SYNC time.
2. **Offline queue** — Device A: airplane mode → add a transaction → Settings shows OFFLINE pill and "1 change waiting to sync" → reconnect → pending returns to 0 and last-sync updates.
3. **Second device pull** — Device B: sign in with the same account → the transaction from step 2 appears after the first pull.
4. **Conflict** — edit the same transaction on both devices while B is offline; reconnect B → the last push wins (LWW).
5. **Guest isolation** — on Device B: sign out → sync stops, guest partition data (if any) is untouched, user rows disappear from the dashboard. Continue-as-guest never touches the network.
6. **Release sanity** — the flows above also verify R8 did not strip auth, supabase-kt, kotlinx-serialization, or Room in the release APK.

Known follow-up (dashboard setting, not configurable via API): enable **Leaked password protection** in Supabase → Authentication → Security.
