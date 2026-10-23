package com.joeabouserhal.financetracker.ui.transactions

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.joeabouserhal.financetracker.ui.components.*
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.data.repositories.enrichTransactions
import com.joeabouserhal.financetracker.utils.Dates
import com.joeabouserhal.financetracker.utils.TransactionCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportTransactionsDialog(filters: TransactionFilterState = TransactionFilterState(), onDismiss: () -> Unit) {
  val container = rememberAppContainer()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var exportOwner by rememberSaveable { mutableStateOf<String?>(null) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    if (uri == null) { busy = false; return@rememberLauncherForActivityResult }
    val owner = exportOwner
    scope.launch {
      try {
        check(owner != null && container.sessionManager.session.first().ownerId == owner) { "Profile changed. Start the export again." }
        val count = withContext(Dispatchers.IO) {
          val db = container.appDatabase
          val items = db.withTransaction {
            TransactionFiltering.apply(enrichTransactions(db.transactionDao().getAll(owner),
              db.categoryDao().getAll(owner), db.accountDao().getAll(owner), db.currencyDao().getAll(owner)), filters)
          }
          check(container.sessionManager.session.first().ownerId == owner) { "Profile changed. Start the export again." }
          requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Unable to open this location." }
            .bufferedWriter(Charsets.UTF_8).use { writer ->
              writer.write("\uFEFF") // Excel recognizes UTF-8 currency symbols and names.
              TransactionCsv.write(items, writer)
            }
          items.size
        }
        Toast.makeText(context, "Exported $count transactions", Toast.LENGTH_LONG).show()
        onDismiss()
      } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Exception) { error = e.message ?: "Export failed. Choose a location and try again." }
      finally { busy = false }
    }
  }
  BrDialog(title = "EXPORT TRANSACTIONS", onDismiss = { if (!busy) onDismiss() }, dismissText = if (busy) null else "CANCEL") {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(if (filters.isDefault()) "Export all transactions in your current profile." else "Export all transactions matching the current search and filters, including entries not yet loaded on screen.")
      Text("CSV files are not encrypted. Amounts are included even when hidden in the app. Transfers are one row with both accounts; expenses are negative.")
      error?.let { Text(it) }
      BrButton(if (busy) "EXPORTING…" else "CHOOSE SAVE LOCATION", enabled = !busy, onClick = {
        busy = true; error = null
        scope.launch {
          exportOwner = container.sessionManager.session.first().ownerId
          launcher.launch("FinanceTracker-${Dates.todayIso()}.csv")
        }
      })
    }
  }
}
