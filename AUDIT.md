# App Audit

Living record of code/behavior audits. Phase 1 = CRUD & data integrity.

## Phase 1 — CRUD / data-integrity audit (done)

### Verified sound (no change needed)
- **Atomic writes**: every repository mutation writes the Room row(s) and its outbox op inside the same `db.withTransaction` — a crash can never leave the DB and the pending-sync queue out of sync.
- **FK-safe sync order**: outbox is drained parents-first (currencies → categories → accounts → presets → transactions → profiles).
- **Guest isolation**: `OutboxWriter.enqueue` skips the guest owner, so offline guest data never touches the network; guest mutations are local-only.
- **Validation**: positive amounts, required names/codes, duplicate name/code checks, "Other" fallback resolution for transactions, last-currency / last-account protections, undeletable seeded "Other".
- **Defaults invariant**: exactly one default currency (promote on delete) and one default account per currency (first account auto-default, promote on archive/delete, sync-side enforcement with `clearDefaultExceptForCurrency`).
- **Category delete**: transactions are reassigned to the seeded "Other" and both changes are queued for sync.
- **Profile**: `setName` preserves `created_at`; guest "Guest" profile seeded once, idempotently.

### Fixed
1. **Account rename synced too much** (`AccountRepository.update`): renaming one account enqueued an UPDATE for *every* account of both the old and new currency, even unchanged ones — wasted network calls and LWW tie pressure. Now only the actually-changed rows are enqueued (the renamed row and, if the move changed defaults, the promoted row).
2. **Editing a preset-created transaction silently lost its preset lineage**: the edit form always passes `presetId = null`, which overwrote the stored `preset_id`. Now a null `presetId` keeps the existing lineage; only an explicit value changes it.

### Regression tests added
- `editing a transaction keeps its preset lineage when presetId is null`
- `renaming an account enqueues only the changed row`

## Phase 2 — sync correctness audit (done)

### Verified sound (no change needed)
- **Push-before-pull order**: local edits are pushed before pulling, so an offline edit is never overwritten by the older server copy in the same run.
- **Watermark discipline**: the keyset cursor only advances after a fully-applied page; a failed pull leaves its watermark untouched (regression-tested).
- **FK-safe drain order** and **per-table key/conflict columns** (including `profiles` keyed by `user_id`).
- **Guest never syncs**; missing API config and missing session both skip cleanly.
- **Auth session**: `ensureAuthenticated` only refreshes when there is no in-memory session — no redundant refresh per run.
- **Worker scoping**: the worker re-reads the session at run time, so queued work after sign-out can never sync a stale partition.

### Fixed
1. **Infinite retry loop for permanently failing pushes**: a bad op (server-side FK rejection, etc.) used to retry forever and keep the worker in `retry()` — burning battery and masking other syncs. Ops now stop being attempted after `MAX_PUSH_ATTEMPTS` (10); they are counted as `deadOps`, kept locally (no data loss), and no longer force the worker to retry.
2. **Invisible sync failures**: the last completed sync outcome is now exposed on `SyncEngine.latestOutcome`, and the Options → SYNC block shows:
   - "N changes waiting to sync" (live outbox count)
   - "N changes couldn't sync — kept on this device" (dead ops)
   - "Last sync hit a problem — it will retry automatically" (live failures/pull errors)

### Regression test added
- `permanently failing op is given up after the attempt cap and no longer blocks retries`

### Documented limitation (by design, revisit if multi-device becomes real)
- **Deletes made on another device don't propagate to this one.** Push/pull has no tombstone channel: a hard-deleted row simply disappears from the server, and pull never deletes local rows. Archivable entities (accounts, presets) already propagate through their `archived` flag; transactions and currencies do not. Fixing this properly requires a `deleted_rows` tombstone table + triggers (see the Phase 2 plan) — deliberately deferred to avoid destabilizing sync for a single-device setup.

### LWW note
- Client-generated `Instant.now()` timestamps mean two devices with skewed clocks editing the same row concurrently can disagree about "newest". Push-before-pull makes the common single-device case safe; multi-device concurrent editing remains best-effort.

## Phase 3 — performance & state retention (done)

### Fixed
1. **Dashboard aggregation was O(accounts × transactions)** — every recomposition filtered the entire transaction list once per account, and again per currency for the month activity. Both are now single-pass hashmap aggregations, so the dashboard stays instant as history grows.
2. **Tab switches wiped page state** — Transactions' search/filters and Report's filters reset every time you changed tabs because the tab content was disposed without a `SaveableStateHolder`. Each tab now keeps its `rememberSaveable` state across switches (same class of bug as the old Presets filter reset).

### Regression coverage
- The existing `dashboard derives per-account and per-currency balances` test still passes against the rewritten single-pass aggregation (identical numbers, cheaper path).

## Phase 4 — refactoring (done)

### What changed (behavior-preserving; full suite green)
1. **One color parser**: all duplicated hex parsers now delegate to `utils/Colors.parseHexColor` (missing-`#` tolerant, gray fallback). The small wrappers (`parseCategoryColor`, `parseColor`, `parsePresetColor`) remain so call sites stay readable.
2. **One preset row**: the identical preset row in Presets and the Add-from-preset picker is now `ui/presets/PresetRowView`.
3. **Dashboard slimmed**: the year/month picker moved to `ui/dashboard/MonthPickerDialog.kt`; `DashboardScreen.kt` keeps only the dashboard sections.
4. **Categories slimmed**: swatches, rainbow tile, and the color-wheel dialog moved to `ui/categories/ColorPickers.kt`; `CategoriesScreen.kt` keeps the list + category dialog.
5. **FilterPanel/Navigation** intentionally left as-is: FilterPanel is already single-purpose after the Phase 3 extraction; further splitting was judged risk > reward for now.

## Phase 5 — robustness & UX hardening (done)

### Changed
1. **Activity bars**: thicker (16dp) and each bar now shows its share at the edges — expense % on the left (red), income % on the right (green), always summing to 100.
2. **Comma decimals accepted**: amount inputs now accept "12,50" style input (normalized to "12.50") in Add/Edit Transaction and the preset amount field, instead of failing validation.

### Final reminders
- **Keystore**: `keystore/finance-tracker-release.jks` is gitignored and NOT backed up by git — back it up manually (SHA-1 `bb21ce…f57`).
- **Schema discipline**: every Room schema change needs a version bump + `Migrations` entry + a hand-rolled migration test + a matching `supabase/00N_*.sql` applied via MCP.
- **Guest seed**: idempotent (inserts only when the guest partition is empty); guest data is never synced.
- **Deferred (documented)**: multi-device hard-delete propagation needs tombstone tables — revisit if a second device ever joins.
