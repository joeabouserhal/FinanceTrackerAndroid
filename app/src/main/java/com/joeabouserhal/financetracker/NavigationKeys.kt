package com.joeabouserhal.financetracker

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Tab host (Dashboard / Transactions / Presets / Categories / Settings). */
@Serializable data object Main : NavKey

/** Auth flow: Email / Google / Continue as guest. */
@Serializable data object AuthFlow : NavKey

/** Add a new transaction (optionally pre-filled from a preset). */
@Serializable data class AddTransaction(val presetId: String? = null) : NavKey

/** Preset picker for "add from preset" (pushed from the FAB menu). */
@Serializable data object PresetPicker : NavKey

/** Edit an existing transaction. */
@Serializable data class EditTransaction(val transactionId: String) : NavKey

/** Currencies & accounts management (pushed from Settings). */
@Serializable data object CurrenciesAccounts : NavKey

/** Theme picker (pushed from Options). */
@Serializable data object Themes : NavKey

/** Category management (pushed from Options). */
@Serializable data object Categories : NavKey

/** Preset templates (pushed from Options). */
@Serializable data object Presets : NavKey

/** Completed goals list (pushed from the Goals tab). */
@Serializable data object CompletedGoals : NavKey

/** One account's detail: balance, monthly totals, its transactions. */
@Serializable data class AccountDetail(val accountId: String) : NavKey
