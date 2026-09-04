# Finance Tracker

Finance Tracker is a native Android app for recording income, expenses, accounts, categories, presets, and financial goals. It is designed as an offline-first app: day-to-day actions are stored locally first, remain available without a connection, and synchronize with the signed-in user's Supabase account when the network returns.

The interface uses a modern brutalist visual style and is built entirely with Jetpack Compose.

## Features

- Track income and expenses across multiple accounts and currencies.
- Organize transactions with categories, notes, dates, and reusable presets.
- Review recent activity, reports, spending and earning breakdowns, and goals.
- Create and update data while offline.
- Synchronize signed-in data through Supabase when connectivity is available.
- Continue in guest mode without configuring or signing in to Supabase.
- Keep guest data separate from every signed-in account. Signing in does not upload guest records.
- Resolve retries and cross-device changes through a durable local sync outbox, tombstones, and versioned mutations.

## Themes

Open **Settings > Themes** to choose an appearance. Alongside the original brutalist themes, the app includes adaptations of [One Dark](https://github.com/atom/one-dark-syntax), [Dracula](https://github.com/dracula/dracula-theme), and [Catppuccin](https://github.com/catppuccin/catppuccin) Mocha, Macchiato, Frappé, and Latte.

Themes apply immediately and are saved locally across restarts. They work offline, do not change category colors or account data, and preserve the app's layout and thin borders. Supporting colors are adapted for text contrast. Follow system continues to switch between the original dark and light themes.

MIT notices are bundled in the APK and available under **Themes > Theme credits & licenses**. The same notices and source links are in [theme_licenses.txt](app/src/main/res/raw/theme_licenses.txt).

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Room for local storage
- DataStore for local preferences and session-related state
- WorkManager for background synchronization and retry scheduling
- Supabase Auth and PostgREST for account-backed synchronization
- Android Credential Manager and Google Identity for Google sign-in

## Requirements

- Android Studio with support for Android Gradle Plugin 9.0.1
- JDK 17
- Android SDK 36
- An Android device or emulator running Android 7.0 (API 24) or newer
- Internet access during the first build so Gradle can download dependencies
- Optional: a Supabase project for authentication and cloud synchronization
- Optional: ADB for command-line installation and device testing

The repository includes the Gradle wrapper, so a separate Gradle installation is not required. The current wrapper uses Gradle 9.1.0.

## Getting started

1. Clone the repository and open its root directory in Android Studio.
2. Install Android SDK 36 if Android Studio prompts for it.
3. Ensure Gradle uses JDK 17.
4. Create or update `local.properties` in the repository root.
5. Sync the Gradle project, then run the `app` configuration on a device or emulator.

## `local.properties`

At minimum, `local.properties` must point to the local Android SDK. Use forward slashes in Windows paths to avoid Java properties escaping issues.

```properties
# Windows example
sdk.dir=C:/Users/you/AppData/Local/Android/Sdk

# macOS example
# sdk.dir=/Users/you/Library/Android/sdk

# Linux example
# sdk.dir=/home/you/Android/Sdk

# Optional: enables Supabase authentication and synchronization
supabaseUrl=https://YOUR_PROJECT_REF.supabase.co
supabaseAnonKey=YOUR_CLIENT_SAFE_PUBLISHABLE_OR_ANON_KEY

# Optional: enables Google sign-in
googleServerClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com

# Optional: required only for a locally signed release build
releaseKeystorePath=keystore/finance-tracker-release.jks
releaseKeystorePassword=YOUR_STORE_PASSWORD
releaseKeyAlias=YOUR_KEY_ALIAS
releaseKeyPassword=YOUR_KEY_PASSWORD
```

The build also accepts these values as Gradle project properties. A value passed with `-P`, or defined in an applicable `gradle.properties`, takes precedence over `local.properties`.

Important security notes:

- Only use a client-safe Supabase publishable or legacy anonymous key in the Android app. Keep the property name `supabaseAnonKey` as shown above.
- Never place a Supabase service-role key, secret key, database password, Google OAuth client secret, or other privileged credential in this repository or in the Android client.
- `local.properties` and the `keystore/` directory are excluded from Git.
- If the Supabase values are blank or omitted, the app still builds and runs in guest-only mode.
- `googleServerClientId` is optional unless Google sign-in is required.

## Building the app

From PowerShell or Command Prompt on Windows:

```powershell
.\gradlew.bat assembleDebug
```

From macOS or Linux:

```bash
./gradlew assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

To build and install the debug app directly on a connected device or running emulator:

```powershell
.\gradlew.bat installDebug
```

Alternatively, install the generated APK with ADB:

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release builds

Create a release APK with:

```powershell
.\gradlew.bat assembleRelease
```

The output is written under `app/build/outputs/apk/release/`. Release builds enable code minification and resource shrinking.

Release signing is configured only when all four `releaseKeystore...` properties shown above are present. Without them, Gradle can produce an unsigned release artifact for local inspection, but it cannot be installed or distributed as a normally signed production APK.

Do not commit a production keystore or its passwords. Store them in a secure credential manager or a protected CI environment.

## Running tests

Run local unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run Android instrumentation tests on a connected device or emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The test suite covers core repositories, Room migrations and DAOs, authentication behavior, synchronization, filtering, formatting, theme data, and view-model behavior. Instrumentation tests cover key Compose UI behavior.

For changes to offline storage or synchronization, also test this sequence manually:

1. Sign in and confirm existing server data has synchronized.
2. Enable airplane mode.
3. Create, edit, and delete records in each applicable section.
4. Force-stop and reopen the app while still offline and verify the changes remain.
5. Restore connectivity and wait for synchronization.
6. Restart the app and verify that no duplicate or deleted records reappear.
7. If possible, open the same account on a second client and verify that changes converge in both directions.
8. Sign out and verify that guest data and account data remain isolated.

## Supabase setup

Supabase is optional for guest-only development. Account authentication and synchronization require a compatible Supabase project.

See [`supabase/README.md`](supabase/README.md) for backend setup, Google OAuth configuration, security checks, and multi-device testing guidance.

Database safety and repository conventions:

- `supabase/001_schema.sql` is a destructive baseline intended for a new or disposable project. It drops and recreates database objects. Do not run it against a project containing data.
- Review all checked-in schema files and compare them with the target project before provisioning a backend. The numbered files after `001_schema.sql` are retained as historical scripts.
- Going forward, apply live schema changes through the Supabase tooling and fold the resulting source-of-truth definition back into `supabase/001_schema.sql`. Do not add new numbered incremental SQL files to this repository.
- Validate Row Level Security, ownership checks, functions, grants, triggers, and indexes after every backend change.
- Enable leaked-password protection in the Supabase Auth settings for deployed projects.

## Google sign-in

Google sign-in requires coordinated configuration in Google Cloud, Supabase, and the Android build:

- Create a Web OAuth client and place its client ID in `googleServerClientId`.
- Configure the same Web client credentials in the Supabase Google auth provider.
- Create an Android OAuth client for package name `com.joeabouserhal.financetracker`.
- Add the SHA-1 fingerprints for every certificate used to sign builds that need Google sign-in, including the debug certificate during development.

If the account chooser reports that no credentials are available, first verify the package name, SHA-1 fingerprint, Web client ID, and enabled Supabase provider.

## Offline-first behavior

Room is the app's immediate source of truth. User CRUD actions commit locally and do not wait for the network. Signed-in mutations are recorded in a durable outbox and later sent by WorkManager. Pulls update the local account partition after validation, while version information and deletion tombstones help prevent stale clients from resurrecting old data.

Background synchronization is network-constrained and scheduled periodically, with additional requests after events such as sign-in or reconnection. Transient failures use backoff and retry. Authentication or validation problems can require user action.

Guest records remain in a separate local partition and are not automatically migrated or uploaded after sign-in.

## Project structure

```text
app/
  src/main/                 Android application code and resources
  src/test/                 Local unit tests
  src/androidTest/          Device and emulator tests
  schemas/                  Exported Room database schemas
supabase/
  001_schema.sql            Destructive baseline for disposable projects
  README.md                 Supabase and auth setup notes
gradle/                     Gradle wrapper and version catalog
AUDIT.md                    Offline-first and synchronization audit notes
```

## Development notes

- Monetary values are stored as integer minor units where applicable. Avoid floating-point arithmetic for persisted money.
- Preserve Room schema exports and add explicit migrations whenever the database version changes. Do not rely on destructive migration for user data.
- Every new CRUD path should remain usable offline and should update local data and its sync metadata atomically.
- User-facing queries should continue to exclude internal deletion tombstones.
- Test compound operations, retries, process restarts, account switching, and stale cross-device edits when modifying synchronization.
- Keep secrets, generated APKs, build directories, IDE state, and local signing material out of version control.
