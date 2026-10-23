# Security, reliability and performance hardening

## Implemented

- Local logical actions now share immutable outbox batch IDs and indices. Nested goal, category, default-selection and recurring calls retain the same action identity. Room data and queued operations commit together.
- Supabase `apply_sync_batch` applies a complete action in one transaction. Ownership, references, defaults and versions are checked; stale compound actions lose as a whole. Acknowledgments are checked against every operation before Room applies all canonical results and removes the complete batch atomically.
- Permanent failures are quarantined. Transient failures retain attempt/error details and durable retry times with bounded exponential backoff. Options exposes affected records, Retry and confirmed Discard. Discard keeps local records but stops uploading that action; the warning explains potential device differences.
- Sync health retains separate successful push/pull timestamps and resolved-conflict counts. Periodic sync runs only for signed-in partitions; guest changes are never uploaded.
- Recurring schedule and entity upserts use updates rather than SQLite's destructive replacement behavior. Schedule edits and pauses preserve occurrence history.
- Generated transactions use deterministic owner/occurrence/generation IDs. Snooze advances the generation rather than trying to resurrect a deleted posting. Existing pre-upgrade random IDs are reconciled to the server's canonical record. Database uniqueness prevents duplicate generations across devices.
- Every catch-up occurrence has its own atomic remote batch. A conflict on one occurrence cannot discard unrelated missed occurrences. Local progress avoids rescanning already processed history, while snoozes remain independently discoverable. Calendar iteration jumps to applicable intervals instead of walking every historical day.
- Recurring times resolve the selected local wall-clock time across daylight-saving changes. Invalid preset references or rules pause the affected schedule rather than continually retrying the entire worker. Stale Verify actions cannot consume snoozed occurrences, and Skip does not overwrite posted entries.
- Notification channels are guarded on Android 7; notification permission is checked explicitly, and revocation cannot turn an already committed transaction into a worker failure. Wake-up and processing work have separate identities so scheduling the next reminder does not cancel the running processor.
- Currency deletion explicitly checks transaction, preset and goal usage before either guest deletion or signed-in tombstoning. Empty dependent accounts and default promotion are queued in the same action. Ordinary transactions, transfers, presets and goals validate their references locally within the mutation transaction.
- Database owner-aware foreign keys protect direct authenticated writes as well as sync RPCs. Deferred link validation protects account currencies, category types and recurring lineage. Partial unique indexes enforce at most one active default; batch validation requires available defaults. The live signup trigger now matches the canonical default-account/profile setup.
- Android backup and device transfer exclude application data, with backup disabled in the manifest as well. Guest data remains device-only; uninstalling or losing the phone removes it. Credentials, pending operations and device clock identity are not restored to another installation.
- Sign-out clears local Supabase credentials even when remote revocation fails. Authentication cancellation is propagated, expired refresh sessions are classified as requiring sign-in, and sign-in re-enables previously auth-blocked operations.
- Transaction filters and modal state reset with the active partition. Report reset writes no longer occur during composition.
- Dashboard balances aggregate in SQLite and recent rows use a bounded query. Normal report periods query only the current/comparison windows and calculate in the background. Transactions load bounded pages after database filtering/sorting; Unicode-aware search remains background Kotlin matching before limiting. Account Details queries only that account and uses typed lookup state rather than unchecked casts.
- AGP is 9.1.1 with Gradle 9.3.1 and a verified distribution checksum. Minified release builds no longer produce the prior Kotlin metadata warnings. A newly surfaced older-Android BigInteger compatibility problem was corrected without removing overflow protection.

## Verification

- 161 unit tests passed, including real Room migrations through schema 16, queued-batch preservation, recurring history/retry/snooze, DST, sparse calendar intervals, partition checks, currency deletion guards, paging and Unicode search.
- Debug and minified release APKs build successfully. Lint has 0 errors; existing non-blocking warnings remain.
- Live Supabase tests used disposable users inside transactions that were fully rolled back. Checks covered atomic rejection, idempotent retry, RLS, direct cross-owner references, defaults, creation timestamps, canonical recurring IDs, duplicate offline posting, snooze/repost, stale updates, pause races and in-use currency deletion.
- Supabase advisors reported no new security issues or missing-FK-index findings. Existing unused indexes were retained. Leaked-password protection remains a dashboard configuration item.
- The connected Galaxy S23+ upgraded without clearing data. Debug and minified code launched with the existing account, and Options reported Up to date with no pending changes. Dashboard, Transactions and Reports loaded after the query changes; month navigation was checked. No screenshots were created.
- The minified smoke APK was signed with the installed development certificate to preserve app data. The production release APK retains its configured release signature.

## Remaining limits and follow-up

- Enable Supabase Auth leaked-password protection in the dashboard. The available MCP tools cannot change this Auth setting.
- All-time reports and text searches still inspect matching local history off the main thread. Very large account/category-detail histories would benefit from further SQL aggregates and dedicated paging. No benchmark-derived speed or memory claim is made.
- No destructive backup/restore, account switching/sign-out, or automated connected-test suite was run against the user's personal phone data. Fresh Google sign-in, denied notification permission and multi-device UI journeys still need disposable-device/user checks; server convergence and local repository paths were tested separately.
- Existing already-queued legacy operations retain singleton batch identities; newly created compound actions use the atomic protocol. Historical partially applied actions cannot be retroactively made atomic.
- Best-effort WorkManager timing and notification delivery are still subject to Android battery/permission policy.

All schema deployment used Supabase MCP and the existing `supabase/001_schema.sql`. No additional SQL migration files were created, and production financial rows were not deleted or rewritten by these migrations.
